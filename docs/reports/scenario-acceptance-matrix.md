# ReleaseHub 场景化验收矩阵

> 日期：2026-05-15
> 目标：按完整业务蓝图定义场景化验收，用来发现现有系统能力不完整、实现覆盖不足和自动化缺口。

## 一、使用口径

本矩阵不以现有实现为边界，也不把 API 调用等同于场景验收。每个场景先定义真实用户要完成的业务动作，再标注前端入口、后端业务约束、真实 GitLab 或数据证据的覆盖情况。

验收写法遵循以下原则：

- 场景主语必须是真实用户角色，例如管理员、发布经理、技术负责人、测试人员。
- 场景动作必须能从前端页面发起、观察或复核。
- API、数据库和 GitLab 检查是证据来源，不是场景本身。
- P0 场景只有在用户旅程、业务约束和关键证据都成立时，才算完整覆盖。

前端用户旅程自动化补充原则：

- ReleaseHub 业务数据必须通过 UI 旅程准备或变更，不能用 API/数据库直接创建分组、仓库、迭代、窗口、挂载关系、冲突解决、编排或版本更新，再把后续页面观察视为完整用户旅程。
- GitLab seed repo、GitLab PAT、Postgres/Backend/Frontend 服务属于外部环境 fixture，可以由脚本管理。
- API、数据库、GitLab 查询只能放在旅程后作为证据复核；如果自动化只复核存量数据，必须显式标注为“可见性/复核”而不是“用户触发动作”。

覆盖状态定义：

| 状态 | 含义 |
|---|---|
| 已覆盖 | 前端用户旅程、后端业务约束、关键 GitLab/数据证据基本满足 P0 验收焦点 |
| 部分覆盖 | 当前已有实现或自动化证据，但缺少关键用户入口、业务约束、真实 GitLab 证据或前端可观察性 |
| 缺口较大 | 当前能力、自动化证据或前端体验与完整用户场景差距明显 |

证据层级：

| 层级 | 主入口 | 在场景验收中的定位 |
|---|---|
| 前端用户旅程 | `frontend/e2e/tests` | 证明用户能从页面完成操作、看到状态、理解结果 |
| 后端业务约束 | `backend/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap`、`scripts/acceptance/run-acceptance.sh` | 证明权限边界、状态流转、校验规则和错误阻断成立 |
| 真实 GitLab 证据 | `scripts/acceptance/run-acceptance.sh` | 证明分支、提交、版本写回等外部实物状态成立 |
| 数据和审计证据 | 数据库审计、Run 详情、验收报告 | 证明过程可追溯、可复核、可长期沉淀 |

## 二、总览

| ID | 分组 | 主角色 | 场景 | P0 验收焦点 | 当前状态 |
|---|---|---|---|---|---|
| SA-001 | 验收运行治理 | 系统 | GitLab Settings 配置并持久化 | 保存、复用、重启持久化、真实 GitLab 可用 | 已覆盖 |
| SA-002 | 验收运行治理 | 系统 | 存量数据质量审计 | Token 安全、分支模式数据、featureBranch、cloneUrl、脏数据可见 | 已覆盖 |
| SA-003 | Admin Setup | 系统管理员 | 建立客户/业务线/品牌分组树 | 三层分组、资源只能挂品牌叶子节点 | 部分覆盖 |
| SA-004 | Admin Setup | 系统管理员 | 配置 GitLab 连接 | 保存、不泄露、重启持久化、真实 API 可用 | 部分覆盖 |
| SA-005 | Admin Setup | 系统管理员 | 纳管代码仓库 | 品牌叶子归属、真实 GitLab 可用、token 安全、默认分支/版本基础信息 | 部分覆盖 |
| SA-006 | Admin Setup | 系统管理员 | 配置分支规则 | feature/hotfix/release 规则在分支创建时生效，不合规拒绝 | 部分覆盖 |
| SA-007 | Admin Setup | 系统管理员 | 配置版本策略 | 基础策略、SemVer、版本校验、Maven 单模块真实写回前置 | 部分覆盖 |
| SA-008 | Release Planning | 发布经理 | 创建品牌发布窗口 | 品牌叶子创建、windowKey、DRAFT、空窗口发布拒绝、列表/日历可见 | 部分覆盖 |
| SA-009 | Release Planning | 技术负责人 | 创建迭代并选择已纳管仓库 | 品牌叶子创建、同品牌仓库选择、iterationKey、分支模式、版本/分支记录 | 部分覆盖 |
| SA-010 | Release Planning | 发布经理 | 挂载迭代到发布窗口 | 同品牌挂载、多迭代多仓计划、release 分支真实创建、细粒度 attach 结果、冲突阻断 | 部分覆盖 |
| SA-011 | Risk & Execution | 测试人员 | 检查冲突与发布风险 | 冲突扫描、类型分布、阻塞发布、解决后重扫清零 | 部分覆盖 |
| SA-012 | Risk & Execution | 技术负责人 | 解决冲突 | 版本冲突 `USE_SYSTEM` 解决、重扫为 0、发布可继续 | 已覆盖 |
| SA-013 | Risk & Execution | 技术负责人 | 触发发布编排 | 无阻塞冲突后 Run COMPLETED/SUCCESS、RunItem > 0、GitLab 状态一致、未解决冲突阻断 | 已覆盖 |
| SA-014 | Risk & Execution | 技术负责人 | 执行版本更新 | Maven 单模块真实写回 release 分支、Run COMPLETED/SUCCESS、GitLab commit 可验证 | 已覆盖 |
| SA-015 | Risk & Execution | 测试人员 | 复核发布状态和执行证据 | 窗口/Run 列表与详情、状态和执行明细可见 | 已覆盖 |
| SA-016 | Post Release | 发布经理 | 关闭发布窗口并完成收尾 | 关闭状态流转、关闭后禁止关键操作、收尾 Run 可见 | 已覆盖 |

