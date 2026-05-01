# ReleaseHub 特性开发快速参考

> 快速查阅卡片，详细指南见 [FEATURE_DEVELOPMENT_GUIDE.md](FEATURE_DEVELOPMENT_GUIDE.md)

## 🚀 快速开始

### 新功能开发

```
用户："我要添加 XXX 功能"
```

AI 自动执行：
1. ✅ 分析任务 → 加载上下文 → 检索经验
2. ✅ 完整目标蓝图 → 分阶段任务 DAG → 未完成项追踪位置
3. ✅ OpenSpec 门禁 → 创建提案（如需要）
4. ✅ 实现代码 → 编写测试
5. ✅ 静态扫码 → 修复 TopN → 记录复扫证据
6. ✅ 沉淀经验 → 更新索引

### Bug 修复

```
用户："修复 XXX 问题"
```

AI 自动执行：
1. ✅ 分析问题 → 加载相关上下文
2. ✅ 明确完整修复目标 → 当前修复步骤 → 后续风险追踪
3. ✅ 跳过 OpenSpec（Bug 修复）
4. ✅ 修复代码 → 添加测试
5. ✅ 静态扫码 → 修复 TopN → 记录复扫证据
6. ✅ 更新经验索引

### 重构

```
用户："重构 XXX，保持行为不变"
```

AI 自动执行：
1. ✅ 分析重构范围
2. ✅ 完整重构蓝图 → 分阶段推进 → 未完成项追踪
3. ✅ 跳过 OpenSpec（不改变行为）
4. ✅ 重构代码 → 验证测试通过
5. ✅ 静态扫码 → 修复 TopN → 记录复扫证据

---

## 📋 开发阶段

| 阶段 | Agent | 触发条件 | 输出 |
|------|-------|---------|------|
| **需求分析** | Phase Router | 用户请求 | 任务类型、上下文列表 |
| **OpenSpec 门禁** | OpenSpec Gate | 任务分析后 | 允许的下一阶段 |
| **提案创建** | Proposal Agent | 需要提案 | 完整目标蓝图 + `openspec/changes/{id}/` |
| **技术设计** | Design Agent | 复杂功能 | 完整架构蓝图 + `design.md` |
| **代码实现** | Implement Agent | 提案/设计完成 | 代码实现 |
| **测试编写** | Test Agent | 代码完成 | 测试代码 |
| **静态扫码** | Static Scan TopN | 代码实现后 | `.ai/reports/static-scan/` 报告 |
| **知识沉淀** | 自动 | 任务完成 | 经验文档、索引更新 |

---

## 🔧 关键 Skills

| Skill | 功能 | 使用场景 |
|-------|------|----------|
| `skill-context-loader` | 自动加载相关上下文 | 任务开始时 |
| `skill-experience-indexer` | 检索历史经验 | 遇到类似问题时 |
| `skill-openspec-gate` | OpenSpec 门禁 | 新功能开发前 |
| `skill-task-analyzer` | 分析任务类型和阶段 | 任务开始时 |

---

## 📚 上下文加载优先级

```
1. 当前任务直接相关的规范/文档（最高）
   - openspec/specs/{capability}/spec.md
   - openspec/changes/{change-id}/
   - requirements/in-progress/{requirement}.md

2. 历史相似任务的经验（次高）
   - .ai/summaries/experience-index.md
   - context/experience/lessons/{lesson}.md

3. 项目通用规范（默认）
   - AGENTS.md
   - context/tech/conventions/backend.md
   - context/tech/conventions/frontend.md

4. 架构和设计文档（按需）
   - context/tech/architecture/backend.md
   - context/business/domain-model.md
```

---

## 🎯 OpenSpec 门禁规则

| 任务类型 | 需要 OpenSpec？ | 行动 |
|---------|----------------|------|
| 新功能 | ✅ 是 | 创建提案 |
| Bug 修复 | ❌ 否 | 直接实现 |
| 重构（不改变行为） | ❌ 否 | 直接重构 |
| 重构（改变行为） | ✅ 是 | 创建提案 |

---

## 📝 任务完成后检查清单

- [ ] 是否有可复用的 Skill？
- [ ] 是否需要新的 Agent？
- [ ] 是否有新经验需要记录？
- [ ] 是否需要更新上下文？
- [ ] 是否已运行静态扫码、修复 TopN 并保留复扫证据？

---

## 🔗 相关文档

- [特性开发指导](FEATURE_DEVELOPMENT_GUIDE.md) - 完整开发流程
- [AI 工程化配置](README.md) - AI 工程化体系
- [项目上下文](summaries/project-context.md) - 项目技术栈
- [经验索引](summaries/experience-index.md) - 历史经验

---

*快速参考卡片 - 最后更新：2026-01-27*
