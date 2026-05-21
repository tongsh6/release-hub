# ReleaseHub 场景化验收矩阵

> 日期：2026-05-21
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
| SA-003 | Admin Setup | 系统管理员 | 建立多层分组树 | 三层分组、资源只能挂叶子分组 | 已覆盖 |
| SA-004 | Admin Setup | 系统管理员 | 配置 GitLab 连接 | 保存、不泄露、重启持久化、真实 API 可用 | 已覆盖 |
| SA-005 | Admin Setup | 系统管理员 | 纳管代码仓库 | 叶子分组归属、真实 GitLab 可用、token 安全、默认分支/版本基础信息 | 部分覆盖 |
| SA-006 | Admin Setup | 系统管理员 | 配置分支规则 | feature/hotfix/release 规则在分支创建时生效，不合规拒绝 | 部分覆盖 |
| SA-007 | Admin Setup | 系统管理员 | 配置版本策略 | 基础策略、SemVer、版本校验、Maven 单模块真实写回前置 | 部分覆盖 |
| SA-008 | Release Planning | 发布经理 | 创建发布窗口 | 叶子分组创建、windowKey、DRAFT、空窗口发布拒绝、列表/日历可见 | 部分覆盖 |
| SA-009 | Release Planning | 技术负责人 | 创建迭代并选择已纳管仓库 | 叶子分组创建、同分组仓库选择、iterationKey、分支模式、版本/分支记录 | 部分覆盖 |
| SA-010 | Release Planning | 发布经理 | 挂载迭代到发布窗口 | 同分组挂载、多迭代多仓计划、release 分支真实创建、细粒度 attach 结果、冲突阻断 | 部分覆盖 |
| SA-011 | Risk & Execution | 测试人员 | 检查冲突与发布风险 | 冲突扫描、类型分布、阻塞发布、解决后重扫清零；`MERGE_CONFLICT`、`CROSS_REPO_VERSION_MISMATCH`、`REPO_AHEAD`、`SYSTEM_AHEAD`、`GIT_PERMISSION_DENIED`、`GIT_UNAVAILABLE` 具备真实 GitLab 强证据 | 已覆盖 |
| SA-012 | Risk & Execution | 技术负责人 | 解决冲突 | 版本冲突 `USE_SYSTEM` 解决、重扫为 0、发布可继续；feature 缺失、release 已存在、分支名不合规具备后端/GitLab 强证据 | 已覆盖 |
| SA-013 | Risk & Execution | 技术负责人 | 触发发布编排 | 无阻塞冲突后 Run COMPLETED/SUCCESS、RunItem > 0、GitLab 状态一致、未解决冲突阻断 | 已覆盖 |
| SA-014 | Risk & Execution | 技术负责人 | 执行版本更新 | Maven 单模块真实写回 release 分支、Run COMPLETED/SUCCESS、GitLab commit 可验证 | 已覆盖 |
| SA-015 | Risk & Execution | 测试人员 | 复核发布状态和执行证据 | 窗口/Run 列表与详情、状态和执行明细可见 | 已覆盖 |
| SA-016 | Post Release | 发布经理 | 关闭发布窗口并完成收尾 | 关闭状态流转、关闭后禁止关键操作、收尾 Run 可见 | 已覆盖 |

## 三、前后端场景证据矩阵

| ID | 用户旅程入口 | 后端业务证据 | GitLab/数据证据 | 当前主要缺口 |
|---|---|---|---|---|
| SA-003 | 管理员在分组页面创建多层分组并查看树 | Group API、非叶子资源挂载拒绝 | 仓库/迭代/窗口 groupCode 均落在叶子分组 | 前端已稳定断言仓库、迭代、发布窗口创建入口只能选择叶子分组；资源移动、关联资源删除保护为 P1/P2 |
| SA-004 | 管理员在系统设置页保存并测试 GitLab 连接 | Settings 保存、读取、重启持久化；`system_settings.gitlab_token` 透明加密；连接测试调用 GitLab `/api/v4/user` | 后续真实 GitLab 分支操作成功且 token 不泄露；验收脚本同时审计仓库 token 和 Settings token 明文数量；无效 token / 不可达会返回 `GITLAB_003` | 已补前端连接测试入口、成功提示和失败错误出口；后续仅保留更细粒度诊断展示 |
| SA-005 | 管理员在仓库页纳管分组仓库并查看详情 | 仓库创建校验、叶子分组归属、重复/错误 URL 校验；仓库列表支持按组织及子组织范围筛选；详情页/抽屉展示组织路径和版本解析状态 | 真实 GitLab cloneUrl、默认分支、token 安全审计；初始版本来源 `versionSource` 可复核 | Clone URL 格式校验、规范化重复纳管保护、版本解析失败修复引导和按组织筛选已补；删除保护为 P1/P2 |
| SA-006 | 管理员在分支规则页配置命名规范 | BranchRule 校验、AUTO/NAMED/EXISTING 分支模式约束 | 创建出的 feature/hotfix/release 分支名称符合规则 | 前端完整规则管理旅程和规则作用域仍不足 |
| SA-007 | 管理员在版本策略页配置版本演进规则 | SemVer 校验、PATCH/MINOR/MAJOR 推导 | Maven/Gradle 写回前置条件可验证 | 策略作用域和策略继承仍不足；多模块/Gradle 写回由 SA-014 覆盖 |
| SA-008 | 发布经理在窗口页创建发布窗口并查看列表/日历 | 发布窗口创建、DRAFT 状态、空窗口发布拒绝 | windowKey 唯一且关联叶子分组 | 前端需补组织路径、筛选和冻结限制证据 |
| SA-009 | 技术负责人在迭代页创建迭代并选择仓库 | 同分组仓库选择、iterationKey、分支模式记录；创建、更新和追加仓库写入前均拒绝跨分组仓库；已挂窗口后禁止变更仓库集合或迭代分组 | feature 分支和版本信息落库并可追踪；跨分组仓库不会触发分支创建、版本记录或迭代保存副作用；已挂窗口后不会归档 feature 分支或污染发布计划 | 同分组候选过滤、后端跨分组拒绝和已挂窗口修改限制已补；删除保护、移除仓库归档更多真实 GitLab 证据和前端迭代详情可观察性仍为 P1 |
| SA-010 | 发布经理在窗口详情页挂载迭代并查看发布计划 | attach/detach 细粒度结果、状态流转、冲突阻断；解除挂载已有前端详情页入口、后端约束、UI E2E 和真实 GitLab 分支归档复核；发布后计划变更已锁定 | release 分支真实创建，WindowIteration 状态一致；detach 后原 release 分支删除且 `archive/unpublished/release-<windowKey>` 存在；部分失败重试已有后端/GitLab 证据 | 发布计划已有最小前端观察；解除挂载已补 Vitest、Slice-1 Playwright 和真实 GitLab 分支归档证据；发布后 attach/detach 已有后端拒绝和前端隐藏入口，后续保留部分成功/失败与失败重试前端复核 |
| SA-011 | 测试人员在窗口详情页触发/查看风险扫描 | 冲突总数、类型分布、阻塞发布；`MERGE_CONFLICT`、`CROSS_REPO_VERSION_MISMATCH`、`REPO_AHEAD`、`SYSTEM_AHEAD`、`GIT_PERMISSION_DENIED`、`GIT_UNAVAILABLE` 已有后端冲突扫描证据和前端展示 | 冲突与 GitLab 分支/版本状态可对应；`MERGE_CONFLICT` 已有真实 feature/release 分支和冲突提交证据；`CROSS_REPO_VERSION_MISMATCH` 已有真实 feature/release 分支、两仓 targetVersion 差异和冲突扫描证据；`REPO_AHEAD`/`SYSTEM_AHEAD` 已有真实 feature 分支 `pom.xml` 版本差异和冲突扫描证据；`GIT_PERMISSION_DENIED`/`GIT_UNAVAILABLE` 已有真实 GitLab 权限不足和不可达探针证据 | 严重级别、建议处理方式、合并冲突、跨仓版本不一致、仓库版本较新、系统版本较新、Git 权限不足和 Git 不可达均已有前端观察；后续保持回归 |
| SA-012 | 技术负责人在冲突详情中执行解决动作 | `USE_SYSTEM` 等解决动作更新记录，重扫清零；feature 缺失、release 已存在、分支名不合规具备后端业务证据 | 必要时写回仓库或保留处理证据；分支名不合规路径已补 GitLab 分支直查与冲突扫描证据 | P0 版本冲突 `USE_SYSTEM` 已闭环；feature 缺失、release 分支已存在和分支名不合规强证据已补；更多冲突类型解决路径为 P1/P2 |
| SA-013 | 技术负责人在窗口详情页触发发布编排 | 无阻塞冲突后 Run COMPLETED/SUCCESS，冲突未解决时拒绝 | RunItem/RunStep、GitLab 分支状态一致 | P0 已闭环；后续补 UI 侧执行后结果复核和失败 Run 观察 |
| SA-014 | 技术负责人在版本操作入口执行版本更新 | 版本更新 Run COMPLETED/SUCCESS，失败原因可见；多仓窗口可从版本更新弹窗提交批量版本更新请求；批量版本更新可保留成功项并暴露失败项原因 | `pom.xml` / `gradle.properties` 在 release 分支真实 commit；批量请求按仓库生成 repoPath 并调用既有后端批量端点；批量部分失败 RunItem 可追溯到成功仓库、失败仓库和失败 POM 路径 | P0 Maven 单模块已闭环；批量版本更新前端入口、Maven 多模块、Gradle 真实写回和多仓部分失败后端/GitLab 证据已补；版本更新失败重试仍为 Phase 2 |
| SA-015 | 测试人员在 Run/窗口详情复核执行证据 | Run 列表、Run 详情、窗口详情返回完整状态；UI 触发失败版本更新后可按窗口、状态和分组筛选并复核失败 Run；窗口详情可复核冲突类型分布、分支/版本详情和建议处理方式；Run 详情可复核一个 Run 内成功项与失败项并存，并可直接重试失败项；后端/GitLab 证据可复核真实部分失败重试只选择失败项；窗口报告 CSV/JSON 可导出最近 Run 与迭代/仓库明细 | RunItem/RunStep 可追溯到窗口、迭代、仓库和失败 POM 路径；Run 分页接口支持按发布窗口分组过滤；retry 新 Run 保留选中失败项且不重复执行成功项；报告端点按窗口聚合 Run 和 WindowIteration 明细 | P0 已闭环；Run 详情失败项重试前端入口、部分失败重试后端/GitLab 强证据与发布报告导出已补 |
| SA-016 | 发布经理在窗口详情页关闭窗口并查看收尾结果 | CLOSED 状态、关闭后关键操作禁止、重复关闭幂等；窗口详情可触发报告导出 | tag、merge、归档和收尾 Run 可追踪；报告导出包含窗口状态、迭代、仓库和最近 Run 证据 | P0 已闭环；真实部分失败重试后端/GitLab 证据与发布报告导出已补 |

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

