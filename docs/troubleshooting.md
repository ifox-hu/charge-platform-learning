# 故障排查

后端：检查数据库环境变量、端口和 JWT 密钥，然后执行 `docker compose logs --tail 100 backend`。

RabbitMQ：首次恢复已有数据卷可能较慢，执行 `docker compose logs --tail 100 rabbitmq` 和 `docker compose ps`，不要删除数据卷。

前端 502 或白屏：确认 backend healthy、Nginx 配置和 `frontend/dist` 存在。

WebSocket 不更新：确认 Nginx `/ws/` 设置了 `Upgrade` 和 `Connection` 请求头。

小程序站点加载失败：确认 `miniapp/config/env.js` 指向可访问的后端地址；真机需要 HTTPS 合法域名。

小程序定位不准：确认手机系统定位和微信权限已开启。项目使用 `gcj02`，不要把 WGS84 坐标直接用于腾讯地图。
