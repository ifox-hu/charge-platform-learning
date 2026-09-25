# Charge Platform Learning

一个面向 Java 基础巩固的轻量充电桩运营管理项目。每个阶段只完成一条小业务链路，并保留运行与练习空间。

## 当前阶段

- 后端：Java 17、Spring Boot 3.5.10，端口 `8081`
- 前端：复用 `charge-platform-lite/frontend` 原页面与样式，Vite 端口 `5174`
- 已接入现有 MySQL 六张表：登录/me、看板、站点、充电桩、充电枪、分时电价、订单接口
- 使用 MyBatis-Plus Mapper，不使用 JPA/Repository
- 不包含建表脚本、演示数据初始化器或自动改表逻辑
- Redis 看板缓存和 RabbitMQ 订单完成事件均已默认开启
- `simulator` 已增加第一版 TCP 虚拟充电桩：支持 HELLO、心跳、START/STOP 和充电状态上报

Spring Boot 3.5.10 属于 Spring Boot 3.5 稳定维护线，基线兼容 Java 17。MyBatis-Plus 固定为 3.5.7，Knife4j 为 4.5.0；依赖先列入项目基线，实际数据库功能会等读取现有表结构后再逐项实现。

## 启动

完整的模拟桩、后端、Web 和联调步骤见：[启动与充电桩联调说明](docs/启动与充电桩联调说明.md)。

### 1. 启动三台模拟充电桩

在 PowerShell 中执行：

```powershell
cd E:\充电桩\charge-platform-learning
$jar = 'E:\充电桩\charge-platform-learning\simulator\target\charge-platform-simulator-0.1.0-SNAPSHOT.jar'

Start-Process java -ArgumentList '-Dsimulator.port=9100','-Dsimulator.device-id=SIM-PILE-001','-Dsimulator.connectors=2','-jar',$jar
Start-Process java -ArgumentList '-Dsimulator.port=9101','-Dsimulator.device-id=SIM-PILE-002','-Dsimulator.connectors=2','-jar',$jar
Start-Process java -ArgumentList '-Dsimulator.port=9102','-Dsimulator.device-id=SIM-PILE-003','-Dsimulator.connectors=4','-jar',$jar
```

确认端口：

```powershell
Get-NetTCPConnection -LocalPort 9100,9101,9102 -State Listen
```

### 2. 在 IDEA 启动后端

后端 IDEA 运行配置已经写入 `backend/.idea/runConfigurations/Backend_Local.xml`。

在 IDEA 中执行 `File → Reload All from Disk`，然后选择运行配置：

```text
Backend Local
```

点击绿色运行按钮。该配置会自动设置：

```text
SIMULATOR_ENABLED=true
SIMULATOR_SYNC_DATABASE=false
SIMULATOR_HOST=127.0.0.1
SIMULATOR_DEVICES=SIM-PILE-001@127.0.0.1:9100,SIM-PILE-002@127.0.0.1:9101,SIM-PILE-003@127.0.0.1:9102
```

后端启动日志出现 `Tomcat started on port 8081` 后，即可访问后端接口。

也可以不用 IDEA，在 `backend` 目录执行：

```powershell
mvn "-Dmaven.repo.local=D:\Maven3.9.16\apache-maven-3.9.16\mvn_repo" spring-boot:run
```

前端（在 `frontend` 目录执行）：

```powershell
npm ci
npm run dev
```

访问 `http://localhost:5174`。健康检查：`http://localhost:8081/api/health`。

登录账号由现有数据库维护，请向管理员获取账号。不要将账号密码写入源码、文档或提交记录。

启动后端前，需在运行环境中设置 `DB_HOST`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 和至少 32 位的随机 `JWT_SECRET`。Redis 与 RabbitMQ 默认连接本机，可分别通过 `REDIS_HOST`、`RABBITMQ_HOST` 覆盖。

接口文档地址：`http://localhost:8081/doc.html`（Knife4j）。

## 学习提示

当前可以沿着站点列表练习完整链路：浏览器请求 `/api/stations`，Vite 将它代理到 Spring Boot；Security 过滤器解析 JWT；Controller 接收请求并调用 Service；Service 通过 MyBatis-Plus Mapper 生成 SQL 查询 `station` 表；结果经过统一响应对象，由 Jackson 序列化为 JSON。分页接口额外使用 `PageResponse` 封装 `rows、total、page、size、totalPages`。

项目启动时不会写入数据库。新增、删除站点和启动/结束订单属于会改变现有数据的操作，练习前请先确认目标记录和业务意图。

## 可选中间件

Redis 看板缓存默认开启，默认连接本机 `6379`；看板统计会缓存 30 秒，站点、设备和订单变化会主动清理看板缓存。如果临时不使用 Redis，可将 `app.redis.enabled` 设置为 `false`。RabbitMQ 订单完成事件默认开启，默认连接本机 `5672`。

结束订单事务提交后会发送 `OrderCompletedEvent` 到 `charge.order.exchange`，由 `charge.order.completed` 队列异步消费。订单已经提交后，即使消息发布失败也不会把接口改成失败；消费者失败时最多重试 3 次，之后拒绝重新入队并记录日志。Redis 故障时看板直接回源 MySQL，不影响核心业务。

## 当前优先级

1. 核心业务：站点、设备、电价、订单、自动计费、CSV 报表、权限和设备状态保护。
2. 可靠性：Redis 看板缓存降级、RabbitMQ 事务后事件、发布失败隔离、消费重试。
3. 工程化：测试 profile 使用 H2 内存库，完整 Maven 测试不依赖外部中间件；前端构建使用 Vite；部署参数通过环境变量覆盖。
4. 可观测性：Spring Boot Actuator 提供健康、信息和指标端点，并区分公开健康检查与管理员运行指标。

当前采用模块化单体。业务规模和团队规模尚未达到拆分 Spring Cloud 的收益点，先把事务边界、权限、缓存一致性、消息可靠性和测试讲扎实，再考虑按订单/设备/报表拆服务。

## 虚拟充电桩模拟器

`simulator` 是独立 Java 17 程序，使用 JDK `ServerSocket` 提供 TCP 设备端，默认监听 `9100`。连接后发送 `HELLO`，接受 `START`、`STOP`、`PING` 命令，并在内存中维护充电枪状态和模拟电量。它不直接操作 MySQL，也不需要启动新的 Docker 容器。

当前阶段先验证设备协议和状态机；下一阶段再在 Spring Boot 中增加 TCP Adapter，把设备 `STATUS` 转换为订单/连接器业务事件。等 Adapter 稳定后，再考虑 Smart-Socket、国标协议字段和 Docker 批量编排。

更多请求链路、部署参数和简历表述见 `docs/`。
