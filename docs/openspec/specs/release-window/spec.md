# release-window Specification

## Purpose
定义发布窗口生命周期、迭代挂载、提测合并以及上线后收尾编排与归档规则。

## Conventions
- **状态文案**：待发布（DRAFT）→ 已发布（PUBLISHED）→ 已关闭（CLOSED）
- **分支命名**：`feature/<iterationKey>`、`hotfix/<iterationKey>`、`release/<windowKey>`
- **分支归档**：重命名为 `archive/<reason>/<original>`，`<reason>` ∈ {`unpublished`, `released`}
## Requirements
### Requirement: 发布窗口状态流转

发布窗口状态 SHALL 按 DRAFT → PUBLISHED → CLOSED 流转。

#### Scenario: 创建与发布
- **GIVEN** 一个新建的发布窗口
- **WHEN** 创建完成
- **THEN** 状态为 DRAFT
- **WHEN** 用户执行发布操作
- **THEN** 状态变为 PUBLISHED

#### Scenario: 关闭窗口
- **GIVEN** 一个已发布的窗口
- **WHEN** 收尾编排完成并执行关闭
- **THEN** 状态变为 CLOSED

### Requirement: 迭代关联发布窗口时创建 release 分支

当迭代关联到发布窗口时，系统 SHALL 为迭代管理的所有代码仓库创建 release 分支，并在存在 feature/hotfix 分支时自动合并到 release。

#### Scenario: 首次关联迭代到窗口
- **GIVEN** 一个发布窗口 RW-20260115-ABCD，一个迭代 ITER-20260110-XYZ
- **AND** 该迭代关联了仓库 A 和仓库 B
- **WHEN** 用户将迭代关联到发布窗口
- **THEN** 系统为仓库 A 从 master 创建分支 release/RW-20260115-ABCD
- **AND** 系统为仓库 B 从 master 创建分支 release/RW-20260115-ABCD
- **AND** 若存在 feature/ITER-20260110-XYZ 或 hotfix/ITER-20260110-XYZ，则自动合并到 release/RW-20260115-ABCD
- **AND** 记录 release 分支名和合并时间

#### Scenario: release 分支已存在（多迭代关联同一窗口）
- **GIVEN** 发布窗口 RW-20260115-ABCD 已关联了迭代 A
- **AND** 仓库已存在 release/RW-20260115-ABCD 分支
- **WHEN** 用户将迭代 B 关联到同一发布窗口
- **THEN** 系统不重复创建 release 分支
- **AND** 系统将迭代 B 关联仓库的 feature/hotfix 分支合并到现有 release 分支

### Requirement: 发布准备合并功能（提测）

发布窗口 SHALL 提供手动合并能力，用于将 feature/hotfix 的最新代码合并到 release 分支以生成测试版本。

#### Scenario: 手动触发代码合并（单个迭代）
- **GIVEN** 发布窗口 RW-20260115-ABCD，已关联迭代 ITER-20260110-XYZ
- **AND** 技术负责人需要生成测试版本
- **WHEN** 用户在发布窗口详情页触发“代码合并”并选择迭代
- **THEN** 系统将该迭代所有仓库的 feature/hotfix 分支合并到 release 分支
- **AND** 返回合并结果（成功/冲突/失败）

#### Scenario: 批量代码合并（所有迭代）
- **GIVEN** 发布窗口关联了多个迭代
- **WHEN** 用户触发“全部合并”
- **THEN** 系统依次将每个迭代的 feature/hotfix 分支合并到 release 分支
- **AND** 返回每个仓库的合并结果

#### Scenario: 代码合并存在冲突
- **GIVEN** feature/hotfix 分支和 release 分支存在冲突
- **WHEN** 用户触发代码合并
- **THEN** 系统返回合并状态为 CONFLICT
- **AND** 返回冲突文件列表和详情
- **AND** 提示用户在 GitLab 中手动解决冲突后重试

### Requirement: 解除挂载归档

系统 SHALL 在解除挂载时按规则归档相关分支。

#### Scenario: 迭代从发布窗口解除挂载
- **GIVEN** 发布窗口与迭代已关联并创建 release 分支
- **WHEN** 用户解除挂载迭代
- **THEN** 系统归档对应 release 分支，归档原因 `unpublished`

#### Scenario: 仓库从迭代解除挂载
- **GIVEN** 迭代已挂载仓库并创建 feature/hotfix 分支
- **WHEN** 用户解除仓库挂载
- **THEN** 系统归档对应 feature/hotfix 分支，归档原因 `unpublished`

### Requirement: 发布收尾编排与任务追踪

收尾编排 SHALL 在“上线完成、关闭窗口前”由用户触发，系统异步执行并生成运行记录与任务列表。

#### Scenario: 触发收尾编排
- **GIVEN** 一个已发布的窗口
- **WHEN** 用户触发收尾编排
- **THEN** 系统创建 Run 记录，状态为 RUNNING
- **AND** 系统为每个收尾步骤创建 RunTask 记录
- **AND** 系统异步开始执行任务

### Requirement: 收尾任务类型

系统 SHALL 支持以下收尾任务类型，按顺序执行：

1. MERGE_RELEASE_TO_MASTER - release 分支合并到默认分支
2. CREATE_TAG - 在默认分支创建发布 tag
3. TRIGGER_CI - 在 release 分支仍可访问时触发收尾 CI
4. ARCHIVE_BRANCHES - 归档 feature/hotfix 分支与 release 分支（归档原因为 released）
5. ARCHIVE_ITERATION - 归档关联迭代

