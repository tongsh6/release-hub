# data-quality Specification

## Purpose

存量数据质量治理用于在验收或发布操作前识别历史数据风险，并把风险转成可审计、可人工复核的清理动作清单。该能力不绕过业务不变量，不直接伪造状态，不输出敏感明文。

## Requirements

### Requirement: 存量数据安全清理 dry-run

系统 SHALL 提供独立的存量数据安全清理 dry-run 入口，输出资产统计、风险清单和建议动作。

#### Scenario: 生成 dry-run 清理报告

- **WHEN** 操作者运行 SA-002 存量数据安全清理脚本
- **THEN** 系统生成 `summary.md`、`actions.md` 和 `actions.jsonl`
- **AND** 每条动作包含资源类型、资源 ID、风险类型、建议动作、是否已执行、应用入口、执行前检查、执行后复核和人工复核决策
- **AND** 默认模式不修改数据库、不删除发布窗口、不触碰 GitLab 远端分支或仓库
- **AND** 报告不得输出 token 明文

#### Scenario: dry-run 口径对齐验收可见数据

- **WHEN** 全量验收脚本通过后端 API 报告 DRAFT 发布窗口残留
- **THEN** SA-002 dry-run SHALL 使用同一后端 API 口径生成 DRAFT 发布窗口复核动作
- **AND** 报告同时输出 `API_VISIBLE_ASSETS`、`DB_AUDIT_ASSETS` 和 `REVIEW_QUEUE_ACTIONS` 三类数据源口径，避免操作者混淆用户可见数据、底层审计数据与人工复核队列
- **AND** 全量验收脚本 SHALL 使用 `API_VISIBLE_ASSETS` 标注通过应用 API 统计到的分组、仓库、发布窗口、迭代和 Run 数量
- **AND** token 明文、BranchCreationMode、featureBranch、cloneUrl 和 branchCreated 等底层字段仍可通过数据库只读审计补充

#### Scenario: 拒绝自动执行危险清理

- **WHEN** 操作者尝试使用自动执行模式批量清理存量数据
- **THEN** 系统拒绝执行，并提示使用 dry-run 动作清单和应用层入口完成人工复核后的修复

### Requirement: 存量清理动作人工复核入口

系统 SHALL 提供应用层人工复核入口，用于接收 dry-run 动作清单并判断动作是否可以进入对应应用页面或受控迁移服务。

#### Scenario: 人工复核通过但不执行清理

- **WHEN** 操作者提交 reviewer、来源报告和已人工确认的清理动作
- **THEN** 系统返回动作复核结果
- **AND** 支持的资源/风险组合可标记为 `ACCEPTED`
- **AND** 响应必须返回应用入口、执行前检查和执行后复核口径
- **AND** 响应必须返回处置等级、允许业务动作、失败回滚边界和审计记录口径
- **AND** 该入口不得直接修改数据库、删除发布窗口或触碰 GitLab 远端资源

#### Scenario: 拒绝越权或不完整动作

- **WHEN** 动作要求 `EXECUTE_DIRECTLY`、`AUTO_EXECUTE` 或缺少应用入口、执行前检查、执行后复核口径
- **THEN** 系统将该动作标记为 `REJECTED`
- **AND** 响应说明拒绝原因
- **AND** 动作不会进入执行状态

### Requirement: 存量数据复核队列页面化

系统 SHALL 提供面向发布经理或运维人员的应用内复核队列，用来承接 SA-002 dry-run 动作清单。

#### Scenario: 导入 dry-run 动作形成复核队列

- **WHEN** 操作者导入 `actions.jsonl` 内容
- **THEN** 页面 SHALL 将每行 dry-run 动作解析为复核队列项
- **AND** 队列项 SHALL 展示资源类型、资源 ID、风险类型、应用入口、建议动作和人工决策
- **AND** 解析阶段不得触发清理执行

#### Scenario: 按资源、风险和状态筛选复核动作

- **WHEN** 操作者选择资源类型、风险类型或复核状态筛选条件
- **THEN** 系统 SHALL 将筛选条件提交到应用层复核 API
- **AND** API SHALL 只返回匹配筛选条件的复核结果
- **AND** 复核摘要 SHALL 展示通过、待复核和拒绝数量