## 三、前后端场景证据矩阵

| ID | 用户旅程入口 | 后端业务证据 | GitLab/数据证据 | 当前主要缺口 |
|---|---|---|---|---|
| SA-003 | 管理员在分组页面创建客户、业务线、品牌并查看树 | Group API、非叶子资源挂载拒绝 | 仓库/迭代/窗口 groupCode 均落在品牌叶子 | 前端需要稳定断言资源创建时只能选择品牌叶子 |
| SA-004 | 管理员在系统设置页保存并测试 GitLab 连接 | Settings 保存、读取、重启持久化 | 后续真实 GitLab 分支操作成功且 token 不泄露 | 前端连接测试和错误提示仍是 P1 |
| SA-005 | 管理员在仓库页纳管品牌仓库并查看详情 | 仓库创建校验、品牌叶子归属、重复/错误 URL 校验 | 真实 GitLab cloneUrl、默认分支、token 安全审计 | 前端仓库详情中的组织路径、版本解析状态需要补强 |
| SA-006 | 管理员在分支规则页配置命名规范 | BranchRule 校验、AUTO/NAMED/EXISTING 分支模式约束 | 创建出的 feature/hotfix/release 分支名称符合规则 | 前端完整规则管理旅程和规则作用域仍不足 |
| SA-007 | 管理员在版本策略页配置版本演进规则 | SemVer 校验、PATCH/MINOR/MAJOR 推导 | Maven 单模块版本写回前置条件可验证 | 策略作用域、多模块、Gradle 写回仍不足 |
| SA-008 | 发布经理在窗口页创建品牌发布窗口并查看列表/日历 | 发布窗口创建、DRAFT 状态、空窗口发布拒绝 | windowKey 唯一且关联品牌叶子 | 前端需补组织路径、筛选和冻结限制证据 |
| SA-009 | 技术负责人在迭代页创建迭代并选择仓库 | 同品牌仓库选择、iterationKey、分支模式记录 | feature 分支和版本信息落库并可追踪 | 前端迭代详情和跨品牌仓库拒绝证据不足 |
| SA-010 | 发布经理在窗口详情页挂载迭代并查看发布计划 | attach 细粒度结果、状态流转、冲突阻断 | release 分支真实创建，WindowIteration 状态一致 | 前端发布计划可见性、解除挂载和部分失败重试不足 |
| SA-011 | 测试人员在窗口详情页触发/查看风险扫描 | 冲突总数、类型分布、阻塞发布 | 冲突与 GitLab 分支/版本状态可对应 | 前端风险详情、严重级别和建议处理方式不足 |
| SA-012 | 技术负责人在冲突详情中执行解决动作 | `USE_SYSTEM` 等解决动作更新记录，重扫清零 | 必要时写回仓库或保留处理证据 | P0 版本冲突 `USE_SYSTEM` 已闭环；更多冲突类型解决路径为 P1/P2 |
| SA-013 | 技术负责人在窗口详情页触发发布编排 | 无阻塞冲突后 Run COMPLETED/SUCCESS，冲突未解决时拒绝 | RunItem/RunStep、GitLab 分支状态一致 | P0 已闭环；后续补 UI 侧执行后结果复核和失败 Run 观察 |
| SA-014 | 技术负责人在版本操作入口执行版本更新 | 版本更新 Run COMPLETED/SUCCESS，失败原因可见 | `pom.xml` 在 release 分支真实 commit | P0 Maven 单模块已闭环；多模块和 Gradle 真实写回仍为 Phase 2 |
| SA-015 | 测试人员在 Run/窗口详情复核执行证据 | Run 列表、Run 详情、窗口详情返回完整状态；UI 触发失败版本更新后可复核失败 Run | RunItem/RunStep 可追溯到窗口、迭代、仓库和失败 POM 路径 | P0 已闭环；冲突详情、部分失败和品牌筛选下的复核旅程为 P1 |
| SA-016 | 发布经理在窗口详情页关闭窗口并查看收尾结果 | CLOSED 状态、关闭后关键操作禁止 | tag、merge、归档和收尾 Run 可追踪 | P0 已闭环；幂等关闭、部分失败重试和发布报告导出仍为 P1/P2 |

## 四、场景详情

### SA-001：GitLab Settings 配置并持久化

目标业务场景：系统具备可信的 GitLab 连接配置，后续仓库、分支、版本更新操作可以使用真实 GitLab。

P0 验收焦点：

- 保存 GitLab `baseUrl` 和 token。
- 已有配置可复用。
- 后端重启后配置不丢失。
- 后续分支列表、分支创建或版本提交能证明配置真实可用。

当前覆盖：

- `run-acceptance.sh` 覆盖 Settings 自动配置、复用和重启持久化。
- 真实 GitLab 分支创建和 branches 端点间接证明配置可用。

缺口：

- 无效 token、GitLab 不可达、权限不足的错误提示作为 P1。
- 前端连接测试体验作为 P1。

### SA-002：存量数据质量审计