- 无效 token、GitLab 不可达、权限不足的更细粒度诊断展示作为 P2。

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
    └── 末级分组Y
        └── 仓库、迭代、发布窗口归属到末级分组Y
```

P0 验收焦点：

- 管理员创建多层分组。
- API 和前端能展示完整树结构。
- 仓库、迭代、发布窗口只能挂叶子分组。
- 发布范围能沿分组层级追溯。

当前覆盖：

- Playwright 有三层分组创建用例。
- 后端 Group API/E2E 覆盖基础 CRUD。
- `run-acceptance.sh` 已固定创建 `验收-客户A -> 验收-业务线X -> 验收-末级分组Y`，并验证仓库、迭代、发布窗口不能挂非叶子分组。
- Slice-1 Playwright 已覆盖仓库、迭代、发布窗口三个资源创建弹窗：非叶子分组节点展示“有子分组”并带 `aria-disabled=true`，用户不能选择非叶子分组作为资源归属。

缺口：

- 删除有关联资源分组的保护为 P1。
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
- `SystemSettingsJpaEntity.gitlabToken` 复用 `GitTokenAttributeConverter` 透明加密，`GitTokenAttributeConverterTest` 覆盖长明文 token 加密、历史无前缀密文兼容和加密关闭透传。
- `run-acceptance.sh` v3.16 已把 `system_settings.gitlab_token` 纳入 SA-002/SA-004 token 明文审计。
- `GET /api/v1/settings/gitlab/test` 已从固定返回 true 改为调用 GitLab `/api/v4/user`；`GitLabAdapterTest` 覆盖成功、401 和配置缺失；`SettingsApiTest` 覆盖 API 委托和业务错误响应。
- 前端设置页“测试连接”成功显示专用文案，失败走统一 `handleError`，`Settings.spec.ts` 已覆盖。

缺口：

- 更细粒度区分权限不足、token 无效、网络不可达的诊断详情为 P2。

### SA-005：管理员纳管代码仓库

目标业务场景：管理员把分组下的真实仓库纳入 ReleaseHub，作为后续迭代和发布范围基础。

P0 验收焦点：

- 仓库只能挂叶子分组。
- 仓库名称、Clone URL、Git Provider 必填。
- token 安全存储，不明文回显。
- 默认分支可配置或识别。
- 基础版本信息可解析或状态明确。
- 真实 GitLab 分支操作可用。

当前覆盖：

- `run-acceptance.sh` 注册/复用 3 个真实 GitLab 仓库并刷新 token。
- 存量审计覆盖仓库 token 明文和 cloneUrl 双前缀异常。
- `GET /api/v1/repositories/{id}/initial-version` 已返回 `versionSource`，可区分手动设置、POM/Gradle 解析和 `VERSION_UNRESOLVED`。
- 仓库详情抽屉和路由详情页已展示分组树解析出的组织路径、初始版本号和版本来源；`RepositoryDrawer.spec.ts` 覆盖详情可观察性。
- Clone URL 创建/更新前会解析为规范化 key；SSH、HTTP(S)、尾部 `.git` 和大小写差异指向同一仓库时以 `REPO_012` 拒绝重复纳管，不可解析地址以 `REPO_013` 拒绝。
- 前端仓库编辑弹窗已补 Clone URL 格式校验，明显错误地址在用户提交前可见。
- 仓库详情页在初始版本为空或来源为 `VERSION_UNRESOLVED` 时提供重新解析入口，调用既有 `sync-version` 接口后页面内刷新版本号和来源。
- 仓库列表已支持选择组织节点筛选；后端按所选组织及其子组织集合分页查询，父组织筛选可返回子组织仓库。

缺口：

- 删除保护为 P1。

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

- archive 规则、作用域到分组/仓库、历史不合规分支治理为 P1。
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
- `run-acceptance.sh` SA-014 已验证 Maven 单模块、多模块和 Gradle 真实 GitLab 写回。

缺口：

- 批量版本更新多仓部分失败已由 SA-014 8.5 覆盖；版本更新失败重试为 P1/P2。
- 分组/仓库作用域和策略继承为 P1/P2。

### SA-008：发布经理创建发布窗口

目标业务场景：发布经理为分组创建发布窗口，明确上线计划和后续发布主线对象。

P0 验收焦点：

- 只能在叶子分组下创建窗口。
- 生成唯一 `windowKey`。
- 创建后为 DRAFT 或待发布状态。
- 空窗口发布被拒绝。
- 列表和日历可见。

当前覆盖：

- `run-acceptance.sh` 创建发布窗口。
- Playwright 覆盖窗口创建、列表、日历冒烟、空窗口发布拒绝。

缺口：

- 分组归属未显式覆盖。
- 组织路径详情、筛选、冻结修改约束、删除保护为 P1。

### SA-009：技术负责人创建迭代并选择仓库

目标业务场景：技术负责人在分组下创建迭代，并选择该分组下已纳管仓库。

P0 验收焦点：

- 只能在叶子分组下创建迭代。
- 只能选择同分组已纳管仓库。
- 生成唯一 `iterationKey`。
- 支持 AUTO/NAMED/EXISTING 分支模式。
- 每个 `迭代 x 仓库` 记录版本、分支和模式信息。

当前覆盖：

- `run-acceptance.sh` 创建迭代并关联 3 个仓库。
- 场景 10 覆盖分支模式。
- `IterationAppService` 在创建、更新和追加仓库时统一校验仓库 `groupCode` 必须等于迭代分组；跨分组仓库以 `ITER_005` 拒绝，并在分支创建、版本记录和迭代保存前中断。
- 迭代详情“添加仓库”弹窗按当前迭代 `groupCode` 过滤候选仓库；`AddReposDialog.spec.ts` 覆盖只展示同分组仓库。
- 已挂载发布窗口的迭代不能再追加、移除仓库，也不能通过更新接口变更仓库集合或分组；详情 API 返回 `attachedToWindow`，前端隐藏添加/移除仓库入口并展示锁定状态。

缺口：

- 移除仓库归档更多真实 GitLab 证据、删除保护为 P1。
- 前端迭代详情可观察性为 P1。

### SA-010：发布经理挂载迭代到发布窗口

目标业务场景：发布经理把迭代挂到发布窗口，系统生成发布计划并准备 release 分支。

P0 验收焦点：

- 只能挂载同分组范围内迭代。
- 支持多迭代、多仓库发布计划。
- 每个仓库创建真实 `release/<windowKey>` 分支。
- attach 返回细粒度结果。
- release 分支已存在、feature 缺失、合并冲突等风险能阻断或显式报告。

当前覆盖：

- `run-acceptance.sh` 场景 4 覆盖 attach、release 分支真实存在、WindowIteration 状态。
- 场景 5/6 覆盖冲突检测和阻断。
- 窗口详情已把原分支状态面板升级为发布计划面板，展示计划顺序、迭代、仓库、feature/release 分支和合并状态；Playwright Slice-2 断言 UI 挂载迭代后计划可见。
- 窗口详情已补解除挂载入口：非冻结、非关闭窗口可从关联迭代列表确认解除挂载，前端调用既有 `detach` API 并刷新关联列表；Vitest 覆盖确认、API 调用和刷新行为。
- Playwright Slice-1 已补解除挂载 UI 旅程：页面创建迭代、列表挂载到发布窗口、进入窗口详情、确认解除挂载、断言 `detach` 请求成功、关联迭代为空且发布计划面板隐藏。
- `run-acceptance.sh` 4.2 已补解除挂载真实 GitLab 分支归档证据：独立窗口/迭代 attach 后确认 `release/<windowKey>` 存在；detach 后确认 WindowIteration 为空、原 release 分支删除、`archive/unpublished/release-<windowKey>` 存在。
- 后端 `AttachAppServiceTest` 已覆盖解除挂载时以 `unpublished` 原因归档 release 分支，并覆盖冻结/关闭窗口拒绝解除。
- 后端 `AttachAppServiceTest` 已补同分组范围约束：跨分组迭代在写入 `WindowIteration`、创建 release 分支和保存 Run 前被 `RW_013` 拒绝；前端挂载弹窗展示迭代分组并禁选非同分组行。
- 发布后发布计划锁定：`AttachAppService` 只允许 DRAFT 且未冻结窗口 attach/detach，PUBLISHED/CLOSED 均返回 `RW_009`；窗口详情页只在 DRAFT 且未冻结时显示挂载和解除挂载入口。

缺口：

- 部分成功/部分失败和失败重试需要补强。
- 更多真实分支状态和部分失败重试的前端复核为 P1。

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
- Playwright Slice-2 已在窗口详情冲突面板断言冲突严重级别和建议处理方式可见。
- Playwright Slice-2 已新增 `MERGE_CONFLICT` 风险详情观察：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情冲突面板重新扫描出 `MERGE_CONFLICT` + `MISMATCH` 类型分布，断言合并冲突计数、源/目标分支、阻断级别、建议处理方式和“请到 Git 平台解决/Resolve in Git”外部处理入口可见，同时确认不会误触发版本同步接口。
- Playwright Slice-2 已新增 `CROSS_REPO_VERSION_MISMATCH` 风险详情观察：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情冲突面板重新扫描出 `CROSS_REPO_VERSION_MISMATCH`，断言跨仓版本冲突计数、系统/仓库版本差异、阻断级别、建议处理方式和无应用内同步按钮。
- Playwright Slice-2 已新增 `REPO_AHEAD` / `SYSTEM_AHEAD` 风险详情观察：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情冲突面板重新扫描出仓库版本较新与系统版本较新，断言类型分布、版本差异、阻断级别、建议处理方式，并确认版本领先类冲突可走应用内“同步版本”处理且请求语义为 `USE_SYSTEM`。
- `run-acceptance.sh` 5.6 已补 `MERGE_CONFLICT` 后端/GitLab 强证据：脚本在真实 GitLab 上创建本轮唯一 feature/release 分支，并分别向 `pom.xml` 写入冲突提交；迭代用 `EXISTING` 模式关联 feature 分支，attach 后产生 `MERGE_BLOCKED` Run 证据，冲突扫描检出 `MERGE_CONFLICT`。
- `run-acceptance.sh` 5.7 已补 `CROSS_REPO_VERSION_MISMATCH` 后端/GitLab 强证据：脚本临时设置两仓库初始版本制造不同 targetVersion，创建同一迭代的真实 GitLab feature/release 分支，复原仓库初始版本后触发冲突扫描并检出跨仓目标版本不一致。
- `run-acceptance.sh` 5.8 已补 `REPO_AHEAD` / `SYSTEM_AHEAD` 后端/GitLab 强证据：脚本创建同一迭代的两条真实 feature 分支，分别把 `pom.xml` 写成高于系统开发版本和低于系统开发版本，冲突扫描检出两类版本领先冲突；干净黄金路径的自动解决候选也覆盖所有版本类冲突。
- `run-acceptance.sh` 5.9 已补 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE` 后端/GitLab 强证据：脚本注册权限不足探针仓库和不可达探针仓库，真实触发 GitLab 401/不可达路径，冲突扫描分别检出两类阻断风险且可追溯到对应仓库。
- Playwright Slice-2 已新增 Git 访问异常风险详情观察：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情冲突面板重新扫描出 `GIT_PERMISSION_DENIED` + `GIT_UNAVAILABLE` 类型分布，断言阻断级别、建议处理方式、“处理 Git 访问”外部处理入口可见，并确认不会误触发版本同步接口。

