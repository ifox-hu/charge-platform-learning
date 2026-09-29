# 快速开始

## 环境要求

- Java 17
- Maven 3.9+
- Node.js 18+
- MySQL 8、Redis 7、RabbitMQ 3.13
- 微信开发者工具（需要联调小程序时）

## 获取和配置

```bash
git clone https://github.com/ifox-hu/charge-platform-learning.git
cd charge-platform-learning
cp .env.example .env
```

编辑 `.env`，至少修改数据库密码和 `JWT_SECRET`。已有数据库卷时，密码必须与原数据库账号一致。

## 一键启动

```bash
docker compose up -d --build
docker compose ps
```

后端默认监听 `8081`，健康检查地址为 `http://localhost:8081/api/health`，前端开发地址通常为 `http://localhost:5174`。

## 分别启动

```bash
cd backend && mvn spring-boot:run
cd frontend && npm ci && npm run dev
```

登录账号来自数据库，不写入源码。登录后依次验证站点、设备、订单开始、实时电量和结束结算。