目标业务场景：验收开始前先确认历史数据不会误导本轮判断。

P0 验收焦点：

- 数据资产数量可见。
- 仓库 token 明文数量为 0。
- `BranchCreationMode` 分布可见。
- `feature_branch` 缺失、cloneUrl 异常、DRAFT 残留可见。

当前覆盖：

- `run-acceptance.sh` 场景 1.x 已覆盖。
- 只报告不清理，符合本地持久化验收原则。

缺口：

- 一键安全清理脚本作为 P1/P2，不进入本轮 P0。

### SA-003：管理员建立组织分组树

目标业务场景：

```text
客户A
└── 业务线X
    └── 品牌Y
        └── 仓库、迭代、发布窗口归属到品牌Y
```

P0 验收焦点：

- 管理员创建客户、业务线、品牌三层分组。
- API 和前端能展示完整树结构。
- 仓库、迭代、发布窗口只能挂品牌叶子节点。
- 发布范围能沿分组追溯到客户、业务线、品牌。

当前覆盖：

- Playwright 有三层分组创建用例。
- 后端 Group API/E2E 覆盖基础 CRUD。
- `run-acceptance.sh` 已固定创建 `验收-客户A -> 验收-业务线X -> 验收-品牌Y`，并验证仓库、迭代、发布窗口不能挂非叶子分组。

缺口：

- 前端资源创建时只能选择品牌叶子的交互证据需要补稳定断言。
- 删除父分组、删除有关联资源分组的保护为 P1。
- code 自动生成为 P1/P2，按实现成熟度评估。

### SA-004：管理员配置 GitLab 连接

目标业务场景：管理员配置系统级 GitLab 连接，并能知道配置是否真实可用。

P0 验收焦点：

- 保存 GitLab `baseUrl` 和 token。
- token 不在接口或页面明文泄露。
- 配置重启后仍存在。
- 分支列表或分支创建证明真实 API 可用。

当前覆盖：

- `run-acceptance.sh` 覆盖保存、复用和重启。
- 真实 GitLab 操作间接覆盖可用性。

缺口：

- 系统级 Settings token 是否加密存储需要反查。
- 无效 token、GitLab 不可达、权限不足错误提示为 P1。
- 前端连接测试为 P1。

### SA-005：管理员纳管代码仓库

目标业务场景：管理员把品牌下的真实仓库纳入 ReleaseHub，作为后续迭代和发布范围基础。

P0 验收焦点：

- 仓库只能挂品牌叶子节点。
- 仓库名称、Clone URL、Git Provider 必填。
- token 安全存储，不明文回显。
- 默认分支可配置或识别。
- 基础版本信息可解析或状态明确。
- 真实 GitLab 分支操作可用。

当前覆盖：

- `run-acceptance.sh` 注册/复用 3 个真实 GitLab 仓库并刷新 token。
- 存量审计覆盖仓库 token 明文和 cloneUrl 双前缀异常。

缺口：

- 三层品牌归属、组织路径展示、按组织筛选需要补。
- 版本解析失败状态、重复仓库、错误 URL、删除保护为 P1。

### SA-006：管理员配置分支规则

目标业务场景：管理员配置统一分支命名规范，后续 feature/hotfix/release 分支创建必须遵守。

P0 验收焦点：

- 配置 feature/hotfix/release 规则。
- `AUTO`、`NAMED`、`EXISTING` 创建分支时规则生效。
- 不合规 NAMED 分支被拒绝。
- EXISTING 不存在分支被拒绝。

当前覆盖：

- `run-acceptance.sh` 场景 10 覆盖分支创建模式。
- 后端 BranchRule E2E 覆盖 TEMPLATE/REGEX 的一部分。

缺口：

- archive 规则、作用域到品牌/仓库、历史不合规分支治理为 P1。
- 前端完整规则管理旅程为 P2，除非后续优先级上调。

### SA-007：管理员配置版本策略

目标业务场景：管理员配置版本演进策略，系统按策略推导和校验目标版本。

P0 验收焦点：

- 基础策略支持 PATCH/MINOR/MAJOR。
- SemVer 校验可用。
- 当前版本能推导目标版本。
- 版本校验能给出明确结果。
- Maven 单模块真实写回所需策略前置可用。

当前覆盖：

- 后端版本推导、校验、更新有单测和 API 测试。
- `run-acceptance.sh` 场景 8 有版本校验。
- `run-acceptance.sh` SA-014 已验证 Maven 单模块真实 GitLab 写回。

缺口：

- Maven 多模块、Gradle 真实写回为 P1。
- 品牌/仓库作用域和策略继承为 P1/P2。

### SA-008：发布经理创建品牌发布窗口

目标业务场景：发布经理为品牌创建发布窗口，明确上线计划和后续发布主线对象。

P0 验收焦点：

- 只能在品牌叶子节点下创建窗口。
- 生成唯一 `windowKey`。
- 创建后为 DRAFT 或待发布状态。
- 空窗口发布被拒绝。
- 列表和日历可见。

当前覆盖：

- `run-acceptance.sh` 创建发布窗口。
- Playwright 覆盖窗口创建、列表、日历冒烟、空窗口发布拒绝。

缺口：

- 三层品牌归属未显式覆盖。
- 组织路径详情、筛选、冻结修改约束、删除保护为 P1。

### SA-009：技术负责人创建迭代并选择仓库

目标业务场景：技术负责人在品牌下创建迭代，并选择该品牌下已纳管仓库。

P0 验收焦点：

