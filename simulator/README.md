# 虚拟充电桩模拟器

这是第一版独立设备模拟器，用于练习设备通信和状态机。它不直接操作 MySQL，也不依赖 Redis、RabbitMQ；单进程运行也不需要 Docker。

## 当前协议

模拟器监听 TCP `9100`，每条消息占一行 JSON：

```json
{"command":"START","connectorId":1}
{"command":"STOP","connectorId":1}
{"command":"PING"}
```

连接建立后先返回 `HELLO`，收到命令后返回 `STATUS`；设备状态包括 `IDLE`、`CHARGING`，并在内存中按秒累加模拟电量。后续接入平台时，平台侧 TCP Adapter 负责把 `STATUS` 转换为订单/设备业务事件。

## 运行

```powershell
cd E:\充电桩\charge-platform-learning\simulator
mvn "-Dmaven.repo.local=D:\Maven3.9.16\apache-maven-3.9.16\mvn_repo" test
mvn "-Dmaven.repo.local=D:\Maven3.9.16\apache-maven-3.9.16\mvn_repo" package
java -Dsimulator.device-id=SIM-PILE-001 -Dsimulator.connectors=2 -jar target/charge-platform-simulator-0.1.0-SNAPSHOT.jar
```

当前还没有让 Spring Boot 后端主动连接该 TCP 服务，这是下一阶段的“设备接入适配器”。不需要现在修改 Docker；完成本地协议和适配器后，再制作 Dockerfile 并用多个容器模拟多台充电桩。

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
$env:SIMULATOR_SYNC_DATABASE="false"
```

平台连接状态可通过管理员登录后访问：

```text
GET http://localhost:8081/api/simulator/status
```

第一阶段建议保持 `SIMULATOR_SYNC_DATABASE=false`，只观察 TCP 消息。确认设备编号和连接器编号映射无误后，再设置为 `true`，平台才会把 `STATUS` 更新到现有 `connector` 表。数据库不会自动建表或改表。
