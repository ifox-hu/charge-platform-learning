# Resume Notes

## Project description

基于 Java 17、Spring Boot 3.5、MyBatis-Plus、MySQL、Redis、RabbitMQ 和 Vue 3 搭建轻量级充电桩运营管理平台，覆盖充电站/充电桩/充电枪管理、分时电价、模拟充电订单、跨时段自动计费、权限控制和订单报表导出。

## Technical highlights

- 使用 Spring Security + JWT 实现 ADMIN/OPERATOR 角色权限；统一处理 401/403 和响应结构。
- 使用 MyBatis-Plus Mapper 访问既有 MySQL 表，订单启动/结束通过事务、行锁和设备状态校验避免重复充电。
- 使用 Redis 缓存看板统计，写操作主动失效缓存，并通过 `CacheErrorHandler` 在 Redis 不可用时回源 MySQL。
- 使用 RabbitMQ 传递订单完成事件，事务提交后发布；增加发布失败隔离和消费者有限重试，避免消息异常影响已提交订单。
- 使用 Spring Boot Actuator 暴露健康、信息和指标端点，区分公开探针与 ADMIN 运行数据权限。
- 使用 Bean Validation、CSV UTF-8 BOM 导出、MockMvc/Mockito 单元测试和 H2 测试 profile，当前后端测试 `16/16` 通过。

## Interview explanation

重点讲清一条链路即可：登录拿 JWT -> 请求经过过滤器解析角色 -> Controller 校验参数 -> Service 校验站点/设备/电价和事务边界 -> Mapper 执行 SQL -> 订单提交后异步发送 RabbitMQ 事件；看板读 Redis，缓存故障时仍能从 MySQL 返回。

Spring Cloud 暂不作为项目卖点。只有当需要独立部署、独立扩缩容或跨团队治理时，再拆分服务并引入注册配置中心、网关和链路追踪。
