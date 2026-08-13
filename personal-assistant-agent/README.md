# 电脑端 Codex Agent

电脑端 Agent 主动从个人辅助系统领取任务，在本机白名单项目中运行 `codex exec`，并回传安全裁剪后的进度和最终回复。

## 环境要求

- Node.js 20 或更高版本。
- 已安装并登录 Codex CLI，执行 `codex --version` 和 `codex login` 可以正常工作。
- 电脑可以通过 HTTPS 访问个人辅助系统服务器。

## 首次配置

1. 在个人辅助系统进入“系统管理 → 远程 Codex”，添加电脑并立即复制一次性 Agent 令牌。
2. 复制 `config.example.json` 为 `config.json`，修改服务器地址和本地项目白名单。
   Windows 上建议将 `codexCommand` 配置为 Codex CLI 的绝对路径，避免与桌面客户端的同名程序冲突。
3. 项目标识是 `projects` 对象的键。网页任务只能填写这里存在的标识，不能下发任意本地路径。
4. 在当前用户环境中设置令牌并启动：

```powershell
$env:PA_AGENT_TOKEN = "pa_agent_创建时显示的令牌"
npm start
```

如果配置文件不在当前目录，可设置 `PA_AGENT_CONFIG` 为配置文件的绝对路径。

## 权限

- `READ_ONLY` 使用 Codex `read-only` 沙箱。
- `WORKSPACE_WRITE` 使用 `workspace-write` 沙箱，并要求项目配置 `allowWrite: true`。
- Agent 不接受服务器下发的本地路径、Shell 命令或 Codex 启动参数。
- 第一版只支持取消尚未领取的任务；运行中任务不能远程终止。

## 开机启动

确认前台运行稳定后，可使用 Windows 任务计划程序，在当前用户登录时执行：

```text
程序：C:\Program Files\nodejs\node.exe
参数：src\index.js
起始于：本目录的绝对路径
```

使用当前 Windows 用户运行，不要使用 `SYSTEM`，否则可能无法读取 Codex 登录状态和本地项目。
