# 数据库与迁移

数据库名默认是 `charge_platform`。部署前先备份：

```bash
mysqldump -uroot -p --single-transaction --routines --events --triggers charge_platform > charge_platform_backup.sql
```

迁移脚本位于 `docs/db/`：

- `V2__station_coordinates.sql`：站点坐标。
- `V3__order_test_and_archive.sql`：测试订单和归档字段。
- `V4__audit_log.sql`：审计日志表。

脚本按版本执行一次。已有数据库执行前确认表结构并保留备份。