缺口：

- `MERGE_CONFLICT`、`CROSS_REPO_VERSION_MISMATCH`、`REPO_AHEAD`、`SYSTEM_AHEAD` 已具备真实 GitLab 强证据和前端详情字段复核。
- Git 访问异常已新增并验证 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE` 两类冲突，后端扫描不再把权限/不可达误判为普通合并冲突或让整次扫描失败；前端冲突面板已能展示类型、严重级别、建议处理方式和外部处理入口。
- SA-011 当前后端/GitLab 强证据与 Playwright 用户旅程证据已闭环，后续仅保留回归和更多恢复体验扩展。

### SA-012：技术负责人解决冲突

目标业务场景：技术负责人根据冲突报告执行解决动作，解决后发布可继续。

P0 验收焦点：

- 版本冲突支持 `USE_SYSTEM` 解决。
- 解决动作更新系统记录，必要时写回仓库。
- 重新扫描冲突为 0。
- 发布或编排可以继续。

当前覆盖：

- `run-acceptance.sh` 5.2 已验证版本冲突 `USE_SYSTEM` 解决后重扫为 0。
- `run-acceptance.sh` 5.3 已补 feature 缺失后端/GitLab 强证据：脚本先创建真实 `feature/<iterationKey>`，再删除本轮唯一分支制造缺失；随后由 GitLab API 直查确认分支不存在，`branch-status` 返回 `featureBranch.exists=false` 且 release 分支存在，Orchestrate RunStep 以 `ENSURE_FEATURE/SKIPPED` 显式报告缺失分支。
- `run-acceptance.sh` 5.4 已补 release 分支已存在后端/GitLab 强证据：脚本先用 GitLab API 预创建本轮 `release/<windowKey>`，attach 前 GitLab 直查确认已存在；attach 后 `branch-status` 返回 release/feature 分支均存在，Attach RunStep 以 `ENSURE_RELEASE/BRANCH_EXISTS` 显式记录已存在分支。
- Playwright 已覆盖前端真实旅程：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情冲突面板重新扫描，点击“同步版本”，确认后提交 `resolution=USE_SYSTEM`，并断言重扫后无冲突。
- Playwright 已新增 feature 缺失发布计划观察：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情发布计划面板断言 `feature/<iterationKey>` 展示为“不存在/Missing”，同时展示对应 `release/<windowKey>`、计划顺序和待合并状态。
- Playwright 已新增分支名不合规路径观察：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情冲突面板重新扫描出 `BRANCH_NONCOMPLIANT`，断言阻断级别、分支名、建议处理方式和“处理此分支/Resolve Branch”外部处理入口可见，同时确认不会误触发版本同步接口。
- Playwright 已新增 release 分支已存在路径观察：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，在窗口详情冲突面板重新扫描出 `BRANCH_EXISTS`，断言阻断级别、`release/<windowKey>`、建议处理方式和“处理此分支/Resolve Branch”外部处理入口可见，同时确认不会误触发版本同步接口。
- `run-acceptance.sh` 5.5 已补分支名不合规后端/GitLab 强证据：脚本创建本轮窗口并 attach 既有迭代后，GitLab API 直查确认 `feature/<iterationKey>` 和 `release/<windowKey>` 分支存在；临时收紧 BranchRule 并用 `branch-rules/check` 确认两类分支不合规；随后触发冲突扫描并断言 `BRANCH_NONCOMPLIANT` 命中，最后恢复原有 BranchRule。

缺口：

- 当前主线可转向 SA-010/SA-011 更多真实冲突类型后端/GitLab 强证据，以及 CI pipeline、PDF/制品归档等报告扩展。
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

- Maven 多模块、Gradle 真实写回和多仓部分失败已补；版本更新失败重试为 P1/P2。
- 失败原因分类和版本更新重试幂等为 P1。

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
- Run 列表已支持分组筛选，Playwright 在 UI 创建出的分组下生成失败版本更新 Run 后，按 `windowKey` + 分组 + `FAILED` 复核同一条 Run。
- Run 详情页和抽屉已兼容 export JSON 的 `runId`、`repo`、`startAt/endAt` 字段，并默认展开 RunStep 明细。
- 后端冲突检测对 mock/不可抽取版本的仓库不再把版本读取失败升级为阻断异常；mock 仓库版本更新按本地路径执行，能真实落失败 Run。
- Playwright 已补窗口详情冲突证据复核：复用同一个 serial UI 旅程创建出的发布窗口、迭代和仓库，从窗口详情复核 `MERGE_CONFLICT`、`BRANCH_NONCOMPLIANT`、`CROSS_REPO_VERSION_MISMATCH` 类型分布、分支/版本详情、建议处理方式和外部处理语义，并确认不会误触发版本同步接口。
- Playwright 已补 Run 详情部分失败复核：复用同一个 serial UI 旅程创建出的窗口标识，从 Run 列表筛出部分失败 Run，并在 Run 详情页复核成功仓库项、失败仓库项、`MERGE_BLOCKED` 结果、失败任务重试次数和错误信息。
- `run-acceptance.sh` 5.9 已补真实 GitLab 部分失败重试证据：同一 attach Run 内构造一个 `MERGED` 仓库项和一个 `MERGE_BLOCKED` 仓库项，再调用 retry API 验证新 Run 只包含选中的失败项。
- 发布窗口报告导出已补后端 JSON/CSV：按窗口汇总 window 基本信息、Run、RunItem、RunStep、结果分布；详情页提供 CSV 导出入口。

缺口：

- 后续保留更完整的报告格式（如 PDF/制品归档）为 P2。

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
- `run-acceptance.sh` 已补重复关闭幂等断言：首次关闭后再次关闭仍返回成功，状态保持 `CLOSED`。
- 收尾 Run 已能按窗口 `windowKey` 查询，并包含 `ARCHIVE_BRANCH`、`MERGE_TO_MASTER`、`CREATE_TAG`、`TRIGGER_CI` 等步骤证据。
- 前端已在 CLOSED 状态隐藏列表页和详情页的挂载入口；编排面板按真实 `windowKey` 加载最近 Run。
- `run-acceptance.sh` 5.9 已补真实部分失败重试后端/GitLab 强证据：部分成功/部分阻塞 attach Run 可选择失败项重试，成功项不会被重复执行。
- 发布窗口报告导出已补：`GET /api/v1/release-windows/{id}/report.json` 返回窗口级结构化报告，`GET /api/v1/release-windows/{id}/report.csv` 返回可下载 CSV；前端发布窗口详情页可导出 CSV。

缺口：

- CI pipeline 触发和更完整的制品化报告为 P2。

## 五、第一批落地顺序

脚本输出应包含 SA 编号，验收报告引用脚本日志时必须保留这些编号。
三层分组 fixture 固定使用“验收-客户A / 验收-业务线X / 验收-末级分组Y”，所有仓库、迭代和发布窗口均挂载到叶子分组。
SA-013 干净黄金路径必须硬断言 Run `COMPLETED/SUCCESS`、`RunItem > 0`、`RunStep > 0`。
SA-014 版本更新优先绑定干净窗口；干净窗口存在时，版本更新或 GitLab commit 验证失败必须计为失败。
SA-015 前端验收至少覆盖 Run 详情和发布窗口详情两条观察路径。

1. 文档矩阵：本文件作为验收蓝图入口。
2. 脚本编号：`run-acceptance.sh` 输出 SA 编号，便于报告回连。
3. 三层分组：脚本已使用三层验收分组。
4. 干净黄金路径：SA-013 的 Run COMPLETED/SUCCESS、RunItem > 0、RunStep 分布改为硬断言。
5. 版本更新绑定干净窗口：SA-014 不再因主窗口冲突长期 SKIP。
6. 前端观察路径：补 SA-015 的 Playwright 最小旅程。

## 六、Phase 2 缺口池

- GitLab token 过期、无效、权限不足。
- GitLab 不可达。
- 分组删除保护、资源移动、code 自动生成。
- 仓库重复、错误 URL、版本解析失败状态。
- 分支规则作用域、archive 规则、历史不合规分支治理。
- 版本策略分组/仓库作用域继承。
- 版本更新失败重试。
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
| P1 | SA-010 发布计划与解除挂载收口 | attach、同分组挂载约束、真实 release 分支、冲突阻断、解除挂载 release 分支归档已有后端/GitLab 证据；发布计划、挂载弹窗非同分组禁选、解除挂载入口与解除挂载 Slice-1 UI E2E、发布后计划变更锁定、冲突严重级别、建议处理方式以及 `MERGE_CONFLICT`/`CROSS_REPO_VERSION_MISMATCH`/`REPO_AHEAD`/`SYSTEM_AHEAD`/`GIT_PERMISSION_DENIED`/`GIT_UNAVAILABLE` 类型分布和详情已补前端观察；上述六类冲突均已补真实 GitLab 后端强证据；Run 详情失败项重试前端入口已补 | 后续保持回归 |
| P1 | SA-015 复核扩展 | P0 已能由 UI 生成失败 Run，并按窗口、分组和失败状态复核失败步骤；窗口详情冲突证据复核、Run 详情部分失败复核、Run 详情失败项重试入口、真实部分失败重试后端/GitLab 证据和发布报告导出已补 | 后续保持回归 |
| P1 | SA-016 收尾扩展 | P0 已闭环，重复关闭幂等、真实部分失败重试和发布报告导出已补 | 后续转入 CI pipeline 触发等 P2 扩展 |
| P1/P2 | SA-012 更多冲突解决路径 | 版本冲突 `USE_SYSTEM`、feature 缺失、release 分支已存在和分支名不合规均已有对应证据 | 后续仅保留更多冲突类型解决路径扩展 |
| P2 | SA-014 版本更新扩展 | Maven 单模块、多模块、Gradle 真实写回已闭环；批量版本更新前端入口、请求契约和多仓部分失败后端/GitLab 证据已补 | 版本更新失败重试 |

## 八、最新验证记录

### 2026-05-21 SA-009 已挂窗口后迭代仓库集合锁定

命令：

```bash
mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl releasehub-interfaces -am -DskipTests compile
pnpm exec vitest run src/views/iteration/__tests__/AddReposDialog.spec.ts src/views/iteration/__tests__/IterationDetail.spec.ts
pnpm i18n:lint
pnpm run typecheck
git diff --check
```

结果：

- 应用层新增仓库集合编辑守卫：迭代已挂载发布窗口后，`addRepos`、`removeRepos` 和 `update` 的仓库集合/分组变更都会在 Git 操作、版本记录和迭代保存前被 `ITER_002` 拒绝。
- 迭代详情响应新增 `attachedToWindow` / `attachedWindowIds`，详情页据此隐藏添加/移除仓库入口并展示仓库集合锁定状态。
- 应用层 `IterationAppServiceTest` 全量通过：`21 PASS / 0 FAIL / 0 SKIP`；迭代前端专项用例通过：`2 PASS / 0 FAIL / 0 SKIP`。

结论：

- SA-009 “已挂窗口后的修改限制”已具备后端权威写入保护和前端入口约束；后续保留删除保护、移除仓库归档更多真实 GitLab 证据和迭代详情可观察性扩展。

### 2026-05-21 SA-009 同分组仓库选择与写入保护

命令：

```bash
mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/iteration/__tests__/AddReposDialog.spec.ts
pnpm i18n:lint
pnpm run typecheck
git diff --check
```

结果：

- 后端新增 `ITER_005` 业务错误，迭代创建、更新和追加仓库时统一拒绝跨分组仓库。
- 跨分组追加仓库在分支创建、`IterationRepo` 版本记录和迭代保存前失败，避免后续发布窗口同分组约束才暴露脏关联。
- 前端迭代详情添加仓库弹窗按当前迭代 `groupCode` 过滤候选仓库，只展示同分组仓库。
- 应用层 `IterationAppServiceTest` 全量通过：`18 PASS / 0 FAIL / 0 SKIP`；前端新增弹窗用例通过：`1 PASS / 0 FAIL / 0 SKIP`。

结论：

- SA-009 “只能选择同分组已纳管仓库”已有前端候选过滤和后端权威写入保护；已挂窗口后的修改限制已在后续切片补齐，后续保留移除仓库归档更多真实 GitLab 证据、删除保护和迭代详情可观察性。

### 2026-05-21 场景矩阵全量复验

命令：

```bash
bash scripts/acceptance/run-acceptance.sh
```

结果：

- 真实 GitLab 场景验收通过：`PASS=159 / FAIL=0 / SKIP=0`。
- 数据资产快照：`323 groups | 120 repos | 293 windows | 465 iterations | 440 runs`。
- Token 安全：`加密=0 | 明文=0 | Flyway=29`。
- 本轮覆盖 SA-010 detach 归档、SA-011 六类风险强证据、SA-013 干净窗口编排、SA-014 Maven 单模块/多模块/Gradle/批量部分失败、SA-015/SA-016 部分失败重试与关闭窗口收尾。
- DRAFT 残留仍按脚本策略只报告不清理，不阻塞验收。

结论：

- v3.15 场景验收脚本当前稳定通过；SA-014 剩余扩展继续收敛为版本更新失败重试。

### 2026-05-20 SA-014 批量版本更新多仓部分失败

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
git diff --check
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `run-acceptance.sh` 升级到 v3.15，新增 SA-014 8.5 批量版本更新多仓部分失败探针。
- 探针使用独立发布窗口，预置两个真实 GitLab release 分支：R1 使用有效 `pom.xml`，R2 使用缺失的 `missing-pom.xml`。
- 批量版本更新 Run 状态为 `FAILED`，RunItem 同时包含 R1 `VERSION_UPDATE_SUCCESS` 和 R2 `VERSION_UPDATE_FAILED`，失败步骤消息包含缺失 POM 路径。
- 成功项不被失败项回滚：R1 release 分支存在 `ReleaseHub: Update` commit，`pom.xml` 已写回 `1.6.0`。
- 本轮顺手修正 8.3 Maven 多模块探针输入分支：Attach 后在本轮 release 分支准备 root/module fixture，再执行版本更新，避免 fixture 只落在 feature 分支导致断言与被测分支不一致。
- 真实 GitLab 验收通过：`PASS=159 / FAIL=0 / SKIP=0`。

结论：

- SA-014 已具备 Maven 单模块、Maven 多模块、Gradle、批量前端入口和批量多仓部分失败的自动化证据；剩余扩展收敛为版本更新失败重试能力。

### 2026-05-20 SA-014 Maven 多模块 / Gradle 真实写回

命令：

```bash
mvn -pl releasehub-infrastructure -Dtest=MavenVersionUpdaterTest,GradleVersionUpdaterTest test
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `run-acceptance.sh` 升级 SA-014 证据段，新增 8.3 Maven 多模块真实写回和 8.4 Gradle `gradle.properties` 真实写回。
- Maven 多模块探针使用独立窗口/迭代和真实 GitLab release 分支，验证版本更新 Run `SUCCESS`、`ReleaseHub: Update` commit 可查，root `pom.xml` 与 `module-a/module-b/pom.xml` 均写回 `2.3.0`。
- Gradle 探针使用独立窗口/迭代和真实 GitLab release 分支，验证版本更新 Run `SUCCESS`、`ReleaseHub: Update` commit 可查，`gradle.properties` 写回 `3.2.0`。
- 脚本为探针临时设置并复原仓库初始版本，确保版本冲突预检不被 fixture 与系统开发版本差异误触发。
- 真实 GitLab 验收通过：`PASS=154 / FAIL=0 / SKIP=0`。
- Maven/Gradle 更新器单测通过：`12 PASS / 0 FAIL / 0 SKIP`。

