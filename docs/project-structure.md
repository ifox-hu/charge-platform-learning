# 项目结构与架构

## 目录树

```text
charge-platform-learning/
├─ backend/          Spring Boot API、业务逻辑、测试
├─ frontend/         Vue 3 + Vite 管理端
├─ miniapp/          原生微信小程序
├─ simulator/        独立 Java TCP 模拟设备
├─ docs/             本手册与增量 SQL
├─ docker-compose.yml         联网构建版本
└─ docker-compose.server.yml  离线服务器版本
```

`backend` 是模块化单体：每个业务目录包含 Controller、Service、Mapper 和领域对象。Web 和小程序都调用同一套 `/api`；模拟桩不直接访问 MySQL，而是通过 TCP 与后端通信。

## 后端模块

| 目录 | 主要职责 | 入口 |
|---|---|---|
| `auth` | 登录、JWT、角色权限 | `/api/auth` |
| `station` | 站点与地图坐标 | `/api/stations` |
| `device` | 桩、枪和状态保护 | `/api/chargers`、`/api/connectors` |
| `price` | 时段电价 | `/api/price-periods` |
| `order` | 开始/停止、计费、导出、测试订单归档 | `/api/orders` |
| `simulator` | TCP 连接、命令确认、状态同步 | `/api/simulator` |
| `realtime` | WebSocket 设备状态广播 | `/ws/status` |
| `audit` | 已登录用户写操作日志 | `/api/audit-logs` |
| `dashboard` | 汇总统计及 Redis 缓存 | `/api/dashboard` |

## 一次充电的数据流

```text
Web / 小程序
    │ POST /api/orders/start
    ▼
后端验证站点、桩和枪，锁定充电枪记录
    │ START 命令，等待模拟桩确认
    ▼
TCP 模拟桩持续上报 STATUS、电量、功率、电流、电压
    │ 后端同步数据库状态并广播 WebSocket
    ▼
Web 实时展示；小程序周期查询状态
    │ POST /api/orders/{id}/stop
    ▼
后端发送 STOP → 读取最终电量 → 分时计费 → 结束订单
    │ 提交事务后发送订单完成事件
    ▼
RabbitMQ 消费；失败重试，最终进入死信队列
```

`ChargeOrderService` 保证订单、充电枪状态和金额在同一业务事务内更新。模拟桩断线后，`SimulatorTcpClient` 会重连并检查离线超时。实际设备连接数和数据库枪记录数可能暂时不同：管理端新建数据库枪并不自动增加模拟器进程的物理枪数，详情见[模拟桩联调](simulator.md)。

## 前端与小程序

Web 主要文件是 `frontend/src/App.vue`、`src/api.js` 和 `src/styles.css`。Vite 开发服务器把 `/api` 和 `/ws` 代理到后端；部署时 Nginx 做同样的代理。

小程序页面在 `miniapp/pages/`，统一请求封装在 `miniapp/utils/request.js`，服务地址在 `miniapp/config/env.js`。微信地图使用 GCJ-02 坐标；模拟桩状态卡从 `/api/simulator/status` 获取实时值，数据库枪列表从 `/api/connectors` 获取配置。

## 中间件边界

- **MySQL**：业务数据来源。基础表和账号需要事先准备。
- **Redis**：看板短期缓存；Redis 故障时查询回源 MySQL。
- **RabbitMQ**：订单完成后的异步事件，不负责 TCP 模拟桩通信。
- **WebSocket**：设备状态推送，不替代 REST 写接口。

[返回文档首页](README.md)
