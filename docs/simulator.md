# 模拟充电桩联调

| 设备 | TCP 端口 | 枪数 |
|---|---:|---:|
| `SIM-PILE-001` | 9100 | 2 |
| `SIM-PILE-002` | 9101 | 2 |
| `SIM-PILE-003` | 9102 | 4 |

三台设备分别启动三个进程。示例：

```powershell
cd simulator
mvn spring-boot:run -Dspring-boot.run.arguments="--simulator.port=9100 --simulator.device-id=SIM-PILE-001 --simulator.connectors=2 --simulator.time-scale=1"
```

`time-scale=1` 表示真实速度。后端设置 `SIMULATOR_DEVICES` 后会自动连接设备。模拟桩停止后后端标记离线；订单停止会读取实时电量并自动计费。

联调顺序：启动中间件 -> 启动模拟桩 -> 启动后端 -> 登录前端 -> 开始订单 -> 观察实时状态 -> 停止订单并检查结算。
