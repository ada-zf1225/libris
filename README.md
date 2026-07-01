# Libris · 图书馆管理系统

[![CI](https://github.com/ada-zf1225/libris/actions/workflows/ci.yml/badge.svg)](https://github.com/ada-zf1225/libris/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> 🚧 项目处于活跃开发中,路线图见 [Issues](https://github.com/ada-zf1225/libris/issues) 与 [Milestones](https://github.com/ada-zf1225/libris/milestones)。
> 🚧 Under active development — see [Issues](https://github.com/ada-zf1225/libris/issues) and [Milestones](https://github.com/ada-zf1225/libris/milestones) for the roadmap.

**Libris** 是一个单馆规模的现代图书馆管理系统(ILS),产品形态对标学术图书馆业界标准 Ex Libris **Primo**(读者发现层)与 **Alma**(馆员流通后台):全文检索与分面过滤、单册级实时在馆状态、借阅/续借/预约/罚款的完整流通工作流,以及读者自助的 My Library。中英双语界面。

**Libris** is a modern, single-branch Integrated Library System whose product shape follows the academic-library standard set by Ex Libris **Primo** (patron discovery) and **Alma** (staff fulfilment): full-text search with facets, real-time per-copy availability, a complete circulation workflow (loans, renewals, holds, fines), and a self-service My Library account. Bilingual UI (中文 / English).

## 功能总览 · Features

| 读者端 Patron | 馆员端 Staff |
|---|---|
| 🔍 全文检索 + 分面过滤 + 输入建议 Full-text search, facets, suggestions | 🏛 流通台:扫码借还续、归还智能路由 Circulation desk with barcode check-out/in |
| 📖 详情页单册级实时状态 Real-time per-copy availability | 📚 书目/单册两级管理 Bibliographic records & item copies |
| 📅 My Library:借阅/续借/历史/罚款 Loans, renewals, history, fines | 👥 读者管理与自动封锁 Patron management & automatic blocks |
| 🔖 预约与到书通知 Holds with pickup notifications | ⚙️ 流通政策配置 Configurable loan policies |
| ⭐ 收藏夹 · 📮 荐购 Favourites & purchase suggestions | 📊 运营仪表盘 · 审计日志 Dashboard & audit log |

## 技术栈 · Tech Stack

- **Backend**: Java 21 · Spring Boot 3 · Spring Security · Spring Data JPA · Flyway · PostgreSQL 16(全文检索 + pg_trgm)
- **Frontend**: Vue 3 · TypeScript · Vite · Element Plus · Pinia · vue-i18n
- **Quality & Ops**: JUnit 5 + Testcontainers · GitHub Actions CI · Dependabot · Docker Compose

## 快速开始 · Quick Start

> 完整的一键启动(Docker Compose)与部署文档将随 v2.0.0 发布。
> One-command startup (Docker Compose) and deployment docs land with v2.0.0.

## 设计边界 · Scope

单馆系统。以下能力有意不在范围内(需要馆间网络或行业数据源):馆际互借/文献传递、MARC 编目、电子资源链接解析、多馆多流通台、真实支付网关、短信通道、引文网络。
Single-branch by design. Deliberately out of scope: interlibrary loan / document delivery, MARC cataloguing, link resolvers, multi-branch circulation, real payment gateways, SMS, citation graphs.

## 开发规范 · Development

- 分支:`feat/*`、`fix/*` → PR → CI 绿 → squash merge
- 提交:[Conventional Commits](https://www.conventionalcommits.org/)
- 安全策略见 [SECURITY.md](SECURITY.md)

## License

[MIT](LICENSE) © 2026 Fan Ziheng