- 只能在品牌叶子节点下创建迭代。
- 只能选择同品牌已纳管仓库。
- 生成唯一 `iterationKey`。
- 支持 AUTO/NAMED/EXISTING 分支模式。
- 每个 `迭代 x 仓库` 记录版本、分支和模式信息。

当前覆盖：

- `run-acceptance.sh` 创建迭代并关联 3 个仓库。
- 场景 10 覆盖分支模式。

缺口：

- 同品牌仓库选择范围未显式覆盖。
- 移除仓库归档、已挂窗口后的修改限制、删除保护为 P1。
- 前端迭代详情可观察性为 P1。

### SA-010：发布经理挂载迭代到发布窗口

目标业务场景：发布经理把迭代挂到品牌发布窗口，系统生成发布计划并准备 release 分支。

P0 验收焦点：

- 只能挂载同品牌范围内迭代。
- 支持多迭代、多仓库发布计划。
- 每个仓库创建真实 `release/<windowKey>` 分支。
- attach 返回细粒度结果。
- release 分支已存在、feature 缺失、合并冲突等风险能阻断或显式报告。

当前覆盖：

- `run-acceptance.sh` 场景 4 覆盖 attach、release 分支真实存在、WindowIteration 状态。
- 场景 5/6 覆盖冲突检测和阻断。

缺口：

- 同品牌范围约束、解除挂载、冻结/已发布/已关闭限制为 P1。
- 部分成功/部分失败和失败重试需要补强。
- 前端发布计划可见性为 P1。

### SA-011：测试人员检查冲突与发布风险

目标业务场景：测试人员在发布前扫描窗口风险，看到阻塞问题并交给技术负责人处理。

P0 验收焦点：

- 可触发冲突扫描。
- 返回冲突总数和类型分布。
- 有阻塞冲突时发布或编排被拒绝。
- 解决后重扫为 0 或仅剩非阻塞项。

当前覆盖：

- `run-acceptance.sh` 场景 5 输出冲突数量和类型分布。
- 场景 6 识别 `CONFLICT_001` 为业务正确拒绝。

缺口：

- 冲突详情字段、严重级别、建议处理方式为 P1。
- GitLab 不可达/权限失败类冲突为 P1。
- 前端风险展示为 P1。

### SA-012：技术负责人解决冲突

目标业务场景：技术负责人根据冲突报告执行解决动作，解决后发布可继续。

P0 验收焦点：

- 版本冲突支持 `USE_SYSTEM` 解决。
- 解决动作更新系统记录，必要时写回仓库。
- 重新扫描冲突为 0。
- 发布或编排可以继续。

当前覆盖：

- `run-acceptance.sh` 5.2 已验证版本冲突 `USE_SYSTEM` 解决后重扫为 0。
- Playwright 已覆盖前端真实旅程：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情冲突面板重新扫描，点击“同步版本”，确认后提交 `resolution=USE_SYSTEM`，并断言重扫后无冲突。

缺口：

- release 分支已存在、feature 缺失、分支不合规等解决路径为 P1。
- 真实仓库写回证据由 `run-acceptance.sh` 承担；Playwright 当前断言前端旅程和请求语义。

### SA-013：技术负责人触发发布编排

目标业务场景：技术负责人确认无阻塞冲突后触发发布编排，系统产生可追踪 Run。

P0 验收焦点：

- 无阻塞冲突后编排成功。
- Run 状态为 COMPLETED 或 SUCCESS。
- RunItem 数量大于 0。
- RunStep 分布可见。
- GitLab release 分支、合并结果等状态一致。
- 未解决冲突时阻断。

当前覆盖：

- `run-acceptance.sh` 场景 5.2 有干净窗口黄金路径。
- 场景 7 读取 Run 详情和 Step 分布。
- 干净路径已升级为正式 PASS/FAIL 验收项，断言 Run `COMPLETED/SUCCESS`、`RunItem > 0`、`RunStep > 0`。
- 真实 GitLab 验收已验证干净窗口编排 `COMPLETED`，且 RunItem/RunStep 中包含 `MERGED`。
- Playwright 已覆盖前端真实旅程：通过 UI 创建分组、纳管仓库、创建迭代、挂载仓库、创建发布窗口、挂载迭代、发布窗口，并从窗口详情触发编排请求；请求体断言包含 UI 创建出的仓库和迭代作用域。

缺口：

- 失败 Run 重试和窗口/Run 状态一致性为 P1。
- 单条连续 UI 旅程直接跑到真实 GitLab Run 成功仍未纳入 Playwright，当前由 `run-acceptance.sh` 强证据补齐。

### SA-014：技术负责人执行版本更新

目标业务场景：技术负责人把目标版本真实写入 release 分支并推送到 GitLab。

P0 验收焦点：

- Maven 单模块 `pom.xml` 在 `release/<windowKey>` 上更新。
- 版本更新 Run COMPLETED 或 SUCCESS。
- GitLab commit 可验证。
- 更新后版本校验通过。

当前覆盖：

- `run-acceptance.sh` 场景 8 有版本更新和 GitLab commit 验证。
- SA-014 已优先绑定干净窗口；干净窗口存在时，版本更新或 GitLab commit 验证失败计为 FAIL。
- 真实 GitLab 验收已验证 Maven 单模块 `pom.xml` 在 release 分支产生 `ReleaseHub: Update` commit。
- Playwright 已覆盖前端真实旅程：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，从窗口详情打开“执行版本更新”，提交目标版本、仓库路径和 POM 路径，并断言最终版本更新请求体正确。

