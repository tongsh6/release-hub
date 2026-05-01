# Phase 5 执行日志：部署与容器化文档

> 日期：2026-05-02
> 执行者：AI
> 状态：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | Phase 5：部署与容器化文档 |
| 用户价值 | ✅ | 开发者可参考文档完成完整部署 |
| 端到端路径 | ✅ | 文档类任务，覆盖后端 + 前端 + Docker + Nginx |
| 单一目标 | ✅ | 仅编写部署与容器化文档 |
| 可独立验证 | ✅ | Markdown 文件内容完整 |
| 可回滚 | ✅ | 仅新增/修改文档文件 |
| 依赖明确 | ✅ | 依赖现有配置文件和环境变量 |
| 风险收敛 | ✅ | 纯文档类变更，无代码副作用 |

## 涉及文件

### 新建（1）
| 文件 | 说明 |
|------|------|
| `docs/deployment.md` | 完整部署指南：Docker/后端/前端/Nginx/安全检查清单 |

### 修改（1）
| 文件 | 变更 |
|------|------|
| `docs/docker-compose.yml` | 修复配置对齐 local profile（用户/密码/端口/数据库名） |

## 文档覆盖内容

1. **部署架构概览**：Nginx → Vue SPA + Spring Boot → PostgreSQL 全链路
2. **Docker Compose**：PostgreSQL 容器快速启动
3. **后端部署**：
   - 环境要求（JDK 21, Maven 3.9+, PostgreSQL 18.1）
   - 5 种 Profile 对照表（local/test/e2e/prd）
   - Maven/Jar 启动步骤
   - 关键配置项（数据库/JWT/CORS）
   - 默认账号说明
4. **前端部署**：
   - 环境要求（Node.js, pnpm）
   - 构建命令（dev/test/prd）
   - 环境变量表（VITE_API_BASE_URL/VITE_APP_TITLE/VITE_PROXY_TARGET）
   - Nginx 静态部署 + 反向代理完整配置示例
5. **Flyway 迁移策略**：各 Profile 的行为差异 + 命名规范
6. **Docker 全栈部署**：后端/前端 Dockerfile 示例 + 构建命令
7. **生产检查清单**：安全/数据库/监控/前端 4 类 15+ 检查项
8. **常见问题**：4 个 FAQ

## docker-compose.yml 修复详情

| 字段 | 修复前 | 修复后 | 原因 |
|------|--------|--------|------|
| POSTGRES_USER | release_hub | postgres | 对齐 local profile |
| POSTGRES_PASSWORD | 123456 | "123456" | 加引号防 YAML 解析问题 |
| POSTGRES_DB | release_hub | release_hub | 不变 |
| 端口映射 | 5432:5432 | 5433:5432 | 对齐 local profile（避免与本机 PG 冲突） |
| 健康检查 | pg_isready -U release_hub | pg_isready -U postgres -d release_hub | 对齐新用户名 + 指定数据库 |
| version 字段 | version: '3.8' | 移除 | Docker Compose V2 已弃用 |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | 覆盖开发/测试/生产全场景部署 |
| 层级闭环 | ✅ | 文档类任务，不需代码层级 |
| 测试闭环 | ✅ | N/A（纯文档任务） |
| 架构闭环 | ✅ | 部署架构与代码架构一致 |
| 性能闭环 | ✅ | N/A |
| 文档闭环 | ✅ | 本日志 + deployment.md |
| 工作区闭环 | ✅ | 2 文件变更 |

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 无 | — | — |
