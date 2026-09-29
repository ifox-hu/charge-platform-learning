# 数据库、迁移与备份

项目默认连接 MySQL 的 `charge_platform` 库。后端使用 MyBatis-Plus，不会自动生成基础业务表，也不会自动创建演示账号。基础表包括 `sys_user`、`station`、`charger`、`connector`、`price_period`、`charge_order`。仓库中的 `docs/db` 只有 V2~V4 增量脚本；新空库不能只执行这些脚本。

## 使用已有数据库

先确认原库确实包含这些表和可登录用户，再备份。以下是在宿主机装有 MySQL 客户端时的交互式示例，`-p` 会提示输入密码，不把密码写进命令历史：

```bash
mysqldump -h 127.0.0.1 -uroot -p \
  --single-transaction --routines --events --triggers \
  charge_platform > charge_platform_backup.sql
test -s charge_platform_backup.sql && echo '备份文件非空'
```

如果 MySQL 在 Docker 中、宿主机没有客户端，可用容器内 `mysqldump`。要使用**数据库中真实有效的账号**；容器环境变量中的密码值可能与旧数据卷里的账号不一致。不要把密码、完整 `.env` 或 SQL 备份粘贴到公开聊天或 GitHub。

## 迁移脚本

| 文件 | 作用 | 前置条件 |
|---|---|---|
| `V2__station_coordinates.sql` | `station` 经纬度及坐标类型 | 已有 `station` 表 |
| `V3__order_test_and_archive.sql` | 测试订单、归档标记 | 已有 `charge_order` 表 |
| `V4__audit_log.sql` | 创建 `audit_log` 表和索引 | MySQL 8 |

如果旧库尚未执行过某个版本，按 V2 → V3 → V4 顺序执行。示例：

```bash
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V2__station_coordinates.sql
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V3__order_test_and_archive.sql
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V4__audit_log.sql
```

这些 SQL 没有 `IF NOT EXISTS`，**不要重复执行**。先用 `SHOW COLUMNS FROM station LIKE 'latitude';`、`SHOW COLUMNS FROM charge_order LIKE 'test_order';` 和 `SHOW TABLES LIKE 'audit_log';` 检查是否已完成。

V3 会把执行前的订单默认标记为测试订单，清理接口只归档已完成的测试订单。若你的库已有真实订单，需要先评估数据，再决定是否直接执行该脚本。

## 恢复

在测试库或停机窗口恢复；恢复会覆盖目标库当前状态，应先保留一份新的备份。

```bash
mysql -h 127.0.0.1 -uroot -p charge_platform < charge_platform_backup.sql
```

恢复后检查六张基础表、迁移字段、用户账号，再启动 backend。更换服务器时还要核对 Compose 的 `MYSQL_DATA_VOLUME` 是否指向正确数据卷。

[返回文档首页](README.md)
