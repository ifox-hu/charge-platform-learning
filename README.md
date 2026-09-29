# Charge Platform Learning

一个可本地开发、容器部署和模拟充电的学习型充电桩运营平台。项目包含 Spring Boot 后端、Vue 管理端、微信小程序、三台 TCP 模拟桩，以及 MySQL、Redis、RabbitMQ 和 WebSocket 联调。

## 功能概览

- 站点、充电桩、充电枪和分时电价管理。
- 订单启动、实时电量、停止结算、CSV 导出与已完成测试订单归档。
- 三台模拟桩自动连接、断线重连、状态同步、WebSocket 推送。
- 管理员/运营员权限、操作审计、Redis 看板缓存、RabbitMQ 订单完成事件与死信队列。
- Web 地图定位和微信小程序站点、设备、订单页面。

## 开发文档

从 **[完整开发手册](docs/README.md)** 开始。手册包含具体命令、配置表、请求示例、部署步骤和故障排查：

| 主题 | 文档 |
|---|---|
| 新库建表或已有库接入、第一次启动 | [快速开始](docs/getting-started.md) |
| 理解请求链路和模块 | [项目结构与架构](docs/project-structure.md) |
| IDEA、Vite、微信开发者工具 | [本地开发](docs/local-development.md) |
| 三台模拟桩与充电结算 | [模拟充电桩联调](docs/simulator.md) |
| 在线/离线 Compose、HTTPS、更新发布 | [Docker 部署](docs/deployment.md) |
| REST、JWT、WebSocket、RabbitMQ | [接口与实时通信](docs/api-and-realtime.md) |
| V1 基础建表、V2~V4 迁移、备份 | [数据库与迁移](docs/database.md) |
| 无法访问、容器异常、定位问题 | [故障排查](docs/troubleshooting.md) |

## 目录

```text
backend/    Java 17 + Spring Boot 3.5 API
frontend/   Vue 3 + Vite Web 管理端
miniapp/    微信小程序
simulator/  Java TCP 模拟充电桩
docs/       开发手册和增量 SQL
```

## 启动前的关键条件

全新数据库可依次执行 `docs/db/V1~V4`；Docker MySQL 仅在**全新空数据卷**首次启动时自动执行这些文件。可选的 `docs/demo_seed.sql` 只供本地演示，必须手动执行，不会自动进入生产库。已有数据库不要执行 V1，只按需补迁移。复制 `.env.example` 为 `.env`，填写你自己的数据库密码和 JWT 密钥。`.env`、证书、日志与数据库备份不会提交到 GitHub。

可联网构建时执行：

```bash
docker compose -f docker-compose.yml up -d --build
```

该版本前端在 `http://localhost:5173`。离线服务器请阅读[Docker 部署](docs/deployment.md)，它使用 `docker-compose.server.yml`、预编译 JAR、前端 `dist` 和 HTTPS 证书。两份 Compose 的用途不同。

后端测试：`cd backend && mvn test`；Web 构建：`cd frontend && npm ci && npm run build`。

## 安全与数据

不要将服务器 `.env`、真实账号密码、JWT 密钥、证书或备份文件提交到仓库。已有 MySQL/RabbitMQ 数据卷不会因为修改 `.env` 就自动更换旧密码。升级或迁移前先备份并确认卷名，不要执行 `docker compose down -v`。
