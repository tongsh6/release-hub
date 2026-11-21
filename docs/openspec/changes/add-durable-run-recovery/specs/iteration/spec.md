# iteration Specification Delta

## RENAMED Requirements

- FROM: `### Requirement: 仓库关联迭代时创建 feature 分支`
- TO: `### Requirement: 迭代仓库配置与 feature 执行分离`

## MODIFIED Requirements

### Requirement: 迭代仓库配置与 feature 执行分离

系统 SHALL 将迭代创建、编辑、新增仓库和移除仓库视为配置操作。配置阶段只校验并保存仓库关系、BranchCreationMode、feature 分支名与版本映射，不创建、归档、删除分支，不提交仓库文件；这些 feature 分支的建立 SHALL 由显式 Orchestrate 的既有 Run/Item/Task 与操作账本承担；显式版本文件动作继续由既有版本用例负责，不混入配置操作。AUTO 与 NAMED 不依赖 GitLab 可用性；EXISTING 必须只读核实所选分支，读取失败不能伪装成可用。

本条修正历史“关联时创建”的触发时机，保留版本推导与模式选择的实际用途。S3-C1 使用已有 IterationAppService、IterationRepoPort 和窗口持久任务，不新增迭代执行器或兼容写入口。

#### Scenario: 创建 feature 分支并设置版本

- **GIVEN** 仓库已配置基准版本 1.2.3，用户选择 AUTO 关联到迭代 ITER-20260115-ABCD
- **WHEN** 创建迭代或新增该仓库
- **THEN** 系统保存 `feature/ITER-20260115-ABCD`、模式 AUTO、基准版本 1.2.3、开发版本 1.3.0-SNAPSHOT 与目标版本 1.3.0
- **AND** GitLab 不发生分支或文件写入
- **WHEN** 用户随后在合法窗口范围内显式 Orchestrate
- **THEN** 已持久受理的 ENSURE_FEATURE 使用冻结计划确保分支存在，实际结果记入 Task/Step 与操作证据
- **AND** 分支建立不隐式更新版本文件；版本文件写回由已有显式版本动作承担

#### Scenario: 版本号升级规则

- **GIVEN** 系统保存的基准版本为 X.Y.Z
- **WHEN** 配置阶段建立迭代与仓库的版本映射
- **THEN** 开发版本为 X.(Y+1).0-SNAPSHOT，目标版本为 X.(Y+1).0
- **AND** 推导与存储不要求远端分支已经创建，也不触发 Git 写入

#### Scenario: NAMED 仅保存通过校验的分支名

- **GIVEN** 用户选择 NAMED 并输入 `feature/upgrade-guava`
- **WHEN** 创建迭代、编辑新增仓库或调用 addRepos
- **THEN** 系统校验 feature 路径、保护前缀和现有 BranchRule，再保存用户指定的分支名与模式
- **AND** 不探测或创建远端分支；后续显式 Orchestrate 使用同一持久名称，不回退到另一分支名

#### Scenario: EXISTING 只读验证且失败不降级

- **GIVEN** 用户选择 EXISTING
- **WHEN** 所选分支经只读查询确实存在
- **THEN** 系统保存该分支与版本映射，零 Git 写入
- **WHEN** 分支不存在或只读查询失败
- **THEN** 配置请求被拒绝，不新增仓库映射，不自动改为 AUTO 或补建分支
- **AND** 后续 Orchestrate 若发现该分支已丢失，仍按 EXISTING 的语义拒绝补建

#### Scenario: 三个新增配置入口保持一致

- **GIVEN** 同组仓库满足命名与关联约束
- **WHEN** 通过 create、update 新增仓库或 addRepos 建立关系
- **THEN** 三个入口复用同一配置初始化职责，保存模式、分支和版本信息
- **AND** AUTO/NAMED 即使 GitLab 不可用也无需调用 Git 适配器，不能靠吞掉写异常伪装成纯配置

#### Scenario: 移除仓库保留远端分支和历史证据

- **GIVEN** 未挂载窗口的迭代关联仓库 A、B，B 已存在 feature 分支
- **WHEN** update 将集合改为 A，或 removeRepos 移除 B
- **THEN** 系统移除 B 的迭代关联与关联版本配置，保留 A 的既有配置
- **AND** 不归档、删除或修改 B 的远端分支，不改写历史 Run/Item/Step 证据
- **AND** 不建立补偿队列或兼容 Git 写入路径

#### Scenario: 配置隔离不放宽范围约束

- **GIVEN** 迭代已挂载窗口，或候选仓库不属于迭代分组
- **WHEN** 请求改变仓库范围或添加跨组仓库
- **THEN** 现有挂窗锁定和同组约束继续拒绝请求
- **AND** 拒绝不产生配置落库或 Git 写入

#### Scenario: repoConfigs 不扩大权威仓库集合

- **GIVEN** 请求通过 repoIds 明确选择仓库集合
- **WHEN** repoConfigs 包含集合外仓库或同一仓库的重复配置
- **THEN** 系统在任何映射保存前拒绝请求，不通过配置列表隐式增加仓库或跨组关联
- **AND** update 对完整请求集合校验配置后，仅为新增仓库初始化映射，保留既有仓库的持久配置

#### Scenario: 部分显式配置仍初始化所有请求仓库

- **GIVEN** repoIds 选择同组仓库 A、B，repoConfigs 只为 A 提供 NAMED 配置
- **WHEN** 创建迭代或更新时新增这些仓库
- **THEN** A 使用其显式配置，B 使用既有默认 AUTO 语义
- **AND** 所有新仓库均有完整模式、分支与版本映射，不产生缺失配置的半成品关联或 Git 写入
