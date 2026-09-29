# Architecture and Request Flows

## Runtime topology

```text
Vue 3 + Vite :5174
        |
        | /api proxy
        v
Spring Boot :8081
  Security/JWT -> Controller -> Service -> MyBatis-Plus Mapper -> MySQL
        |                         |
        |                         +-> Redis dashboard cache (30 seconds)
        +-> AFTER_COMMIT OrderCompletedEvent -> RabbitMQ exchange/queue
```

The application uses the MySQL instance configured through environment variables. It does not create, alter, or seed tables.

## Typical request chain

1. The browser sends `POST /api/auth/login` or a business request through the Vite `/api` proxy.
2. `JwtAuthenticationFilter` validates the Bearer token and loads the user role from `sys_user`.
3. `SecurityConfig` applies the ADMIN/OPERATOR method rules.
4. The Controller validates the request and delegates to a Service.
5. The Service checks business invariants and calls a MyBatis-Plus Mapper.
6. MyBatis executes SQL against the existing table and the common response object is serialized as `{ code, message, data, timestamp }`.

## Order completion flow

`ChargeOrderService.stop` locks the charging order, calculates fees across price periods, restores the connector to `IDLE`, and commits the MySQL transaction. Only after commit is `OrderCompletedEvent` sent to RabbitMQ. A Rabbit publish error is logged and isolated because the order is already durable. The listener retries a failed handler three times with exponential backoff. After the final failure, RabbitMQ routes the rejected message to `charge.order.completed.dlq` through `charge.order.dlx` instead of requeueing forever.

## Configuration overrides

Set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, and a strong `JWT_SECRET` in the runtime environment. Redis and RabbitMQ default to localhost; override `REDIS_HOST`, `REDIS_PORT`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD` when needed. Set `app.redis.enabled=false` or `app.rabbit.enabled=false` only when that dependency is intentionally unavailable.

Check both middleware connections with:

```text
GET http://localhost:8081/api/health/dependencies
```

## Observability

Spring Boot Actuator exposes `/actuator/health`, `/actuator/info`, and `/actuator/metrics`. Health and info are public so a deployment probe can use them; other Actuator endpoints require the ADMIN role. The application-specific `/api/health/dependencies` reports Redis and RabbitMQ separately, which makes a middleware outage visible without hiding the core application's status.

## Virtual charger simulator

The standalone `simulator` module represents the device side of the system. It listens on TCP `9100`, sends a `HELLO` frame after connection, accepts line-delimited JSON commands (`START`, `STOP`, `PING`), and reports connector status and simulated energy. Its state is kept in memory and never bypasses the platform database.

The planned integration boundary is:

```text
Virtual charger -> TCP -> Spring Boot TCP Adapter -> device/order services -> MySQL
                                      |
                                      +-> RabbitMQ device events (later)
```

The first implementation deliberately uses JDK `ServerSocket` so the state machine and message flow remain easy to debug. Smart-Socket, GB/T interoperability fields, reconnection, authentication, and Docker multi-device orchestration are later increments, not prerequisites for the current business flow.
