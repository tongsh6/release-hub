# 经验索引

> 根据任务关键词自动检索相关历史经验，避免重复踩坑。

## 使用说明

- **维护原则**：每次解决问题后，及时添加索引条目
- **检索方式**：AI 根据任务关键词自动匹配
- **更新频率**：新增经验时立即更新索引

---

## 经验索引

### 问题类别：状态管理

- **问题**：如何实现 ReleaseWindow 的冻结/解冻功能
- **解决方案**：使用领域事件 + 状态机模式，避免直接修改状态
- **相关文件**：`context/experience/lessons/release-window-freeze-pattern.md`
- **标签**：`state-management`, `domain-event`, `release-window`
- **相关度关键词**：freeze, unfreeze, frozen, state, 状态管理

### 问题类别：版本策略

- **问题**：版本号格式验证的边界情况
- **解决方案**：使用策略模式，支持多种版本格式（SemVer, CalVer）
- **相关文件**：`context/experience/lessons/version-policy-validation.md`
- **标签**：`version-policy`, `validation`, `strategy-pattern`
- **相关度关键词**：version, policy, validation, format, 版本号

### 问题类别：DDD 架构

- **问题**：如何确保 Domain 层不依赖其他层
- **解决方案**：使用 ArchUnit 门禁测试，强制架构约束
- **相关文件**：`context/experience/reports/backend-structure.md`
- **标签**：`ddd`, `architecture`, `archunit`
- **相关度关键词**：domain, layer, dependency, architecture, DDD

### 问题类别：数据库迁移

- **问题**：Flyway 迁移脚本的命名和版本管理
- **解决方案**：使用 V{数字}__{描述}.sql 格式，避免版本冲突
- **相关文件**：`context/tech/conventions/database.md`
- **标签**：`flyway`, `migration`, `database`
- **相关度关键词**：migration, flyway, database, schema, 迁移

### 问题类别：API 设计

- **问题**：REST API 的响应格式统一
- **解决方案**：使用 ApiResponse 统一包装，包含 code, message, data
- **相关文件**：`context/tech/api/release-window.md`
- **标签**：`api`, `rest`, `response`
- **相关度关键词**：api, response, rest, controller

### 问题类别：前端测试

- **问题**：`pnpm test` 误把 e2e 脚本当作 Vitest 用例执行
- **解决方案**：在 Vitest 配置中排除 `e2e/**`，将 e2e 固定走 `pnpm test:e2e`
- **相关文件**：`context/experience/lessons/frontend-vitest-e2e-separation.md`
- **标签**：`vitest`, `e2e`, `workflow`, `ci`
- **相关度关键词**：vitest, e2e, pnpm test, No test suite found, exclude

---

## 业务经验

### 问题类别：发布流程

- **问题**：发布窗口生命周期如何设计，状态和冻结如何区分
- **解决方案**：三态流转（DRAFT→PUBLISHED→CLOSED）+ 冻结作为横切机制
- **相关文件**：`context/experience/lessons/release-window-lifecycle.md`
- **标签**：`release-window`, `lifecycle`, `state-machine`, `freeze`
- **相关度关键词**：发布窗口, 状态流转, 生命周期, publish, close, draft, 发布, 上线, lifecycle

### 问题类别：分支管理

- **问题**：多仓库场景下分支命名和归档策略
- **解决方案**：类型前缀 + 业务Key（feature/hotfix/release）+ archive/<reason>/<original>归档
- **相关文件**：`context/experience/lessons/branch-naming-strategy.md`
- **标签**：`branch`, `naming`, `archive`, `git`
- **相关度关键词**：分支, branch, feature, hotfix, release, archive, 归档, 命名, naming, git

### 问题类别：迭代管理

- **问题**：如何设计迭代与发布窗口、仓库的挂载关系
- **解决方案**：两层N:N模型（窗口←→迭代←→仓库），挂载/解除时自动管理分支
- **相关文件**：`context/experience/lessons/iteration-attach-detach.md`
- **标签**：`iteration`, `attach`, `detach`, `release-window`, `repository`
- **相关度关键词**：迭代, iteration, 挂载, attach, detach, 解除, 关联, 仓库, repository, 发布范围

---

## 添加新经验

### 模板

```markdown
### 问题类别：{category}

- **问题**：{问题描述}
- **解决方案**：{解决方案摘要}
- **相关文件**：`context/experience/lessons/{filename}.md`
- **标签**：`tag1`, `tag2`, `tag3`
- **相关度关键词**：keyword1, keyword2, keyword3
```

### 注意事项

1. **关键词要全面**：包含中英文、同义词、相关术语
2. **类别要准确**：便于后续检索和分类
3. **解决方案要简洁**：一句话概括核心思路
4. **及时更新**：发现问题或解决方案改进时，及时更新索引