#### Scenario: 任务按顺序执行
- **GIVEN** 一个已创建的 Run，包含多个 RunTask
- **WHEN** 系统开始执行任务
- **THEN** 任务按照 task_order 顺序执行
- **AND** 前一个任务完成后才执行下一个任务

#### Scenario: 任务执行失败停止
- **GIVEN** 一个正在执行的 Run
- **WHEN** 某个 RunTask 执行失败且重试次数已达上限
- **THEN** 后续任务不再执行
- **AND** Run 状态变为 FAILED

### Requirement: 关闭窗口后的 GitLab 收尾证据
系统 SHALL 在发布窗口关闭后留下可按 `windowKey`、`iterationKey` 和 `repoId` 复核的真实 GitLab 状态。

#### Scenario: 关闭窗口后复核 GitLab 状态
- **GIVEN** 一个已发布窗口 RW-20260115-ABCD 关联迭代 ITER-20260110-XYZ 和仓库 A
- **AND** 仓库 A 已存在 feature 分支和 release/RW-20260115-ABCD 分支
- **WHEN** 用户关闭发布窗口
- **THEN** release 分支合并到仓库默认分支
- **AND** 默认分支上创建对应发布 tag
- **AND** 原 feature 分支不再作为活跃分支存在，`archive/released/feature-ITER-20260110-XYZ` 可查询
- **AND** 原 release 分支不再作为活跃分支存在，`archive/released/release-RW-20260115-ABCD` 可查询
- **AND** 收尾 Run 记录 `MERGE_TO_MASTER`、`CREATE_TAG`、`TRIGGER_CI` 和两个 `ARCHIVE_BRANCH` 步骤

### Requirement: GitLab 种子仓库分支清理保护
系统 SHALL 提供本地验收 GitLab 种子仓库的受控分支清理入口，避免历史 release/feature 分支累积影响 clean-room 复现。

#### Scenario: dry-run 生成候选清单
- **WHEN** 操作者以默认模式运行种子分支清理脚本
- **THEN** 系统生成 `summary.md`、`branches.md` 和 `branches.jsonl`
- **AND** 每条分支事件包含仓库、Project ID、分支、动作、HTTP 状态和保护原因
- **AND** 默认模式不得删除任何 GitLab 分支

#### Scenario: execute 只删除非种子分支
- **WHEN** 操作者显式传入 `--execute`
- **THEN** 系统只删除固定种子仓库中的非种子分支
- **AND** `main` 与脚本内置 seed feature 分支必须保留
- **AND** 执行后报告必须记录 `POST_KEEP` 和 `POST_REMOVED` 复核事件
- **AND** 重复执行不得产生新的删除动作

### Requirement: 发布窗口列表分页与筛选
系统 SHALL 提供发布窗口列表的服务端分页查询，使用 1-based `page` 与 `size`，并支持名称筛选。

#### Scenario: 发布窗口分页查询
- **WHEN** 用户按 `page=1&size=20&name=alpha` 请求发布窗口列表
- **THEN** 返回对应分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based

### Requirement: 发布窗口关联迭代分页
系统 SHALL 提供窗口关联迭代的服务端分页查询，使用 1-based `page` 与 `size`。

#### Scenario: 关联迭代分页查询
- **WHEN** 用户按 `page=1&size=20` 请求某窗口的迭代列表
- **THEN** 返回对应分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based

### Requirement: 多窗口并行发布可观测性
系统 SHALL 在同一组织存在多个活跃发布窗口时，提供可按 `windowId` / `windowKey` 追溯的并行窗口观察口径，避免列表、日历、详情和发布计划把不同窗口的迭代或仓库混淆。

#### Scenario: 查看同组并行窗口范围
- **GIVEN** 同一叶子分组下存在两个 DRAFT 或 PUBLISHED 发布窗口
- **AND** 两个窗口分别关联不同迭代与仓库
- **WHEN** 用户查看发布窗口列表、日历或窗口详情
- **THEN** 系统展示同组活跃窗口数量与窗口 Key
- **AND** 窗口详情返回当前分组下每个活跃窗口的迭代数、仓库数和发布计划项
- **AND** 每个发布计划项必须保留所属 `windowKey`、`iterationKey` 和 `repoId`
- **AND** 其他组织的发布窗口、迭代、仓库和发布计划不得出现在当前并行范围内

### Requirement: 发布窗口报告制品包归档
系统 SHALL 为发布窗口提供可下载的报告制品包，用于发布证据归档，并保持既有 JSON、CSV、Markdown 报告契约兼容。

#### Scenario: 下载发布窗口报告制品包
- **GIVEN** 一个存在的发布窗口
- **WHEN** 用户从发布窗口详情页选择导出制品包
- **THEN** 系统返回 `application/zip` 制品包
- **AND** 响应头包含可审计文件名 `release-window-<windowKey>-evidence.zip`
- **AND** 制品包包含 `manifest.txt`、`report.json`、`report.csv`、`report.md`
- **AND** `manifest.txt` 说明窗口 ID、窗口 Key、状态、Run/Item/Step 数量和包内文件清单
- **AND** `report.json`、`report.csv`、`report.md` 与对应单文件报告端点表达同一窗口证据
