#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

required=(PA_AGENT_TOKEN CODEX_API_KEY WECOM_CORP_ID WECOM_AGENT_ID WECOM_SECRET WECOM_TOKEN WECOM_ENCODING_AES_KEY WECOM_DEFAULT_CODEX_AGENT_ID WECOM_ALLOWED_USERS)
missing=()
for name in "${required[@]}"; do
  value=$(grep -E "^${name}=" .env | tail -n 1 | cut -d= -f2- || true)
  if [[ -z "$value" ]]; then missing+=("$name"); fi
done

if ((${#missing[@]})); then
  echo "缺少环境变量: ${missing[*]}"
  exit 1
fi

docker compose --env-file .env config --quiet
docker compose --env-file .env build codex-agent backend
docker compose --env-file .env run --rm --no-deps codex-agent codex --version

echo "配置检查通过。企业微信回调地址应为："
echo "https://你的域名/api/public/wecom/callback"
