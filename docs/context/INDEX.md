# ReleaseHub 项目知识库

> 本目录是项目的长期记忆中心，按领域分层组织，供 AI 按需加载上下文。

## 目录结构

```
context/
├── business/          # 业务领域知识
│   ├── domain-model.md      # 领域模型
│   ├── project-plan.md      # 项目规划
│   └── user-stories-v1.3.md  # 用户故事
├── tech/              # 技术背景
│   ├── architecture/        # 架构文档
│   ├── api/                 # API 文档
│   ├── conventions/         # 开发规范
│   └── services/            # 服务分析（按需生成）
└── experience/        # 历史经验
    ├── reports/             # 审计报告
    └── lessons/             # 踩坑记录
```

## 使用指南

### 何时加载

| 场景 | 推荐加载 |
|------|----------|
| 理解业务需求 | `business/` |
| 技术实现决策 | `tech/architecture/` |
| API 开发 | `tech/api/` |
| 编写后端代码 | `tech/conventions/backend.md` |
| 编写前端代码 | `tech/conventions/frontend.md` |
| 编写测试 | `tech/conventions/testing.md` |
| 数据库迁移 | `tech/conventions/database.md` |
| 避免重复踩坑 | `experience/` |

### 内容维护原则

1. **位置即语义**：文档放在哪里，就代表它属于哪个领域
2. **复利沉淀**：每次问题排查后，将经验记录到 `experience/lessons/`
3. **及时更新**：架构变更后同步更新 `tech/architecture/`

## 快速导航

### 业务知识
- [领域模型](business/domain-model.md) - DDD 核心概念
- [项目规划](business/project-plan.md) - 整体规划与里程碑

### 技术文档
- [仓库快照](tech/REPO_SNAPSHOT.md) - 技术栈、模块划分、基础命令（AI 快速理解）
- [AI 工程治理准则](tech/architecture/ai-engineering-governance.md) - 长期演进原则、垂直切片、深模块、DAG、切面化治理
- [后端架构](tech/architecture/backend.md) - 模块划分与依赖
- [Release Window API](tech/api/release-window.md) - 核心 API 文档

### 开发规范
- [后端开发规范](tech/conventions/backend.md) - Java DDD 模式
- [前端开发规范](tech/conventions/frontend.md) - Vue 3 组合式 API
- [TDD 测试规范](tech/conventions/testing.md) - 测试驱动开发
- [数据库迁移规范](tech/conventions/database.md) - Flyway 迁移