缺口：

- Maven 多模块、Gradle 真实写回为 P1。
- 失败原因分类、重试幂等、多仓部分失败为 P1。

### SA-015：测试人员复核发布状态和执行证据

目标业务场景：测试人员通过 UI 或接口判断发布准备是否可信。

P0 验收焦点：

- 窗口列表和详情可见。
- Run 列表和详情可见。
- Run 状态、类型、RunItem、RunStep 可见。
- 发布阻断原因可见。

当前覆盖：

- Playwright 覆盖窗口、仓库、Run、日历冒烟。
- 后端 RunView 已暴露 items 字段。
- Playwright 已补最小观察路径：从 Run 列表进入 Run 详情，从发布窗口列表进入窗口详情。
- Playwright Slice-2 已通过 UI 创建分组、仓库、迭代和发布窗口，并从窗口详情提交真实版本更新请求生成 `FAILED` Run。
- Run 列表可按 `windowKey` 和 `FAILED` 过滤，Run 抽屉可复核 `VERSION_UPDATE_FAILED`、`UPDATE_VERSION` 和缺失 POM 路径。
- Run 详情页和抽屉已兼容 export JSON 的 `runId`、`repo`、`startAt/endAt` 字段，并默认展开 RunStep 明细。
- 后端冲突检测对 mock/不可抽取版本的仓库不再把版本读取失败升级为阻断异常；mock 仓库版本更新按本地路径执行，能真实落失败 Run。

缺口：

- 冲突详情、部分失败和品牌筛选下的复核旅程为 P1。

### SA-016：发布经理关闭发布窗口并完成收尾

目标业务场景：上线完成后关闭窗口，系统完成发布收尾并留下证据。

P0 验收焦点：

- 已发布窗口可关闭。
- 关闭后状态变为 CLOSED。
- 关闭后禁止挂载迭代、修改关键配置、执行版本更新。
- 收尾 Run 可见。

当前覆盖：

- 用户故事和历史 Slice 4 设计包含收尾。
- 后端存在 `Slice4_Post_Release_Cleanup_E2ETest`。
- Playwright Slice-1 已通过 UI 创建含迭代的发布窗口，发布后关闭窗口，并断言状态为 `CLOSED`。
- `run-acceptance.sh` 已把关闭窗口、关闭后禁止挂载迭代、关闭后禁止版本更新、收尾 Run 可见作为 SA-016 主线断言。
- 收尾 Run 已能按窗口 `windowKey` 查询，并包含 `ARCHIVE_BRANCH`、`MERGE_TO_MASTER`、`CREATE_TAG`、`TRIGGER_CI` 等步骤证据。
- 前端已在 CLOSED 状态隐藏列表页和详情页的挂载入口；编排面板按真实 `windowKey` 加载最近 Run。

缺口：

- 幂等关闭、部分失败重试为 P1。
- CI pipeline 触发和发布报告导出为 P2。

## 五、第一批落地顺序

脚本输出应包含 SA 编号，验收报告引用脚本日志时必须保留这些编号。
三层分组 fixture 固定使用“验收-客户A / 验收-业务线X / 验收-品牌Y”，所有仓库、迭代和发布窗口均挂载到品牌叶子节点。
SA-013 干净黄金路径必须硬断言 Run `COMPLETED/SUCCESS`、`RunItem > 0`、`RunStep > 0`。
SA-014 版本更新优先绑定干净窗口；干净窗口存在时，版本更新或 GitLab commit 验证失败必须计为失败。
SA-015 前端验收至少覆盖 Run 详情和发布窗口详情两条观察路径。

1. 文档矩阵：本文件作为验收蓝图入口。
2. 脚本编号：`run-acceptance.sh` 输出 SA 编号，便于报告回连。
3. 三层分组：脚本已使用客户/业务线/品牌三层验收分组。
4. 干净黄金路径：SA-013 的 Run COMPLETED/SUCCESS、RunItem > 0、RunStep 分布改为硬断言。
5. 版本更新绑定干净窗口：SA-014 不再因主窗口冲突长期 SKIP。
6. 前端观察路径：补 SA-015 的 Playwright 最小旅程。

## 六、Phase 2 缺口池

- GitLab token 过期、无效、权限不足。
- GitLab 不可达。
- 分组删除保护、资源移动、code 自动生成。
- 仓库重复、错误 URL、版本解析失败状态。
- 分支规则作用域、archive 规则、历史不合规分支治理。
- 版本策略品牌/仓库作用域继承。
- Maven 多模块和 Gradle 真实写回。
- release 分支累积冲突的一键清理脚本。
- 合并冲突制造、解决和 Run retry。
- 关闭窗口后的 tag、merge to main、分支归档真实 GitLab 验证。
- 多窗口并行发布。
- 空仓库、无版本文件、异常版本号。
- 批量窗口和大规模迭代。

## 七、当前推进队列

`scenario-acceptance-matrix.md` 是当前场景化推进主线。后续推进按矩阵缺口而不是按单个脚本数字排序。

