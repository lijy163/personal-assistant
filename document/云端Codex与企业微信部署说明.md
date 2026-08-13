# 云端 Codex 与企业微信部署说明

## 架构

企业微信消息进入 Spring Boot 回调接口，服务端创建 Codex 任务。`codex-agent` 容器主动领取任务，在云服务器仓库中运行 Codex CLI。只读任务直接运行；写入任务必须在企业微信回复确认，并从只读生产仓库本地克隆到独立任务目录执行，不修改当前生产目录。

## 消息命令

```text
问 personal-assistant 分析登录失败原因
改 personal-assistant 修复登录失败并运行测试
确认 任务ID
状态 任务ID
```

不填写项目标识时使用 `WECOM_DEFAULT_PROJECT_KEY`。只有 `WECOM_ALLOWED_USERS` 中的企业微信 UserId 可以创建任务。

## 服务器准备

1. 域名解析到服务器并配置 HTTPS。企业微信回调使用：

```text
https://你的域名/api/public/wecom/callback
```

2. 在网页“远程 Codex”创建一台“云服务器 Agent”，复制一次性 `pa_agent_` 令牌，并记录该电脑在数据库/网页中的 Agent ID。

3. 在 `deploy/.env` 配置：

```dotenv
PA_AGENT_TOKEN=pa_agent_一次性令牌
CODEX_API_KEY=OpenAI或兼容服务的APIKey
OPENAI_BASE_URL=https://api.openai.com/v1

WECOM_ENABLED=true
WECOM_CORP_ID=企业ID
WECOM_AGENT_ID=自建应用AgentId
WECOM_SECRET=自建应用Secret
WECOM_TOKEN=回调Token
WECOM_ENCODING_AES_KEY=回调EncodingAESKey
WECOM_DEFAULT_CODEX_AGENT_ID=云服务器Agent ID
WECOM_DEFAULT_PROJECT_KEY=personal-assistant
WECOM_ALLOWED_USERS=允许提问的企业微信UserId
```

多个微信用户用英文逗号分隔。所有密钥只保存在服务器 `.env`，不能提交 Git 或发送截图。

4. 检查并部署：

```bash
cd personal-assistant/deploy
chmod +x verify-codex-wecom.sh
./verify-codex-wecom.sh
docker compose --env-file .env up -d --build
docker compose logs -f codex-agent backend
```

Codex CLI 在容器中通过 `CODEX_API_KEY` 运行，不依赖桌面客户端。API Key 认证按对应 API 服务计费。

## 企业微信设置

1. 创建企业微信自建应用，记录企业 ID、AgentId 和 Secret。
2. 进入“接收消息”，设置回调 URL、Token、EncodingAESKey。
3. URL 必须是公网 HTTPS，且证书有效。
4. 应用可见范围需要包含你的企业微信账号。
5. 将你的企业微信 UserId 填入 `WECOM_ALLOWED_USERS`。

## 安全边界

- 回调消息必须通过 SHA-1 签名校验、AES 解密和 CorpId 校验。
- 微信用户必须命中白名单。
- `问` 只获得 `read-only` 权限。
- `改` 先进入 `WAITING_CONFIRMATION`，确认后才执行。
- 写任务在独立克隆中创建 `codex/task-任务ID` 分支和 `/workspace/codex-worktrees/task-任务ID` 工作目录。
- 当前实现不会自动合并、推送或部署修改，需要人工审查。
- 不要把 Codex 指向正在运行的生产容器文件系统。