结论：

- SA-014 Maven 单模块、多模块和 Gradle 真实写回均已有自动化后端/GitLab 强证据；多仓部分失败已在后续补齐，剩余扩展聚焦版本更新失败重试。

### 2026-05-20 SA-014 批量版本更新前端入口

命令：

```bash
pnpm exec vitest run src/views/release-window/__tests__/VersionUpdateDialog.spec.ts
pnpm run typecheck
pnpm i18n:lint
```

结果：

- 前端 API 新增 `executeBatchVersionUpdate`，接入既有 `/execute/batch-version-update` 后端端点。
- 多仓发布窗口版本更新弹窗新增单仓/已选仓库范围切换；批量模式默认选中当前窗口关联仓库。
- 批量请求按每个仓库 cloneUrl 派生 repoPath，并复用统一 buildTool、targetVersion、POM/Gradle 路径。
- `VersionUpdateDialog.spec.ts` 通过：`2 PASS / 0 FAIL / 0 SKIP`，覆盖当前窗口仓库作用域和批量请求体。
- TypeScript typecheck 和 i18n lint 均通过。

结论：

- SA-014 多仓版本更新已具备前端入口和请求契约；Maven 多模块、Gradle 真实写回和多仓部分失败已在后续验收补齐，版本更新失败重试仍保留为后续扩展。