| 优先级 | 场景 | 当前判断 | 下一步验收焦点 |
|---|---|---|---|
| P1 | SA-012 更多冲突解决路径 | 版本冲突 `USE_SYSTEM` 已闭环 | 扩展 release 分支已存在、feature 缺失、分支不合规等处理路径 |
| P1 | SA-010/SA-011 发布计划与风险详情 | attach、真实 release 分支、冲突阻断已有后端/GitLab 证据 | 补前端发布计划可见性、冲突严重级别和建议处理方式 |
| P1 | SA-015 复核扩展 | P0 已能由 UI 生成失败 Run 并复核失败步骤 | 补冲突详情、部分失败和品牌筛选下的复核旅程 |
| P1 | SA-016 收尾扩展 | P0 已闭环 | 补幂等关闭、部分失败重试和发布报告导出 |
| P2 | SA-014 版本更新扩展 | Maven 单模块已闭环 | Maven 多模块、Gradle、失败重试和多仓部分失败 |

## 八、最新验证记录

### 2026-05-15 SA-015 UI 生成失败 Run 复核闭环

命令：

```bash
mvn -pl releasehub-application -Dtest=ConflictDetectionAppServiceTest test
mvn -pl releasehub-infrastructure -Dtest=MavenVersionUpdaterTest,GradleVersionUpdaterTest test
cd frontend && pnpm run typecheck
cd frontend && pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
bash scripts/acceptance/run-acceptance.sh
cd frontend && pnpm exec playwright test
```

结果：

- 冲突检测单测：`7 PASS / 0 FAIL / 0 SKIP`。
- Maven/Gradle 版本更新器单测：`12 PASS / 0 FAIL / 0 SKIP`。
- 前端类型检查通过。
- Slice-2 UI 创建发布编排旅程：`4 PASS / 0 FAIL / 0 SKIP`。
- 后端/真实 GitLab 场景验收：`PASS=51 / FAIL=0 / SKIP=0`。
- 前端完整 Playwright 回归：`30 PASS / 0 FAIL / 0 SKIP`。

已验证新增能力：

- SA-015 不再通过脚本/API 造失败数据；失败 Run 由测试人员在窗口详情 UI 提交版本更新产生。
- Run 列表按 `windowKey` + `FAILED` 过滤后能定位该 UI 生成的 `VERSION_UPDATE` Run。
- Run 抽屉默认展开执行步骤，可直接复核 `VERSION_UPDATE_FAILED`、`UPDATE_VERSION` 和缺失 POM 路径。
- mock 仓库冲突检测不再被版本抽取异常阻断，mock provider 版本更新按本地路径执行并真实记录失败 Run。

仍保留缺口：

- 冲突详情、部分失败和品牌筛选下的复核旅程为 P1。

### 2026-05-15 SA-016 发布后收尾闭环

命令：

```bash
bash scripts/acceptance/run-acceptance.sh
scripts/dev/start-local-env.sh hold
cd frontend && pnpm run test:e2e
scripts/dev/start-local-env.sh stop
```

结果：

- 后端/真实 GitLab 场景验收：`PASS=50 / FAIL=0 / SKIP=0`。
- 前端完整 Playwright 回归：`29 PASS / 0 FAIL / 0 SKIP`。

已验证新增能力：

- SA-016 关闭窗口成功，状态变为 `CLOSED`。
- SA-016 关闭后挂载迭代被 `RW_009` 拒绝。
- SA-016 关闭后版本更新被 `RW_009` 拒绝。
- SA-016 收尾 Run 可按窗口 `windowKey` 查询，并包含归档分支、合并到主分支、创建 Tag、触发 CI 等步骤证据。
- 前端 CLOSED 窗口不再展示挂载入口，窗口详情按 `windowKey` 加载最近 Run。

仍保留缺口：

- 幂等关闭、部分失败重试为 P1。
- 发布报告导出和更完整的失败态收尾复核为 P2。

### 2026-05-15 场景矩阵基线复验与统一服务入口修正

命令：

```bash
scripts/dev/start-local-env.sh hold
bash scripts/acceptance/run-acceptance.sh
cd frontend && pnpm run test:e2e
scripts/dev/start-local-env.sh stop
```

结果：

- 后端/真实 GitLab 场景验收：`PASS=46 / FAIL=0 / SKIP=0`。
- 前端完整 Playwright 回归：`29 PASS / 0 FAIL / 0 SKIP`。

已验证新增能力：

- `scripts/dev/start-local-env.sh` 已成为本地前后端统一入口，支持 `start|hold|stop|restart|status`。
- 统一入口使用 `local,real` profile 启动后端，并用 `VITE_PROXY_TARGET=http://localhost:8080` 启动前端，前端 `/api` 代理登录返回 200。
- SA-012/SA-013/SA-014 的 P0 验收焦点已同时具备前端用户旅程、后端业务约束和真实 GitLab/数据证据，状态调整为“已覆盖”。
- SA-016 已具备 UI 关闭窗口证据，状态从“缺口较大”调整为“部分覆盖”。

仍保留缺口：

- SA-016 仍缺关闭后的禁止关键操作、收尾 Run 可见性，以及 tag/merge/archive 的真实 GitLab 收尾证据。
- SA-015 仍缺失败 Run、冲突详情和部分失败复核旅程。
- SA-012 更多冲突类型解决路径仍为 P1/P2。

### 2026-05-12 真实 GitLab 验收

命令：

```bash
bash scripts/acceptance/run-acceptance.sh
```

结果：`PASS=36 / FAIL=1 / SKIP=0`。

已验证新增能力：

- SA 编号已出现在脚本输出中。
- SA-003 三层分组 fixture 创建成功：`验收-客户A -> 验收-业务线X -> 验收-品牌Y`。
- 非叶子分组创建发布窗口、迭代、仓库均被拒绝。
- SA-001/SA-004 Settings 重启持久化仍通过。

