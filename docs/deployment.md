# Docker 部署与更新

仓库有两份 Compose：

| 文件 | 用途 | 前端入口 | 产物来源 |
|---|---|---|---|
| `docker-compose.yml` | 联网主机、首次构建 | `http://localhost:5173` | Docker 多阶段构建 |
| `docker-compose.server.yml` | 离线服务器、复用现有镜像 | `https://服务器IP` | 本地编译的 JAR 和 `frontend/dist` |

部署前必须准备**已有基础数据库**，见[数据库与迁移](database.md)。MySQL 容器 `healthy` 只表示服务可连接，不表示业务表和管理员账号已经存在。

## 1. 配置 `.env`

Linux：`cp .env.example .env`；PowerShell：`Copy-Item .env.example .env`。编辑 `.env`，设置 MySQL、RabbitMQ 和 JWT 参数。不要把 `.env` 上传到 GitHub 或发到聊天中。

已有数据卷的实际 MySQL 密码和 RabbitMQ 用户由卷内数据决定，**改 `.env` 不会重设旧密码**。如果要复用旧卷，在 `.env` 中配置 `MYSQL_DATA_VOLUME`、`MYSQL_CONFIG_VOLUME`、`REDIS_DATA_VOLUME`、`RABBITMQ_DATA_VOLUME`、`RABBITMQ_PLUGINS_VOLUME` 和旧 `RABBITMQ_HOSTNAME`。先核对卷名：

```bash
docker inspect mysql --format '{{range .Mounts}}{{println .Name "->" .Destination}}{{end}}'
docker inspect redis --format '{{range .Mounts}}{{println .Name "->" .Destination}}{{end}}'
docker inspect rabbitmq --format '{{range .Mounts}}{{println .Name "->" .Destination}}{{end}}'
```

不要删除现有数据卷，也不要执行 `docker compose down -v`。

## 2. 在线构建版

主机能访问 Docker Hub、Maven 和 npm 时，在项目根目录执行：

```bash
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps
```

访问 `http://localhost:5173`。这份配置使用 `frontend/nginx.http.conf`，适合本机演示；若从别的设备通过局域网 HTTP IP 访问，浏览器定位接口会被禁止。后端访问 `http://localhost:8081/api/health`。

## 3. 离线服务器版

先在本机构建：

```powershell
cd E:\充电桩\charge-platform-learning\backend
mvn package
cd ..\simulator
mvn package
cd ..\frontend
npm ci
npm run build
```

上传下面内容到服务器相同相对路径：

```text
docker-compose.server.yml
.env（只在服务器填写真实值，不进 Git）
backend/target/charge-platform-learning-0.1.0-SNAPSHOT.jar
simulator/target/charge-platform-simulator-0.1.0-SNAPSHOT.jar
frontend/dist/
frontend/nginx.conf
docs/db/（只在确需迁移时使用）
```

服务器要已有 `centos7-jdk17:latest`、`nginx:1.22.1`、`mysql:8.0.30`、`redis:7.0.10`、`rabbitmq:3.13-management` 镜像。用 `docker image inspect ...` 检查。`docker-compose.server.yml` 中设置 `pull_policy: never`，不会替你下载镜像。

### HTTPS 证书

离线版 Nginx 配置监听 443 并读取 `/etc/nginx/certs/server.crt`、`server.key`。Compose 把宿主机 `./certs` 挂载进去，**证书缺失时前端容器会反复重启**。有正式域名时使用受信任证书；仅局域网演示可用包含 IP SAN 的自签名证书：

```bash
mkdir -p certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout certs/server.key -out certs/server.crt \
  -subj '/CN=192.168.6.100' \
  -addext 'subjectAltName=IP:192.168.6.100'
chmod 600 certs/server.key
```

示例 IP 按实际服务器地址替换。自签名证书不适合公开生产环境；浏览器首次访问要在本机确认信任。不要上传 `certs/` 到 GitHub。

### 启动和核验

```bash
docker compose -f docker-compose.server.yml config --quiet
docker compose -f docker-compose.server.yml up -d
docker compose -f docker-compose.server.yml ps
docker port charge-platform-learning-frontend-1
```

前端应该 `Up`，`docker port` 中应有 `443/tcp -> 0.0.0.0:443`；backend、MySQL、Redis、RabbitMQ 应为 `healthy`。访问 `https://服务器IP`。RabbitMQ 首次恢复旧卷可能需要几十秒；其健康检查使用 90 秒启动宽限期。

## 4. 更新发布

1. 修改代码后运行 `mvn test`、`npm run build`。
2. 备份数据库，并确认备份非空。
3. 重新打包 JAR；上传新的 JAR、`dist` 和变化的配置文件。
4. 先检查 `docker compose -f docker-compose.server.yml config --quiet`。
5. 重建变化的服务，例如 `docker compose -f docker-compose.server.yml up -d --force-recreate backend frontend simulator-001 simulator-002 simulator-003`。
6. 核对 `ps`、`logs`、健康接口、页面登录和一次充电链路。

构建产物 `target/`、`dist/` 不放进 GitHub；从仓库拉取源码后必须重新构建。服务器的 `.env` 和证书应保留在服务器，不用 Git 覆盖。

[返回文档首页](README.md)