#### Scenario: 人工决策仍不执行清理

- **WHEN** 操作者将动作标记为待复核或批准进入应用入口并提交复核
- **THEN** 系统 SHALL 返回每条动作的复核状态、原因、应用入口、执行前检查和执行后复核口径
- **AND** 系统 SHALL 返回每条动作的处置等级、允许业务动作、失败回滚边界和审计记录口径
- **AND** 所有复核结果 SHALL 保持 `executionPermitted=false`
- **AND** 页面不得提供直接执行、自动执行或绕过应用层的清理按钮

### Requirement: 人工复核后的受控处置策略

系统 SHALL 在允许任何存量数据处置前形成受控处置策略，区分可处置、不可处置和必须暂缓的风险类型，并为每类可处置风险定义执行前检查、应用层动作、执行后复核、失败回滚和审计记录。

#### Scenario: 区分风险处置等级

- **WHEN** 操作者复核 dry-run 动作清单
- **THEN** 系统 SHALL 明确每类风险的处置等级：只读观察、应用层人工处置、独立迁移服务或暂缓
- **AND** 复核 API 和复核队列页面 SHALL 展示处置等级、允许业务动作、失败回滚边界和审计记录口径
- **AND** 未明确处置等级的风险 SHALL 保持 `executionPermitted=false`
- **AND** 处置等级不得暗示可以通过脚本直接更新数据库、删除发布窗口或触碰 GitLab 远端资源

#### Scenario: 允许应用层人工处置前检查

- **WHEN** 某类风险被策略标记为可进入应用层人工处置
- **THEN** 策略 SHALL 定义执行前检查项、操作者确认项、目标应用入口和允许的业务动作
- **AND** 执行动作 SHALL 复用现有应用服务的不变量和权限边界
- **AND** 策略 SHALL 定义执行后复核查询、审计字段和失败回滚边界

#### Scenario: 暂缓危险或不可逆处置

- **WHEN** 风险处置需要批量迁移、批量删除、跨系统远端资源变更或不可逆数据修正
- **THEN** 系统 SHALL 将该风险保持为暂缓或要求独立迁移服务设计
- **AND** 不得在复核队列页面直接提供执行按钮
- **AND** 需要继续输出可审计的原因和下一步负责人/入口

### Requirement: 数据质量受控处置执行审计

系统 SHALL 在任何真实存量数据处置前创建受控处置执行审计 case，用于记录动作来源、执行前快照、人工处理状态、执行后复核、失败恢复和幂等防重。

#### Scenario: 从已接受复核动作创建处置 case

- **WHEN** 操作者基于 `ACCEPTED` 的 SA-002 dry-run 复核动作创建处置 case
- **THEN** 系统 SHALL 保存来源报告、复核批次、资源类型、资源 ID、风险类型、处置等级、应用入口、允许动作、回滚边界和审计记录口径
- **AND** 系统 SHALL 保存脱敏后的 dry-run 动作快照
- **AND** 系统 SHALL 生成稳定幂等键，重复创建同一动作时返回既有 case
- **AND** 创建 case 不得修改业务数据、关闭发布窗口、迁移业务字段或触碰 GitLab 远端资源

#### Scenario: 应用层人工处置只记录审计状态

- **WHEN** `APPLICATION_MANUAL` case 进入人工处置
- **THEN** 系统 SHALL 记录执行前检查结果和脱敏执行前快照
- **AND** 系统 SHALL 只提供目标应用入口跳转，由既有业务页面和应用服务完成真实动作
- **AND** 系统 SHALL 在操作者返回后记录执行后复核证据、复核人和复核时间
- **AND** 数据质量复核队列不得提供直接执行清理按钮

#### Scenario: 只读观察和迁移服务风险不允许直接执行

