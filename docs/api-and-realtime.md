# 接口与实时通信

REST 基础地址：`http://localhost:8081/api`。登录后请求头携带 `Authorization: Bearer <JWT>`。

常用资源：`/auth/login`、`/stations`、`/chargers`、`/connectors`、`/price-periods`、`/orders`、`/dashboard`、`/simulator/status`。

WebSocket 地址：`ws://localhost:8081/ws/status`；HTTPS 页面使用 `wss://`。连接中断时前端保留轮询兜底。

订单完成事件发送到 `charge.order.exchange`，消费队列为 `charge.order.completed`，失败消息最终进入 `charge.order.completed.dlq`。Redis 用于看板统计缓存，默认有效期 30 秒。
