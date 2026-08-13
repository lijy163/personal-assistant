#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
DEPLOY_DIR="$PROJECT_DIR/deploy"
ENV_FILE="$DEPLOY_DIR/.env"
BRANCH="${DEPLOY_BRANCH:-master}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/api/system/health}"
SKIP_GIT_PULL="${SKIP_GIT_PULL:-0}"

export DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-1}"
export COMPOSE_DOCKER_CLI_BUILD="${COMPOSE_DOCKER_CLI_BUILD:-1}"

log() {
  printf '\n[%s] %s\n' "$(date '+%F %T')" "$*"
}

fail() {
  printf '\n发布失败: %s\n' "$*" >&2
  exit 1
}

command -v git >/dev/null 2>&1 || fail "未安装 git"
command -v docker >/dev/null 2>&1 || fail "未安装 Docker"
command -v curl >/dev/null 2>&1 || fail "未安装 curl"
docker compose version >/dev/null 2>&1 || fail "未安装 Docker Compose 插件"
[ -f "$ENV_FILE" ] || fail "缺少 $ENV_FILE，请先从 .env.example 创建并配置"

env_value() {
  grep -E "^${1}=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true
}

export CODEX_DEBIAN_MIRROR="${CODEX_DEBIAN_MIRROR:-$(env_value CODEX_DEBIAN_MIRROR)}"
export CODEX_NPM_REGISTRY="${CODEX_NPM_REGISTRY:-$(env_value CODEX_NPM_REGISTRY)}"
CODEX_DEBIAN_MIRROR="${CODEX_DEBIAN_MIRROR:-http://mirrors.cloud.tencent.com}"
CODEX_NPM_REGISTRY="${CODEX_NPM_REGISTRY:-https://registry.npmmirror.com}"
export CODEX_DEBIAN_MIRROR CODEX_NPM_REGISTRY

cd "$PROJECT_DIR"

compose() {
  docker compose --project-directory "$DEPLOY_DIR" \
    --env-file "$ENV_FILE" \
    -f "$DEPLOY_DIR/docker-compose.yml" "$@"
}

print_diagnostics() {
  log "Docker Compose 服务状态"
  compose ps || true

  log "后端日志 backend（最近 200 行）"
  compose logs --tail=200 backend || true

  log "数据库日志 postgres（最近 80 行）"
  compose logs --tail=80 postgres || true

  log "Nginx 日志 nginx（最近 80 行）"
  compose logs --tail=80 nginx || true

  log "Codex Agent 日志（最近 100 行）"
  compose logs --tail=100 codex-agent || true
  compose logs --tail=100 codex-public-agent || true
}

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  fail "服务器 Git 工作区存在已跟踪文件改动，请先处理后再发布。临时热修后可用 git diff 查看差异。"
fi

if compose ps --status running --services 2>/dev/null | grep -qx backend; then
  log "备份当前数据库和应用文件"
  compose exec -T backend /app/scripts/backup.sh || fail "发布前备份失败"
else
  log "后端尚未运行，跳过发布前备份"
fi

GIT_FETCH_RETRIES="${GIT_FETCH_RETRIES:-5}"
GIT_LOW_SPEED_LIMIT="${GIT_LOW_SPEED_LIMIT:-100}"
GIT_LOW_SPEED_TIME="${GIT_LOW_SPEED_TIME:-300}"

fetch_origin() {
  local attempt delay
  for attempt in $(seq 1 "$GIT_FETCH_RETRIES"); do
    log "拉取 origin/$BRANCH（第 $attempt/$GIT_FETCH_RETRIES 次）"
    if git -c http.lowSpeedLimit="$GIT_LOW_SPEED_LIMIT" \
      -c http.lowSpeedTime="$GIT_LOW_SPEED_TIME" \
      fetch --prune origin "$BRANCH"; then
      git merge --ff-only FETCH_HEAD
      return 0
    fi
    if [ "$attempt" -lt "$GIT_FETCH_RETRIES" ]; then
      delay=$((attempt * 15))
      log "GitHub 网络不稳定，${delay} 秒后重试"
      sleep "$delay"
    fi
  done
  fail "拉取 origin/$BRANCH 失败。可先在服务器热修文件后用 SKIP_GIT_PULL=1 ./deploy.sh 临时发布；长期建议切换到国内代码仓库或配置 GitHub 加速。"
}

if [ "$SKIP_GIT_PULL" = "1" ]; then
  log "SKIP_GIT_PULL=1，跳过 git 拉取，使用服务器当前代码发布"
else
  fetch_origin
fi

log "校验 Docker Compose 配置"
compose config --quiet

log "构建服务镜像（BuildKit=${DOCKER_BUILDKIT}）"
log "Codex Agent 构建镜像：Debian=${CODEX_DEBIAN_MIRROR}，npm=${CODEX_NPM_REGISTRY}"
if ! compose build --progress=plain; then
  print_diagnostics
  fail "Docker 镜像构建失败"
fi

log "启动服务"
if ! compose up -d; then
  print_diagnostics
  fail "Docker Compose 启动失败"
fi

log "等待后端健康检查"
for attempt in $(seq 1 60); do
  if curl --fail --silent --show-error "$HEALTH_URL" >/dev/null 2>&1; then
    compose ps
    log "发布完成，后端健康检查通过"
    exit 0
  fi
  sleep 2
done

print_diagnostics
fail "后端在 120 秒内未通过健康检查"
