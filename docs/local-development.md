# 本地开发

## IDEA 启动后端

设置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 和 `JWT_SECRET`。模拟桩联调时设置：

```text
SIMULATOR_ENABLED=true
SIMULATOR_SYNC_DATABASE=true
SIMULATOR_DEVICES=SIM-PILE-001@127.0.0.1:9100,SIM-PILE-002@127.0.0.1:9101,SIM-PILE-003@127.0.0.1:9102
```

## 前端

```bash
cd frontend
npm ci
npm run dev
npm run build
```

API 统一封装在 `src/api.js`，开发代理在 `vite.config.js`。

## 小程序

在微信开发者工具打开 `miniapp/`。API 地址在 `miniapp/config/env.js`。使用局域网 HTTP 地址时，开发者工具中关闭合法域名和 HTTPS 证书校验；真机正式使用应配置 HTTPS 合法域名。定位和地图统一使用 `gcj02` 坐标。

## 测试

```bash
cd backend
mvn test
```
