# 个人辅助系统

## 当前进度（2026-06-29，第一版开发路线完成）

项目当前已完成第 0～7 阶段第一版开发，包括核心业务、自动化、部署和运维能力。

已完成：

- Vue 3 + TypeScript + Element Plus 前端骨架和 Spring Boot 后端骨架。
- PostgreSQL、Liquibase、MyBatis Plus、Docker Compose 基础环境。
- JWT 登录、鉴权、当前用户和退出接口。
- 前端登录、令牌保存、请求自动携带 JWT、401 自动回登录页和退出登录。
- 快捷记录新增、待整理列表和归档闭环。
- 系统健康检查、统一响应、异常处理和 Trace ID。
- 快速记录服务层最小自动化测试。
- 统一事项模型，以及生活/工作事项的筛选、新增、编辑、完成、归档。
- 工作事项复盘新增与查询，仪表盘今日生活待办和工作重点。
- 学习计划、学习记录、学习总结、成长统计及对应前端页面。
- 仪表盘学习时长、进行中计划和已完成计划摘要。
- 提醒新增、编辑、完成、取消、到期扫描及发送失败记录。
- Webhook 通知通道加密存储、脱敏展示、测试发送和发送日志。
- Quartz 动态 Cron 任务、启停、JobHandler 分发和执行日志。
- A 股/美股/港股关注项、筛选、启停和仪表盘摘要。
- 股票 API 配置加密存储、脱敏展示、接口测试和采集结果。
- `STOCK_COLLECT` 定时采集处理器和失败日志。
- 数据库/Quartz/磁盘状态监控和部署运维页面。
- PostgreSQL 与文件完整备份、保留策略、定时备份处理器和恢复脚本。

最终代码验证：后端 102 个源文件编译成功，累计 23 个测试全部通过；前端类型检查和生产构建通过；PostgreSQL 实际启动成功，Liquibase 8 个变更集执行完成，Quartz JDBC 调度器和健康接口均为 UP。应用 Docker 镜像构建受本机镜像拉取超时影响未完成，备份恢复演练、真实 Webhook/行情 API 和云服务器上线仍需在部署环境执行。

## 项目结构

```text
personal-assistant
├── deploy                    # Docker Compose
├── personal-assistant-server # Spring Boot 后端
└── personal-assistant-web    # Vue 3 前端
```

## 默认账号

首次启动会初始化默认管理员账号。首次登录后请在“系统设置”中立即修改密码。

## 本地启动

先启动 PostgreSQL：

```powershell
cd personal-assistant\deploy
docker compose up -d postgres
```

启动后端：

```powershell
cd personal-assistant\personal-assistant-server
mvn spring-boot:run
```

启动前端：

```powershell
cd personal-assistant\personal-assistant-web
npm install
npm run dev
```

访问 `http://localhost:5173`。

## 构建和测试

```powershell
cd personal-assistant\personal-assistant-server
mvn -B test

cd ..\personal-assistant-web
npm run build
```

## Docker 部署

首次使用先将 `deploy/.env.example` 复制为 `deploy/.env`，修改数据库密码和 `JWT_SECRET`，然后执行：

```powershell
cd personal-assistant\deploy
docker compose --env-file .env up -d --build
```

服务包括 PostgreSQL、Adminer、后端和 Nginx；数据库及应用文件使用 Docker volume 持久化。
## 云服务器一键发布

首次拉取脚本后，在项目目录执行 chmod +x deploy.sh。

以后在项目目录执行 ./deploy.sh。脚本会依次执行发布前备份、拉取 origin/master、校验 Compose 配置、构建并启动容器，以及后端健康检查。服务器存在已跟踪文件改动时，脚本会停止，避免覆盖服务器上的修改。
