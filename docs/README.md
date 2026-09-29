# 充电桩运营平台 · 开发手册

下面先说明项目能做什么、如何运行一条完整充电链路；每个主题链接包含具体命令、配置、接口字段和排错方法。

> **项目定位**：Java 17 + Spring Boot 3.5 的学习型运营平台。包括 Web 管理端、微信小程序、三台 TCP 模拟桩、MySQL、Redis 和 RabbitMQ。当前仓库**没有完整基础建表及种子数据脚本**，首次运行需要先准备已有的 `charge_platform` 基础库；`docs/db/V2~V4` 只是增量迁移。

## 5 分钟了解系统

| 模块 | 能做什么 | 主要入口 |
|---|---|---|
| 运营看板 | 站点、设备、订单汇总；Redis 缓存 30 秒 | `GET /api/dashboard` |
| 站点/设备 | 编辑站点、桩、枪；设备状态同步 | `/api/stations`、`/api/chargers`、`/api/connectors` |
| 电价/订单 | 分时电价、启动/停止、按实际电量计费、测试订单归档 | `/api/price-periods`、`/api/orders` |
| 模拟桩 | 三台设备通过 TCP 上报状态、电量、电压、电流 | `/api/simulator/status` |
| 实时推送 | WebSocket 广播设备状态，断线后前端轮询兜底 | `/ws/status` |
| 审计/消息 | 写操作审计，订单完成事件及死信队列 | `/api/audit-logs`、RabbitMQ |

一次充电的路径：在 Web 或小程序选择空闲枪 → 后端锁定数据库记录并发送 `START` → 模拟桩上报实时电量 → 点击停止发送 `STOP` → 后端按分时电价结算并发出订单完成事件。详见[模拟桩联调](simulator.md)和[接口与实时通信](api-and-realtime.md)。

## 从哪里开始

| 你要做的事 | 阅读 |
|---|---|
| 第一次拉取、准备数据库、看到页面 | [快速开始](getting-started.md) |
| 理解目录、业务链路、数据流 | [项目结构与架构](project-structure.md) |
| 在 IDEA、Vite、微信开发者工具中改代码 | [本地开发](local-development.md) |
| 启动三台桩，完成一次充电与结算 | [模拟充电桩联调](simulator.md) |
| 在线/离线 Compose、HTTPS、更新发布 | [Docker 部署](deployment.md) |
| 调用接口、查看 WebSocket 和消息队列 | [接口与实时通信](api-and-realtime.md) |
| 备份、执行 V2~V4 迁移 | [数据库与迁移](database.md) |
| 页面白屏、连接拒绝、定位偏差等 | [故障排查](troubleshooting.md) |

已有的[详细启动与联调记录](启动与充电桩联调说明.md)保留了 Windows 和服务器实际操作步骤。

## 运行前必须知道

1. `.env.example` 只是模板。复制为 `.env` 并填写自己的密码和随机 JWT 密钥；不要提交 `.env`、证书、数据库备份或日志。
2. `docker-compose.yml` 是联网构建版，访问 `http://localhost:5173`；`docker-compose.server.yml` 是使用现有镜像与已编译产物的离线部署版，前端证书需放在 `certs/`，访问 `https://服务器地址`。
3. Web 地图定位需要 HTTPS 或 `localhost`。微信小程序正式真机请求需要合法 HTTPS 域名，开发者工具可临时关闭域名校验。
4. 修改后端或模拟桩代码后，要重新打包 JAR 并重建容器；修改 Web 后要重新构建 `frontend/dist`。

[回到仓库首页](../README.md)