### 2026-05-20 SA-015 Run 详情失败项重试前端闭环

命令：

```bash
pnpm exec vitest run src/views/run/__tests__/RunDetail.spec.ts
pnpm run typecheck
pnpm i18n:lint
```

结果：

- Run 详情页新增“重试失败项”入口，仅在存在 `FAILED` 或 `MERGE_BLOCKED` RunItem 时显示。
- 前端按 `windowKey::repoId::iterationKey` 只提交失败项，retry 成功后切换到新 Run 详情并刷新详情/任务列表。
- `RunDetail.spec.ts` 通过：`2 PASS / 0 FAIL / 0 SKIP`，覆盖部分失败 Run 只重试失败项、无失败项隐藏入口。
- TypeScript typecheck 和 i18n lint 均通过。

结论：

- SA-015 前端复核从“能看到部分失败证据”补齐到“能在 Run 详情直接发起失败项重试”；真实 GitLab/后端只重试失败项的强证据继续由 `run-acceptance.sh` 5.9 承担。

### 2026-05-20 SA-010 发布后发布计划变更锁定

命令：

```bash
mvn -pl releasehub-application -am -Dtest=AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
```

结果：

- `AttachAppService` 只允许 DRAFT 且未冻结窗口执行 attach/detach；PUBLISHED 和 CLOSED 均通过 `RW_009` 拒绝，冻结窗口继续通过 `RW_006` 拒绝。
- `AttachAppServiceTest` 通过：`9 PASS / 0 FAIL / 0 SKIP`，新增 PUBLISHED attach/detach 拒绝路径。
- 窗口详情页 `canChangeIterations` 收紧为 `status === 'DRAFT' && !frozen`，发布后不再显示挂载和解除挂载入口。
- `ReleaseWindowDetail.spec.ts` 通过：`4 PASS / 0 FAIL / 0 SKIP`，新增发布后隐藏计划变更控件断言。

结论：

- SA-010 “更完整发布计划限制”已补发布后计划锁定：发布计划一旦进入 PUBLISHED，不允许继续变更迭代集合，避免发布编排和 release 分支证据被后续 attach/detach 污染。

### 2026-05-20 SA-010 同分组挂载范围约束

命令：

```bash
mvn -pl releasehub-application -am -Dtest=AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 后端新增 `RW_013`，跨分组迭代在 `AttachAppService.attach` 写入窗口关联、创建 release 分支和保存 Run 前被拒绝。
- `AttachAppServiceTest` 通过：`7 PASS / 0 FAIL / 0 SKIP`。
- 前端挂载弹窗读取发布窗口 `groupCode`，展示迭代分组并禁选非同分组迭代；typecheck 和 i18n lint 均通过。
- 静态扫描通过，报告：`.ai/reports/static-scan/20260520-010558/summary.md`。

结论：

- SA-010 “只能挂载同分组范围内迭代”已有后端强约束和前端挂载入口限制；后续 P1 聚焦部分成功/失败与失败重试前端复核。

### 2026-05-20 SA-003 资源创建叶子分组前端断言

命令：

```bash
pnpm exec playwright test slice-1-group-window.spec.ts --grep "resource creation only allows leaf groups"
pnpm run test:e2e:slice-1
```

结果：

- 快速 RED 反馈暴露新增 helper 使用了不符合 Element Plus 实际渲染的 `.el-tree-select` 选择器，已按既有 helper 收敛为 `.el-tree-select, .el-select`。
- Slice-1 Playwright 通过：`11 PASS / 0 FAIL / 0 SKIP`。
- 新增第 4 个场景覆盖仓库、迭代、发布窗口三个资源创建入口，断言 UI 创建出的三层分组中非叶子节点带 `aria-disabled=true`，用户只能选择叶子分组。

结论：

- SA-003 的“资源创建时只能选择叶子分组”已具备前端用户旅程稳定证据；后端拒绝和真实数据证据继续由 `run-acceptance.sh` 基线承担。

### 2026-05-20 SA-010 解除挂载真实 GitLab 分支归档复核

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
git diff --check
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `run-acceptance.sh` 升级到 v3.13，新增 4.2 解除挂载 GitLab release 分支归档证据段。
- 真实 GitLab 验收通过：`PASS=142 / FAIL=0 / SKIP=0`。
- 新增段创建独立窗口和迭代，验证 attach 后 `release/RW-20260520-B1F5` 存在，detach 后 WindowIteration 为空、原 release 分支删除、`archive/unpublished/release-RW-20260520-B1F5` 存在。

结论：

- SA-010 解除挂载已从前端用户旅程和后端单测扩展到真实 GitLab 外部状态证据；后续 P1 聚焦部分成功/失败与失败重试前端复核。

### 2026-05-20 SA-011 Git 访问异常真实证据与前端旅程

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `run-acceptance.sh` 升级到 v3.12，新增 5.9 Git 访问异常证据段。
- 真实 GitLab 验收通过：`PASS=134 / FAIL=0 / SKIP=0`，新增段检出 `GIT_PERMISSION_DENIED` count=2、`GIT_UNAVAILABLE` count=2，并确认冲突可追溯到权限不足/不可达探针仓库。
- Slice-2 Playwright 通过：`23 passed`，新增前端旅程复核 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE` 类型过滤、阻断级别、建议处理方式、外部 Git 访问处理入口和不触发版本同步。

