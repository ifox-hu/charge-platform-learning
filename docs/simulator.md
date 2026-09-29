# 模拟充电桩联调

模拟器是独立 Java 17 程序，通过 TCP 与后端连接。它不直接访问数据库、Redis 或 RabbitMQ。每条 TCP 消息是一行 JSON；连接建立后发送 `HELLO`，状态变化发送 `STATUS`，后端发送 `START`、`STOP` 和 `PING`。

## 设备和端口

| 设备 ID | 本机端口 | 默认物理枪数 |
|---|---:|---:|
| `SIM-PILE-001` | 9100 | 2 |
| `SIM-PILE-002` | 9101 | 2 |
| `SIM-PILE-003` | 9102 | 4 |

## Windows 启动三台设备

先打包：

```powershell
cd E:\充电桩\charge-platform-learning\simulator
mvn package
```

分别打开三个 PowerShell 窗口，使用相同 JAR、不同端口和设备 ID：

```powershell
java -Dsimulator.port=9100 -Dsimulator.device-id=SIM-PILE-001 -Dsimulator.connectors=2 -Dsimulator.time-scale=1 -jar target\charge-platform-simulator-0.1.0-SNAPSHOT.jar
```

```powershell
java -Dsimulator.port=9101 -Dsimulator.device-id=SIM-PILE-002 -Dsimulator.connectors=2 -Dsimulator.time-scale=1 -jar target\charge-platform-simulator-0.1.0-SNAPSHOT.jar
```

```powershell
java -Dsimulator.port=9102 -Dsimulator.device-id=SIM-PILE-003 -Dsimulator.connectors=4 -Dsimulator.time-scale=1 -jar target\charge-platform-simulator-0.1.0-SNAPSHOT.jar
```

`time-scale=1` 为真实时间。只在需要快速演示时临时提高倍率。后端 `SIMULATOR_DEVICES` 必须和这三个端口一致；容器部署时使用服务名和容器内部端口 `9100`。

## 完整联调

1. 后端启用 `SIMULATOR_ENABLED=true`、`SIMULATOR_SYNC_DATABASE=true`，设置 `SIMULATOR_DEVICES` 后启动。
2. 登录后请求 `GET /api/simulator/status`；每个设备的 `connected` 应为 `true`，`connectors` 里有枪状态。
3. 确认数据库中的桩编码能映射到设备 ID，例如 `PILE-003` 对应 `SIM-PILE-003`；枪编码末尾 A/B/C/D 对应本地枪 1/2/3/4。
4. 在 Web 或小程序选择空闲枪并开始订单。后端等待 `START` 确认，状态转为 `CHARGING`。
5. 观察 `GET /api/orders/{id}/live` 的 `energyKwh`、`powerKw`、`currentA`、`voltageV`。
6. 停止订单。后端读取最终电量、按站点分时电价结算，订单转为 `COMPLETED`。

## Web 增加第 5 把枪为什么不能真实充电？

Web 管理端修改的是 MySQL `connector` 记录，模拟器进程启动时的 `simulator.connectors=4` 仍只生成四把物理枪。小程序可以把第 5 把配置显示出来，但模拟器尚未上报它的实时数据，启动充电会被后端拒绝。要让第 5 把真正可用，需把 `SIM-PILE-003` 的枪数改为 `5`，重新启动对应模拟桩，并确保数据库编码映射正确。

## 排错

- `connected=false`：检查端口、设备 ID 和 `SIMULATOR_DEVICES`。
- “未上报对应充电枪状态”：数据库枪编号超过模拟器物理枪数，或编码后缀映射错误。
- 充电过快：检查 `SIMULATOR_TIME_SCALE` 或 `-Dsimulator.time-scale` 是否大于 1。
- 后端代码改动后服务器仍旧行为：重新打包并替换 JAR，再重建 backend 容器。

[返回文档首页](README.md)
