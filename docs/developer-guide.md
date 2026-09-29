# 充电桩运营平台开发手册

这是项目的统一开发文档，覆盖环境准备、架构、本地开发、模拟桩、接口、数据库、Docker 部署和故障排查。

## 目录

1. [项目概览](#1-项目概览)
2. [环境准备与首次启动](#2-环境准备与首次启动)
3. [项目结构与架构](#3-项目结构与架构)
4. [本地开发](#4-本地开发)
5. [模拟桩联调](#5-模拟桩联调)
6. [接口和实时通信](#6-接口和实时通信)
7. [数据库](#7-数据库)
8. [Docker 部署](#8-docker-部署)
9. [故障排查](#9-故障排查)
10. [验证清单](#10-验证清单)

## 1. 项目概览

项目由 Java 17、Spring Boot 3.5、MyBatis-Plus、Vue 3、MySQL、Redis、RabbitMQ、微信小程序和 TCP 模拟桩组成。

主要功能：

- 站点、充电桩、充电枪、分时电价和订单管理。
- ADMIN/OPERATOR 权限、操作审计、订单导出。
- Redis 看板缓存，RabbitMQ 订单完成事件和死信队列。
- WebSocket 设备状态推送，断线后轮询兜底。
- Web 与小程序地图定位、地址解析和模拟充电。

完整链路：客户端选择空闲枪 -> 后端校验并锁定 -> 发送 START -> 模拟桩上报电量 -> 发送 STOP -> 分时电价结算 -> 事务提交后发布订单完成事件。

仓库：https://github.com/ifox-hu/charge-platform-learning

## 2. 环境准备与首次启动

### 2.1 软件

| 工具 | 用途 | 检查 |
|---|---|---|
| JDK 17 | 后端、模拟桩 | java -version |
| Maven 3.9+ | 构建测试 | mvn -version |
| Node.js/npm | Web 构建 | node -v、npm -v |
| Docker Compose | 容器运行 | docker compose version |
| 微信开发者工具 | 小程序调试 | 选择 miniapp/ |

### 2.2 获取代码

~~~bash
git clone https://github.com/ifox-hu/charge-platform-learning.git
cd charge-platform-learning
~~~

复制 .env.example 为 .env，填写数据库、RabbitMQ 和 JWT_SECRET。密码、证书、备份和日志不要上传 GitHub。

### 2.3 新数据库

空库按 V1 到 V4 顺序执行：

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | docs/db/V1__baseline_schema.sql | 六张基础表 |
| V2 | docs/db/V2__station_coordinates.sql | 站点 GCJ-02 坐标 |
| V3 | docs/db/V3__order_test_and_archive.sql | 测试订单、归档字段 |
| V4 | docs/db/V4__audit_log.sql | 审计表 |

Docker MySQL 只在全新空数据卷第一次启动时自动执行 docs/db。演示账号和演示设备不会自动创建，本地需要时手动导入：

~~~bash
docker compose -f docker-compose.yml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' < docs/demo_seed.sql
~~~

demo_admin、demo_operator 的演示密码是 123456，只用于本地隔离环境。

已有数据库不要执行 V1 或 demo_seed.sql。先备份，再检查并只执行缺少的 V2、V3、V4：

~~~sql
SHOW TABLES;
SHOW COLUMNS FROM station LIKE 'latitude';
SHOW COLUMNS FROM charge_order LIKE 'test_order';
SHOW TABLES LIKE 'audit_log';
~~~

### 2.4 首次启动

~~~bash
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps
~~~

访问 http://localhost:5173；后端健康检查为 http://localhost:8081/api/health。局域网 IP 的浏览器定位需要 HTTPS。

## 3. 项目结构与架构

~~~text
backend/       Spring Boot 后端、权限、订单、TCP 适配器
frontend/      Vue 3 + Vite Web 管理端
miniapp/       微信小程序
simulator/     Java TCP 模拟充电桩
docs/          统一手册、迁移脚本和部署资料
~~~

请求链路：

~~~text
客户端 -> JWT 过滤器 -> Controller -> Service -> Mapper -> MySQL
                                      |             |
                                      +-> Redis     +-> 提交后 RabbitMQ
~~~

JWT 过滤器解析令牌，SecurityConfig 负责 ADMIN/OPERATOR 权限。订单停止使用事务和行锁，提交后才发送 OrderCompletedEvent。Redis 缓存默认 30 秒，故障时回源 MySQL。模拟桩只通过 TCP 连接后端。

## 4. 本地开发

### 4.1 IDEA 环境变量

~~~text
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=charge_platform
DB_USERNAME=本地数据库用户
DB_PASSWORD=本地数据库密码
JWT_SECRET=至少32字符的随机字符串
REDIS_HOST=127.0.0.1
RABBITMQ_HOST=127.0.0.1
SIMULATOR_ENABLED=true
SIMULATOR_SYNC_DATABASE=true
SIMULATOR_DEVICES=SIM-PILE-001@127.0.0.1:9100,SIM-PILE-002@127.0.0.1:9101,SIM-PILE-003@127.0.0.1:9102
~~~

不要把真实密码写进 application.yml。PowerShell 临时变量格式是 $env:变量名 = '值'。

### 4.2 后端

~~~powershell
cd E:\充电桩\charge-platform-learning\backend
mvn test
mvn spring-boot:run
~~~

使用仓库内缓存：mvn "-Dmaven.repo.local=.m2-repo" test。后端端口为 8081。

### 4.3 Web

~~~powershell
cd E:\充电桩\charge-platform-learning\frontend
npm ci
npm run dev -- --host 0.0.0.0
~~~

访问 http://localhost:5174。Vite 把 /api 和 /ws 代理到 8081；发布执行 npm run build。

### 4.4 小程序

微信开发者工具选择 miniapp/，修改 miniapp/config/env.js 的 baseUrl。手机中的 localhost 指手机本身；正式真机需要 HTTPS 合法域名。地图坐标使用 gcj02。小程序约每 5 秒刷新枪状态，Web 增加数据库枪后，模拟器物理枪数仍需手动调整。

## 5. 模拟桩联调

| 设备 | 端口 | 默认枪数 |
|---|---:|---:|
| SIM-PILE-001 | 9100 | 2 |
| SIM-PILE-002 | 9101 | 2 |
| SIM-PILE-003 | 9102 | 4 |

模拟器使用逐行 JSON TCP 协议，支持 HELLO、STATUS、START、STOP、PING。time-scale=1 是真实速度。

~~~powershell
cd E:\充电桩\charge-platform-learning\simulator
mvn package
java -Dsimulator.port=9100 -Dsimulator.device-id=SIM-PILE-001 -Dsimulator.connectors=2 -Dsimulator.time-scale=1 -jar target\charge-platform-simulator-0.1.0-SNAPSHOT.jar
java -Dsimulator.port=9101 -Dsimulator.device-id=SIM-PILE-002 -Dsimulator.connectors=2 -Dsimulator.time-scale=1 -jar target\charge-platform-simulator-0.1.0-SNAPSHOT.jar
java -Dsimulator.port=9102 -Dsimulator.device-id=SIM-PILE-003 -Dsimulator.connectors=4 -Dsimulator.time-scale=1 -jar target\charge-platform-simulator-0.1.0-SNAPSHOT.jar
Get-NetTCPConnection -LocalPort 9100,9101,9102 -State Listen
~~~

联调步骤：确认三个端口监听；启动后端并检查 GET /api/simulator/status；确认设备和枪编码映射；启动订单；查询 GET /api/orders/{id}/live；停止订单并核对 COMPLETED 和最终金额。

Web 新增第 5 把枪只改了数据库，不能自动增加模拟器物理枪。必须增加 simulator.connectors 并重启对应模拟桩。

## 6. 接口和实时通信

基础地址为 http://localhost:8081/api。除登录和健康检查外都需要 Authorization: Bearer token。

~~~http
POST /api/auth/login
Content-Type: application/json

{"username":"已有账号","password":"账号密码"}
~~~

常用接口：

| 模块 | 接口 |
|---|---|
| 站点 | GET/POST/PUT/DELETE /stations |
| 桩 | GET/POST/PUT/DELETE /chargers |
| 枪 | GET/POST/PUT/DELETE /connectors |
| 电价 | GET/POST/PUT/DELETE /price-periods |
| 订单 | GET /orders、POST /orders/start、POST /orders/{id}/stop |
| 审计 | GET /audit-logs、DELETE /audit-logs/before?days=7 |

启动订单字段包含 stationId、connectorId、plateNumber、testOrder。车牌是地区简称、大写字母和 5 或 6 位数字。模拟桩启用时停止请求体可以为空，后端读取最终电量。

WebSocket 为 ws://localhost:8081/ws/status，HTTPS 页面使用 wss://。Nginx 必须转发 Upgrade 和 Connection。RabbitMQ 使用 charge.order.exchange、charge.order.completed 和 charge.order.completed.dlq；队列为 0 可能代表消息已被即时消费。

## 7. 数据库

V1 建立 sys_user、station、charger、connector、price_period、charge_order；V2 增加坐标；V3 增加 test_order、archived、archived_at；V4 建立 audit_log。

手动建库：

~~~sql
CREATE DATABASE charge_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
~~~

~~~bash
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V1__baseline_schema.sql
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V2__station_coordinates.sql
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V3__order_test_and_archive.sql
mysql -h 127.0.0.1 -uroot -p charge_platform < docs/db/V4__audit_log.sql
~~~

备份与恢复：

~~~bash
mysqldump -h 127.0.0.1 -uroot -p --single-transaction --routines --events --triggers charge_platform > charge_platform_backup.sql
test -s charge_platform_backup.sql && echo '备份文件非空'
mysql -h 127.0.0.1 -uroot -p charge_platform < charge_platform_backup.sql
~~~

恢复会替换目标库，执行前再次备份。不要上传备份、.env 或密码。

## 8. Docker 部署

在线版使用 docker-compose.yml；离线服务器使用 docker-compose.server.yml。离线版入口为 HTTPS 服务器 IP。

本地构建：

~~~powershell
cd E:\充电桩\charge-platform-learning\backend; mvn package
cd ..\simulator; mvn package
cd ..\frontend; npm ci; npm run build
~~~

上传服务器：Compose 文件、服务器 .env、两个 JAR、frontend/dist、frontend/nginx.conf 和需要的 docs/db。服务器需已有 centos7-jdk17、nginx、mysql、redis、rabbitmq 镜像。

启动核验：

~~~bash
docker compose -f docker-compose.server.yml config --quiet
docker compose -f docker-compose.server.yml up -d
docker compose -f docker-compose.server.yml ps
docker port charge-platform-learning-frontend-1
~~~

离线版 Nginx 监听 443，需要 certs/server.crt 和 certs/server.key。证书缺失时前端容器会反复重启。更新时先备份，再上传新 JAR/dist，最后执行：

~~~bash
docker compose -f docker-compose.server.yml up -d --force-recreate backend frontend simulator-001 simulator-002 simulator-003
~~~

不要使用 docker compose down -v，以免删除数据卷。

## 9. 故障排查

先看状态和日志：

~~~bash
docker compose -f docker-compose.server.yml ps
docker compose -f docker-compose.server.yml logs --tail 80 backend frontend rabbitmq
~~~

- 页面无数据：检查六张基础表、audit_log、V2 到 V4；全新库还需手动导入 demo_seed.sql。
- RabbitMQ unhealthy：等待旧卷恢复，查看日志和 State.Health.Status，不要删除数据卷。
- HTTPS 拒绝连接：检查 443 映射、容器状态、证书路径和 Nginx 日志。
- 浏览器不弹定位：局域网 HTTP IP 不是安全来源，使用 HTTPS 或 localhost。
- 小程序加载失败：检查 env.js 的 baseUrl、8081 可达性、合法域名和 gcj02。
- Web 加枪后数量不一致：重新进入详情；数据库枪数和模拟器物理枪数需要分别配置。
- WebSocket 不推送：检查 /ws/status 握手、wss 和 Nginx Upgrade 转发。
- 代码修改不生效：重新打包 JAR、重新构建 dist、上传并重建对应容器。

## 10. 验证清单

| 改动 | 最低验证 |
|---|---|
| 后端 | mvn test、健康接口、相关业务接口 |
| Web | npm run build、登录和关键交互 |
| 小程序 | 开发者工具编译、页面切换、定位 |
| 模拟桩 | mvn test、三个 TCP 连接、START/STOP |
| 数据库 | 备份、按 V1 到 V4 执行、检查字段索引 |
| Docker | config --quiet、ps、日志、HTTPS、完整充电链路 |

旧版分主题文件和 Windows 联调记录仍保留在 docs/，用于兼容旧链接；本文件是统一入口。

