# Tasks: BranchCreationMode 独立迁移服务

## 1. 设计门禁

- [x] 新建需求文档并登记到 `docs/requirements/INDEX.md`。
- [x] 新建 OpenSpec proposal，反向引用需求文档。
- [x] 新建设计文档，明确迁移对象、排除范围、候选分类、幂等、防重、快照、执行、复核和回滚边界。
- [x] 新建 data-quality delta spec，定义 `MIGRATION_REQUIRED` 风险进入独立迁移服务且复核队列不得执行迁移。
- [x] 新建 iteration delta spec，定义历史 `branch_creation_mode` 只允许受控迁移服务补齐。
- [x] 同步场景矩阵、项目台账、执行路线图和任务记录。

## 2. 最小 dry-run 切片（等待 proposal 批准）

- [ ] 新增 BranchCreationMode migration dry-run 应用服务。
- [ ] 只读扫描 `iteration_repo.branch_creation_mode` 缺失、空白、可规范化和非法值。
- [ ] 输出 `SAFE_AUTO_DEFAULTABLE`、`NORMALIZE_LEGAL_VALUE`、`MANUAL_MAPPING_REQUIRED`、`NOT_MIGRATABLE_IN_THIS_SERVICE` 分类。
- [ ] 生成 Markdown/JSON 报告，包含候选统计、推断依据、执行计划草案和拒绝原因。
- [ ] 补应用层测试和任务记录。

## 3. 执行计划与受控执行（等待 dry-run 验收）

- [ ] 新增执行计划确认模型，未确认批次不得执行。
- [ ] 执行时仅更新 `iteration_repo.branch_creation_mode`。
- [ ] 记录迁移前快照、迁移后快照、候选级状态和批次审计。
- [ ] 补幂等、快照不一致拒绝、人工映射缺失拒绝和部分失败测试。

## 4. 复核与回滚（等待受控执行验收）

- [ ] 通过 API/version-info 复核迁移后 `branchCreationMode` 可被领域枚举解析。
- [ ] 输出数据库只读审计结果和回滚清单。
- [ ] 补回滚演练或失败记录。
- [ ] 同步场景矩阵、项目台账、路线图和发布候选报告。

## 5. 明确禁止

- [x] 当前设计门禁不实现执行器。
- [x] 不从数据质量复核队列直接执行迁移。
- [x] 不修改 `feature_branch`、版本字段、发布窗口、迭代仓库集合或 GitLab 远端资源。
- [x] 不引入 RBAC、通知或审批流。
