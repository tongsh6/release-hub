# 需求：SA-002 BranchCreationMode 独立迁移服务设计

## 背景

SA-002 数据质量 dry-run、人工复核队列和处置 case 已能识别 `BRANCH_CREATION_MODE_MISSING_OR_INVALID` 风险，并把该风险标记为 `MIGRATION_REQUIRED`。当前仍缺少独立迁移服务的产品与技术边界：哪些历史记录可以安全补齐 `iteration_repo.branch_creation_mode`，哪些必须人工判定，迁移如何 dry-run、如何审计、失败如何回滚，以及为什么不能从复核队列或脚本直接写库。

## 目标

- 为 BranchCreationMode 历史风险建立独立迁移服务 proposal、迁移范围、回滚计划和验收证据。
- 只处理历史 `iteration_repo.branch_creation_mode` 缺失或非法记录，不修改 feature 分支、版本号、发布窗口、迭代仓库集合或 GitLab 远端资源。
- 将迁移拆成 dry-run、人工确认、受控执行、执行后复核和回滚演练，避免把 SA-002 复核队列升级成直接清理入口。
- 保留 OpenSpec 实现前审批门禁：本需求当前只完成设计，不进入执行器和数据库迁移实现。

## 验收标准

- [x] 建立 OpenSpec change：`docs/openspec/changes/add-branch-creation-mode-migration-service/`。
- [x] OpenSpec proposal 反向引用本需求文档。
- [x] 本需求文档反向引用 OpenSpec proposal。
- [x] 设计明确迁移对象范围、排除范围、候选分类、幂等键、执行前后快照、审计记录、回滚计划和验收证据。
- [x] delta spec 明确数据质量风险如何进入独立迁移服务，以及迭代领域如何保护 BranchCreationMode 语义。
- [x] 场景矩阵、项目台账、执行路线图和任务记录同步到“设计门禁完成，等待 proposal 审批/实现决策”。
- [x] proposal 经过人工评审并明确进入最小 dry-run 实现。

## 技术方案

规范提案与设计见：

- `docs/openspec/changes/add-branch-creation-mode-migration-service/proposal.md`
- `docs/openspec/changes/add-branch-creation-mode-migration-service/design.md`
- `docs/openspec/changes/add-branch-creation-mode-migration-service/tasks.md`
- `docs/openspec/changes/add-branch-creation-mode-migration-service/specs/data-quality/spec.md`
- `docs/openspec/changes/add-branch-creation-mode-migration-service/specs/iteration/spec.md`

核心方案是新增独立的 BranchCreationMode migration service：先以只读 dry-run 扫描历史 `iteration_repo` 记录，把候选分为可安全补齐 `AUTO`、可规范化合法值、需要人工映射、不可迁移四类；只有人工批准的迁移批次才能受控写入 `branch_creation_mode`，并保留迁移前后快照、批次审计和回滚清单。

## 进度

- [x] 需求文档创建并登记到需求索引。
- [x] OpenSpec proposal、设计、任务清单和 delta spec 已形成。
- [x] 上一条数据质量处置审计 change 中“BranchCreationMode 独立迁移服务另建 proposal”的遗留项已关闭。
- [x] 场景矩阵、项目台账、路线图和任务记录已同步。
- [x] proposal 评审门禁已完成，结论为 `APPROVE_DRY_RUN_ONLY`。
- [ ] 等待最小 dry-run 实现。