结论：

- SA-011 Git 访问异常已从代码链路/组件证据推进到真实 GitLab 强证据和前端用户旅程闭环。

### 2026-05-19 SA-011 Git 访问风险类型化

命令：

```bash
mvn -pl releasehub-domain,releasehub-application -Dtest=ConflictReportTest,ConflictDetectionAppServiceTest test
mvn -pl releasehub-infrastructure -am -Dtest=GitLabGitBranchAdapterTest,GitHubGitBranchAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ConflictPanel.spec.ts
pnpm run typecheck
pnpm i18n:lint
```

结果：

- 新增 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE` 冲突类型，权限不足和 Git 不可达不再被吞掉、冒泡为扫描失败或误判为普通 `MERGE_CONFLICT`。
- `MergeabilityResult` 增加失败原因，GitLab/GitHub adapter 可把 401/403 与网络/未知异常区分传回应用层。
- 冲突扫描读取 feature/release 分支状态时做安全分型；发生 Git 访问异常时返回可展示的阻断风险，并停止该仓库后续 mergeability 判断。
- 前端冲突面板新增类型过滤、阻断展示、处理提示和中英文文案。
- 后端领域/应用测试 20 PASS，Git adapter 测试 20 PASS，前端组件测试 1 PASS，typecheck 与 i18n lint 通过。

结论：

- SA-011 Git 访问异常已具备代码链路和组件证据；真实 GitLab 权限/不可达验收与 Playwright 用户旅程复核已在 2026-05-20 补齐。

### 2026-05-19 SA-010 解除挂载 UI E2E 复核

命令：

```bash
pnpm run typecheck
pnpm i18n:lint
pnpm run test:e2e:slice-1
```

结果：

- Slice-1 新增 `detach iteration from window detail via UI`：通过 UI 创建迭代、挂载到发布窗口、进入窗口详情并点击解除挂载。
- 测试断言 `POST /api/v1/release-windows/{id}/detach` 请求成功、关联迭代列表刷新为空、发布计划面板隐藏，且 DRAFT 窗口仍可继续挂载。
- 复用 `createIteration` 和 `attachIterationToWindow` helper，避免解除挂载与关闭窗口用例重复维护同一段 UI 建数流程。
- Slice-1 Playwright 回归通过：11 PASS / 0 FAIL / 0 SKIP。
- `vue-tsc --noEmit` 通过；i18n lint 通过。

结论：

- SA-010 解除挂载已从前端入口/Vitest 证据补强到真实页面用户旅程复核；当前剩余重点转向 GitLab 不可达/权限失败类风险、带仓库解除挂载的真实 GitLab 分支归档复核和发布计划限制。

### 2026-05-19 SA-010 发布窗口详情解除挂载入口补强

命令：

```bash
pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 窗口详情页关联迭代列表新增解除挂载入口；非冻结、非关闭窗口可确认解除挂载。
- 前端复用既有 `POST /api/v1/release-windows/{id}/detach` 能力，解除成功后刷新关联迭代列表。
- 解除挂载入口与关联迭代按钮统一使用 `canChangeIterations` 状态约束，避免前端展示与后端 `AttachAppService` 的冻结/关闭约束漂移。
- 目标 Vitest 通过：`ReleaseWindowDetail.spec.ts` 3 PASS。
- `vue-tsc --noEmit` 通过；i18n lint 通过。
- 静态扫描报告：`.ai/reports/static-scan/20260519-001037/summary.md`，TopN 未发现代码问题。

结论：

- SA-010 解除挂载从后端既有能力补齐到窗口详情前端可观察/可操作入口；后续保留真实 GitLab/UI E2E 复核和发布计划限制。

### 2026-05-18 SA-011 REPO_AHEAD / SYSTEM_AHEAD 前端风险详情观察

命令：

```bash
pnpm run typecheck
pnpm i18n:lint
pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts
```

结果：

- `vue-tsc --noEmit` 通过。
- i18n lint 通过。
- Slice-2 Playwright 回归通过：22 PASS / 0 FAIL / 0 SKIP。
- 新增 SA-011 前端观察路径：窗口详情风险面板可展示 `REPO_AHEAD` / `SYSTEM_AHEAD` 类型分布、版本差异、阻断级别、建议处理方式。
- 版本领先类冲突保留应用内处理语义：点击“同步版本”会进入确认流程，并向 `resolve-conflict` 提交 `resolution=USE_SYSTEM`。

结论：

- SA-011 版本领先类风险已从后端/GitLab 强证据补齐到前端用户可观察和可处理路径。

### 2026-05-18 SA-011 REPO_AHEAD / SYSTEM_AHEAD 后端/GitLab 强证据补强

命令：

```bash
mvn -pl releasehub-application -am -Dtest=ConflictDetectionAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
```

结果：

- 目标单测通过：`ConflictDetectionAppServiceTest` 9 PASS，覆盖 `MISMATCH`、`REPO_AHEAD`、`SYSTEM_AHEAD`、`MERGE_CONFLICT`、`CROSS_REPO_VERSION_MISMATCH`。
- `run-acceptance.sh` 升级到 v3.11，真实 GitLab 验收通过：126 PASS / 0 FAIL / 0 SKIP。
- 新增 SA-011 5.8 证据路径：脚本临时把两仓库初始版本设为 `1.0.0`，创建同一迭代得到系统开发版本 `1.1.0-SNAPSHOT`，随后把两条真实 GitLab feature 分支的 `pom.xml` 分别写为 `1.2.0-SNAPSHOT` 和 `1.0.0-SNAPSHOT`。
- 后端证据：冲突扫描分别检出 `REPO_AHEAD` 和 `SYSTEM_AHEAD`。
- 回归修复：干净黄金路径的版本冲突解决候选已覆盖 `MISMATCH`、`REPO_AHEAD`、`SYSTEM_AHEAD`、`CROSS_REPO_VERSION_MISMATCH`，避免版本细分后阻断 SA-013/SA-014/SA-016。

结论：

- SA-011 版本领先类风险已从枚举/前端展示能力扩展为后端分类实现和真实 GitLab 强证据。

### 2026-05-17 SA-016 发布窗口报告导出补强

命令：

```bash
mvn -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
pnpm run typecheck
pnpm i18n:lint
```

结果：

- 后端新增窗口报告导出 API：`/api/v1/release-windows/{id}/report.json` 和 `/api/v1/release-windows/{id}/report.csv`。
- MockMvc 覆盖窗口报告 JSON 的 `windowId/windowKey/runCount/itemCount/runs[].runId`，并覆盖 CSV header 与窗口/Run 关键内容。
- 前端发布窗口详情页新增 CSV 报告导出入口，Vitest 覆盖按钮打开 `/api/v1/release-windows/{id}/report.csv`。
- `pnpm run test -- src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` 的用例全部通过，但该脚本默认启用全局 coverage，窄范围运行因全局函数覆盖率阈值返回失败；已用 `pnpm exec vitest run ...` 复核目标用例通过。

结论：

- SA-016 发布报告导出从 P2 缺口补强为可用能力；后续仅保留 PDF/制品归档等更完整报告形态。

### 2026-05-17 SA-015/SA-016 真实部分失败重试后端/GitLab 强证据补强

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `bash -n scripts/acceptance/run-acceptance.sh` 通过。
- `run-acceptance.sh` 真实 GitLab 验收通过：112 PASS / 0 FAIL / 0 SKIP。
- `run-acceptance.sh` 升级到 v3.10，新增 5.8 证据段：创建一个真实 GitLab 发布窗口和迭代，分别为两个仓库准备成功合并分支与冲突分支。
- 后端证据：Attach 生成同一个 `ATTACH_ITERATION` Run，包含一个 `MERGED` item 和一个 `MERGE_BLOCKED` item。
- 重试证据：脚本调用 `POST /api/v1/runs/{id}/retry`，只传入 `windowKey::repoId::iterationKey` 失败项 key，并断言新 Run 只有 1 个 item、未重复执行成功仓库项且包含 `TRY_MERGE` step。

结论：

- 真实部分失败重试已从前端/Run 详情观察扩展为后端/GitLab 强证据；发布报告导出已在后续切片补齐。

