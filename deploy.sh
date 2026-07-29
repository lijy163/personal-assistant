#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
DEPLOY_DIR="$PROJECT_DIR/deploy"
ENV_FILE="$DEPLOY_DIR/.env"
BRANCH="${DEPLOY_BRANCH:-master}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/api/system/health}"

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

cd "$PROJECT_DIR"

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  fail "服务器 Git 工作区存在已跟踪文件改动，请先处理后再发布"
fi

compose() {
  docker compose --project-directory "$DEPLOY_DIR" \
    --env-file "$ENV_FILE" \
    -f "$DEPLOY_DIR/docker-compose.yml" "$@"
}

if compose ps --status running --services 2>/dev/null | grep -qx backend; then
  log "备份当前数据库和应用文件"
  compose exec -T backend /app/scripts/backup.sh
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
  fail "拉取 origin/$BRANCH 失败。建议将 origin 切换为 ssh.github.com:443 后重试"
}

fetch_origin

log "校验 Docker Compose 配置"
compose config --quiet

log "构建并启动服务"
compose up -d --build

log "等待后端健康检查"
for attempt in $(seq 1 60); do
  if curl --fail --silent --show-error "$HEALTH_URL" >/dev/null 2>&1; then
    compose ps
    log "发布完成，后端健康检查通过"
    exit 0
  fi
  sleep 2
done

compose ps
compose logs --tail=100 backend >&2
fail "后端在 120 秒内未通过健康检查"