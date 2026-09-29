# 故障排查

先看容器状态和日志，不要删除数据卷或反复重建整个环境：

```bash
docker compose -f docker-compose.server.yml ps
docker compose -f docker-compose.server.yml logs --tail 80 backend frontend rabbitmq
```

## 后端启动但页面没有业务数据

`/api/health` 只检查服务存活。确认 MySQL 中存在 `sys_user`、`station`、`charger`、`connector`、`price_period`、`charge_order`，并执行了需要的 V2~V4 迁移。再检查 `.env` 中的 `MYSQL_USER`、`MYSQL_PASSWORD` 是否与旧数据卷里的真实账号一致。`MYSQL_ROOT_PASSWORD` 环境变量不会修改已有数据库的 root 密码。

## RabbitMQ unhealthy

旧卷恢复可能需要几十秒，5 秒健康检查超时会误报。离线 Compose 已设 `timeout: 15s`、`start_period: 90s`。查看：

```bash
docker logs --tail 100 charge-platform-learning-rabbitmq-1
docker inspect --format='{{.State.Health.Status}}' charge-platform-learning-rabbitmq-1
```

日志出现 `Server startup complete`、状态最终为 `healthy` 才算恢复。队列深度为 0 可能是消息已被消费者处理。不要删除 RabbitMQ 数据卷。

## HTTPS 页面“拒绝连接”

先检查 443 是否映射：

```bash
docker port charge-platform-learning-frontend-1
docker ps -a --filter name=charge-platform-learning-frontend-1
docker logs --tail 50 charge-platform-learning-frontend-1
```

如果容器是 `Restarting` 且日志有 `cannot load certificate /etc/nginx/certs/server.crt`，检查宿主机 `certs/server.crt` 和 `certs/server.key` 是否存在，以及离线 Compose 是否挂载 `./certs:/etc/nginx/certs:ro`。只有端口映射、容器 `Up`、证书加载三项都正确后，`https://服务器IP` 才能访问。

## Web 地图不弹定位权限

浏览器 Geolocation 只允许安全来源。`http://局域网IP:5173` 即使网页正常，也可能直接拒绝定位。使用 `https://服务器IP` 或本机 `http://localhost`。若是自签名证书，先在浏览器里确认信任，再检查站点权限和系统定位权限。

## 小程序站点加载失败或定位偏差

站点加载失败先检查 `miniapp/config/env.js` 的 `baseUrl`、后端 `8081` 是否可达及微信开发者工具网络面板。手机中的 `localhost` 指手机自己。开发者工具可临时关闭合法域名校验，正式真机需要合法 HTTPS 域名。地图使用 `gcj02`，设备定位精度和开发者工具模拟位置会影响结果。

## Web 加枪后小程序数量不一致

小程序详情页约 5 秒查询数据库枪列表。若仍是旧数量，先重新进入详情或清理开发者工具缓存。数据库新枪会显示，但模拟桩启动参数 `simulator.connectors` 不会自动跟着 Web 改；超过物理枪数的枪没有 TCP 实时数据，不能真实启动充电。请检查编码后缀 A/B/C 等和模拟器物理枪数。

## WebSocket 不推送

浏览器开发者工具 Network 中确认 `/ws/status` 握手；HTTPS 页面应使用 `wss://`。Nginx 配置要转发 `Upgrade` 和 `Connection: upgrade`。连接失败时前端保留轮询兜底，因此页面可能仍在更新。

## 代码修改后服务器不生效

GitHub 更新的是源码，不会自动更新服务器容器里的 JAR 或 `frontend/dist`。后端重新 `mvn package`、模拟桩重新打包、Web 重新 `npm run build`；上传产物后用对应 Compose 文件重建变化的服务。

[返回文档首页](README.md)
