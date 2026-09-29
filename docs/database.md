# 数据库建表、迁移与演示数据

建表文件依据现有 `charge_platform` MySQL 8 备份中的表结构整理，只保留 DDL，**没有复制你的用户密码散列、车牌、订单、审计记录或真实站点地址**。它是学习和新环境部署用的参考基线；现有服务器数据库不需要重建。

## 脚本清单与执行顺序

| 顺序 | 文件 | 作用 | 是否自动执行 |
|---:|---|---|---|
| 1 | [`db/V1__baseline_schema.sql`](db/V1__baseline_schema.sql) | 建 `sys_user`、`station`、`charger`、`connector`、`price_period`、`charge_order` 六张基础表 | 全新 Docker MySQL 数据卷首次启动 |
| 2 | [`db/V2__station_coordinates.sql`](db/V2__station_coordinates.sql) | 站点 GCJ-02 经纬度及索引 | 同上 |
| 3 | [`db/V3__order_test_and_archive.sql`](db/V3__order_test_and_archive.sql) | 测试订单与归档字段、索引 | 同上 |
| 4 | [`db/V4__audit_log.sql`](db/V4__audit_log.sql) | 审计表及索引 | 同上 |
| 可选 | [`demo_seed.sql`](demo_seed.sql) | 本地演示账号、站点、三台桩、八把枪及全天电价 | **不会自动执行** |

`docker-compose.yml` 和 `docker-compose.server.yml` 都把 `docs/db/` 挂载到 MySQL 初始化目录。MySQL 官方镜像**仅在空数据卷第一次初始化时**按文件名顺序执行其中的 SQL。已有数据卷即使更新了文件，也不会自动补迁移。`demo_seed.sql` 特意放在 `docs/db/` 外，避免部署时自动创建弱口令账号。

## 路线 A：全新空库

先复制 `.env.example` 为 `.env`，设置自己的数据库密码和 JWT 密钥，再启动：

```bash
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps
```

V1～V4 会自动运行。此时只有表结构，没有业务数据和登录账号。仅在**本地隔离开发环境**需要快速体验时，手动导入演示数据：

```bash
docker compose -f docker-compose.yml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' < docs/demo_seed.sql
```

PowerShell 不支持上述 `<` 输入重定向；可使用：

```powershell
Get-Content .\docs\demo_seed.sql -Raw -Encoding UTF8 | docker compose -f docker-compose.yml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'
```

演示账号是 `demo_admin` 和 `demo_operator`，密码仅用于本地演示，为 `123456`。**不要在能被其他人访问的环境使用这些账号**。演示站点地址是占位文本，经纬度为空，地图上需要自行编辑真实地址和坐标。若在没有演示数据的情况下访问登录接口，会收到用户名或密码错误。

使用主机 MySQL 客户端手动建库时，先创建空数据库：

```sql
CREATE DATABASE charge_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

然后在项目根目录按 V1→V4 顺序运行：

```bash
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V1__baseline_schema.sql
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V2__station_coordinates.sql
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V3__order_test_and_archive.sql
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V4__audit_log.sql
```

## 路线 B：已有库

**不要执行 V1，也不要执行演示数据脚本。**先备份，查看已有字段，再只执行缺少的 V2、V3 或 V4。重复执行增量脚本会报“字段/表已存在”；这不是可以忽略的幂等操作。

```sql
SHOW TABLES;
SHOW COLUMNS FROM station LIKE 'latitude';
SHOW COLUMNS FROM charge_order LIKE 'test_order';
SHOW TABLES LIKE 'audit_log';
```

V3 把执行前的订单默认标为测试订单。若已有真实订单，先评估数据再执行，不要直接把真实订单纳入“清理测试订单”的范围。

## 备份与恢复

主机安装 MySQL 客户端时可用交互式 `-p`，避免密码出现在命令历史中：

```bash
mysqldump -h 127.0.0.1 -uroot -p --single-transaction --routines --events --triggers charge_platform > charge_platform_backup.sql
test -s charge_platform_backup.sql && echo '备份文件非空'
```

恢复会替换目标数据库当前数据，先确认库名和备份文件：

```bash
mysql -h 127.0.0.1 -uroot -p charge_platform < charge_platform_backup.sql
```

现有数据卷中的账号密码可能与容器环境变量不同。迁移服务器时应同时核对 `MYSQL_DATA_VOLUME`、`MYSQL_CONFIG_VOLUME` 和数据库里的实际用户。不要把备份 SQL、`.env` 或密码上传 GitHub。

[返回文档首页](README.md)