当轮暴露缺口（已在 2026-05-13 SA-013/SA-014 收口复验中关闭）：

- SA-012 的版本冲突解析脚本存在 shell 引号问题，已修复为单引号 Python 片段和 process substitution。
- SA-013/SA-014 当轮未通过：系统在 attach 后把正常存在的 `feature/<iterationKey>` 和 `release/<windowKey>` 判定为 `BRANCH_EXISTS`，并产生 `MERGE_CONFLICT`，导致干净窗口仍有 4 个冲突，版本更新被 `CONFLICT_001` 阻断。

结论：本轮自动化成功把“干净路径不可达”从历史 SKIP/WARN 升级为明确 FAIL。后续需要修正冲突检测语义，区分“发布准备阶段应存在的分支”和“真正阻断发布的重复/冲突分支”。

### 2026-05-13 后端服务管理与冲突语义复验

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
mvn -pl releasehub-application -Dtest=ConflictDetectionAppServiceTest test
bash scripts/acceptance/run-acceptance.sh --start-services
bash scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh --stop-services
```

结果：完整验收为 `PASS=37 / FAIL=1 / SKIP=0`。

已验证新增能力：

- 验收脚本已内置后端服务状态检查、启动和停止入口。
- 验收脚本启动后端前会先安装当前 workspace 的 backend reactor 模块，避免重启后加载本地 Maven 仓库里的旧 application 包。
- SA-001/SA-004 的 Settings 重启持久化验证复用统一的 `stop_backend/start_backend` 服务生命周期逻辑。
- `BRANCH_EXISTS` 不再出现在 attach 后的主窗口和干净窗口冲突扫描中。
- `ConflictDetectionAppServiceTest` 已新增回归用例，确认已管理的 feature/release 分支存在不应被判为 `BRANCH_EXISTS`。

当轮暴露缺口（已在 2026-05-13 SA-013/SA-014 收口复验中关闭）：

- SA-013/SA-014 当轮未完全通过：干净窗口剩余 `MISMATCH=1` 和 `MERGE_CONFLICT=1`，版本更新被冲突预检阻断。
- 下一步需要继续定位版本提取/版本记录不一致，以及 attach 后 mergeability 对已合并分支的判断语义。

### 2026-05-13 SA-013/SA-014 收口复验

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
mvn -pl releasehub-infrastructure -Dtest=GitLabGitBranchAdapterTest test
mvn -pl releasehub-application -Dtest=AttachAppServiceTest,IterationAppServiceTest,ConflictDetectionAppServiceTest test
bash scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh --stop-services
```

结果：完整验收为 `PASS=45 / FAIL=0 / SKIP=0`。

已验证新增能力：

- SA-012 版本冲突 `USE_SYSTEM` 会把系统版本写回 feature 分支，重扫后冲突为 0。
- SA-013 干净窗口黄金路径已通过：Publish 成功，编排 Run 为 `COMPLETED`，RunItem > 0，RunStep > 0，步骤结果包含 `MERGED`。
- SA-013 主窗口未解决冲突时仍被编排预检拒绝，阻断语义正确。
- SA-014 版本更新已绑定干净窗口，Run 为 `SUCCESS`，GitLab release 分支可查到 `ReleaseHub: Update` commit。
- GitLab MR `commits_status` / `No commits between` 被视为已无可合并提交的幂等成功，避免 attach 已合入后再次编排失败。

- 前端触发编排、冲突解决和版本更新旅程已有 UI 建数 + 请求语义自动化；真实 GitLab Run/commit 强证据仍由验收脚本承担。
- Maven 多模块、Gradle 真实写回、失败重试和多仓部分失败仍为 Phase 2。

### 2026-05-13 前端 Playwright 基线复验

命令：

```bash
pnpm run test:e2e
```

结果：`23 PASS / 0 FAIL / 3 SKIP`。

已验证前端旅程：

- 登录页渲染、表单校验、错误凭据、记住我切换、成功登录、Enter 提交均通过。
- Slice-1 分组 + 发布窗口旅程通过：三层分组创建、树层级复核、叶子品牌创建窗口、冻结/解冻、空窗口发布拒绝、窗口详情查看。
- Slice-2 全链路观察旅程通过：Dashboard、仓库列表、发布窗口列表、创建窗口、迭代页、Run 历史、日历页、主要页面可访问、SA-015 Run 详情和窗口详情复核。

显式 SKIP：

- 删除含子节点父分组的保护断言尚未纳入前端 E2E。
- 非叶子分组创建窗口的前端断言尚未纳入前端 E2E。
- 关闭窗口需要 PUBLISHED 前置，等待后续 Slice 3/4 旅程补齐。

仍保留缺口：

- SA-012/SA-013/SA-014 已有前端触发请求证据，但单条连续 UI→真实 GitLab Run 成功仍由验收脚本证据补齐。

### 2026-05-13 SA-013 前端用户旅程自动化启动

命令：

```bash
pnpm exec playwright test slice-2-full-flow.spec.ts -g "SA-013 frontend path"
pnpm run test:e2e
```

结果：

