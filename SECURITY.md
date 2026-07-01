# Security Policy · 安全策略

## Reporting a Vulnerability · 漏洞报告

如发现安全问题,请**不要**公开提 Issue,直接邮件联系 <zf1225@ic.ac.uk>,我会在 72 小时内响应。
Please **do not** open a public issue for security problems. Email <zf1225@ic.ac.uk>; you will get a response within 72 hours.

## Security Design · 安全设计

Libris 按 OWASP Top 10 对照设计,核心承诺:

| 领域 | 措施 |
|---|---|
| 认证 Authentication | BCrypt 密码哈希;登录成功轮换 Session ID(防会话固定);登录失败限流(429);改密码需验证旧密码;密码强度校验 |
| 授权 Authorization | URL 级 + 方法级(`@PreAuthorize`)双层 RBAC;"我的"资源全部做属主校验,防水平越权;鉴权矩阵有集成测试覆盖 |
| 会话 Session | Cookie `httpOnly` + `SameSite=Lax`(生产加 `Secure`);登出服务端失效;SPA 采用 Cookie-based CSRF Token |
| 注入 Injection | 全部数据访问经 JPA 参数化查询,禁止字符串拼接 SQL;输入经 Bean Validation 白名单校验 |
| XSS | 前端不使用 `v-html`,用户输入一律按纯文本渲染 |
| 信息暴露 | RFC 7807 统一错误响应,不泄露堆栈;Actuator 仅暴露健康检查;生产环境关闭 OpenAPI 页面 |
| HTTP 安全头 | CSP、`X-Content-Type-Options`、`Referrer-Policy`、`X-Frame-Options`;生产反代启用 HSTS |
| 供应链 Supply chain | Dependabot(Maven / npm / Actions)+ CI 依赖审计,高危漏洞阻断合并 |
| 秘密管理 Secrets | 仓库零秘密:配置走环境变量,提供 `.env.example`;日志不记录敏感字段 |
| 容器 Container | 多阶段构建、非 root 运行 |

## Supported Versions

| Version | Supported |
|---|---|
| main / latest release | ✅ |