### 2026-05-17 SA-011 CROSS_REPO_VERSION_MISMATCH 后端/GitLab 强证据补强

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `bash -n scripts/acceptance/run-acceptance.sh` 通过。
- `run-acceptance.sh` 真实 GitLab 验收通过：99 PASS / 0 FAIL / 0 SKIP。
- 新增 SA-011 5.7 证据路径：脚本临时把两仓库初始版本设为 `2.0.0` 与 `3.0.0`，创建同一迭代后生成不同 targetVersion（本轮为 `2.1.0` 与 `3.1.0`），随后复原/同步仓库初始版本。
- GitLab 证据：脚本直查两仓库 `feature/<iterationKey>` 和 `release/<windowKey>` 分支均存在。
- 后端证据：冲突扫描检出 `CROSS_REPO_VERSION_MISMATCH`，并在消息/版本字段中包含两仓目标版本差异。

结论：

- SA-011 `CROSS_REPO_VERSION_MISMATCH` 已从前端详情观察扩展为后端/GitLab 强证据；当前 P1 队列继续保留部分失败重试和更多真实冲突类型扩展。

### 2026-05-17 SA-011 MERGE_CONFLICT 后端/GitLab 强证据补强

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `bash -n scripts/acceptance/run-acceptance.sh` 通过。
- `run-acceptance.sh` 真实 GitLab 验收通过：88 PASS / 0 FAIL / 0 SKIP。
- 新增 SA-011 5.6 证据路径：脚本创建本轮唯一 `feature/acceptance-merge-conflict-*` 和 `release/<windowKey>` 分支，分别向 `pom.xml` 写入不同版本内容制造真实 Git 合并冲突；迭代用 `EXISTING` 模式关联 feature 分支，attach 产生 `MERGE_BLOCKED` Run 证据，冲突扫描检出 `MERGE_CONFLICT`。

结论：

- SA-011 `MERGE_CONFLICT` 已从前端详情观察扩展为后端/GitLab 强证据；当前 P1 队列仍保留更多真实冲突类型和部分失败重试。

### 2026-05-17 SA-012 分支名不合规后端/GitLab 强证据补强

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `bash -n scripts/acceptance/run-acceptance.sh` 通过。
- `run-acceptance.sh` 真实 GitLab 验收通过：77 PASS / 0 FAIL / 0 SKIP。
- 新增 SA-012 5.5 证据路径：脚本创建本轮窗口并 attach 既有迭代后，GitLab API 直查确认 `feature/<iterationKey>` 和 `release/<windowKey>` 分支均存在；临时收紧 BranchRule 后，`branch-rules/check` 确认待检 feature/release 分支不合规；冲突扫描检出 `BRANCH_NONCOMPLIANT`；最后删除临时规则并恢复原有 BranchRule。

结论：

- SA-012 分支名不合规路径已从前端外部处理观察扩展为后端/GitLab 强证据；SA-012 当前保留的扩展点仅为更多冲突类型解决路径。

### 2026-05-17 SA-015 Run 详情部分失败复核补强

命令：

```bash
pnpm --dir frontend run typecheck
pnpm --dir frontend i18n:lint
pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
```

结果：

- `pnpm --dir frontend run typecheck` 通过。
- `pnpm --dir frontend i18n:lint` 通过。
- Playwright Slice-2 serial 旅程通过：11 passed / 0 failed；新增 SA-015 观察路径断言测试人员可从 Run 列表筛出部分失败 Run，并在 Run 详情页复核成功仓库项、失败仓库项、`MERGE_BLOCKED` 结果、失败任务重试次数和错误信息。

结论：

- SA-015 前端复核扩展已覆盖失败 Run、分组筛选、窗口详情冲突证据和 Run 详情部分失败观察；后续真实部分失败生成/重试可并入 SA-010/SA-016 扩展。

### 2026-05-17 SA-015 窗口详情冲突证据复核补强

命令：

```bash
pnpm --dir frontend run typecheck
pnpm --dir frontend i18n:lint
pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
```

结果：

- `pnpm --dir frontend run typecheck` 通过。
- `pnpm --dir frontend i18n:lint` 通过。
- Playwright Slice-2 serial 旅程通过：10 passed / 0 failed；新增 SA-015 观察路径断言测试人员可从窗口详情复核 `MERGE_CONFLICT`、`BRANCH_NONCOMPLIANT`、`CROSS_REPO_VERSION_MISMATCH` 类型分布、分支/版本详情、建议处理方式和外部处理语义，并确认该复核路径不会调用版本同步解决接口。

结论：

- SA-015 已从失败 Run 复核、分组筛选复核扩展到窗口详情冲突证据复核；后续 P1 缺口收敛为部分失败复核旅程。

### 2026-05-15 SA-012 release 分支已存在后端/GitLab 强证据补强

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `bash -n scripts/acceptance/run-acceptance.sh` 通过。
- `run-acceptance.sh` 真实 GitLab 验收通过：68 PASS / 0 FAIL / 0 SKIP。
- 新增 SA-012 5.4 证据路径：脚本创建发布窗口和迭代后，先用 GitLab API 预置本轮唯一 `release/<windowKey>` 分支，GitLab 直查确认 attach 前已存在；attach 后 `branch-status` 同时确认 release 分支和 feature 分支存在；Attach RunStep 返回 `ENSURE_RELEASE/BRANCH_EXISTS` 并包含 release 分支名。

结论：

- SA-012 release 分支已存在路径已从前端外部处理观察扩展为后端/GitLab 强证据；当前 P1 队列转向 SA-010/SA-011 风险详情强证据、SA-015 复核扩展和 SA-016 收尾扩展。

