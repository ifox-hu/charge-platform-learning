# 项目结构

```text
backend/      Spring Boot API、鉴权、订单、缓存、消息和 WebSocket
frontend/     Vue + Vite 运营管理端
miniapp/      微信小程序端
simulator/    Java TCP 模拟充电桩
docs/         开发、部署、联调和数据库文档
```

后端模块包括 `auth`、`station`、`device`、`price`、`order`、`simulator`、`realtime` 和 `audit`。设备状态通过 TCP 进入后端，后端同步连接器状态并广播 WebSocket；Redis 用于看板缓存，RabbitMQ 用于订单完成事件。

请求链路：浏览器或小程序 -> Controller -> Service 事务 -> MyBatis-Plus Mapper -> MySQL。
