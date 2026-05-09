# ReleaseHub 需求管理

> 日常需求跟踪与管理，与 OpenSpec 规范驱动开发互补。

## 目录结构

```
requirements/
├── INDEX.md           # 本文件 - 需求索引
├── in-progress/       # 进行中需求
└── completed/         # 已完成需求
```

## 需求分类

| 类型 | 管理方式 | 说明 |
|------|----------|------|
| 功能需求 | `in-progress/` → `completed/` | 日常功能开发 |
| 规范变更 | `openspec/changes/` | 涉及 API/架构的规范驱动开发 |
| Bug 修复 | 直接修复 | 无需需求文档 |

## 门禁规则（强制）

为降低返工并保证“先聊需求、后提案、再实现”的节奏，建立以下门禁规则：

1. **创建 OpenSpec change 前必须先有需求文档**
   - 在 `requirements/in-progress/` 新建需求文档（按模板：背景/目标/验收标准/讨论要点/进度）
   - 在本索引“当前进行中需求”表中登记该需求
2. **OpenSpec proposal 必须反向引用需求文档**
   - `openspec/changes/<change-id>/proposal.md` 必须包含 `requirements/in-progress/...`（或 `requirements/completed/...`）的链接或路径
3. **需求文档必须反向引用 OpenSpec change**
   - 当进入提案阶段，需求文档中必须包含 `openspec/changes/<change-id>/proposal.md` 的链接
4. **实现阶段门禁**
   - 未完成上述互链与校验，不进入代码实现与数据库迁移

## 当前进行中需求

| 需求 | 负责人 | 状态 | 关联提案 |
|------|--------|------|---------|
| [迭代仓库分支三层关联](in-progress/迭代仓库分支三层关联.md) | - | 设计中 | `openspec/changes/add-branch-creation-mode/proposal.md` |

> 2026-05-09：新建。背景：迭代与仓库关联无法表达 feature 分支创建模式（自动/自定义/已有），`create()` 和 `addRepos()` 行为不对称。

## 已完成需求

| 需求 | 负责人 | 完成时间 |
|------|--------|----------|
| [发布协调日历视图](completed/发布协调日历视图.md) | - | 2026-03-04 |
| [代码仓库类型区分](completed/代码仓库类型区分.md) | - | 2026-02-10 |
| [GitFlow分支生命周期管理](completed/GitFlow分支生命周期管理.md) | - | 2026-03-02 |
| [版本更新功能](completed/版本更新功能.md) | - | 2026-02-05 |
| [分页标准化](completed/分页标准化.md) | - | 2026-02-05 |
| [完善新增代码仓库功能](completed/完善新增代码仓库功能.md) | - | 2026-01-16 |

参见 [completed/](completed/) 目录查看更多

## 需求文档模板

```markdown
# 需求：[需求名称]

## 背景
[为什么需要这个功能]

## 目标
[期望达成的结果]

## 验收标准
- [ ] 标准 1
- [ ] 标准 2

## 技术方案
[简要技术方案，复杂方案链接到 openspec/changes/]

## 进度
- [ ] 任务 1
- [ ] 任务 2
```

## 与 OpenSpec 的协作

- **简单需求**：直接在 `requirements/` 管理
- **规范变更**：先在 `openspec/changes/` 创建 proposal，完成后同步到 `requirements/completed/`
- **交叉引用**：需求文档可链接到 OpenSpec specs/changes