### 2026-05-15 SA-012 feature 缺失后端/GitLab 强证据补强

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
```

结果：

- `bash -n scripts/acceptance/run-acceptance.sh` 通过。
- `run-acceptance.sh` 真实 GitLab 验收通过：60 PASS / 0 FAIL / 0 SKIP；后续补入 release 分支已存在证据后，最新汇总为 68 PASS / 0 FAIL / 0 SKIP。
- 新增 SA-012 5.3 证据路径：脚本创建真实 `feature/<iterationKey>` 后删除本轮唯一分支，GitLab API 直查返回不存在，`branch-status` 返回 `featureBranch.exists=false` 且 `release/<windowKey>` 存在，Orchestrate RunStep 返回 `ENSURE_FEATURE/SKIPPED` 并包含缺失分支名。
- 同步修正 SA-010 release 分支 GitLab 直查：不再硬编码 GitLab project id，改为从仓库 `cloneUrl` 推导项目路径；本轮验证 `GitLab 真实 release 分支: 3/3`。

结论：

- SA-012 feature 缺失已从前端发布计划观察扩展为完整后端/GitLab 强证据；release 分支已存在路径对应的后端/GitLab 强证据已在后续 `run-acceptance.sh` 5.4 补齐。

### 2026-05-15 SA-011 CROSS_REPO_VERSION_MISMATCH 风险详情前端观察补强

命令：

```bash
pnpm --dir frontend run typecheck
pnpm --dir frontend i18n:lint
pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
```

结果：

- `pnpm --dir frontend run typecheck` 通过。
- `pnpm --dir frontend i18n:lint` 通过。
- Playwright Slice-2 serial 旅程通过：9 passed / 0 failed；新增 `CROSS_REPO_VERSION_MISMATCH` 观察路径断言类型分布中跨仓版本冲突可见，筛选后能看到 `1.4.0 ≠ 2.0.0`、阻断级别、建议处理方式，并确认该类冲突不会展示应用内“同步版本”按钮。

结论：

- SA-011 风险详情前端观察已继续扩展到跨仓库版本不一致；后续仍需补更多真实冲突类型后端/GitLab 强证据和部分失败重试。

### 2026-05-15 SA-012 feature 缺失发布计划观察补强

命令：

```bash
pnpm --dir frontend run typecheck
pnpm --dir frontend i18n:lint
pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
```

结果：

- `pnpm --dir frontend run typecheck` 通过。
- `pnpm --dir frontend i18n:lint` 通过。
- Playwright Slice-2 serial 旅程通过：8 passed / 0 failed；新增 feature 缺失发布计划观察路径，断言 UI 创建出的发布窗口详情中 `feature/<iterationKey>` 显示为“不存在/Missing”，同时展示 `release/<windowKey>`、计划顺序、仓库、迭代和待合并状态。

结论：

- SA-012 feature 缺失已具备前端可观察证据；后端/GitLab 强证据已在后续 `run-acceptance.sh` 5.3 补齐。

### 2026-05-15 SA-011 MERGE_CONFLICT 风险详情前端观察补强

命令：

```bash
pnpm --dir frontend run typecheck
pnpm --dir frontend i18n:lint
pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
```

结果：

- `pnpm --dir frontend run typecheck` 通过。
- `pnpm --dir frontend i18n:lint` 通过。
- Playwright Slice-2 serial 旅程通过：7 passed / 0 failed；新增 `MERGE_CONFLICT` 观察路径断言类型分布中 `MERGE_CONFLICT (1)` 与 `MISMATCH (1)` 可见，筛选合并冲突后能看到源/目标分支、阻断级别、建议处理方式和 Git 平台处理入口，并确认不会调用版本同步解决接口。

结论：

- SA-011 风险详情前端观察已从通用严重级别/建议处理方式，扩展到已建模的合并冲突类型分布和处理入口。
- 后续仍需补更多真实冲突类型详情、部分失败重试，以及对应后端/GitLab 强证据。

### 2026-05-15 SA-012 release 分支已存在冲突外部处理路径补强

命令：

```bash
pnpm --dir frontend run typecheck
pnpm --dir frontend i18n:lint
cd frontend && pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
```

结果：

- `pnpm --dir frontend run typecheck` 通过。
- `pnpm --dir frontend i18n:lint` 通过。
- Playwright Slice-2 serial 旅程通过：6 passed / 0 failed；新增 `BRANCH_EXISTS` 观察路径断言冲突类型、阻断级别、`release/<windowKey>`、建议处理方式、外部处理入口可见，并确认不会调用版本同步解决接口。

结论：

- SA-012 非版本冲突前端观察已覆盖分支名不合规和 release 分支已存在两类外部处理路径。
- feature 缺失后端/GitLab 强证据已在后续 `run-acceptance.sh` 5.3 补齐；release 分支已存在路径对应的后端/GitLab 强证据已在后续 `run-acceptance.sh` 5.4 补齐。

### 2026-05-15 SA-015 分组筛选复核补强

命令：

```bash
mvn -pl releasehub-bootstrap -am -Dtest=RunPagedApiTest -Dsurefire.failIfNoSpecifiedTests=false test
cd frontend && pnpm run typecheck
cd frontend && pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
scripts/dev/static-scan-topn.sh 10
```

结果：

- Run 分页接口新增可选 `groupCode` 筛选，按 RunItem `windowKey` 关联发布窗口分组；`RunPagedApiTest` 覆盖同 operator、同失败状态、不同发布窗口分组时只返回目标分组 Run。
- Run 列表新增分组树筛选，复用现有 `GroupTreeSelect`，请求参数透传到 `/api/v1/runs/paged?groupCode=...`。
- Playwright Slice-2 在 UI 创建出的分组下生成失败版本更新 Run 后，按 `windowKey` + 分组 + `FAILED` 筛选，并从抽屉复核 `VERSION_UPDATE_FAILED`、`UPDATE_VERSION`、仓库和缺失 POM 路径。
- 静态扫描复扫报告 `.ai/reports/static-scan/20260515-123640/summary.md`：SpotBugs 0，frontend lint/typecheck 通过。

结论：

- SA-015 的分组筛选复核旅程已补齐；后续 P1 仍保留冲突详情和部分失败复核旅程。

### 2026-05-15 前端 i18n lint 清理

命令：

```bash
cd frontend && pnpm i18n:lint
cd frontend && pnpm run typecheck
```

结果：

- `CalendarView.vue` 中星期、周视图星期名和中文年月标签已改为 `calendar.*` i18n key。
- `RepositoryEdit.vue` 中 Git 配置区标题、Provider 占位文案、Mock 选项和当前 Token 提示已改为 `repository.git.*` i18n key。
- `pnpm i18n:lint` 通过。
- `pnpm run typecheck` 通过。

结论：

- 2026-05-15 早前记录的既有硬编码中文缺口已清理，前端 i18n lint 回到绿色。

### 2026-05-15 SA-012 分支规则冲突外部处理路径补强

命令：

```bash
cd frontend && pnpm run typecheck
cd frontend && pnpm i18n:lint
cd frontend && pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
```

结果：

- `pnpm run typecheck` 通过。
- `pnpm i18n:lint` 未通过，失败项仍为既有硬编码中文：`CalendarView.vue`、`RepositoryEdit.vue`；本轮新增 `conflict.*` key 复用既有文案，未引入新缺失。
- Playwright Slice-2 serial 旅程通过：5 passed / 0 failed；新增 `BRANCH_NONCOMPLIANT` 观察路径断言冲突类型、阻断级别、分支名、建议处理方式、外部处理入口可见，并确认不会调用版本同步解决接口。

结论：

- SA-012 已从单一版本冲突 `USE_SYSTEM` 路径扩展到非版本冲突的外部处理观察路径。
- feature 缺失和 release 分支已存在路径的后端/GitLab 强证据已在后续 `run-acceptance.sh` 5.3/5.4 补齐。

### 2026-05-15 SA-010 发布计划前端观察补强

命令：

```bash
cd frontend && pnpm run typecheck
cd frontend && pnpm i18n:lint
```

结果：

- `pnpm run typecheck` 通过。
- `pnpm i18n:lint` 未通过，`BranchStatusPanel.vue` 已从失败列表消失；剩余失败为既有硬编码中文：`CalendarView.vue`、`RepositoryEdit.vue`。
- `pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"` 通过：4 passed / 0 failed；版本更新弹窗点击已稳定为可见性等待 + DOM click。

结论：

- 窗口详情的发布计划最小观察路径已补齐：测试人员能看到计划顺序、迭代、仓库、feature/release 分支和合并状态。
- Playwright Slice-2 已新增 UI 挂载迭代后发布计划可见性断言，并在真实前后端联调环境通过。

### 2026-05-15 SA-010/SA-011 风险详情前端观察补强

命令：

```bash
cd frontend && pnpm run typecheck
cd frontend && pnpm i18n:lint
cd frontend && pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"
```

结果：

- `pnpm run typecheck` 通过。
- `pnpm i18n:lint` 未通过，失败项为既有硬编码中文：`CalendarView.vue`、`RepositoryEdit.vue`；本轮已清理 `BranchStatusPanel.vue` 硬编码文案，新增 `conflict.*` key 未引入缺失。
- Playwright Slice-2 serial 旅程最终通过：SA-012 冲突面板已断言 `MISMATCH`、阻断级别、建议处理方式和 `Use system version` 可见；SA-014 版本更新弹窗点击已稳定，最终目标回归 4 passed / 0 failed。

结论：

- SA-011 的前端风险详情最小观察路径已补齐：测试人员能在窗口详情冲突面板看到阻断级别和建议处理方式。
- SA-010/SA-011 后续仍保留更多真实冲突类型详情、类型分布复核和部分失败重试。

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

- 冲突详情、部分失败和分组筛选下的复核旅程为 P1。

### 2026-05-15 SA-016 发布后收尾闭环 + 幂等关闭补强

命令：

```bash
bash scripts/acceptance/run-acceptance.sh
scripts/dev/start-local-env.sh hold
cd frontend && pnpm run test:e2e
scripts/dev/start-local-env.sh stop
```

结果：

- 后端/真实 GitLab 场景验收：`PASS=51 / FAIL=0 / SKIP=0`。
- 前端完整 Playwright 回归：`29 PASS / 0 FAIL / 0 SKIP`。

已验证新增能力：

- SA-016 关闭窗口成功，状态变为 `CLOSED`。
- SA-016 重复关闭保持幂等，再次关闭仍返回成功且状态保持 `CLOSED`。
- SA-016 关闭后挂载迭代被 `RW_009` 拒绝。
- SA-016 关闭后版本更新被 `RW_009` 拒绝。
- SA-016 收尾 Run 可按窗口 `windowKey` 查询，并包含归档分支、合并到主分支、创建 Tag、触发 CI 等步骤证据。
- 前端 CLOSED 窗口不再展示挂载入口，窗口详情按 `windowKey` 加载最近 Run。

仍保留缺口：

- 真实部分失败重试已在 2026-05-17 补后端/GitLab 强证据。
- 更完整的失败态收尾复核为 P2；发布报告导出已在 2026-05-17 通过窗口 CSV/JSON 报告端点补齐最小闭环。

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
- SA-003 三层分组 fixture 创建成功：`验收-客户A -> 验收-业务线X -> 验收-末级分组Y`。
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
- Maven 多模块、Gradle 真实写回和多仓部分失败已在后续补齐；版本更新失败重试仍为 Phase 2。

### 2026-05-13 前端 Playwright 基线复验

命令：

```bash
pnpm run test:e2e
```

结果：`23 PASS / 0 FAIL / 3 SKIP`。

已验证前端旅程：

- 登录页渲染、表单校验、错误凭据、记住我切换、成功登录、Enter 提交均通过。
- Slice-1 分组 + 发布窗口旅程通过：三层分组创建、树层级复核、叶子分组创建窗口、冻结/解冻、空窗口发布拒绝、窗口详情查看。
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
