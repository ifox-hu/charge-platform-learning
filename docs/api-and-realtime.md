# 接口、权限与实时通信

基础地址为 `http://localhost:8081/api`。Nginx 会把前端同源的 `/api/` 转到后端。除登录和健康检查外，接口需要 JWT：`Authorization: Bearer <token>`。

## 统一返回格式

普通业务接口返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-09-29T10:00:00"
}
```

创建接口通常是 HTTP/业务码 201；权限错误为 401/403。`GET /api/orders/export` 返回 CSV 文件，不使用 JSON 包装。

## 登录与角色

```http
POST /api/auth/login
Content-Type: application/json

{"username":"<已有账号>","password":"<账号密码>"}
```

响应 `data.token` 用于后续请求，`GET /api/auth/me` 返回当前用户。`ADMIN` 可管理站点、桩、枪、电价和审计日志；`OPERATOR` 可读业务数据并开始/结束订单。实际权限以 `SecurityConfig` 为准。

## 资源接口

| 资源 | 常用读取 | 管理操作 |
|---|---|---|
| 站点 | `GET /stations`、`/stations/page`、`/stations/{id}` | `POST /stations`、`PUT /stations/{id}`、`DELETE /stations/{id}` |
| 桩 | `GET /chargers?stationId=1` | `POST /chargers`、`PUT /chargers/{id}`、`PUT /chargers/{id}/status`、`DELETE /chargers/{id}` |
| 枪 | `GET /connectors?chargerId=1` | `POST /connectors`、`PUT /connectors/{id}`、`DELETE /connectors/{id}` |
| 电价 | `GET /price-periods?stationId=1` | `POST /price-periods`、`PUT /price-periods/{id}`、`DELETE /price-periods/{id}` |
| 订单 | `GET /orders`、`/orders/page`、`/orders/{id}`、`/orders/export` | `POST /orders/start`、`POST /orders/{id}/stop` |
| 审计 | `GET /audit-logs?page=1&size=20` | `DELETE /audit-logs/before?days=7` |

分页接口返回 `rows`、`total`、`page`、`size`、`totalPages`。订单导出可按 `plateNumber` 过滤。审计清理只允许管理员，前端提供 7 天和 30 天选择。

## 订单请求示例

启动：

```http
POST /api/orders/start
Authorization: Bearer <token>
Content-Type: application/json

{"stationId":1,"connectorId":1,"plateNumber":"京A12345","testOrder":true}
```

站点必须运营中，所属桩在线，枪空闲。车牌格式是地区简称、大写字母和 5 或 6 位数字。`testOrder=true` 用于模拟练习订单。

查询 `GET /api/orders/{id}/live` 可获得模拟桩实时状态、电量、功率、电压和电流。启用模拟桩时，停止请求体可为空 `{}`；后端会读取最终电量：

```http
POST /api/orders/1/stop
Authorization: Bearer <token>
Content-Type: application/json

{}
```

未启用模拟桩时必须传正数电量，如 `{"energyKwh":1.25}`。`DELETE /api/orders/completed-test` 使用 `{"ids":[1,2]}`，仅归档已完成测试订单，不删除进行中订单。

## WebSocket

地址为 `/ws/status`，开发环境为 `ws://localhost:8081/ws/status`，HTTPS 页面对应 `wss://`。后端在模拟设备状态变化后广播 fleet 状态；Web 在连接中断时用轮询兜底。Nginx 必须转发 `Upgrade` 与 `Connection: upgrade`。WebSocket 仅负责状态通知，写操作仍走 REST。

## Redis 与 RabbitMQ

Redis 看板缓存默认 30 秒；站点、设备、订单变化会触发缓存失效。RabbitMQ 的订单完成事件进入 `charge.order.exchange`，路由键 `order.completed`，消费队列 `charge.order.completed`；失败重试后进入 `charge.order.completed.dlq`。队列消息数为 0 不一定是异常，可能已被消费者即时处理。

[返回文档首页](README.md)
