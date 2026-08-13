# 公开 Codex 问答部署说明

公开访问地址为 `/ask`。该页面无需登录，只提供提问和当前浏览器会话的回答，不显示侧边栏、Agent、任务历史或系统管理功能。

## 安全隔离

- 公开接口只能创建 `READ_ONLY` 任务，访客不能指定 Agent、项目或权限。
- 每个浏览器标签页使用随机会话凭证，只能查询自己提交的问题。
- 问题最长 2000 字，默认 10 秒内只能提交一次，同时最多保留 2 个执行中任务。
- 公开任务由独立 `codex-public-agent` 容器执行，该容器不挂载生产项目，只能访问容器内空白目录 `/app/public-qa`。
- 管理端 `/codex-agents` 及其他系统接口仍然必须登录。

## 启用步骤

1. 登录系统，进入“系统管理 → 远程 Codex”，再创建一台名为“公开问答 Agent”的电脑。
2. 记录新 Agent 的数字 ID，并复制只显示一次的 `pa_agent_...` 令牌。不要与管理端 Agent 共用令牌。
3. 在云服务器 `deploy/.env` 填写：

```dotenv
PUBLIC_CODEX_ENABLED=true
PA_PUBLIC_AGENT_TOKEN=新建的公开问答Agent令牌
PUBLIC_CODEX_AGENT_ID=新建的公开问答Agent数字ID
PUBLIC_CODEX_PROJECT_KEY=public-qa
PUBLIC_CODEX_MINIMUM_INTERVAL_SECONDS=10
PUBLIC_CODEX_MAX_ACTIVE_TASKS_PER_SESSION=2
PUBLIC_CODEX_MAX_ACTIVE_TASKS=20
```

4. 构建并启动公开问答服务：

```bash
cd personal-assistant/deploy
docker compose --profile public-codex --env-file .env up -d --build
docker compose --profile public-codex logs -f backend codex-public-agent nginx
```

5. 浏览器打开 `https://你的域名/ask`，提交一个普通知识问题并等待回答。

如需临时关闭公开问答，将 `PUBLIC_CODEX_ENABLED=false` 后重启后端；还可以执行 `docker compose --profile public-codex stop codex-public-agent` 停止公开 Agent。