- **WHEN** case 的处置等级为 `OBSERVE_ONLY`、`MIGRATION_REQUIRED` 或暂缓
- **THEN** 系统 SHALL 记录阻断原因和下一步入口
- **AND** 系统 SHALL 禁止进入应用层执行状态
- **AND** `MIGRATION_REQUIRED` 风险 SHALL 要求独立迁移服务 proposal、迁移范围、回滚计划和验收证据

#### Scenario: 失败恢复和重复提交可审计

- **WHEN** 处置 case 执行前检查失败、人工处理失败或执行后复核失败
- **THEN** 系统 SHALL 记录失败阶段、失败原因、操作者和恢复说明
- **AND** 系统 SHALL 保留原始动作快照和执行前快照
- **AND** 重试 SHALL 显式关联原 case，不得覆盖原失败记录
- **AND** 失败记录不得暗示系统已自动恢复业务数据

### Requirement: 验收数据命名空间与保留策略

系统 SHALL 为 SA-002 dry-run 动作提供数据命名空间、复核批次、资产范围和保留策略元数据，避免操作者只按 DRAFT 残留数量判断数据质量风险。

#### Scenario: dry-run 动作携带批次与资产范围

- **WHEN** 操作者生成 SA-002 dry-run 报告
- **THEN** `summary.md` SHALL 记录数据命名空间、复核批次、当前批次标识、保留策略和资产范围口径
- **AND** `actions.md` 与 `actions.jsonl` 中的每条动作 SHALL 包含 `dataNamespace`、`reviewBatchId`、`assetScope` 和 `retentionPolicy`
- **AND** `assetScope` SHALL 至少区分 `CURRENT_BATCH`、`HISTORICAL_ACCEPTANCE`、`USER_BUSINESS` 和 `UNKNOWN_LEGACY`
- **AND** 默认保留策略 SHALL 只允许人工复核后进入应用层入口，不允许脚本自动删除或迁移业务数据

#### Scenario: 应用内复核保留命名空间元数据

- **WHEN** 操作者把带命名空间元数据的 `actions.jsonl` 导入复核队列并提交复核
- **THEN** 复核 API SHALL 在每条结果中保留数据命名空间、复核批次、资产范围和保留策略
- **AND** 页面 SHALL 展示这些字段，并允许按资产范围筛选
- **AND** 筛选后的复核摘要 SHALL 仍只统计匹配动作
- **AND** 复核接口 SHALL 继续保持 `executionPermitted=false`

### Requirement: 验收脚本与应用 API 数据源口径统一

系统 SHALL 在全量验收、SA-002 dry-run 报告和应用内复核队列之间使用一致的数据源边界，明确哪些指标代表用户可见资产、哪些只代表数据库审计资产、哪些代表人工复核队列动作。

#### Scenario: 报告和页面展示统一资产边界

- **WHEN** 操作者查看全量验收输出、SA-002 dry-run `summary.md` 或数据质量复核队列结果
- **THEN** 系统 SHALL 使用 `API_VISIBLE_ASSETS` 表示应用 API 可见资产统计
- **AND** 系统 SHALL 使用 `DB_AUDIT_ASSETS` 表示数据库只读审计统计
- **AND** 系统 SHALL 使用 `REVIEW_QUEUE_ACTIONS` 表示进入人工复核队列的动作数量和资产范围分布
- **AND** 复核 API SHALL 返回资产边界说明和 `assetScope` 计数，页面 SHALL 展示这些边界说明
- **AND** 这些指标不得暗示 dry-run 或复核接口会自动删除数据库记录、关闭发布窗口或触碰 GitLab 远端资源

### Requirement: 存量数据风险类型

系统 SHALL 至少识别仓库 token 明文、系统设置 token 明文、BranchCreationMode 缺失或非法、featureBranch 缺失、cloneUrl 异常、DRAFT 发布窗口残留和挂载分支未创建。

#### Scenario: 识别风险并给出业务修复入口

- **WHEN** 存量数据存在上述风险
- **THEN** dry-run 报告为每个资源输出对应风险类型
- **AND** 建议动作指向仓库设置、系统设置、迭代详情、发布窗口页面或受控迁移服务
- **AND** 不建议直接通过数据库 UPDATE/DELETE 绕过应用层约束
