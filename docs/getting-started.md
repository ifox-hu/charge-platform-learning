# 快速开始

本页给出两条数据库路线：**全新空库**依次运行 `docs/db/V1~V4`，**已有库**保留原数据、只执行缺失迁移。之后可用 Compose 启动，或在本机分别启动 IDEA、Maven 和 Vite。

## 1. 准备软件

| 工具 | 版本/用途 | 检查命令 |
|---|---|---|
| JDK | Java 17，后端和模拟桩 | `java -version` |
| Maven | 3.9+，打包和测试 | `mvn -version` |
| Node.js/npm | Node 20 推荐，构建 Web | `node -v`、`npm -v` |
| Docker + Compose | 容器路线 | `docker compose version` |
| 微信开发者工具 | 小程序调试，可选 | 打开 `miniapp/` |

## 2. 获取代码

```bash
git clone https://github.com/ifox-hu/charge-platform-learning.git
cd charge-platform-learning
```

项目路径没有固定要求。Windows PowerShell 可使用 `Copy-Item .env.example .env`；Linux/macOS 使用 `cp .env.example .env`。

## 3. 准备数据库

全新空库：`V1__baseline_schema.sql` 创建六张基础表，V2～V4 增加坐标、归档和审计。Docker MySQL 首次创建空数据卷时会按文件名顺序执行 `docs/db/` 中的 SQL。建表后没有自动创建账号；本地演示可手动执行 `docs/demo_seed.sql`，它会创建独立的 `demo_admin`、`demo_operator` 和三台模拟桩的数据。详见[数据库文档](database.md)。

已有库：先备份，**不要执行 V1 或演示数据脚本**；确认哪些增量字段/表尚缺，再执行相应 V2～V4。已有数据卷不会重新运行 Docker 初始化 SQL。

## 4. 配置运行参数

```bash
cp .env.example .env
```

在 `.env` 中至少设置 `MYSQL_ROOT_PASSWORD`、`MYSQL_USER`、`MYSQL_PASSWORD` 和不少于 32 字符的 `JWT_SECRET`。已有 MySQL 数据卷的实际账号由数据库内用户决定，修改 `.env` 不会重置旧密码。`MYSQL_*_VOLUME` 用于复用已有数据卷，迁移服务器时必须确认卷名。

| 变量 | 用途 | 说明 |
|---|---|---|
| `MYSQL_DATABASE` | 数据库名 | 默认 `charge_platform` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 后端连接账号 | 建议独立应用账号 |
| `MYSQL_ROOT_PASSWORD` | MySQL 管理员密码 | 仅新数据卷初始化时生效 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | 消息队列账号 | 已有数据卷不会自动改旧账号 |
| `JWT_SECRET` | 登录令牌签名 | 保持私密且稳定 |
| `SIMULATOR_TIME_SCALE` | 模拟速度倍率 | `1` 为默认真实速度 |

## 5. Compose 启动（联网可构建）

```bash
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps
```

看到 MySQL、Redis、RabbitMQ 和 backend 为 `healthy`、frontend 和三台 simulator 为 `Up` 后，打开 `http://localhost:5173`。如使用全新空库，可手动导入 `docs/demo_seed.sql` 后用演示账号登录。后端健康检查为 `http://localhost:8081/api/health`。**普通 HTTP 局域网 IP 页面不能调用浏览器定位**，需要 HTTPS。

## 6. 本机分别启动（适合改代码）

在终端先启动 MySQL、Redis、RabbitMQ。后端需要 `DB_HOST`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 和 `JWT_SECRET`；模拟桩联调还需要 `SIMULATOR_ENABLED=true` 与 `SIMULATOR_DEVICES`。详细环境变量和三台模拟桩启动命令见[本地开发](local-development.md)及[模拟桩联调](simulator.md)。

```bash
cd backend
mvn test
mvn spring-boot:run
```

在另一个终端：

```bash
cd frontend
npm ci
npm run dev
```

Vite 默认端口是 `5174`，会代理 `/api` 和 `/ws` 到本机 `8081`。

## 7. 验证一条业务链路

1. 浏览器登录已有管理员账号，确认站点列表有数据。
2. 打开设备管理，确认三台模拟桩在线且枪状态出现。
3. 选择空闲枪，输入符合“地区简称 + 大写字母 + 5/6 位数字”的车牌。
4. 启动订单，观察实时电量、功率和电流增长。
5. 停止订单，核对结束金额和订单状态；RabbitMQ 队列消息可能即时被消费，所以队列深度为 0 也可能正常。

下一步阅读[接口与实时通信](api-and-realtime.md)或[故障排查](troubleshooting.md)。
