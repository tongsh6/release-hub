# 经验索引

> ReleaseHub 项目经验的可检索索引。AI 执行任务前应检索此索引，加载相关经验，避免重复踩坑。

## 使用说明

### AI 检索流程

```
1. 提取任务关键词
2. 匹配下方索引的「关键词」字段
3. 加载相关经验文档
4. 在执行任务前展示经验摘要
```

---

## 经验列表

### ReleaseWindow 冻结/解冻：领域事件 + 状态机模式

- **类别**：设计决策
- **日期**：2026-01
- **关键词**：`freeze`, `unfreeze`, `frozen`, `状态机`, `state machine`, `领域事件`, `domain event`, `release-window`, `状态流转`
- **摘要**：用聚合根内的状态机表达允许的状态跳转，通过领域事件描述状态变更语义。应用层只编排用例与事务边界，不直接修改状态细节。
- **文档**：[lessons/release-window-freeze-pattern.md](lessons/release-window-freeze-pattern.md)

---

### 发布窗口生命周期设计

- **类别**：设计决策
- **日期**：2026-01-29
- **关键词**：`lifecycle`, `生命周期`, `status`, `状态`, `DRAFT`, `PUBLISHED`, `CLOSED`, `release-window`, `状态流转`
- **摘要**：三态流转模型（DRAFT → PUBLISHED → CLOSED），冻结作为独立的横切关注点，任何状态下都可冻结/解冻。
- **文档**：[lessons/release-window-lifecycle.md](lessons/release-window-lifecycle.md)

---

### 迭代挂载与解除设计

- **类别**：设计决策
- **日期**：2026-01-29
- **关键词**：`attach`, `detach`, `挂载`, `解除`, `iteration`, `迭代`, `window-iteration`, `N:N`, `多对多`, `分支`, `branch`
- **摘要**：两层挂载模型（ReleaseWindow ←N:N→ Iteration ←N:N→ CodeRepository），解除挂载时要考虑分支归档等副作用。
- **文档**：[lessons/iteration-attach-detach.md](lessons/iteration-attach-detach.md)

---

### 分支命名与归档策略

- **类别**：设计决策
- **日期**：2026-01-29
- **关键词**：`branch`, `分支`, `naming`, `命名`, `archive`, `归档`, `feature`, `hotfix`, `release`, `git`
- **摘要**：分支命名约定（feature/hotfix/release + businessKey），归档策略（archive/reason/original）。自动化触发分支创建和归档。
- **文档**：[lessons/branch-naming-strategy.md](lessons/branch-naming-strategy.md)

---

### 版本策略校验：策略模式 + 边界用例回归

- **类别**：设计决策
- **日期**：2026-01
- **关键词**：`version`, `版本`, `policy`, `策略`, `SemVer`, `CalVer`, `validation`, `校验`, `正则`, `regex`
- **摘要**：把版本格式建模为策略，每种格式负责自身校验与比较语义。通过回归用例集固化边界，避免改一处坏一片。
- **文档**：[lessons/version-policy-validation.md](lessons/version-policy-validation.md)

---

### 前端测试分层：Vitest 单测与 e2e 脚本解耦

- **类别**：踩坑记录
- **日期**：2026-01
- **关键词**：`vitest`, `e2e`, `test`, `测试`, `单测`, `分层`, `puppeteer`, `前端`
- **摘要**：Vitest 只收集 src/ 下的单测用例，e2e 使用独立命令入口（pnpm test:e2e），避免互相污染。
- **文档**：[lessons/frontend-vitest-e2e-separation.md](lessons/frontend-vitest-e2e-separation.md)

---

### 列表筛选功能全栈实现模式

- **类别**：实现模式
- **日期**：2026-02
- **关键词**：`filter`, `筛选`, `列表`, `list`, `分页`, `paged`, `status`, `状态`, `全栈`, `full-stack`, `JPA`, `Repository`, `Port`, `Vue`
- **摘要**：从「发布窗口状态筛选」提炼的 5 层后端 + 3 层前端修改清单，包含 JPA 查询方法命名、条件组合策略、前端状态管理。
- **文档**：[lessons/list-filter-full-stack.md](lessons/list-filter-full-stack.md)

---

## 按领域索引

### ReleaseWindow（发布窗口）

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [冻结/解冻模式](lessons/release-window-freeze-pattern.md) | `freeze`, `状态机` | 领域事件 + 状态机模式 |
| [生命周期设计](lessons/release-window-lifecycle.md) | `lifecycle`, `状态流转` | 三态流转 + 冻结横切 |

### Iteration（迭代）

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [挂载与解除设计](lessons/iteration-attach-detach.md) | `attach`, `detach`, `N:N` | 两层挂载模型 |

### VersionPolicy（版本策略）

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [版本策略校验](lessons/version-policy-validation.md) | `SemVer`, `CalVer`, `validation` | 策略模式 + 边界回归 |

### Git/分支

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [分支命名与归档](lessons/branch-naming-strategy.md) | `branch`, `archive`, `naming` | 命名约定 + 归档策略 |

### 前端

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [测试分层](lessons/frontend-vitest-e2e-separation.md) | `vitest`, `e2e`, `测试` | 单测与 e2e 解耦 |
| [列表筛选全栈模式](lessons/list-filter-full-stack.md) | `filter`, `筛选`, `分页` | 5 层后端 + 3 层前端 |

---

## 按类别索引

### 设计决策

- [冻结/解冻模式](lessons/release-window-freeze-pattern.md)
- [生命周期设计](lessons/release-window-lifecycle.md)
- [挂载与解除设计](lessons/iteration-attach-detach.md)
- [分支命名与归档](lessons/branch-naming-strategy.md)
- [版本策略校验](lessons/version-policy-validation.md)

### 踩坑记录

- [前端测试分层](lessons/frontend-vitest-e2e-separation.md)

### 实现模式

- [列表筛选全栈模式](lessons/list-filter-full-stack.md)

---

## 新增经验

当发现有价值的经验时，按以下步骤添加：

1. 在 `lessons/` 目录创建经验文档
2. 在本索引文件中添加条目
3. 确保包含足够的关键词以便检索
4. 分类到对应的领域和类别索引中