- SA-013 新增前端旅程通过：从 UI 创建分组、纳管仓库、创建迭代、给迭代添加仓库、创建发布窗口、挂载迭代、发布窗口，并在窗口详情页触发“执行收尾”。
- 新增旅程未用 API/DB 直接准备 ReleaseHub 业务数据；最终编排请求仅在前端层拦截以断言请求体包含 UI 创建出的 `repoIds` / `iterationKeys` / `failFast=false` / `operator=frontend`。
- 完整 Playwright 回归结果更新为 `24 PASS / 0 FAIL / 3 SKIP`。
- 修复前端编排请求缺口：`OrchestrationPanel` 不再只传 `windowId`，会从窗口详情关联迭代/仓库派生编排作用域。
- 修复迭代添加仓库弹窗的真实点击选择问题，保证用户点击仓库行后“已选择 N 个新仓库”正确更新。
- 验收脚本新增 `--hold-services`，用于自动化验证期间保活脚本托管的后端服务，避免回到一次性手写启动命令。

仍保留缺口：

- SA-013 目前前端层断言到“触发请求作用域正确”，Run 完整执行证据仍由 `run-acceptance.sh` 后端/GitLab 验收承担。
- SA-012 冲突解决 UI 旅程已在 2026-05-14 补齐版本冲突 `USE_SYSTEM` 路径。

### 2026-05-13 SA-014 前端用户旅程自动化补齐

命令：

```bash
pnpm exec playwright test slice-2-full-flow.spec.ts -g "UI-created release orchestration journey"
pnpm run test:e2e
```

结果：

- SA-013/SA-014 serial 前端旅程通过：同一条 Playwright 旅程通过 UI 创建业务数据，再从窗口详情触发编排和版本更新。
- SA-014 新增前端旅程通过：版本更新弹窗只使用当前发布窗口关联仓库；单仓场景自动选中该仓库；提交请求体包含 UI 创建出的 `repoId`、`targetVersion=1.4.1`、`buildTool=MAVEN`、`repoPath` 和 `pomPath=pom.xml`。
- 修复前端事件契约缺口：`OrchestrationPanel` 显式 emit `open-version-update`，与 `ReleaseWindowDetail` 监听保持一致，避免用户点击“执行版本更新”无响应。
- 修复版本更新仓库作用域：`VersionUpdateDialog.open(windowId, windowRepositories)` 优先使用当前窗口仓库，不再打开后全局加载所有仓库。
- 完整 Playwright 回归结果更新为 `25 PASS / 0 FAIL / 3 SKIP`。

仍保留缺口：

- SA-014 的真实 GitLab commit、Run SUCCESS 由 `run-acceptance.sh` 验收脚本承担；Playwright 当前断言前端旅程和请求作用域，不直接让 UI 创建的 mock 仓库执行真实版本写回。

### 2026-05-14 SA-012 前端用户旅程自动化补齐

命令：

```bash
pnpm exec playwright test slice-2-full-flow.spec.ts -g "UI-created release orchestration journey"
pnpm run test:e2e
```

结果：

- SA-012/SA-013/SA-014 serial 前端旅程通过：同一条 Playwright 旅程通过 UI 创建业务数据，再从窗口详情完成版本冲突解决、发布编排触发和版本更新触发。
- SA-012 新增前端旅程通过：冲突面板重新扫描出版本不一致，用户点击“同步版本”，确认后请求 `resolution=USE_SYSTEM`，随后重扫为无冲突。
- 修复冲突解决事件链路：`ConflictPanel` 显式 emit `resolve`，`ReleaseWindowDetail` 调用 `iterationApi.resolveVersionConflict(..., 'USE_SYSTEM')` 并刷新冲突面板。
- 修复两个非本轮引入但影响旅程稳定的小问题：冲突解决按钮不再包在 tooltip 触发器里；迭代详情“添加仓库”不再在可见按钮后做静默前端权限二次拦截。
- 完整 Playwright 回归结果更新为 `26 PASS / 0 FAIL / 3 SKIP`。

仍保留缺口：

- SA-012 的真实仓库写回证据仍由 `run-acceptance.sh` 承担；Playwright 当前断言前端旅程和请求语义。
- release 分支已存在、feature 缺失、分支不合规等更多冲突解决路径仍为 P1/P2。

### 2026-05-14 Slice-1 历史 SKIP 可执行化

可执行入口：

```bash
bash scripts/acceptance/run-acceptance.sh --start-services --hold-services
cd frontend && pnpm run test:e2e:slice-1
cd frontend && pnpm run test:e2e
bash scripts/acceptance/run-acceptance.sh --stop-services
```

结果：

- Slice-1 单入口：`10 PASS / 0 FAIL / 0 SKIP`。
- 完整 Playwright 回归：`29 PASS / 0 FAIL / 0 SKIP`。
- `frontend/e2e/tests` 中已无 `test.skip` / `.skip(`。

已补齐原显式 SKIP：

- 删除含子节点父分组的保护断言：通过 UI 选中父分组、点击删除并确认，断言父分组和子分组仍存在。
- 非叶子分组创建发布窗口：通过创建窗口弹窗验证非叶子节点禁用，用户不能选择非叶子分组提交窗口。
- 关闭窗口前置：通过 UI 创建迭代、在发布窗口弹窗中关联迭代、发布窗口、关闭窗口并断言状态为 `CLOSED`。

同步修复的小问题：

- 创建子分组弹窗的 `parentCode` 预填和锁定不稳定；现在打开后会回填并禁用父编码，提交成功后关闭弹窗。
- 关联迭代弹窗清空选择只清业务状态、不清表格选中态；现在同步清空 Element Plus 表格 selection。
- 分组 TreeSelect 默认展开，减少大量存量分组下叶子节点不可发现的问题。
