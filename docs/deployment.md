# Docker 部署

## 在线构建

```bash
cp .env.example .env
# 编辑 .env
docker compose up -d --build
docker compose ps
```

## 离线服务器

`docker-compose.server.yml` 使用已有镜像，直接挂载已编译的 JAR 和前端 `dist`：

```bash
docker compose -f docker-compose.server.yml config --quiet
docker compose -f docker-compose.server.yml up -d
docker compose -f docker-compose.server.yml ps
```

不要执行 `docker compose down -v`，这会删除数据卷。更新发布前先备份数据库，再上传 JAR、`dist` 和配置，最后使用 `--force-recreate` 重建对应服务。

浏览器定位要求安全来源。生产环境使用域名证书；局域网演示可以使用自签名证书并挂载到 `certs/`，访问 `https://服务器地址`。
