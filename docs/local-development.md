# 本地开发

本页以 Windows PowerShell、IDEA 和微信开发者工具为例。先按[快速开始](getting-started.md)准备基础数据库；本地编译不需要修改服务器容器。

## 后端环境变量

在 IDEA 的 Run/Debug Configuration 中给 Spring Boot 配置以下环境变量，值使用你自己的本地账号：

```text
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=charge_platform
DB_USERNAME=<本地数据库用户>
DB_PASSWORD=<本地数据库密码>
JWT_SECRET=<至少32字符的随机字符串>
REDIS_HOST=127.0.0.1
RABBITMQ_HOST=127.0.0.1
SIMULATOR_ENABLED=true
SIMULATOR_SYNC_DATABASE=true
SIMULATOR_DEVICES=SIM-PILE-001@127.0.0.1:9100,SIM-PILE-002@127.0.0.1:9101,SIM-PILE-003@127.0.0.1:9102
```

PowerShell 临时设置当前终端变量的格式是 `$env:DB_HOST = '127.0.0.1'`。不要把密码直接写入 `backend/src/main/resources/application.yml`；该文件使用 `${DB_PASSWORD}` 占位符。

## 构建、测试和启动

```powershell
cd E:\充电桩\charge-platform-learning\backend
mvn test
mvn spring-boot:run
```

如果要指定仓库内 Maven 缓存，PowerShell 中参数要加引号：`mvn "-Dmaven.repo.local=.m2-repo" test`。后端默认端口 `8081`。启动后先检查 `http://localhost:8081/api/health`，再登录。

## Web 前端

```powershell
cd E:\充电桩\charge-platform-learning\frontend
npm ci
npm run dev
```

打开 `http://localhost:5174`。`frontend/vite.config.js` 将 `/api` 和 `/ws` 代理到 `localhost:8081`。修改源代码后 Vite 会热更新；准备发布时执行 `npm run build`，产物在 `frontend/dist/`。

## 微信小程序

1. 微信开发者工具选择 `miniapp/` 为项目目录。
2. 检查 `miniapp/config/env.js` 中的 `baseUrl`。当前值是局域网演示服务器 `http://192.168.6.100:8081/api`；若后端在本机，应改成电脑局域网 IP，**手机中的 `localhost` 是手机自己**。
3. 开发者工具调试 HTTP IP 时，可在“详情 → 本地设置”中临时关闭合法域名校验。正式真机发布应使用已配置的 HTTPS 请求域名。
4. 编译后检查站点列表；若地图位置偏移，确认手机定位权限、开发者工具模拟位置和 GCJ-02 坐标系。

小程序状态每隔约 5 秒查询。修改 Web 数据库中的枪数量后，模拟桩未必有对应的物理枪；新枪可以显示数据库状态，但要实现真实充电，还需调整模拟器的 `connectors` 数量并重启。

## 常用验证

| 改动 | 至少验证 |
|---|---|
| 后端 Java | `mvn test`、健康接口、相关业务接口 |
| Web Vue/CSS | `npm run build`、浏览器交互 |
| 小程序 WXML/WXSS | 微信开发者工具编译、页面切换 |
| 模拟桩 | `mvn test`、三台设备 TCP 连接与一次 START/STOP |

[返回文档首页](README.md)
