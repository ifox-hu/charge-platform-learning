# Charge Platform 开发文档

面向本地开发、模拟充电桩联调和 Docker 部署的项目指南。

## 阅读路线

- [快速开始](getting-started.md)
- [项目结构](project-structure.md)
- [本地开发](local-development.md)
- [模拟充电桩联调](simulator.md)
- [Docker 部署](deployment.md)
- [接口与实时通信](api-and-realtime.md)
- [数据库迁移](database.md)
- [故障排查](troubleshooting.md)

项目覆盖站点、设备、分时电价、订单、自动计费、模拟设备通信、Redis、RabbitMQ 和 WebSocket。

不要提交 `.env`、数据库密码、JWT 密钥、证书、生产日志或数据库备份。复制 `.env.example` 为 `.env` 后只在本机或服务器填写真实值。
