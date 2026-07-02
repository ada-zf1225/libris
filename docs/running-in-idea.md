# 在 IntelliJ IDEA 中运行 Libris · Running Libris in IntelliJ IDEA

本项目已预置 IDEA 运行配置(`.run/`),打开即可一键启动前后端。

## 前置条件 · Prerequisites

| 依赖 | 说明 |
|------|------|
| IntelliJ IDEA | 2024.1+(WebAuthn 运行配置需较新版本);Ultimate 版对 Spring Boot 支持最佳 |
| JDK 21 | 项目 SDK 已指向 `temurin-21`;若未安装,用任意 JDK 21 并在 `File → Project Structure → Project` 选中 |
| Node ≥ 20 + pnpm | 前端所需;IDEA 一般自动探测,首次若报错见下方"前端"一节 |
| PostgreSQL 16 | 提供 `libris` 库。本机已用 Homebrew 安装并自启:`brew services start postgresql@16` |

数据库连接默认走 `application.yaml`:`localhost:5432/libris`,用户 `libris` / `libris_dev`。首次启动时 Flyway 会自动建表并灌入种子数据(约 290 种真实书目 + 一年流通历史)。

## 打开项目 · Open

`File → Open` 选择仓库根目录,等待右下角 Maven 导入完成(首次会下载依赖并建立索引,几分钟)。导入结束后左侧应显示 `backend [libris-backend]` 模块。

## 运行配置 · Run configurations

右上角运行配置下拉里已有四个(`.run/` 已随仓库提供):

| 配置 | 作用 |
|------|------|
| **Libris ▶ 全栈 (backend + frontend)** | 一键同时启动前后端 —— 推荐 |
| **backend [spring-boot:run]** | 仅后端,走 Maven 插件,最省心 |
| **backend [LibrisApplication]** | 直接运行主类,启动更快、便于断点调试(需 Maven 导入完成) |
| **frontend [pnpm dev]** | 仅前端 Vite 开发服务器 |

启动后:

- 前端 → http://localhost:5173
- 后端 API / Swagger UI → http://localhost:8080/swagger-ui.html
- 健康检查 → http://localhost:8080/actuator/health

演示账号:管理员 `admin` / `LibrisAdmin#2026`,读者 `zhanghua`(教师)、`zhangminghua`(学生) / `LibrisReader#2026`。

## 常见问题 · Troubleshooting

- **端口 8080 被占 / 启动报 `Port 8080 was already in use`**:本地 IDEA 运行与 `docker compose` 全栈不能同时占用 8080。若之前跑过 Docker,先 `docker compose down` 释放端口。
- **前端首次启动报找不到 Node/pnpm**:`Settings → Languages & Frameworks → Node.js`,把 Node interpreter 指向本机 Node(如 mise 的 `~/.local/share/mise/installs/node/*/bin/node`),Package manager 选 `pnpm`。
- **邮件通知**:站内通知始终可用;若要收发邮件(到期/逾期/重置密码),需本地 MailHog:`docker compose up -d mailhog`(SMTP 1025 / Web UI 8025)。缺少 MailHog 不影响应用运行 —— 健康检查已刻意不把邮件计入聚合。
- **数据库未就绪**:确认 `brew services list` 里 `postgresql@16` 为 started;`libris` 库不存在时,首次后端启动会由 Flyway 自动创建结构与种子数据(需要 `libris` 角色有建表权限)。

## 与 Docker 的关系

`docker compose up --build` 是面向部署的一键全栈(见根 [README](../README.md));IDEA 运行配置面向日常开发调试。两者都连本机 5432 的 PostgreSQL,二选一使用即可。
