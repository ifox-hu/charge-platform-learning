# 虚拟充电桩模拟器

这是第一版独立设备模拟器，用于练习设备通信和状态机。它不直接操作 MySQL，也不依赖 Redis、RabbitMQ；单进程运行也不需要 Docker。

## 当前协议

模拟器监听 TCP `9100`，每条消息占一行 JSON：

```json
{"command":"START","connectorId":1}
{"command":"STOP","connectorId":1}
{"command":"PING"}
```

连接建立后先返回 `HELLO`，收到命令后返回 `STATUS`；设备状态包括 `IDLE`、`CHARGING`，电量按实际经过时间累计。默认使用 `1x` 真实时间；本地快速测试可通过 `-Dsimulator.time-scale=10` 或环境变量 `SIMULATOR_TIME_SCALE=10` 临时加速。

## 运行

```powershell
cd E:\充电桩\charge-platform-learning\simulator
mvn "-Dmaven.repo.local=D:\Maven3.9.16\apache-maven-3.9.16\mvn_repo" test
mvn "-Dmaven.repo.local=D:\Maven3.9.16\apache-maven-3.9.16\mvn_repo" package
java -Dsimulator.device-id=SIM-PILE-001 -Dsimulator.connectors=2 -Dsimulator.time-scale=60 -jar target/charge-platform-simulator-0.1.0-SNAPSHOT.jar
```

Spring Boot 后端会通过 TCP Adapter 连接该服务，读取实时状态并确认 START/STOP 命令。模拟器仍然不直接访问 MySQL。

TCP 集成测试覆盖真实 `ServerSocket`：随机端口启动、连接握手、`START` 命令和 `STATUS` 响应。下一阶段进入容器编排时，会保留同一套协议和状态机。

## Docker 多实例

已提供 `Dockerfile` 和 `docker-compose.simulator.yml`，仅编排虚拟桩，不包含 MySQL、Redis、RabbitMQ 或其他已有服务。

先在有 Maven 的环境打包 JAR，再把 `target/charge-platform-simulator-0.1.0-SNAPSHOT.jar` 和本目录文件复制到 Docker 主机。Compose 直接使用 Docker 主机已有的 `centos7-jdk17:latest` 镜像并挂载 JAR，不执行 `docker build`，因此不需要访问 Docker Hub。

```powershell
cd E:\充电桩\charge-platform-learning\simulator
mvn "-Dmaven.repo.local=D:\Maven3.9.16\apache-maven-3.9.16\mvn_repo" package
```

在 Docker 主机执行：

```bash
docker compose -f docker-compose.simulator.yml config
docker compose -f docker-compose.simulator.yml up -d --pull never
docker compose -f docker-compose.simulator.yml ps
```

实例和端口：

```text
SIM-PILE-001 -> 宿主机 9100
SIM-PILE-002 -> 宿主机 9101
SIM-PILE-003 -> 宿主机 9102
```

先执行 `config` 检查编排文件，再执行 `build/up`。不要在不确认目标 Docker 主机的情况下执行 `down -v`，本编排不需要数据卷。

## 与平台联调

先启动本模拟器，再重启 Spring Boot，并设置：

```powershell
$env:SIMULATOR_ENABLED="true"
$env:SIMULATOR_SYNC_DATABASE="true"
```

平台连接状态可通过管理员登录后访问：

```text
GET http://localhost:8081/api/simulator/status
```

启用 `SIMULATOR_SYNC_DATABASE=true` 后，设备状态和实时电量会同步到现有 `connector` 与进行中订单。后端还会定时校准设备与订单状态，设备异常停止且仍有有效电量时会自动结算。数据库不会自动建表或改表。
