# 2026-11-21 SA-013：分支收敛与唯一编排语义

状态：已交付阶段及其固定 SHA 证据按各节保留；C2-B4 已完成分阶段推送与固定 SHA 5/5，交付收口见 17.5。当前 C2-B5 已完成独立 RED、实现和本地验收，相关 692 项分批汇总、API 48、普通 816 及静态证据见第 18 节；后端 0642828 已提交推送，仍为 HEAD，文档阶段及固定该后端完整 SHA 的 CI 待完成。任务选择以[唯一执行队列](../../docs/execution-roadmap.md)为准，C2 整体未完成。

## 1. 目标与范围

用户授权持续研发、必要重构、按可评审粒度提交和分阶段推送；最新指令先收敛全部分支，再从共同基线拉 feature 开发。

本轮接续 SA-013 S1/S2，先统一配置与执行、不可变计划、仓库队列、MR-first、状态与证据、精确重试，再推进持久恢复。完整蓝图位于 `docs/openspec/changes/add-durable-run-recovery/design.md`，切片追踪在同目录 `tasks.md`。不新建平行计划、第二套 Git Port 或假恢复接口。

## 2. 分支核实及收敛

| 对象 | 核实结果 |
|---|---|
| 仓库 | `https://github.com/tongsh6/release-hub.git` |
| 原研发 HEAD | `5d85b1703f609326aecc14dd21329f9826d3ab99` |
| 收敛提交 | `d4534ccc34fedb9bbf394ced89b4726f43ec197c` |
| 保留历史 | 合并父提交 `5d85b17` 与 `9e07c8a`，无历史重写 |
| 收敛后 main/develop | 本地与远端均精确指向 `d4534cc` |
| 文件树 | `6d7e9993e60cee27c08421e2abe1a06adf6ce81b`，与原研发 HEAD 相同 |
| 旧 feature | `feature/initial-admin-bootstrap` 的提交全部可达后，删除本地和远端分支 |
| 新研发分支 | `feature/orchestration-consistency`，已建立远端同名 upstream |
| 未提交工作 | 暂存保护 76 个 tracked + 42 个 untracked 文件，恢复后逐文件 SHA-256、状态与 index 完全相等 |
| 恢复摘要 | `2c9cc3838a7515aa5bf996bcd200c983489739d65354a68623bb979100899a61` |

先完成 main/develop 收敛，再创建 feature。后续实现提交留在 feature；未经整阶段验收不把研发改动回写 main/develop。临时 stash 在内容和 index 全部核验后已删除。

## 3. 独立目标与最强反例

| 责任 | 正例 | 必须阻止的反例 |
|---|---|---|
| Attach/Detach/Publish | 只保存关系或生命周期；重复 Attach 保留原 attachAt | 配置即建分支、合并、归档；混组批次写入一半 |
| 完整计划受理 | 全部范围校验后，同事务保存 Run 和原计划快照 | 不存在仓库被静默省略；详情请求失败导致范围缩小 |
| 仓库队列 | R1 阻塞只跳过 R1 后续项，R2 正常完成 | 全局 failFast 提前结束，跳过项没有记录 |
| UNKNOWN | 丢响应、本地 journal 完成失败、无 MR 身份的“成功”进入待复核 | 把未知当 FAILED，CONTINUE 或普通 retry 再次写入 |
| 分支所有权 | AUTO/NAMED 按原 ref 确保分支；EXISTING 只验证存在 | 已删除的外部 EXISTING 分支被自动重建 |
| MR 幂等 | 复用唯一精确 opened MR；当前 target 包含 source SHA 可确认已满足 | 历史 merged MR 掩盖 target 被重置；不完整分页当作不存在 |
| 精确重试 | 明确 Item ID，沿用源计划，保留来源与原顺序 | 重新计算仓库×迭代范围；只选 skipped 绕过前序阻塞 |
| 文件 CAS | 使用内容读取时的原 last_commit_id | 内容从 A 派生，发送前换成 B 的 revision，从而覆盖 B |
| 单一状态 | 领域汇总后端、API、列表、导出与 UI 同用 | 仅凭 finishedAt、通用 SUCCESS 或 release 分支存在推断完成 |
| 安全开关 | 默认保护；关闭时 GET/HEAD 可用、真实写入零发送 | disabled 直接放行 HTTP 写操作 |
| 脱敏证据 | 允许列表阻塞原因保留，MR IID 与请求 ID 独立 | 脱敏抹掉审批/流水线/保护分支原因；IID 冒充 X-Request-Id |
| 真实读取 | JPA 读取与映射在短只读事务内，OSIV=false 可用 | HTTP 默认 OSIV 掩盖 LAZY items 读取错误 |
| 会话收口 | HTTP APPLIED 但业务 UNKNOWN 时保留 guard | 正常返回而非抛异常被当作整次业务已确认，释放写锁 |
| 远端稳定身份 | 解析项目后将实际 GitLab 写 URI 固定为 numeric ID | 账本锁 A，但 namespace 被重用后实际请求写 B |

## 4. 已有职责及实际消费者

- `AttachAppService / WindowIterationPort`：纯绑定，删除独立建 release/merge-feature 路由。
- `RunPlanAppService / RunPlanSnapshot`：唯一范围准备、执行准入、不可变快照与精确 retry；`PlanAppService` 和 parallel-scope 复用它。
- `RunAppService / Run / RunItem / RunStep`：唯一同步编排执行与状态汇总；在 Git 前保存计划与执行意图，逐步保存结果。
- `GitMergeWorkflow / GitMergeRequestPort / GitBranchPort`：MR 查找、读取、创建、按 SHA 合并，真实阻塞和未决证据；删除无消费者 close 契约。
- `GitOperationJournalPort / GitWriteHttpInterceptor / GitSafetySessions`：沿用 S2 独立账本及仓库保留，派发前落意图，未知请求不自动重发。
- `GitLabFilePort.FileSnapshot`：内容与原 revision 同源，全消费者迁移；删除旧文件读写重载。
- `EvidenceChainPanel / runPresentation`：已有证据组件接真实 Item metadata；Run 视图统一状态、动作与重试资格。
- 删除 `CodeMergeService/Controller/Dialog`、`DryPlanAppService/Controller/ItemView` 和自动 Publish listener/event，不保留旧写路径。
- 前端重试列表入口转到同一详情选择界面；默认不勾选，缺少 FAIL_FAST 前序阻塞项时明确提示，不自动扩大选择。

## 5. 本轮验证记录

以下是各目标测试在其完成时的结果，可能包含同一用例在不同轮次重跑，不能简单相加为总覆盖数。

| 验证 | 实际证据 |
|---|---|
| 交接基线 | 后端编译、119 项针对性测试、26 项前端组件测试和类型检查通过；未据此认定业务正确 |
| Attach 目标 | 8 项先复现 5 个失败，再 8/8 通过 |
| 计划与重试 | 21 项通过后追加 5 项边界反例；26 项先 5 失败，再全过；GitLab-only 再增加 2 项 |
| Plan/Publish | 非 GitLab 新受理、非 GitLab 精确重试、发布发事件共 3 个 RED；修改后 28+14=42 项通过 |
| Run 状态 | 8 个目标断言 RED→GREEN |
| Run 执行器 | 15 项通过；覆盖仓库 FAIL_FAST、显式 CONTINUE、未知停止、EXISTING、范围验证和成功证据 |
| 既有 Run 用例 | 6 项通过，版本重试保留来源；未宣称 cleanup 已完善 |
| 原 revision | 六类 45 项通过；其中 interceptor 9 项后来扩展组合边界测试 |
| MR/分支/HTTP 组合 | Workflow 30、MR Adapter 26、GitLab Branch 27、GitHub Branch 8、Interceptor 23，共 114 项通过 |
| 安全开关 RED | 6 项中四个写方法确实走旧直通、两项只读正例通过；修复后全过 |
| 前端最终回归 | 37 文件/132 项通过，typecheck、i18n、lint 0 errors/8 既有 warnings、生产构建通过；包含明确选择重试与 A4 分支事实面板 |
| 脚本 | 改动脚本已 bash -n；未运行真实 GitLab 验收脚本 |
| 全后端常规测试 | Surefire 49 类共 491 项：486 PASS、5 既有 SKIP、0 failure/error；跳过属于 GroupAppServiceTest/GroupDeleteTest/GroupGetByCodeTest |
| H2/API + PostgreSQL | 6 类共 53 项全过：RunPagedApiTest 15、WindowRunApiTest 1、RunPlanApiIT 10、RunRecoveryPersistenceEvidenceIT 5、GitWriteSafetyIT 14、GitOperationJournalIT 8；后两类将在保护补充修复后定向重跑 |
| V37 混合数据迁移 | 在 H2 和 PostgreSQL 各从 V36 建库，插入 15 类真实混合状态后迁移 V37，验证与领域聚合一致；修复真实方言及关联聚合差异 |
| A4 分支事实 | 35 个 Plan + 14 个 Window 目标测试通过；删除无依据 mergeStatus，分支存在不推断已合并；详情读取失败明确呈现 |
| C1 初始独立快照 | tree b01495a15ca36c272219ff6a0763a3870b24e8ef clean 编译及 183 目标测试全过，SpotBugs 0；交叉审查仍发现两个组合缺陷，未直接提交 |
| C1 最终精确快照 | tree cf918c6468953aa6ae2bafa76451ff2c87b46aca，59 路径；14 类 194 项全过，含 GitWriteSafetyIT 19 / journal 8 / Pact 6；六模块 SpotBugs 0 |
| C1 真实装配红绿 | 旧 tree 覆新 IT 得到 4 个真实断言失败、0 errors；修复后共享树 27/27 通过，最终独立树再次覆盖。UNKNOWN 保留 NEEDS_REVIEW 与 guard，后继写零发送；BLOCKED 与纯读正例正常 |
| C1 追加单位红绿 | 28 项先 3 个真实断言失败，修复后 28/28：APPLIED 后会话正确收口、普通 body 与带 query 写钉 numeric ID；BLOCKED 释放及无写 UNKNOWN 不造 guard |
| 完整页面旅程 | Playwright 只检查用例发现，本轮尚未执行真实 GitLab/UI 全旅程 |
| 静态扫描 | `.ai/reports/static-scan/20261121-140308/summary.md`：diff/typecheck 通过、SpotBugs 0、lint 0 errors/8 既有 fixture warnings；C1 初扫唯一 NullPointer catch 已改显式判空并复扫为 0；保护修复后精确 C1 六模块再次 0，见 `.ai/reports/static-scan/20261121-c1-cf918c64/summary.md` |
| 路线图 | `bash scripts/dev/check-roadmap.sh` 通过，唯一 HEAD 指向 SA-013 |

环境固定 JDK 21、Maven offline、Node 24.19.0、pnpm 10.33.0。测试仓库及 HTTP fixture 与业务 GitLab 隔离。H2/PostgreSQL/API、组件和 mock HTTP 证据不会称为真实 GitLab E2E。

## 6. 分阶段提交方案

1. Git 安全基础：S2 journal/guard、MR-first 及证据、原 revision 文件契约与全部实际消费者。
2. 唯一编排语义：领域状态、计划、手动配置边界、精确重试、API/导出/界面和目标测试。
3. 文档与验收记录：当前事实、静态扫描及准确提交清单。

基础模块存在真实编译依赖，不能把新 Port 留在一个提交、其消费者留在另一个破坏编译的提交。

| 阶段 | 提交 | 内容 | 推送复核 |
|---|---|---|---|
| C1 | `53eed67f702aad7dbcdeefd5cb3fbec70d370145` | 59 路径，Git MR-first/账本/保护/原 revision，精确验证 tree cf918c64 | 第一阶段已推送，远端 hash 相等 |
| C2 后端 | `a4007f2fe30fb539e6cd11be64cec269237cf1ae` | 47 路径，纯配置、不可变执行计划、状态/重试/API及脚本 | 第二阶段随前端一起推送 |
| C2 前端 | `71a148752538817f288cdf49cd61d5e58a966444` | 37 路径，权威范围、Run/分支事实、证据与精确重试 | 第二阶段远端 feature 精确为 71a1487 |

两阶段推送后 main/develop 仍同指 d4534cc；未 force push，未改写原历史。文档随后独立提交，接续 B2。

## 7. S3-B2：仓库级关闭受理与收尾证据

### 实际根因与取舍

旧收尾按迭代循环处理共享仓库，把 Tag/CI 不明确结果当作继续执行的条件，并用伪 iterationKey 表示仓库动作；关闭与运行意图不在同一短事务。B2 复用 RunPlanAppService、Run/Item、GitBranchPort、既有 MR 工作流与操作账本，不新建 cleanup 聚合或并行执行入口。

- V38 只放开 run_item.iteration_key 的空值；真实仓库级 Item 不伪造迭代，参与迭代保存在不可变计划和 metadata。JPA、API、导出、VersionOps、页面实际消费者均迁移。
- 关闭以窗口行锁原子保存 CLOSED、Run 和计划；同一关闭意图返回原 Run/actor，CI 选择变化返回 409。Attach/Detach/Publish/Freeze/Delete、普通编排与 retry 受理共用该锁；RUNNING/NEEDS_REVIEW 阻断首次关闭，PLANNED 预览不阻断。
- 每仓只执行一次 MERGE_TO_MASTER → CREATE_TAG → TRIGGER_CI。release 必须真实存在，Tag 使用已确认 MR mergeSha；无 SHA 不猜 main 当前 tip。已有 Tag 只有精确名称/SHA 相同才能复用。
- CI 默认 false，显式 true 才触发同一 Tag ref；完整 project/id/ref/SHA/URL 证明已派发，不能据此宣称流水线通过。写结果 UNKNOWN 保留保护并停止同仓后继。
- 分支引用策略为 RETAIN；不归档、不删除、不关闭共享 Iteration。失败或未知收尾不能走普通 feature→release retry。
- 结果保存异常向外传播，不能被 Git catch 变成普通失败再继续；计划 hash 以 itemId 读取来源字段，排除 JPA 行顺序偶然性。
- Mock/InMemory 真实替身提供明确合成的项目/MR/Tag/CI 证据，未降低真实校验；实际 test profile 消费 InMemory。
- Springdoc 从不兼容的 2.6.0 调整为经本地运行验证的 2.8.14，Boot 3.4.1 不变；2.8.17 在当前 Spring 路径解析上启动失败，未采用。通过现有 OpenApiConfig 定向修正实际 nullable 契约；schema.d.ts 从当前运行应用生成，99 paths/171 schemas。运行中的 finishedAt 保持 null。

### 独立正反例与最终验收

各轮 RED/GREEN 用于证明根因，重复测试不能相加为总覆盖数。

| 证据 | 实际结果 |
|---|---|
| 旧收尾行为 | 8 项中 2 个真实断言失败：Tag 失败仍继续、共享 Iteration 被关闭；修复后通过 |
| Tag/CI 真实出口 | 37 项先出现 3 个断言失败；读取/拦截组合 44 项先出现 12 个断言失败，均为 0 errors；精确身份/响应/SHA 修复后通过 |
| 在途准入 | 23 项先出现 4 个断言失败；普通 admit/retry 与 close 共锁后通过 |
| 真实替身 | 14 项中 12 个断言失败，修复后 14/14，加工厂 3/3 |
| 计划读取 | 改变 JPA Item 读取顺序产生 RUN_007；改为按 itemId 读取后稳定通过 |
| 前端空值 | finishedAt 的 null 被转为空串产生真实 RED；保留 null 后通过 |
| 重试夹具 | 同批 attach 时间相同时创建顺序不是计划顺序；用权威 Item 身份修正，严格保持首项 2 次、后项 1 次调用断言 |
| 普通后端最终 | 54 类 613 项：608 PASS、5 既有 SKIP、0 failure/error；日志 .ai/reports/validation/b2-backend-final.log/json |
| 数据库/API/HTTP 最终 | 70/70：RunPlanApiIT 18、RunPagedApiTest 17、WindowRunApiTest 1、GitWriteSafetyIT 21、GitOperationJournalIT 8、RunRecoveryPersistenceEvidenceIT 5；日志 .ai/reports/validation/b2-api-final.log/json |
| 前端最终 | 38 文件/149 项通过；coverage、typecheck、i18n:lint、lint、build 全过；日志 .ai/reports/validation/b2-frontend-final.log |
| E2E 与脚本 | 8 类既有 E2E 及 Fixtures 已迁移并编译，acceptance 脚本 bash -n 通过；本轮未执行真实 GitLab E2E |
| 最终静态扫描 | .ai/reports/static-scan/20261121-153304/summary.md：SpotBugs 0、diff/typecheck 通过、lint 0 errors/8 既有 fixture warnings |

静态 TopN 逐项处理已写入最终报告：CleanupAdmission 返回调用内新建/重建聚合是显式所有权交接，服务不保留引用，因此定向说明 EI_EXPOSE_REP，而不复制聚合或隐藏规则；移除重复及已无必要的 suppressions，复扫为 0。8 个 ESLint 提醒来自既有组件测试夹具的 props/default 声明，保留其测试语义，不改规则，作为现存低风险项记录。初扫及各轮复扫日志保留。

### B2 提交与推送

| 阶段 | 提交 | 精确范围 |
|---|---|---|
| 后端、迁移和脚本 | e127023d18b8d03b4756b2daaadf9e8674638dc1 | 70 路径，tree c7a1d2ed2439cd10d611d94e575faf73f9a83be5 |
| 前端、生成契约与组件验证 | 5ad0666e2dfd40ddf0da69b737f1a9ca8ddc02ac | 22 路径，tree d3c96a91972b97e363145b859c737b29fe35a127 |

两项已正常 fast-forward 推送，远端 feature 精确为 5ad0666；远端只有 main/develop/feature/orchestration-consistency，main/develop 仍同指 d4534cc。逐文件 SHA-256 与暂存范围、提交树一致。提交脚本首次因 NUL 分隔输出末尾空项校验失败而停在 commit 前，核实 70 个暂存文件完整后修正过滤并完成，没有重放提交或丢弃工作。

## 8. B2 完成时的边界与后续切片

以下保留 B2 收尾时的任务边界；S3-C1 的新增实现与验证事实见第 9 节。

- B2 完成后接续的 HEAD 为 S3-C1：复用已有 run_task，唯一 WINDOW_ORCHESTRATION 路径按四动作持久提交；请求 key、MR 分段、任务/Item CAS、guard 事务确认与进程中断安全是同一纵向切片。
- B2 提交时 Run 仍按聚合保存；仅增加 @Version 不能解决已在领域递增版本和多仓覆盖。C1 因此要求定向提交并从数据库事实汇总。
- C1 请求线程消费持久任务，目标为中断后可查询且安全停住；C2 才引入同一执行器的后台 worker、202/轮询、数据库扫描、核对与继续，不保留双轨。
- UNKNOWN 的只读核对不会自动解除仓库保留；普通 retry 不接受 NEEDS_REVIEW。SEND_STARTED 之后不能用租约过期或一次 404 证明允许重发。
- 原子删除分支没有已核实的 provider 前置条件，真实 HTTP 删除继续拒绝。cleanup 的自动恢复属于 S4，B2 不宣称具备。
- 实际 HTTP X-Request-Id 尚未向上采集，字段保持 null，不能用 MR IID 代替。
- 截至 B2 收尾，本轮未执行真实 GitLab、浏览器全旅程或独立进程 kill/restart。后续 S3-C1 已取得的独立进程硬杀证据及其限制单列于第 9.5 节。

## 9. S3-C1：持久四动作与安全中断（专项验证记录）

状态：**C1 已验收并分阶段推送，固定 SHA Full-Link 5/5 通过**。后端/API/真实 PostgreSQL 与本地 HTTP/进程硬杀/前端及静态门禁已通过；编排受理已收窄为 runId 回执，配置隐式 Git 写和 repoConfigs 越界已修复。四组提交至 e5094ba，真实 GitLab CI 44899945002 成功，自动恢复和浏览器完整旅程不在本节完成范围。本节的 S3-C1 是恢复蓝图阶段，不是第 6 节已交付的 Git 安全提交分组 C1。

### 9.1 目标、依赖与非目标

依赖已交付的 S3-A/B1、S3-B2，接续同一份 `docs/openspec/changes/add-durable-run-recovery/design.md` 和 `tasks.md`。用户可观察的目标是：一次窗口编排先持久受理，再逐动作提交；重复请求返回原记录；已提交的 MR 检查点可查询，进程中断或结果未决不会导致盲目重发。

本次由请求线程消费真实持久任务，替换原 WINDOW_ORCHESTRATION 直接循环；cleanup 仍是实际存在的复合 MR 消费者，但复用同一 ensure/try 规则。未新增第二套 Git Port、恢复实体或并行窗口执行入口，已删除经全仓消费者核查后确认无实际入口的 ReleaseBranchService 及其旧自测。

C1 不引入后台 worker、202/自动轮询、lease、代次接管、自动重领、自动核对后续跑或自动重发。C2 才将同一执行器交给有界 worker 与持久扫描；cleanup 自动恢复仍属于 S4。真实 HTTP DELETE 继续阻断，历史 GitLab 验收不作为本轮重跑证据。

### 9.2 已确认契约与实际消费者

| 责任 | 当前实现与边界 |
|---|---|
| 受理幂等 | RunPlanAppService 使用调用方原始 request key、持久 actor 与规范化请求 hash；同 key 同意图返回原 Run，不再 claim；同 key 异动作或异 payload 拒绝。历史无 task 的 Run 只读，不补造任务。 |
| 四动作任务 | 复用已有 run_task，RunPort 提供 createTasks、claimNextTask、completeTask、findTasks；固定 ENSURE_FEATURE → ENSURE_RELEASE → ENSURE_MR → TRY_MERGE。TaskFence 只含 taskId/claimToken；TaskClaim 携带已持久化 actor、repo、action 和计划/检查点 metadata。 |
| 唯一执行路径 | RunAppService 从存储领取任务，在 GitOperationContext.withTask(claim) 内执行窄动作并提交。completeTask 位于 Git 异常转换之外；提交失败向外传播，不领取后继，不回退旧聚合 save。 |
| MR 分段 | GitBranchPort.ensureMergeRequest(url, token, source, target, message) 返回 `GitWriteResult<MrEvidence>`；APPLIED 表示本动作目标已确认，可能是纯读复用，不能直接当作已合并。tryMergeRequest(url, token, checkpoint, message) 只消费持久 checkpoint；复核当前包含关系、IID 与 SHA 后才尝试一次合并，不补造 MR、不自动 close。 |
| 检查点证据 | 复用 MrEvidence 与 GitMergeWorkflow.isValidCheckpoint；保存 numeric project ID、source/target、IID/URL（如有）、冻结 source SHA、disposition 和合并证据。零差异可凭真实包含关系成立，不制造空 MR。历史 merge SHA 须另证实仍被 target 包含，不能拿旧历史 SHA 给 cleanup 打 Tag。 |
| 首次纯读身份 | 现有 BranchStatus 增加 projectId，统一 present(projectId, sha)/missing(projectId)，无旧重载。GitLab 读取先复用 resolver，再访问 numeric URI；只有真实分支 404 才是 missing。首次 ALREADY_EXISTS 或 missing 的项目 ID 同样由结果事务持久化。 |
| 跨任务身份 | withTask 从持久 git.projectId 初始化；bindProjectId 只能绑定同一任务的同一项目。claim/complete 汇集同 Run、同 repo 的既有项目 ID，首个 Item 纯读也能约束后续 Item；retry 继承该事实。namespace 改向时，在远端发送之前拒绝。不同 repo 可绑定不同 ID。 |
| 意图与发送 | GitOperationJournalPort.Command 带 TaskFence；begin/sending/finish 校验持久 task/token、actor、repo、action、计划和检查点。一次 task 仅对应一个写 operation；调用方省略 fence 不能绕过持久任务协议。 |
| 结果原子提交 | 统一 Run → Task → guard 锁顺序。completeTask 定向提交 Step、Item、metadata、绑定，再在 task 仍 RUNNING 时调用 MANDATORY acknowledgeTask；随后 task terminal CAS、从数据库重汇总 Run，处于同一短事务。HTTP 在这些结果事务之外。 |
| guard 收口 | task scope 退出不自动释放。HTTP APPLIED 但业务 UNKNOWN 转 NEEDS_REVIEW 并保留 guard；SENT/未决请求不重发。纯读没有 operation 时不制造 guard；已确认拒绝/阻塞按结果事务收口。其他仓库已变 UNKNOWN 时，本仓合法 task 仍可完成。 |
| 证据不可覆盖 | TRY 的 UNKNOWN/部分响应只更新结果字段，不能覆盖已确认项目、MR IID、source SHA 等身份；不一致的“成功”转 UNKNOWN。实际 X-Request-Id 未取得时保持 null，不能用 IID 冒充。 |

迁移使用 V39，将 task 领取身份、operation/task 关联、唯一 task Step 等约束落到既有持久化边界；没有另建任务平台。旧历史不伪造 task/claim/step 证据。代码责任位于既有 application/run、application/gitoperation、application/port/out、infrastructure/git 与 infrastructure/persistence/run、gitoperation。

### 9.3 独立反例与 RED → GREEN

以下 RED 均记录真实断言失败；编译失败未计为 RED。部分旧行为在独立旧树中复现，避免协同迁移期间的编译缺口掩盖根因。后续 GREEN 与同用例的重复验证不能相加为总覆盖数。

| 反例 | 已取得的 RED | 修复与 GREEN 依据 |
|---|---|---|
| 旧 API 同意图重复执行、请求身份约束不成立 | RunPlanApiIT 6/6 断言失败、0 error/skip；`.ai/reports/validation/c1-api-idempotency-red.log`、`.xml` | 当前 RunPlanApiIT 24/24，归入下表 API/HTTP 联合 88 项；最新 HTTP 方法边界与该类合并的 63 项复验也已通过，重叠结果不重复计数。 |
| 受理后没有持久任务、重叠范围仍被接收 | 独立旧树 RunTaskPersistenceIT 5 项中 4 个断言失败、0 error；`.ai/reports/validation/c1-task-red/tree/backend/releasehub-bootstrap/target/surefire-reports/TEST-io.releasehub.bootstrap.api.RunTaskPersistenceIT.xml` | 真实 PostgreSQL RunTaskPersistenceIT 16/16，归入下表 core 联合 82 项。 |
| 不完整 SHA/URL 被当作合法 MR checkpoint | 36 项中 6 个断言失败、0 error；`.ai/reports/validation/c1-mr-checkpoint-red.xml` | GitMergeWorkflowTest 最终 42/42；完整形状校验由既有 workflow 统一承担。 |
| 历史 merged MR 的 merge SHA 已不在当前 target 中 | 独立旧树 GitMergeHistoryEvidenceTest 3/3 断言失败；`.ai/reports/validation/c1-git-red/tree/backend/releasehub-application/target/surefire-reports/TEST-io.releasehub.application.gitoperation.GitMergeHistoryEvidenceTest.xml` | 同类最终 3/3；只确认 source 包含关系时不暴露未经确认的历史 merge SHA。 |
| task scope 随机生成 session、正常返回或 UNKNOWN 提前释放 guard | GitSafetySessionsTaskTest 5 项中 3 个断言失败、0 error；`.ai/reports/validation/c1-task-scope-red.xml` | 同类最终 5/5；复用 claimToken，释放移交结果事务。 |
| 首次纯读未固定项目、namespace 改向后仍允许写入 | GitTaskIdentityTest 4/4 断言失败、0 error；`.ai/reports/validation/c1-task-identity-red.xml` | 同类最终 4/4；真实 RestTemplate/interceptor + 本地 HTTP fixture 验证 numeric 读取和改向零写。 |
| A 仓 UNKNOWN 后 B 仓结果被 journal 错拦 | 真实 PostgreSQL 1/1 断言失败；`.ai/reports/validation/c1-task-protocol/cross-repository-unknown-red.xml` | journal 允许 Run 为 RUNNING 或 NEEDS_REVIEW，同时严格校验本 task/token；16/16 持久化组包含 B 仓完成与真实 ack。 |
| retry 丢失同仓已确认项目身份 | 3/3 断言失败；`.ai/reports/validation/c1-task-protocol/retry-project-red.xml` | 复用 Run.repositoryProjectId 统一规则，由 Plan 与持久化实际消费；归入 core 联合 GREEN。 |
| 协调器漏存首次纯读 ID、错误 MR 项目覆盖原事实 | RunOrchestrationTest 19 项中 4 个目标断言失败、0 error | 最终 19/19；另覆盖 checkpoint 提交失败后零 TRY、UNKNOWN 保留原 IID/SHA、重复受理零 claim。证据见下表 driver/process-kill 日志。 |

前端另保留 45 项定向用例的真实 RED（11 断言失败、34 通过）→GREEN（45/45）：retry 复用 apiPost 解包原 Run ID；RunDetail 以当前 route 为唯一事实，拒绝旧查询/旧确认/离页响应覆盖新页；无持久计划的 VERSION_UPDATE 不发计划查询；OrchestrationPanel 离页后不继续导航。完整前端回归为 170/170，不与定向结果相加。证据：`.ai/reports/validation/c1-ui-receipt-lifecycle-{red,green}.log`。

### 9.4 当前已运行的专项 GREEN

| 范围 | 结果 | 精确归档 |
|---|---|---|
| Git 契约、scope、身份、HTTP 与真实替身 | 9 类 163/163，0 failure/error/skip：GitMergeWorkflowTest 42、GitMergeHistoryEvidenceTest 3、GitTaskIdentityTest 4、GitLabMergeRequestAdapterTest 26、GitLabGitBranchAdapterTest 37、GitSafetyBeanDecoratorTest 6、GitSafetySessionsTaskTest 5、InMemoryGitBranchEvidenceTest 14、GitWriteHttpInterceptorTest 26 | `.ai/reports/validation/c1-git-green/` 各类 Surefire XML |
| 领域、受理、重试、并发持久化 | 5 类 82/82：RunIdentityTest 3、RunPlanAppServiceTest 38、CleanupAdmissionTest 23、RunRepositoryScopePersistenceTest 2、RunTaskPersistenceIT 16 | `.ai/reports/validation/c1-task-protocol/green/` 各类 Surefire XML |
| 非默认 schema | 既有 3 个方法复验 3/3，非新增 3 个覆盖用例；Hibernate/Flyway schema=c1_task_scope，连接默认 schema=public，定向 SQL 仍落到 c1_task_scope | `.ai/reports/validation/c1-task-protocol/nondefault-schema/maven.log` 及 `TEST-io.releasehub.bootstrap.api.RunTaskPersistenceIT.xml` |
| API、真实 HTTP 与隔离数据库组合 | 6 类 88/88，0 failure/error/skip：RunPlanApiIT 24、RunPagedApiTest 22、WindowRunApiIT 1、GitWriteSafetyIT 28、GitOperationJournalIT 8、RunRecoveryPersistenceEvidenceIT 5 | `.ai/reports/validation/c1-api-http-combined-green.log`、`.json` 与对应 green-xml 归档 |
| 协调器与独立进程硬杀 | RunOrchestrationTest 19/19 + RunTaskProcessKillIT 1/1，0 failure/error/skip | `.ai/reports/validation/c1-driver-process-kill-green.log`、`.json` |
| 普通后端回归 | 最近一次完整回归 57 类 657 项：652 PASS、5 既有 SKIP，0 failure/error；其后范围校验由最终 148 项覆盖 | `.ai/reports/validation/c1-backend-candidate-final.log`、`.json`；初轮 639 项日志保留 |
| 前端组件与工程门禁 | 38 文件 170/170；typecheck、i18n、lint、build 全过；0 errors、8 既有 fixture warnings | `.ai/reports/validation/c1-frontend-final-{tests,typecheck,i18n,lint,build}.log` |
| CI fixture 与 HTTP 方法契约 | 63/63：GitLabE2EFixtureTest 13、RepositorySyncApiTest 4、RunPagedApiTest 22、RunPlanApiIT 24；含错误 POST=405、Allow 真实方法、版本不变及合法 PUT | `.ai/reports/validation/c1-ci-fixture-http-contracts.json`、`.log` 与对应 XML；后续 Settings 前置补丁另验 |
| OpenAPI 真实生成 | 100 paths；必填 Idempotency-Key、TaskView 不暴露 claim token；生成类型逐字节一致 | `.ai/reports/validation/c1-openapi.json`、`c1-openapi-schema.patch` |

非默认 schema 的三个方法分别验证跨仓并发结果不覆盖、retry 继承已成功前缀且不制造额外 MR、UNKNOWN 只停止本仓且不妨碍另一仓完成。Git 组使用离线 Maven，命令为 `mvn -B -o -pl releasehub-infrastructure -am -Dtest=GitMergeWorkflowTest,GitMergeHistoryEvidenceTest,GitLabMergeRequestAdapterTest,GitLabGitBranchAdapterTest,GitWriteHttpInterceptorTest,GitSafetyBeanDecoratorTest,GitSafetySessionsTaskTest,GitTaskIdentityTest,InMemoryGitBranchEvidenceTest -Dsurefire.failIfNoSpecifiedTests=false test`，在 backend 目录执行。

### 9.5 真实进程硬杀：已证明与未证明

RunTaskProcessKillIT 使用独立 JVM、隔离 PostgreSQL 与本地 GitLab HTTP fixture。测试仅在真实数据库 ENSURE_MR 已提交 COMPLETED 后、下一次 claim 之前暂停，随后由父进程强制终止子进程；它没有用抛异常或重建同一内存对象冒充进程重启。

| 观察点 | 本轮实际值 |
|---|---|
| 原 Run | a9e7b049-8383-4bca-a903-18f3fa3238a2 |
| 被终止的 JVM | PID 38279，退出码 137 |
| 重启后的 JVM | PID 38341 |
| Git 写入 | MR POST=1，MERGE PUT=0 |
| 相同意图重放后的 HTTP | 新增请求=0 |
| 重启后的结果 | 返回原 Run；任务保持 COMPLETED、COMPLETED、COMPLETED、PENDING；既有 MR/project/source SHA 证据仍可读，Step 数仍为 3 |

该证据证明：**已提交的 MR 检查点跨进程保留；同一请求重启后返回原记录且不重放远端操作。**

它没有证明 SEND_STARTED 后、响应丢失时或本地 checkpoint 提交前的硬杀恢复；没有执行自动继续、worker 领取或 lease 接管，也没有证明后台最终完成。TRY_MERGE 保持 PENDING 正是本次安全停住的预期。测试使用本地 HTTP fixture，不能称为业务 GitLab 或浏览器全旅程 E2E。

### 9.6 交叉复核、风险与待收口项

只读交叉复核已确认：结果提交在 Git 异常转换之外；TRY UNKNOWN 不覆盖已确认身份；首次纯读项目 ID 在结果事务内保存；其他仓库 UNKNOWN 不会使本仓合法 ack 失效。前端 receipt/路由问题已取得独立反例并修复，API/HTTP/数据库组合 88 项与前端 170 项均已通过；最新 receipt、迭代范围和 CI fixture 已在最终 9 类 148 项组合中复验，源码 hash 未在运行期间变化。

| 事后检查 | 当前判定 |
|---|---|
| 行为 | 已取得四动作提交、重复请求零执行、原身份保留与检查点硬杀专项证据；未把自动恢复计入完成。 |
| 层级 | Run/Plan/Port/JPA/Git 与 API/HTTP/数据库组合已验证；前端 retry envelope、同页路由和离页生命周期已修复并通过 170 项回归、类型与构建门禁。 |
| 测试 | RED/GREEN、普通后端 652 PASS/5 SKIP、最后候选 148 PASS、独立进程硬杀和前端 170 PASS 已归档；固定 e5094ba 的 Full-Link 44899945002 为 5 切片 PASS/0 FAIL。 |
| 架构 | 复用 run_task、现有 Port/metadata/guard；无第二套 WINDOW executor，无租约重发或兼容重载。 |
| 性能 | HTTP 不占结果事务；C1 仍由请求线程逐项消费，不宣称已交付仓库后台并发或性能基准。 |
| 文档 | 台账、路线图、矩阵、设计及 iteration delta 已同步；OpenSpec 2.1.1 strict 通过，C1 真实 CI 收口后标记 DONE，当前队首为 C2-A。 |
| 工作区 | 后端、前端、E2E/脚本、文档四组已推送到 feature，远端 HEAD=e5094ba；main/develop 均保持 d4534cc。验收结果及 C2 切片计划随后单独提交。 |

静态首扫 `.ai/reports/static-scan/20261121-173206/summary.md` 的 OrchestrationAdmission 两项可变聚合暴露已通过 runId 回执消除，无 suppress 或新抽象。复扫 `.ai/reports/static-scan/20261121-175548/summary.md`：六模块 SpotBugs 0，typecheck 通过，lint 0 errors/8 个既有测试替身 warnings。复扫首次发现本记录 EOF 多一个空行，本次已修正并在报告中追加独立 diff-check 复验；原始失败日志保留。TopN 逐项处理及仍保留的 fixture warning 理由写入两轮报告。

中断安全的剩余限制是有意保留的：未决 task/guard 可能持续停留，需要后续 C2 在证据约束下继续处理；不能通过手工改终态、清库、跳过 guard 或降回旧同步路径来“恢复”。V39 后已有真实任务/operation 证据时，回退必须先停止派发并保留数据，不能把删除新证据当作代码回滚。

### 9.7 本阶段代码提交

| 分组 | 提交 | 范围 |
|---|---|---|
| 后端 | `44e742c2f590a7368e122c840e17e02df2558057` | 持久任务、原子结果、冻结身份、幂等回执、配置零 Git、请求范围与真实故障验证 |
| 前端 | `376668606cff57a1500e3e5faa5032f9ee6ba34f` | 持久任务展示、回执保留、精确重试、路由/离页生命周期及配置文案 |
| E2E/脚本 | `4c6b3e9ff7f17c36a4556a629f5eb05b05cc6ac4` | 隔离 GitLab fixture、真实首次编排/合并、正确 HTTP 方法、配置边界消费者；删除无实际消费者的旧脚本 |
| 文档 | `e5094ba7971066749221f1ca4356f716b1514a4c` | 本地验收、证据边界和设计同步；本阶段固定 SHA Full-Link 的真实执行提交 |

提交前 staged diff-check 额外发现新 GitLabE2EFixtureTest 的 EOF 空行，已仅删除该空行并重新检查；没有变更验证后的 Java 语义，也没有绕过钩子或改写历史提交。四组提交已推送，远端 feature 与固定 SHA CI 均核对到 `e5094ba7971066749221f1ca4356f716b1514a4c`；证据为 `.ai/reports/validation/c1-stage-push.json`。

## 10. Full-Link E2E：按实际失败层逐项修复

本节记录 GitHub Actions 的实际执行结果，不把启动成功或测试编译当作业务闭环；该工作流是后端 API + 隔离 GitLab 验收，不是浏览器用户旅程。

| 固定提交 / run | 观察到的失败或进展 | 处置与证据 |
|---|---|---|
| `d4534cc` / [44869040704](https://github.com/tongsh6/release-hub/actions/runs/44869040704) | Dockerfile 固定参数 `gitlab-e2e` 覆盖 Compose 的 `e2e`，JWT 测试配置未加载，后端未启动；尚未执行业务断言 | `08432e9` 删除启动参数中的 profile，Compose 统一选择；未增加等待或引入真实密钥。`.ai/reports/validation/ci-profile-original-e2e-logs.txt`、`ci-compose-profile-preflight.json` |
| `08432e9` / [44885542824](https://github.com/tongsh6/release-hub/actions/runs/44885542824) | 后端约 20 秒就绪，GitLab 健康；init-gitlab.sh 因 test-runner 缺少 python3 在 Java 测试前退出 127 | `a7181fa` 在既有 Alpine test-runner 安装 python3；shell 语法与消费者审计通过，远端下一轮证明初始化已越过。`ci-profile-44885542824-job-136789914349.log` |
| `a7181fa` / [44886918919](https://github.com/tongsh6/release-hub/actions/runs/44886918919) | 已执行 5 个业务切片：Slice1/2/4 PASS；Slice3 的假仓库地址/空 token 导致 branch-status 500；Slice5 对 PUT 端点发 POST 得到 500 | 真实隔离项目 fixture、正确 PUT 与显式 405 已补；未放宽原业务成功断言。`ci-python-44886918919-job-136794077207.log`、`ci-python-44886918919-slice-summary.txt` |
| `e5094ba` / [44899945002](https://github.com/tongsh6/release-hub/actions/runs/44899945002) | 2026-11-21 19:45（Asia/Shanghai）完成 SUCCESS，5 个切片全部 PASS、0 FAIL；后端约 20 秒就绪，首次真实编排/合并、同 key 幂等及 close DONE 通过 | job 136833534514；`.ai/reports/validation/c1-full-link-44899945002-status.json`、`-job-136833534514.log`、`-slice-summary.txt`。该 SHA 包含本阶段三个代码提交和文档提交 |

进一步核对发现两个原验收缺口：Slice3 未显式 Orchestrate，close 即使失败也会有三个 Step，旧断言可能假通过；Slice1 会把 GLOBAL GitLab 设置写成假地址，真实安全 resolver 读取数据库设置而不是 YAML。因此本轮保持生产身份校验，fixture 在核实专用 E2E 用户与独立项目之后显式配置现有 Settings API；Slice3 用实际 plan 准备 feature 差异，由 Orchestrate 首建 release/MR，断言 Run DONE、完整四动作、数值项目/IID/SHA、远端 merged 与同 key 零额外 MR，并要求 close 真实 DONE。

本轮运行已核对固定 SHA，C1 验收出口完成；没有重跑旧 SHA 冒充修复验证。GitHub workflow_dispatch 不接受裸 commit SHA 的调用返回明确 422，未创建运行；改用已核对 branch ref 触发，并再次核对实际 run.head_sha=e5094ba。真实 UI 黄金旅程、Slice4 更强清理断言、Slice5 真实 Git 冲突和自动恢复仍各自保留未完成边界。只读预研中的“201 暂缺 SHA”假设没有在本轮形成实际失败证据，未据此改动 MR 完整性或增加重试。


## 11. 真实 fixture 揭露的迭代配置边界

旧 IterationAppService 在 create/update/addRepos 配置事务内调用 createBranch，并吞掉失败；update/removeRepos 又会直接归档 feature。假地址和空 token 使这些写入失败，不能证明“配置零 Git”。修正真实 fixture 后，本轮独立的零写断言定位到这两条真实旧入口。

当前复用原 initializeRepoMapping 保存模式、分支名和版本映射；AUTO/NAMED 零 Git 读写，EXISTING 保留只读存在验证。update/remove 仍由原持久化适配器删除被移除的关联行，保留其他仓库配置、远端分支与历史 Run。旧建分支/归档私有路径已经删除，feature 建立交给已有 ENSURE_FEATURE。

同时修复 repoConfigs 范围漏洞：repoIds 是权威集合；额外同组或跨组配置、重复配置均在写入前拒绝。update 对完整请求校验后才筛新增仓；未指定配置的新仓使用既有 AUTO 默认，合法既存配置不被错误拒绝，也不被重写。

| 反例与验收 | RED | GREEN / 归档 |
|---|---|---|
| create/add/update 建分支、update/remove 归档，以及 Git 不可用时配置仍偷偷访问远端 | 27 tests 中 8 个断言失败、0 error；映射断言先成立，再因旧 Git 交互失败 | 27/27；`.ai/reports/validation/iteration-config-boundary/{red,green}/` |
| repoConfigs 越界、重复，以及只在既存仓重复也必须拒绝 | 扩展后的 37 tests 中 7 个断言失败、0 error | 37/37；`.ai/reports/validation/iteration-repo-config-scope/{red,green}/`；与上行重叠，不累计 |
| Settings fixture 必须纠正旧 GLOBAL，并拒绝保存后读回仍错误 | 2 tests、2 断言失败、0 error | 全类 15/15；`c1-e2e-settings-{red,green}.xml` |
| 最后候选契约组合 | 无编译错误冒充 RED | 9 类 148/148：Iteration 37 + bootstrap/API/PG/HTTP/fixture/独立进程 111；`.ai/reports/validation/c1-final-contracts.{log,json}` 与对应 XML，backend 源码 hash 在运行前后完全一致 |

普通后端最近一次完整回归为 57 类 657 项（652 PASS、5 既有 SKIP）；其后新增范围校验由上述 37 项及最终 148 项覆盖。证据在 `c1-backend-candidate-final.{log,json}`，记录当次完整 source manifest。前端本轮文案迁移的 3 文件/6 项及 i18n 另验通过，不与此前 170 项相加。

未纳入本切片的输入边界：HTTP `repoConfigs:[null]` 会在旧 DTO 转换器进入服务前解引用；本轮不宣称所有非法 JSON 都已返回统一校验错误。该边界不会被本轮服务层范围/重复测试冒充覆盖。真实 GitLab 五切片与浏览器黄金旅程仍分别按第 10 节和后续阶段验收。

## 12. C2-A / C2-B 的独立切片依据

C1 收口后按授权继续，唯一 HEAD 推进为 C2-A。先核实生产中不存在现成 scheduler/executor；复用 RunPlanAppService 的 receipt-first 受理、RunAppService 四动作、RunPort/RunJpaPersistenceAdapter 的 claim/eligible/complete，以及 GitOperationContext 的持久主体，不增加同义 domain 实体、队列 Port 或运行时 DAG。详细方案已回写现有 design.md/tasks.md。

C2-A 的正例是持久 PENDING 在请求返回、无唤醒或重启后仍可执行；强反例包括线程池拒绝前已 claim、按整个 Run 互斥导致跨仓停滞、最早阻塞页饿死后续 Run、NEEDS_REVIEW 时另一仓仍活动但 UI 停止查询、冻结或空 scope 后丢失旧 receipt。按单任务工作、有界游标发现及原 actor/key/payload 找回解决；新意图继续验证当前范围。扫描只消费当前 WINDOW 协议，不误派发历史、预览、cleanup 或 version。C2-A 对 RUNNING/UNKNOWN 不重领，不能宣称任意中断点恢复。

真实消费者包含两个受理 controller、API 集成测试、RunTaskProcessKillIT、Slice3/Connectivity E2E、RunDetail/OrchestrationPanel、executionIntent、生成 OpenAPI、Playwright 和验收脚本。它们必须一起从“POST 已完成”迁移到“202 已受理、按原 Run/Task 观察”，有界等待不能放宽 DONE、四动作、项目/IID/SHA 和外部次数断言。既有 wait_for_run 仅看 RUNNING，须覆盖 NEEDS_REVIEW 下另仓活动。

C2-B 的独立风险是当前 Journal 的历史 operation 与 Task token 绑定、task_id 唯一，以及 FOUND 证据不总是完整 checkpoint；不能仅旋转 token 或重新造 operation。历史 HTTP 事实、当前发送权和当前业务投影权分别核对，租约或单次 404 不能替代远端 fencing。各项仍未实施或验收，本节不是完成声明。

## 13. C2-A：异步受理、持久扫描与回执观察

状态：本地实现及验收通过，代码停止变更，正在分组交付；本次固定 SHA 的 Full-Link 待运行，尚未将 C2-A 从队首移出。

### 13.1 实际责任与默认边界

- RunPlanAppService 只受理；Orchestrate/Retry controller 返回 202/Run ID。移除无消费者的 OrchestrationAdmission.created，保留 cleanup 仍消费的独立 created。
- RunAppService.executeNextTask 一次领取并提交一个任务；RunPort/JPA 只发现当前 WINDOW protocol 3、RUNNING/NEEDS_REVIEW 且有 PENDING 的候选。最终资格继续由 claimNextTask/eligible 判定。
- 复用既有 AsyncConfig 作为唯一后台装配，移除无消费者的 EnableAsync；源码核查唯一 Scheduled 消费者是新 worker，没有顺带激活旧自动 Git 入口。
- worker 默认启用，parallelism=2、queue-capacity=2、scan-batch-size=32、scan-delay-ms=1000。只有 test profile 默认关闭；e2e 保留启用。提交后才在 worker 线程领取，拒绝或取消排队不改数据库；无 Run 级互斥，游标轮转避免前页长期挡住后续候选。
- 一个命令只消费一个 Task，Git I/O 不占持久化事务。异常不从内存重试、不回退 PENDING；RUNNING/UNKNOWN 的接管尚未开放。关闭不等待挂起的候选查询，查询恢复后也不能派发。
- 页面只有一个待确认意图和最近一个已确认回执；历史复用现有 Run 列表。既有两页共同消费窄只读观察器，不另建请求队列或恢复实体。

### 13.2 正反例与 RED

先定义受理零 Git、单 Task 执行、DB 候选资格、有界池拒绝/取消、跨仓进展、冻结/空范围找回、NEEDS_REVIEW 继续观察，以及检查点后仅 PENDING 继续，再验证旧路径。

| 范围 | 实际 RED | 最终证据 |
|---|---|---|
| 应用受理 | 原路径在请求线程完成四动作、retry 调用 claim；2 个断言失败、0 errors | 应用/计划/cleanup/worker 定向 90/90 |
| API | 新任务不应在请求结束时全完成、OpenAPI 应声明 202；2 个断言失败、0 errors | RunPaged 22 + RunPlan API 25 全过 |
| worker 新组件 | 可编译 scanOnce=0 契约骨架，6 项中 4 个断言失败；不是旧生产缺陷 | 首轮 6/6，补充关闭与挂起查询后 7/7 |
| 页面基础 | 首轮四文件 62 项中 15 个行为断言失败 | 最终四文件 89/89 |
| 真实 Element Plus 表格 | 轮询替换行后选中项丢失，1 个断言失败 | reserve-selection 与按 Run 重建表格后通过 |
| 初次明确拒绝与跨快照可见性 | 4 个断言失败 | 明确拒绝与未知请求分开，RUNNING/已完成 Task 快照仍恢复观察 |
| 确认期间收到原回执 | 4 个断言失败 | 固定原 pending key，确认后重读该 receipt，不新造 key/POST |
| 旧页 400 先于新页同 key 202 | 独立审查发现，2 个 deferred 行为断言失败 | 先检查当前页面代次与主体，再允许清明确拒绝；旧页不得删除新页意图 |
| E2E 原 Run 身份 | 最终消费者审查发现领域 RunId 对象与字符串直接比较，1 个断言失败、0 errors | 改为比较 ID 值，既有 fixture 测试 16/16 |

首次基础设施测试误用未声明 AssertJ 导致编译失败，单列 worker-compile-preflight，不计 RED。首轮集成另暴露 test YAML 拼行和两项测试预期错误：冻结新意图依既有契约为 400/RUN_006，Repository 会翻译限量 IllegalArgumentException；修正配置和断言表达，不放宽行为约束。测试失败日志均保留。

### 13.3 真实集成与范围

| 验证 | 结果及界限 | 工件 |
|---|---|---|
| 普通后端 | 677 项：672 PASS、5 既有 SKIP；补充关闭与 E2E 身份断言之前的整轮 | c2-a-backend-final/ordinary |
| API/数据库 | RunPaged 22、RunPlanApiIT 25、WindowRunApiIT 1、RunTaskPersistenceIT 19，共 67/67；受验源前后 hash 一致 | c2-a-backend-final/integration-corrected |
| HTTP + PostgreSQL | GitWriteSafetyIT 29/29，含真实 worker 双仓进展；使用手动 scanOnce，不冒称定时器证明 | c2-a-integration/attempt-1 |
| 独立进程 | 默认 Spring worker，kill exit=137；重启先扫描再查原 receipt，MR POST=1、MERGE PUT=1、成功前缀 3 项完全不变 | c2-a-integration/attempt-1 |
| 最终 worker | 7/7，含候选查询挂起时 close 可返回、恢复后零派发 | c2-a-backend-final/worker-shutdown |
| E2E fixture 契约 | 16/16，含原 Run ID 值相等；同时复核三脚本 bash -n 和 diff-check 均为 0 | c2-a-e2e-helper/green |
| 运行时 OpenAPI | 冻结 JAR + 独立 PostgreSQL 启动，100 paths；两受理仅声明 202；生成 schema 与仓库文件字节一致 | c2-a-openapi |
| 最终前端 | 39 文件 214/214；typecheck、i18n、lint、build 均通过，lint 仅 8 既有 warnings | c2a-final-*.log |
| 消费者解析 | 3 shell 脚本 bash -n PASS；Playwright 发现 23 项，未运行真实 UI 旅程 | c2a-consumer-syntax.log 及 UI 记录 |

上表存在重复测试，不累计成总数。所有工件相对 `.ai/reports/validation/`。API 的受控测试关闭 worker 并显式单任务驱动，只证明受理/事务契约；WindowRunApiIT 另启用真实默认装配，先收到 202，再观察 Git latch，HTTP 返回与 Git 完成解耦。真实 HTTP 并行反例中 R1 POST 响应仍在途，R2 feature Task 已完成，R1 后继保持 PENDING；解除后原 POST 仍恰一次。

进程验证刻意停在 ENSURE_MR 已提交、TRY_MERGE 尚未领取的边界。重启使用原 numeric project/IID/冻结 SHA 完成合并和 guard 确认；不说明已有 SEND、UNKNOWN 或 RUNNING 能自动接管。

### 13.4 静态处置、交叉审查与下一步

最终报告：`.ai/reports/static-scan/20261121-203554/summary.md`。首扫 203333 命中一个 EI_EXPOSE_REP2；独立审查确认 RunAppService 是不可复制的 Spring 协作者，在 worker 构造器精确说明，不另造接口/包装、不扩大抑制。复扫六模块 SpotBugs=0，typecheck PASS，8 条测试 fixture warnings 逐项记录保留理由。Run delta 的 EOF 空行已定向修复，diff-check 复查为 0。

独立代码审查检查了同仓资格、Run→Task→guard 锁序、局部结果投影、队列拒绝、游标公平、关闭屏障和页面迟到响应；发现的旧页 400 删除同 key 在途回执问题已 RED→GREEN，无剩余已证实提交阻断。

两个 Java E2E、既有 E2ETestFixtures、Playwright 和三个 shell 消费者已迁移至 202 + 原 Run/Task 的有界等待，保留 DONE、顺序、身份和 MR 次数断言。最终消费者审查补上 RunId 值比较回归，RED→GREEN；另将 SA-006 脚本中工具编辑拼在同一行的两个合法 Bash 赋值拆回独立行，并重验三个脚本语法。未增加服务启动等待，也未加入真实业务密钥。下一步按后端、前端、E2E 消费者、文档分组提交并推送，以本次 SHA 运行 Full-Link。C1 5/5、当前本地 HTTP fixture 和组件回归都不能代替本次 CI 或真实 UI 全旅程。

C2-B 的首个候选子片已只读核实：只考虑没有任何 git_operation 的过期 RUNNING，在 Run→Task 同一锁边界内重新确认后才可撤销旧 token。现有 operation 的原 token 仍承担 sending/finish/observe/ack；不得换 token 后抛弃迟到事实或另造 operation。此候选未实施，不提前宣称租约或完整恢复已交付。

### 13.5 分组提交与待验门禁

| 组 | 本地提交 | 内容 |
|---|---|---|
| 后端 | `06aee3b0282c1b56f9497a35296a47ba40da3f84` | 持久 Task 单步消费、有界后台 worker、202 与数据库/进程反例 |
| 前端 | `b37397f5a7785eaded728bd8d2e678d2030aaad6` | 原请求回执与只读观察、迟到响应隔离、运行时 schema |
| E2E 消费者 | `e0c635687337214f9e5247d88347a4f31ebcde55` | 202 与原 Run/Task 有界等待、RunId 值比较回归 |

文档第四组为 `1761572bca6c149aecba78d26a7b25cb98b5251a`。四组已正常快进推送到 `feature/orchestration-consistency`，推送前核对受验源码 hash 未变；远端分支精确一致。`main`/`develop` 仍同指 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，本切片尚未合入。

### 13.6 固定 SHA 远端验收与下一片

[Full-Link 44905843754](https://github.com/tongsh6/release-hub/actions/runs/44905843754) 的 head_sha 为完整 `1761572bca6c149aecba78d26a7b25cb98b5251a`，job 136851120556 成功。2026-11-21 20:59:28（Asia/Shanghai）的结果行明确 Passed: 5 / Failed: 0；后端与 GitLab 健康检查、五个纵向切片均完成，未增加启动等待或真实业务密钥。原始日志和固定 SHA 结果保存在 `.ai/reports/validation/c2-a-full-link/`。

该 CI 是隔离 GitLab 的后端业务链验收，不是浏览器黄金旅程。Slice3 使用本次 202 回执和原 Run/Task 有界等待，并保留 MR-first、重复 key、身份、DONE 断言；Slice4/5 的既有限制未因整体绿灯消失。C2-A 正式 DONE；文档收口后直接进入 C2-B1，无需再请求继续授权。

## 14. S3-C2-B1：无写意图的过期 RUNNING 接管

实施前已独立定义反例，并由两个只读审查交叉核实锁序、时间消费者、已有 operation 归属和真实预写读取路径。设计、Run delta 和任务队列已记录最小范围：只在原 Run→Task 锁内确认当前 task_id 没有任何 operation/Step 后换 token；原 Task 首次时间保留，本次 Claim/Step 同 worker 时钟，claim_expires_at 由拿锁后数据库时间驱动。无心跳首片采用保守 60 分钟默认，配置过短的活性限制明确；隔离测试不等待该生产默认期限。

契约先于实现固定；以下先记录真实 RED，再扩展既有候选查询和唯一领取路径。任何已有 INTENT/SEND/结果操作、UNKNOWN 精确恢复仍属于后续子片，不提前标记完成。

### 14.1 独立反例与 RED 证据

| 范围 | 实际结果 | 工件（相对 .ai/reports/validation/） |
|---|---|---|
| schema | 2 项、2 个 assertion FAIL、0 error；Flyway 与 Hibernate update 都缺新增期限列 | c2-b1/schema-red |
| schema 基础后的持久行为 | 27 项：22 PASS、5 assertion FAIL、0 error；失败为期限未写、末尾 RUNNING 不发现、无法接管、双 worker 与锁等待期限 | c2-b1/behavior-red-jdk21 |
| HTTP/Journal | 5 项：3 PASS、2 assertion FAIL、0 error；扣住真实 GET 响应后原任务不能被接管；SEND 在途/INTENT 先赢锁与全状态排除反例已通过 | c2-b1/http-process-red |
| 独立 JVM | 2 项：PENDING 原场景 PASS，TRY 已领取但无 operation 场景 1 assertion FAIL、0 error；强杀后默认扫描一直留下原 RUNNING | c2-b1/http-process-red |
| 配置边界 | 1 项、1 assertion FAIL、0 error；零期限尚未在构造时拒绝 | c2-b1/config-negatives-red-jdk21 |
| PG 额外边界 | 4 项：3 PASS、1 assertion FAIL、0 error；NEEDS_REVIEW 下另一仓的过期任务尚不能接管 | c2-b1/pg-negative-boundaries-red-jdk21 |

上述验证均保留 backend 源码前后 hash 一致证据；范围有重叠，不相加为总数。schema RED 后只补了 V40 可空列与现有实体映射，再运行领取行为 RED；未用缺列 SQL 错误冒充行为失败。一次验证运行器误选 Java 25，Clock spy 触发 Byte Buddy 版本限制，属于测试环境预检失败，不计 RED；后续固定 Temurin 21 重跑。

HTTP 反例扣住客户端收到的短响应，服务端未被 latch 阻塞。旧写路径要求特定 BusinessException、零 HTTP 写和零 operation；旧纯读路径先要求 MR_REUSED，再检验旧 complete。INTENT 竞争在真实 begin 的物理事务中持有 Run→Task 锁，通过 pg_blocking_pids 确认接管连接确实等待，提交后接管必须拒绝。各 operation 状态/旧 token 的 SQL 投影仅用于验证保守排除，未称为真实核对流程。

进程 RED 中原 PENDING 案例保持 MR POST=1、MERGE PUT=1；已领取案例的重启 JVM 实际报告前三任务 COMPLETED、TRY_MERGE=RUNNING，未出现启动错误。测试只在默认 worker 已完成后用原 key 核对 receipt。独立审查另指出“成功前缀不变”初版只比较 Task 行，GREEN 前已补上 Item ID 集合和前三条 Step 完整行对比，下节记录最终验证；不把这些后补断言计入 RED 证据。

新增期限列的 local 用例只证明现有 JPA/Hibernate update 建列；未宣称完整 local 应用旅程。已核实既有 RunStepJpaEmbeddable 确有 task_id 映射，不因错误的缺列假设新增初始化器或扩展本片范围。下节记录 GREEN、静态整理后的复验与交付边界。

### 14.2 最终实现、冻结回归与静态处置

复用原 RunPort / JPA / RunTaskWorker 和单任务执行器，移除旧候选方法名。V40 与已有 Task 实体只增加可空期限；原 Task/Item/Run 身份、成功前缀、首次 started_at、已有实际顺序与持久 MR 检查点保持。Run→Task 锁取得后读取数据库 clock_timestamp，CAS 同时检查原 token、原期限和当前 task 无任何 operation/Step；只换 token 与期限，不引入第二个恢复执行器。到期本身不否定仍有效的原 owner，也不变更 operation/guard。

独立复审发现一个额外负例：已领取 task 若丢失 Item.executedOrder，原实现在接管时会补造顺序。先运行 `incomplete-order-red-jdk21` 得到 1 个失败用例（4 条事实不变断言失败），再限制接管必须已有正顺序，且只在首次 PENDING 领取时分配顺序；`incomplete-order-green-jdk21` 通过。下面全部后端门禁均在该修正之后执行。

| 验证范围 | 结果 | 工件（相对 .ai/reports/validation/c2-b1/） |
|---|---|---|
| 冻结后端组合 | 76/76：PG 31、真实 HTTP 33、独立 JVM 2、worker 7、scope/config 3，0 FAIL/ERROR/SKIP | final-backend/result.json |
| API 回归 | 48/48：RunPaged 22、RunPlanApiIT 25、WindowRunApiIT 1 | final-backend/result.json |
| 普通后端 | 680 项＝675 PASS＋5 既有 SKIP，0 FAIL/ERROR | final-backend/result.json |
| 静态整理后的最终组合 | 两处 SQL 从 formatted 改为固定片段拼接、构造器精确标注后再次 76/76 | static-fix-green/result.json |
| 最终静态 | 六模块新鲜 SpotBugs XML 均 0；typecheck PASS；lint 0 errors/8 既有 fixture warnings | static-fix-green/static-result.json |

各轮使用 Temurin 21，保留该轮 backend 源码前后 hash，均一致；重叠测试不累计。API/普通后端是在两处 SQL 表达方式与构造器标注整理之前的冻结树运行，整理后的最终树另以覆盖该适配器的完整 76 项组合和静态扫描验证，不伪称所有门禁在同一次运行。B1 没有 UI/API 改动，前端 214 项是 C2-A 的已有基线，本片实际重跑 lint/typecheck。

最终独立 JVM 日志：

- PENDING：原 Run `a4419959-9f2e-46fc-b5d1-514837eba3c8`，PID 45587 强杀退出 137，PID 45959 重启默认扫描完成；MR POST=1、MERGE PUT=1。
- CLAIMED_UNWRITTEN：原 Run `2d19d0f8-0785-4194-873a-961fdded5d00`，PID 46412 强杀退出 137，PID 46781 重启默认扫描接管；MR POST=1、MERGE PUT=1。
- 两场景均在默认 worker 完成后才查询原 receipt，原 Item ID 集合、全部 Task ID、前三个成功 Task 完整行及 Step 完整行保持；只新增最后一步结果。已领取场景原首次时间保持，token 和期限更新。没有用重发受理触发恢复。

静态首扫 `20261121-213959` 的原始日志实际有 4 项，摘要漏列 CT_CONSTRUCTOR_THROW 且把 SpotBugs check 的失败误标为环境问题；未以运行器退出码 0 当作通过。两处 VA_FORMAT_STRING_USES_NEWLINE 已改为编译期固定 SQL 片段组合，参数仍逐项绑定。EI_EXPOSE_REP2 与 CT_CONSTRUCTOR_THROW 仅在现有构造器标注：保存 Spring 事务绑定 EntityManager 代理属于真实职责，不对外暴露；非法期限在任何字段赋值前拒绝，不分配资源或发布 this，类保留事务代理能力。独立只读复审确认这些依据，不新造包装器或全局忽略。复扫最终报告：`.ai/reports/static-scan/20261121-214959/summary.md`，8 条既有测试 fixture 告警逐项记录保留理由。

身份反例复核：首次 GET 只绑定当前动作上下文；动作结果提交才写入 Item metadata，写意图另由既有 operation 固定目标。原进程若仅有未提交读取、无 operation/Step/metadata，重启不能声称保留其未提交 numeric ID。已有同 Run 同仓持久身份则由 Run.repositoryProjectId 汇总并注入每次 Claim，在实际读写前拒绝 namespace 重绑定；当前 task 任何 operation 直接排除本片接管。设计与规范已精确区分这两种情况。

本地门禁通过时，C2-B1 保持 HEAD 等待后端及必要测试、文档分组提交和固定 SHA Full-Link；后续实际结果见 14.3–14.4。默认 60 分钟、无心跳，短期限可造成健康长读反复失权；NULL/不完整领取事实保持停住。任何已有 INTENT/SEND/APPLIED/UNKNOWN 的恢复、完整 local 业务旅程和真实 UI 黄金旅程均不属于本片已验收能力。

### 14.3 分组提交与推送

后端及必要测试提交为 `3f810c887afa3bdbf35e0786822756f3e0b2f711`，共 10 文件。候选方法改名涉及真实 PG/HTTP 测试消费者，代码和这些回归同组，确保中间提交可编译；不人为拆出断裂接口提交。文档提交为 `5441df6bdc971953dee390c9470d4786fe0eab44`，共 10 文件。两组已正常快进推送到 feature/orchestration-consistency；提交和推送前均核对最终受验 backend hash、index、分支与预期父提交，推送后本地/远端 SHA 一致且工作区干净。main/develop 仍同指 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`。详细清单见 `.ai/reports/validation/c2-b1/backend-commit.json`、`grouped-commits.json`、`push-result.json`。

### 14.4 固定 SHA Full-Link 验收完成

[Full-Link 44911634004](https://github.com/tongsh6/release-hub/actions/runs/44911634004) 的 head_sha 精确为 `5441df6bdc971953dee390c9470d4786fe0eab44`，job 136868521085 成功。2026-11-21 22:09:44（Asia/Shanghai）的原始结果行明确 Passed: 5 / Failed: 0，后端和 GitLab 健康检查及五个纵向切片通过；未延长启动等待，未使用真实业务密钥。原始结果和日志在 `.ai/reports/validation/c2-b1/full-link/result.json`、`136868521085.log`，未复用 C2-A 的 CI 结论。

C2-B1 完成本片验收并标记 DONE。该 CI 仍是后端 API 与隔离 GitLab 的业务链；它与本地两类独立 JVM 故障证据分别承责，不代替完整浏览器旅程。Slice4/5 的既有弱覆盖、任何已有写意图/未知结果自动恢复的未交付边界保留。

## 15. S3-C2-B2：建分支成功证明与 APPLIED 结果投影恢复

B1 收口后直接进入这一已授权子片，先固定契约与独立正反例；初始真实 RED 与同一源码树本地 GREEN/静态门禁已完成，当前待分组推送与本片远端验收。目标仅为 ENSURE_FEATURE/ENSURE_RELEASE 的 CREATE_BRANCH：统一正确的创建成功证明，并让后台在 HTTP 已确认成功、Task 结果未提交时补齐原 Task/Step/项目身份和 guard；补投影阶段零网络请求、不旋转历史 HTTP token。复用原事件 JSON、Journal、RunPort/JPA 与 executeNextTask，不新增恢复实体、API/UI 或另一条执行路径。

只读复审推翻了“APPLIED 摘要已足够”的首选假设：interceptor 从 commit/object 的 id/sha 依次写同一个 commitSha，合法但错误的 commit.id=B 可被 object.id=A 或 commit.sha=A 覆盖；当 A 为请求冻结 SHA 时，账本可能 APPLIED，而正常 adapter 仅验证 B 是合法 SHA，业务可错误提交 CREATED。反向例中正确 commit.id=A 被额外字段 B 覆盖，也会错误拒绝成功。上述初始代码路径推导已由真实 HTTP + Task 反例复现，详见 15.1；没有把原实现或旧摘要当作正确性标准。

独立标准依据 [GitLab 创建分支 API](https://docs.gitlab.com/api/branches/#create-repository-branch)：201 Created、匹配名称、合法且精确来自 commit.id 的 SHA；202、字段类型不符、只有 object.id、名称或冻结 SHA 不符均不构成创建成功证明。只收紧 CREATE_BRANCH 的创建确认，不改变 GET 或 Tag 契约。计划中的 plan.defaultBranch/operation.sourceRef 是命名引用，operation.target.ref 才是写前解析并固定的 SHA；补记时核对两层，不重新解析当前分支头。

正反例还覆盖：完整事件/attempt/operation/guard 归属，缺证据不补造；双 worker、旧 owner 与恢复者分别先提交、UNKNOWN 先提交；结果事务故意回滚时 guard 同回滚；独立 JVM 在 APPLIED 后、Task/Step 前强杀，重启补原任务且恢复阶段 HTTP 计数不增加。事务沿 Run→Task→guard，Journal 校验加入现有事务而非 REQUIRES_NEW；外层必须真正经过 Spring 事务代理，不能依赖自调用注解。当前补投影 Step 使用本次处理起止时间，旧 Task 首次时间与 HTTP event 历史时间保留。MR、INTENT 发送权、SEND_STARTED/UNKNOWN 及其他动作继续排队。

### 15.1 初始真实 RED（生产实现前）

证据目录：`.ai/reports/validation/c2-b2/initial-red-jdk21/`。使用 JDK 21 和离线 Maven，两个独立阶段全 backend 源码 hash 前后一致；原始日志、命令、逐文件 hash 和新鲜 Surefire XML 已保留。运行器脚本退出 0 只表示成功采集预期 RED，下表是实际测试结果。

| 范围 | 实际结果 | 失败定位 |
|---|---|---|
| 正常客户端完整类 | 55 项：52 PASS / 3 FAIL / 0 ERROR | HTTP 200/202/206 即使正文完整也被错误当作创建成功 |
| 真实 HTTP → Task 业务链 | 9 项：9 FAIL / 0 ERROR | 错误 commit.id 可被 object.id 或 commit.sha 覆盖，任务错误 COMPLETED/CREATED、operation APPLIED、guard 释放；反向正确字段被干扰成 UNKNOWN；额外覆盖非 201、40/64 位大小写 SHA 与规范字段持久化缺口 |
| PostgreSQL 原任务投影 | 18 项：15 PASS / 3 FAIL / 0 ERROR | 两类分支动作和整体回滚正例都因 executeNextTask=false 无法恢复；14 个错误/缺失证明与资格反例及 UNKNOWN owner 先提交反例均保持拒绝 |
| 独立 JVM 中断重启 | 1 项：1 FAIL / 0 ERROR | 首进程已在 CREATE_BRANCH/APPLIED 后、completeTask 前被强制结束；重启默认 worker 运行后仍停在 ENSURE_FEATURE=RUNNING、后三项 PENDING，未形成投影检查点 |

因此，错误成功已从代码推导升级为真实业务反例，而恢复缺口有原执行器和进程边界证据。成功证明只认可类型严格的 name/commit.id；合法 SHA 先校验 40/64 位十六进制，再以忽略大小写的值比较身份，branch/sourceRef 仍精确匹配。该 RED 检查点之后实施同片 GREEN；后续实际验证见 15.2。

### 15.2 GREEN、独立复审与同一源码树本地门禁

正常 CREATE_BRANCH 客户端、HTTP 账本和数据库恢复复用 `GitLabGitBranchAdapter.createdBranchSha`；事件仅记录整数 httpStatus、文本 name 与 commit.id 的规范来源。冻结 SHA 必须匹配；缺少完整证明的 2xx 响应保持 UNKNOWN，其他字段不能覆盖规范 commit.id。明确拒绝沿用既有 REJECTED 处理。GET/Tag 和其他动作契约未改变。

`RunAppService.executeNextTask` 先消费 `RunPort.projectNextAppliedBranchTask`，补记成功即返回；没有合格投影才继续原 claim 路径。原 Run→Task→guard 结果事务验证完整过期领取、实际顺序、无当前 Step、仓内前序、唯一 operation/session/generation、同 attempt 的 sequence 2 APPLIED 与完整计划/项目/响应证明；Journal 校验显式 MANDATORY。沿原 completeTask 原子提交 Step、Item、绑定、Task/Run 和 guard。原 token、期限、首次 Task 时间、operation/events 不变；Step 使用当前 worker 时间。未新增实体、API/UI、兼容路径或恢复发送入口。

扫描 SQL 与上一基线逐字一致：当前固定四动作中，合法 RUNNING 分支 Task 必有 PENDING 后继，既有扫描足以发现。未找到需要新增候选分支的生产反例，因此不增加猜测性扫描逻辑；未来改变动作图时须重新核对这项依赖。独立源码复审覆盖物理事务、自调用、flush/clear、原 owner 两个胜出顺序及未知结果；唯一发现的空 baseUrl 可规范化为空问题已在本次受验源码中拒绝 null/blank，未改查当前 Settings，也未收紧原 guard 消费者。

| 门禁 | 同一 backend 源码树结果 | 证据目录 |
|---|---|---|
| 针对性组合 | 189/189：应用 25、HTTP 42、PG 54、独立 JVM 3、正常客户端 55、仓库范围 3、worker 7；0 FAIL/ERROR/SKIP | `.ai/reports/validation/c2-b2/initial-green-jdk21/` |
| API | 48/48：RunPagedApiTest、RunPlanApiIT、WindowRunApiIT | `.ai/reports/validation/c2-b2/final-gates/api/` |
| 普通后端 | 700 项：695 PASS、5 既有 SKIP；0 FAIL/ERROR | `.ai/reports/validation/c2-b2/final-gates/ordinary/` |
| 静态 | 六模块新鲜 SpotBugs XML 均 0；typecheck PASS、lint 0 errors/8 既有 fixture warnings，TopN 逐项处置保留 | `.ai/reports/static-scan/20261121-224714/summary.md` |

上述执行全程固定 JDK 21，阶段前后逐文件 hash 一致，API/普通/静态结果还与组合 GREEN 的同一 backend hash 对齐；重叠测试不累计为独立总数。前端没有代码修改，本片只复核 lint/typecheck，不将 C2-A 的 UI 214 项或完整浏览器旅程移作本片验收。

新增 PG 23 cases 包括两类分支投影、错误/缺失/别名/字符串状态/项目/attempt/guard 等拒绝条件、整体回滚、MANDATORY 边界、两个并发发起投影分别返回 true/false、旧 owner 成功或 UNKNOWN 先提交，以及恢复先提交后的迟到结果拒绝。并发用例验证最终唯一性和原事实保持，不声称新增了数据库阻塞等待 trace。

独立 JVM 原始结果：`PROCESS_APPLIED_BRANCH_RESUME run=bec9cda6-3f2c-4f7c-b40a-b828d939f979 killedPid=13969 exit=137 resumedPid=13978 projectionHttp=0 branchPost=1 mrPost=1 mergePut=1 preservedToken=true preservedEvents=3`。第二进程在投影事务返回后、下一 Task 前暂停，直接核对 HTTP 计数未增加、原 operation/events 整行相同、token/expiry/首次时间保持和当前 Step 时间；释放暂停后同一 Run 完成。PENDING 与 CLAIMED_UNWRITTEN 两类既有进程重启也重新通过，各保持前三项成功前缀、MR POST 1、merge PUT 1。

本地门禁完成时 C2-B2 仍为 HEAD；后续阶段推送与本片固定 SHA Full-Link 已完成，见 15.3–15.4。缺少规范证明的旧 APPLIED、MR、INTENT/SEND/UNKNOWN、版本/收尾恢复不在本片交付范围；默认 60 分钟候选期限、无心跳及完整浏览器旅程边界不变。

### 15.3 分组提交与阶段交付

后端已按可评审职责分为两个本地提交：`fe4c0061715d2e9d679a294659ff750ac9b8671a` 修正正常建分支与账本共用的规范成功证明，含客户端及真实 HTTP 反例；`1386ccc358c81b661cfd986bf40e8e10472efde0` 沿原 Task/结果事务补记已确认分支结果，含应用、PG 与独立进程验收。提交后全部 backend 文件 hash 与 15.2 的最终测试、扫描源码仍完全一致；两个测试文件沿用原基线无文件末尾换行的格式，未在门禁后改动源码。

设计、需求、台账、验收矩阵与此记录已作为第三组提交 `cc800362f333418776f4a816e1fa74ee9f08c04f`。三组均已推送 `feature/orchestration-consistency`；远端 main/develop 仍同指 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，无额外分支。

### 15.4 本片固定 SHA 远端验收

固定 `cc800362f333418776f4a816e1fa74ee9f08c04f` 的 [Full-Link 44917254215](https://github.com/tongsh6/release-hub/actions/runs/44917254215) 为 5/5；run head_sha 已与完整提交值核对。job `136885413516` SUCCESS，原始日志 `2026-11-21T15:13:25.8996671Z`（北京时间23:13:25）为 Passed 5 / Failed 0，测试、日志上传及环境清理均成功。证据位于 `.ai/reports/validation/c2-b2/full-link/result.json` 与 `136885413516.log`。本地 189/48/700 与静态扫描的 backend 文件 hash 在三组提交后仍完全相同。

本片行为、事务、故障验证、相关回归、静态 TopN、独立复审、文档与分阶段推送均完成，C2-B2 标记 DONE。本次 Full-Link 是后端 API＋隔离 GitLab，不扩大 Slice4/5 的既有覆盖，也不代替完整浏览器旅程。

## 16. C2-B3 接续：创建 MR 完整证明与原检查点恢复

B2 固定 SHA 验收后，唯一 HEAD 推进 C2-B3；先独立完成正反例、源码职责及官方响应 producer 核对，再执行实现前初轮 RED（16.1）。365/365 中间 GREEN 经交叉复审暴露 SHA 等值表示消费者缺口，新增 6 例独立 RED 后取得 371/371（16.2）；普通门禁 718 项失败历史保留于 16.3。仅修两份旧夹具后，最终定向 403/403、API 48/48、普通 738 项及静态检查已通过，源码复审无阻塞（16.4）；TopN 处置完成，后端 d87dfa8 与文档 544983b 已推送，固定完整 SHA `544983beff74604852c050209e4c8caa93678ef2` 的 Full-Link 44924378741 为 5/5，B3 已 DONE（16.5–16.6）。当前唯一 HEAD 转为 C2-B4，实施前契约见 17。目标与验收继续归属原 design/tasks/run spec，不建立新提案或第二套队列。

定位依据：`GitLabMergeRequestAdapter.create/write/evidence/complete` 与 `GitMergeWorkflow.ensureMergeRequest` 分别承担 provider 响应与业务身份确认；初轮 RED 基线中的 CREATE_MR HTTP 账本仅凭 2xx+iid 即 APPLIED，事件缺项目、分支和 URL。原 operation.target.sha 已在 begin 前冻结；原 MrEvidence、ENSURE_MR 检查点和 TRY 消费者足以承责。独立强反例包括非 201 完整正文、同名 fork、仅 iid 的旧摘要；实现前初轮已通过真实 HTTP→Task 复现错误成功及保护释放，并保留尚未通过的恢复正例，具体证据与范围见 16.1。

同仓 CREATE_MR 必须使用原始项目/source_project/target_project 身份、分支、IID/URL、冻结SHA与opened证明。GitLab 17.11 producer 的三个官方文件已独立读取并保存 hash/range，见 `.ai/reports/validation/c2-b3/baseline/gitlab-17-11-mr-producer.json`；响应字段缺失不能沿用请求参数默认值。readiness 不等于创建结果，TRY 仍负责当前状态；target 已精确包含原SHA时保留 ALREADY_MERGED 语义。

实施复用原结果事务和 metadata patch；投影入口只改名扩到 ENSURE_MR，删除旧名，新 Journal 证明返回既有 MrEvidence。保留原token/期限/历史事实、新Step用当前Clock，null清除语义与正常路径一致，requestId不伪装成operationId。MERGE_MR终态发现、INTENT/SEND/UNKNOWN与其他动作仍后续独立验收。

### 16.1 实现前初轮 RED：正常创建证明、原检查点投影与进程恢复

原始证据为 `.ai/reports/validation/c2-b3/initial-red-jdk21/result.json` 及同目录各阶段 XML。固定 JDK 21；adapter、workflow、business 三阶段的 `sourceStable` 和整轮 `sourceStable` 均为 true，阶段前后源码 hash 保持一致。三个 Maven 阶段实际退出码均为 1；失败是目标断言的 RED，采集脚本完成不表示测试通过。下列 suite 不与 business 阶段汇总重复累计。

| 范围 | 初轮实际结果 | 复现的缺口 |
|---|---|---|
| GitLabMergeRequestAdapterTest | 56 项：37 PASS / 19 FAIL / 0 ERROR / 0 SKIP | 非 201、同名 fork/跨项目、缺失或错误类型字段、与请求检查点矛盾的响应仍可 APPLIED；opened 创建响应还携入不属于创建动作的 mergeSha |
| GitMergeWorkflowTest | 44 项：42 PASS / 2 FAIL / 0 ERROR / 0 SKIP | 创建检查点携入无关 mergeSha，后继错误查询该提交；一个分支还把应为 ALREADY_MERGED 的结果变成 UNKNOWN |
| GitWriteSafetyIT | 28 项：15 PASS / 13 FAIL / 0 ERROR / 0 SKIP | 11 项真实 HTTP→Task 反例实际为 COMPLETED/CREATED、operation APPLIED、Run RUNNING 且 guard 已释放，后继仍 PENDING；另两项分别暴露规范原字段/整数类型未落库和 mergeSha 污染 |
| RunTaskPersistenceIT | 38 项：35 PASS / 3 FAIL / 0 ERROR / 0 SKIP | 原 MR 检查点不能投影；双恢复者均返回 false；预期的投影后回滚路径未进入。其余反例通过不等于恢复能力已交付 |
| RunTaskProcessKillIT | 2 项：1 PASS / 1 FAIL / 0 ERROR / 0 SKIP | 既有分支投影重启场景通过；新增 MR 场景重启后原 ENSURE_MR 仍 RUNNING，TRY_MERGE 仍 PENDING |

进程 RED 的完整 failure 位于 `business/xml/releasehub-bootstrap/TEST-io.releasehub.bootstrap.api.RunTaskProcessKillIT.xml`。第二进程 PID 27308 在北京时间 23:35:50.208 已正常启动，日志为 `Started RunTaskProcessKillIT.Child in 3.393 seconds`；其后触发 `Scheduled worker did not finish original task`。35 秒上限由当时 `RunTaskProcessKillIT.Child` 的 deadline 明确设置。原 Run `2c8762a9-c7b3-4c1b-8d30-072f2fa19cd9` 的两个分支任务已 COMPLETED，ENSURE_MR 保持 RUNNING，TRY_MERGE 保持 PENDING；这是未实现 MR 恢复的业务 RED，不能只凭 result.json 中被截短的“JVM exited before checkpoint”摘要归为启动失败。

附加历史检查点消费者 RED 已单独执行，证据为 `.ai/reports/validation/c2-b3/additional-red-http-jdk21/result.json`：`historical-consumer` 阶段 2 tests / 2 FAIL / 0 ERROR / 0 SKIP，阶段与整轮 sourceStable=true，实际退出码 1。`mrCreationProjectsHistoricalCheckpointBeforeCheckingChangedRemoteState` 的两个参数场景均为 expected true、实际 false；它们覆盖历史冻结 SHA=A 与当前 MR 关闭/source 改为 B 的分离要求，此时仍未取得贯穿 GREEN。该附加运行单独留痕，不与初轮 HTTP 28 项混称同一轮结果。

初轮及附加 RED 只证明实现前缺陷，该轮结束时尚无 GREEN 或本片交付结论；后续实现、中间 GREEN、SHA 消费者再 RED 与本轮 371 项定向证据见 16.2。B2 的 CI 与分支恢复正例不代替 B3 验收，该轮当时 HEAD 保持 C2-B3。

### 16.2 原职责实现、SHA 消费者再 RED 与阶段定向 GREEN

实现沿已有创建响应、操作账本、Task 检查点及结果事务收敛，没有新增实体、表、API 或第二条执行路径。现有 operation.target.sha 仍是 begin 前冻结的意图；原响应和实际消费者分别证明该意图已发生及其后继仍成立。

| 现有职责 | 已实现行为与消费者 |
|---|---|
| GitLabMergeRequestAdapter.confirmedCreatedMr | 正常 create、GitWriteHttpInterceptor 和 Journal 投影共用 HTTP 201 与原始完整正文判定；三个正整数项目 ID、IID、URL、原分支、合法等值 SHA 与 opened 必须同时成立，不从请求填补响应 |
| GitWriteHttpInterceptor / APPLIED event | CREATE_MR 保存规范原字段及整数 httpStatus，保留 JSON 类型；不沿用仅 iid 的扁平摘要，不采纳 merge_commit_sha，失败证明保持 UNKNOWN 和保护 |
| GitOperationPersistenceAdapter.confirmedAppliedCreatedMr | 复用原 Task/operation/attempt、项目及 guard 锁内校验和唯一 sequence 2 APPLIED；返回既有 MrEvidence，disposition=CREATED、mergeSha=null |
| RunJpaPersistenceAdapter.projectNextAppliedTask / RunPort.mrEvidenceUpdates | 删除旧分支专用入口名，沿原完整到期 RUNNING 资格补原 ENSURE_MR；与正常路径共用 metadata null 清除规则，经 completeTask 同一结果事务提交检查点、Step 和 guard 确认 |
| GitMergeWorkflow / RunAppService | 创建事实清除无关 mergeSha；原 TRY 使用同一项目/IID/source SHA 读取当前状态，成功结果再确认同一检查点，不以历史 CREATED 宣称当前可合并 |

原 token、期限、首次时间、实际顺序及三条 HTTP 历史事件保持；补记 Step 使用当前 Clock，requestId 不冒充 operationId。PG 覆盖双恢复者、原 owner 胜出顺序、结果事务整体回滚（含 guard 确认），以及旧摘要、错误证明和 UNKNOWN owner 的拒绝。当前 MR 已关闭或 source 从 A 变 B 时先纯 DB 补历史 A，再由原 TRY 复核；target 不含 A 则保持 UNKNOWN 且无重复写，精确包含 A 则沿原 ALREADY_MERGED 规则。

中间轮 `.ai/reports/validation/c2-b3/initial-green-jdk21/result.json` 为 9 suites、365/365，0 FAIL/ERROR/SKIP、sourceStable=true；四类独立 JVM 均真实 exit=137。该轮 MR Run `004cdadf-b50d-4222-b63b-cac3ddc7f218`：PID 35837 被强杀，PID 35845 恢复，projectionHttp=0、branchPost=0、mrPost=1、mergePut=1，原 token/三条事件保持。这一轮保留为中间证据，不替代下述 SHA 修正后的 371 项定向验收。

交叉复审用最强反例检查同一合法摘要的不同表示：CREATE 接收大写 A，原 IID 的 GET/MERGE 返回等值小写 a。`.ai/reports/validation/c2-b3/sha-consumer-red-jdk21/result.json` 独立复现应用 2 tests / 2 FAIL、HTTP 4 tests / 4 FAIL，均 0 ERROR/0 SKIP、各阶段及整轮 sourceStable=true。应用预期 MERGED、实际 UNKNOWN；贯穿 HTTP 预期 DONE、实际 NEEDS_REVIEW。清单核对显示该 RED 相对 365 中间轮仅改变 RunOrchestrationTest 与 GitWriteSafetyIT，生产实现未变。

修复仅将 GitMergeWorkflow.matchesCheckpoint 和 RunAppService 成功结果确认的 sourceSha 比较改为 equalsIgnoreCase；保留合法摘要校验、原响应表示、检查点、PUT 意图及 GET/MERGE 解析。该 RED 到本轮 371 项 GREEN 的 495 文件清单只在这两个生产文件变化，补充测试未改；不同摘要 B、项目/IID/分支不符仍拒绝。

SHA 修正后 `.ai/reports/validation/c2-b3/final-green-jdk21/result.json` 为退出码 0、9 suites、371/371，0 FAIL/ERROR/SKIP；source-before/source-after 的 495 个 backend 文件 hash 完全一致。中间与本轮不能累计，SHA 新增 6 例已包含在本轮总数内。

| Suite | 中间 GREEN | SHA 修正后 GREEN |
|---|---:|---:|
| GitMergeWorkflowTest | 46 | 46 |
| RunOrchestrationTest | 25 | 27 |
| GitWriteSafetyIT | 74 | 78 |
| RunTaskPersistenceIT | 93 | 93 |
| RunTaskProcessKillIT | 4 | 4 |
| GitLabGitBranchAdapterTest | 55 | 55 |
| GitLabMergeRequestAdapterTest | 58 | 58 |
| RunRepositoryScopePersistenceTest | 3 | 3 |
| RunTaskWorkerTest | 7 | 7 |
| 总计 | 365 | 371 |

本轮独立进程原文位于 `final-green-jdk21/xml/releasehub-bootstrap/TEST-io.releasehub.bootstrap.api.RunTaskProcessKillIT.xml`，四类均真实 exit=137 后由默认 worker 继续：

| 强杀检查点 | killed / resumed PID | 本轮实际结果 |
|---|---|---|
| PENDING 后继 | 37397 / 37446 | MR POST=1、MERGE PUT=1，原 3 项前缀保持、四任务 COMPLETED |
| 已领取但无写意图 | 37496 / 37532 | MR POST=1、MERGE PUT=1，原 3 项前缀保持、四任务 COMPLETED |
| CREATE_BRANCH/APPLIED、业务结果前 | 37598 / 37616 | 投影 HTTP=0、branchPost=1、MR POST=1、MERGE PUT=1；原 token/三条事件保持 |
| CREATE_MR/APPLIED、业务结果前 | 37648 / 37654 | 投影 HTTP=0、branchPost=0、MR POST=1、MERGE PUT=1；原 token/三条事件保持 |

本轮 MR Run 为 `244e55f6-bbc4-4f60-ad3e-b21c9fec5494`。这里的 HTTP=0 仅指纯 DB 投影阶段，后继 TRY 仍按原规则访问原 IID；这些受控 HTTP fixture 和真实 JVM 中断证据不等于真实 GitLab 或完整 UI 黄金旅程。

实现及本轮 371 项定向通过仍有范围边界；后续门禁失败及最终本地复验见 16.3–16.4，当时 HEAD 保持 C2-B3。MERGE_MR 末尾候选发现、INTENT/SEND/UNKNOWN、其他执行类型恢复与完整 UI 仍需独立验收，C2 整体未完成。

### 16.3 普通门禁失败：旧响应夹具（历史轮）

`.ai/reports/validation/c2-b3/final-gates/api/result.json` 确认 API 48/48、0 FAIL/ERROR/SKIP、sourceStable=true。普通门禁未通过：`final-gates/ordinary/membership-diagnostic.json` 为实际 718 tests / 2 FAIL / 1 ERROR / 5 SKIP、sourceStable=true；infra 失败导致 bootstrap 的 ArchitectureRulesTest 与 GitLabE2EFixtureTest 合计 20 项未执行。原汇总脚本先报 suite membership 缺失只是包装症状，不能掩盖已执行测试的真实失败，也不能把缺席 suite 算作通过。

原 XML/log 留存 3 处旧夹具问题：GitSafetyBeanDecoratorTest 两处、GitWriteHttpInterceptorTest 一处，仍使用 HTTP 200 或不完整 CREATE_MR 正文并期待 APPLIED；这与实现前独立定义、真实 RED 后已验证的 201/完整证明契约冲突。修正范围为这两个测试文件，保留原 guard、阻塞与隐私验证目的，生产契约不退回旧行为。该轮结束时修正后的新增定向及普通门禁尚待实测，371/371 仅代表当时局部通过；最终复验见 16.4，不能倒写为该失败轮已通过。

该轮结束时，静态门禁、最终复审、分组提交推送及本片固定 SHA Full-Link 尚未完成全部验收，B3 未出队；之后的修正与本地最终复验见 16.4，原失败证据保留。

### 16.4 旧夹具修正后的最终本地验收

修正后的 `.ai/reports/validation/c2-b3/final-green-v2-jdk21/result.json` 为 11 suites、403/403，退出码 0、0 FAIL/ERROR/SKIP、sourceStable=true。相对 371 轮，495 文件清单仅 GitSafetyBeanDecoratorTest 与 GitWriteHttpInterceptorTest 两个测试文件变化，所有生产文件完全相同；两类原有 guard/阻塞/隐私测试的 6+26 项加入定向选择器，不能把 403−371=32 说成 32 个新增业务场景。365、371 与 403 为先后验证轮，重叠不累计。

| 本地验收范围 | 最终实际结果 | 原始证据 |
|---|---|---|
| 11 类定向组合 | 403 PASS / 0 FAIL / 0 ERROR / 0 SKIP | final-green-v2-jdk21/result.json 与 XML |
| API 三类 | 48 PASS / 0 FAIL / 0 ERROR / 0 SKIP，suiteInventoryMatches=true | final-gates-v2/api/result.json |
| 普通后端 | 738 tests = 733 PASS + 5 既有 SKIP，0 FAIL/ERROR，suiteInventoryMatches=true | final-gates-v2/ordinary/result.json |
| 静态检查 | 六份 fresh SpotBugs XML 各 0；typecheck PASS；lint 0 errors/8 既有 fixture warnings | final-gates-v2/static-result.json、静态报告 20261122-001110 及 raw 日志 |

普通复验已执行此前因 infra 失败而缺席的 ArchitectureRulesTest 4 项与 GitLabE2EFixtureTest 16 项；它们通过不扩大 Full-Link Slice4/5 或完整 UI 旅程的验收范围。定向前后、API 前后、普通前后以及 source-after-static.json 的同一 495 个 backend 文件 hash 均一致，不能只用相近总数推断代码相同。

403 轮四类真实独立 JVM 再次 exit=137 后恢复，原文为 `final-green-v2-jdk21/xml/releasehub-bootstrap/TEST-io.releasehub.bootstrap.api.RunTaskProcessKillIT.xml`：

| 检查点 | killed / resumed PID | 已核实结果 |
|---|---|---|
| PENDING 后继 | 41043 / 41051 | MR POST=1、MERGE PUT=1，原前缀保持、四任务 COMPLETED |
| 已领取但无写意图 | 41059 / 41067 | MR POST=1、MERGE PUT=1，原前缀保持、四任务 COMPLETED |
| CREATE_BRANCH/APPLIED、结果前 | 41090 / 41093 | 投影 HTTP=0、branchPost=1、MR POST=1、MERGE PUT=1，原 token/三条事件保持 |
| CREATE_MR/APPLIED、结果前 | 41103 / 41112 | 投影 HTTP=0、branchPost=0、MR POST=1、MERGE PUT=1，原 token/三条事件保持 |

最新 MR Run 为 `40bf6c0a-0914-4783-85a9-8978b3b9ccc4`；零 HTTP 仅指纯 DB 投影阶段，后继 TRY 仍复核原 IID/source SHA。此前 365/371 的进程证据保留为相应源码轮次的历史，不混用 Run/PID。

静态报告为 `.ai/reports/static-scan/20261122-001110/summary.md`，原始 lint 明确为 0 errors/8 warnings。backend 完整 fenced source review 已逐页核对 8 页且无阻塞；TopN 逐项处置及本地代码提交事实见 16.5，不能把扫描命令退出码当作全部问题已处置。本地验证轮结束时固定 SHA Full-Link 尚待验证，B3 当时保持 HEAD；随后远端交付见 16.6。

本片范围仍仅为 CREATE_MR 完整证明及原 ENSURE_MR 检查点恢复。MERGE_MR 末尾候选发现、INTENT/SEND/UNKNOWN、其他类型恢复和完整 UI 仍待独立验收，C2 整体未完成。

### 16.5 本地后端提交与交付准备

后端完整纵向切片已本地提交：

| 项目 | 核实事实 |
|---|---|
| 提交 | d87dfa87751713b2a8a146bdb8062ef06c9571ce |
| 父提交 | f6ca0682c096547aa0fc178f860f6b5f4b25b9d4 |
| 提交标题 | feat(run): recover confirmed MR creation checkpoints |
| 范围 | 16 个 backend 文件，创建证明、原检查点投影、消费者修正与相关测试同一完整切片 |
| 验证绑定 | 提交源码与最终 403/403、API 48/48、普通 738（733 PASS/5 SKIP）及静态验收为同一 backend 树 |
| 此时交付状态 | 后端仅本地提交；文档提交、分阶段推送与本片固定 SHA Full-Link 待完成，B3 保持 HEAD |

静态 TopN 处置已写回原报告：八项既有 fixture warning 逐项列出保留理由、实际消费者和覆盖边界；告警继续存在，未修改源码或关闭规则。当前 lint raw 与 B2 字节一致，两份前端 fixture 与 B2 固定提交一致；六份 fresh XML 的 BugInstance 与 total_bugs 均为 0。XML suite 属性及 testcase/failure/error/skipped 节点已分别计数，与 403/48/738 一致；五项既有 SKIP 为 GroupAppServiceTest 1、GroupDeleteTest 2、GroupGetByCodeTest 2。

API 清单另经只读核对：实际三类为 RunPagedApiTest、RunPlanApiIT、WindowRunApiIT，与命令选择器精确相等；清单结论依据该独立核对，不能单靠通用 suiteInventoryMatches 字段。原静态结果文件保留生成时的 summary 快照，TopN 人工复核只追加到原报告，没有回写或替换 raw、result.json 与源码快照。

文档同步消除两处旧歧义：C2-A 历史段把 C2-B2 明确为当时队首；Run spec 区分“已有 APPLIED 后业务证据不足”与“CREATE_MR 原始响应证明不完整即 UNKNOWN”。结果事务的 NEEDS_REVIEW/guard 约束保持，不改变实现或扩大恢复范围。

本地验收与后端提交均不代替远端交付验收。此时仍需文档提交、推送和固定 SHA Full-Link；随后完成的交付事实见 16.6，未倒写本地验收时的状态。

### 16.6 B3 分阶段推送与固定 SHA 远端验收

后端 `d87dfa87751713b2a8a146bdb8062ef06c9571ce` 与文档 `544983beff74604852c050209e4c8caa93678ef2` 已推至 feature。`.ai/reports/validation/c2-b3/phase-push.json` 核实远端 feature 从 f6ca0682 前进到完整 SHA `544983beff74604852c050209e4c8caa93678ef2`；main/develop 仍同指 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，推送后工作区干净。该工作区描述属于推送时检查，不覆盖随后 B4 契约文档编辑。

固定 `544983beff74604852c050209e4c8caa93678ef2` 的 [Full-Link 44924378741](https://github.com/tongsh6/release-hub/actions/runs/44924378741) 已 completed/success，job `136907137667` 亦 success，watchExitCode=0。日志汇总为 Passed: 5 / Failed: 0，时间 `2026-11-21T16:34:33.2489840Z`，即北京时间 `2026-11-22 00:34:33`；run 更新完成时间 `2026-11-21T16:34:57Z`。原始结果与逐行日志留于 `.ai/reports/validation/c2-b3/full-link/result.json`、同目录 `136907137667.log`。

本次 CI 绑定 B3 自身完整 SHA，backend 与 403/48/738/静态验收树保持一致；B3 标记 DONE。Full-Link 的后端 API 与隔离 GitLab 切片覆盖不等于完整 UI 黄金旅程，不扩大 Slice4/5 已有较弱边界，也不为 MERGE_MR 或 INTENT/SEND/UNKNOWN 提供恢复验收。365/371 中间轮、718 项失败和补充 RED 均保留，重叠结果不累计。

## 17. C2-B4：合并 MR 完整证明与末尾原任务结果恢复

B3 固定 SHA CI 通过后，当时唯一 HEAD 切换 C2-B4。实施前先完成正反例、官方 producer 与持久化/调度职责审查，再运行独立 RED；实现、最终定向、本地门禁及后端提交过程见 17.1–17.4。随后分阶段推送并用本片固定完整 SHA 完成 Full-Link 5/5，B4 标记 DONE，证据见 17.5；C2 整体未完成。

实施前源码定位：findTaskCandidateRunIds 当时只纳入 PENDING 或无 operation/Step 的到期 RUNNING，末尾 TRY_MERGE 已有 APPLIED 且无 PENDING 时不入候选；projectNextAppliedTask 当时只接受 ENSURE。completeTask 已拥有 Step、metadata、Item、lastMergeAt、guard、Task 与 Run 的原子结果职责，随后用真实 RED 验证上述缺口。

独立强反例先于实现：合法 FF/FF+squash 的 merge_commit_sha=null 不能被非空假设拒绝；原 source SHA 大写 A 与响应等值小写 a 不得触发全量 identity patch 的 stale；坏 APPLIED 证据位于前页时，后页合法末尾候选仍须可发现。恢复只补原历史 MERGED，与后来 target 重置或远端不可读分离，不重新 GET/PUT。

官方基线 `.ai/reports/validation/c2-b4/baseline/gitlab-17-11-merge-producer.json` 保存九文件 URL、范围和 SHA256：GitLab v17.11.3 的合并策略、服务、API/服务用例、实体、API 入口、Gemfile.lock，以及 grape-entity 1.0.1 exposure 和默认 nil 暴露用例。merge_commit_sha 必须存在，可显式 JSON null 或合法文本 SHA；缺失、错类型或别字段补造均拒绝。严格 200/merged、原项目/IID/source SHA、真实非空分支与有效 URL 共用 provider 证明，精确分支仍由 workflow/原 claim 绑定，不加 nullable 参数或 header。

原检查点仅 CREATED/MR_REUSED；ALREADY_MERGED 不会 PUT。投影保留原 identity 字节与 disposition，只复用正常 TRY 的 MR_RESULT 四字段 null patch。原 token/lease/首次时间/顺序/op/events 不变，新 Step 和 lastMergeAt 为当前处理 Clock；完整到期资格、原 PUT/suffix/SHA、attempt/guard/checkpoint、仓内前序及仓库汇总在原结果事务内核验。完整独立契约保留在原 design、tasks 与 Run delta，无新 proposal、实体、表、DTO、API 或恢复框架。

### 17.1 独立 RED 与各轮源码边界

最初 `initial-red-frozen-jdk21` 因新增测试调用参数缺失而在编译阶段退出，实际 0 tests、complete=false；原 result/log 保留，不能计作业务 RED。仅修正测试后，`initial-red-frozen-v2-jdk21` 在 HEAD `77f206c5b1610e4b5d8c814b39b72a5f191970da` 的 backend 基底与当时五份冻结测试组成的隔离快照执行。PG 则在其测试冻结后的当前树独立运行，随后又补强旧字段前置断言；所有这些轮次的生产文件仍为实施前基线。

| 独立阶段 | 实际 tests / FAIL | 原始结果 |
|---|---|---|
| 冻结快照 v2：MR adapter | 105 / 20 | initial-red-frozen-v2-jdk21/adapter/result.json |
| 冻结快照 v2：Workflow + RunOrchestration | 77 / 0 | initial-red-frozen-v2-jdk21/workflow-app/result.json |
| 冻结快照 v2：真实 HTTP | 34 / 28 | initial-red-frozen-v2-jdk21/http/result.json |
| 冻结快照 v2：独立进程 | 3 / 1 | initial-red-frozen-v2-jdk21/process/result.json |
| 独立 PG | 44 / 12 | initial-red-pg-jdk21/pg/result.json |
| 旧字段 DB 前置断言补强 PG | 5 / 5 | stale-fields-red-pg-jdk21/pg/result.json |

以上路径均位于 `.ai/reports/validation/c2-b4/`。正确执行的各阶段全部 0 ERROR/0 SKIP，suite 清单与实际 testcase 数一致，各自源码 hash 前后稳定；不同轮次含不同测试修改，不能称为同一测试树。后补 5 例属于原 44 例的选定用例，结果不累计。

HTTP 28 个失败并非同一种错误，其中 13 例实际错误 DONE/APPLIED 并释放 guard，其余包含规范证明字段/JSON 类型与可空结果等断言失败。进程轮的 MERGE/APPLIED 在重启后仍保留原 TRY 为 RUNNING，其他两个既有 APPLIED 恢复场景通过。PG 12 个失败均指向发现或投影正例未实现；拒绝组没有被误写成实现前已具备恢复能力。后补 5 例先通过 JDBC 确认真实旧 mergeSha、blockReason、requestId 三行存在，再在未实现的发现/投影处失败，不能把“始终缺字段”当作 null 清除验证。

### 17.2 已实现职责与首轮回归失败

正常 merge、HTTP 事件和 Journal 已共用 confirmedMergedMr；事件白名单保留三个 numeric project、IID/URL、source/target、source SHA、merged、整数 httpStatus 及显式可空 merge_commit_sha。原 RunPort 提取 mrCheckpoint 和 mrResultUpdates，正常 TRY 与原投影入口共同消费；恢复只写四个 MR_RESULT 键，不全量重写 ENSURE 身份。

候选 SQL 仅增加完整到期 RUNNING、TRY_MERGE、MERGE_MR/APPLIED 且无当前 Step 的分支，未放宽原 NO_TASK_EVIDENCE 接管路径。原 worker 游标不变；Run→Task→remote/guard 锁内核原 operation、attempt sequence 2、token/session/generation、项目、PUT/suffix/SHA 和计划/检查点，再沿原 completeTask 提交，不产生新 HTTP、operation 或事件。独立只读源码复审未发现阻塞性偏差。

`first-green-jdk21/result.json` 实际为 11 suites、533 tests / 2 FAIL / 0 ERROR / 0 SKIP，sourceStable=true；目录名不代表该轮通过。两个失败及最小修正如下：

| 失败位置 | 根因及修正 |
|---|---|
| GitWriteSafetyIT.committedIntentWinsTheLockAndEveryOperationStatePreventsTakeover | 旧断言要求任何 operation 状态都不能进入候选，混淆发现与授权。本片允许末尾 APPLIED 被发现；仅有 INTENT 事件的账本仍不能投影或重新领取。修正后分别断言候选、锁内拒绝、原 token/guard/operation/events 与零新增 HTTP。 |
| RunTaskPersistenceIT.mrMergeRecoveryKeepsRepositoryOrderWhileAnotherRepositoryProgresses | 加载后的 items 没有集合顺序承诺，原断言把 [3,1] 误判为执行越序。fixture 的 attachAt 已固定先后，计划据此生成 plannedOrder，Task 再生成 taskOrder；修正为按 iterationKey 绑定真实 executedOrder，断言 firstIteration>0 且先于 secondIteration，未排序结果掩盖问题。同组另一处结果断言也改按原 failed/later Item ID 精确归属。 |

首轮与最终轮的 495 文件映射只在 GitWriteSafetyIT、RunTaskPersistenceIT 两个测试文件上不同，生产实现完全一致；首轮失败 XML/log 和原快照保留，没有把测试错误改写为生产缺陷或删除失败记录。

### 17.3 最终定向与五类真实进程中断

`.ai/reports/validation/c2-b4/final-green-jdk21/result.json` 为 11 suites、533/533，exitCode=0、0 FAIL/ERROR/SKIP、sourceStable=true；同目录 XML 的实际节点和 suite 属性已独立核对。分布为 Workflow 48、RunOrchestration 29、真实 HTTP 112、PG 137、独立进程 5、branch adapter 55、MR adapter 105、Decorator 6、interceptor 26、repository scope 3、worker 7；这些是本轮组合范围，不全部等同新增场景。

PG 覆盖终态候选、CREATED/MR_REUSED、40/64 位等值大小写、合法 null 清旧值、错误证明和领取资格、原 owner 正常/UNKNOWN 胜出、双恢复者、仓内迭代与跨仓汇总。事务验证为外层 TransactionTemplate.setRollbackOnly() 触发原结果事务整体回滚，含绑定与 guard；没有声称单独注入 ack 故障。双恢复者通过同步起跑和最终一条 Step 验证结果竞争，没有声称取得确定的 PostgreSQL 锁等待轨迹。坏页验证为真实 SQL keyset 分页与拒绝投影，结合原 worker 游标用例；它不等于整个坏页场景另做了一次进程强杀。

最终进程原文位于 `final-green-jdk21/xml/releasehub-bootstrap/TEST-io.releasehub.bootstrap.api.RunTaskProcessKillIT.xml`：

| 中断点 | killed / resumed PID | 最终核实结果 |
|---|---|---|
| PENDING 后继 | 89290 / 89299 | exit=137；原前缀保持，MR POST=1、MERGE PUT=1，四任务完成 |
| 已领取但无写意图 | 89309 / 89319 | exit=137；原前缀保持，MR POST=1、MERGE PUT=1，四任务完成 |
| CREATE_BRANCH/APPLIED、结果前 | 89327 / 89334 | exit=137；投影 HTTP=0、branch POST=1，原 token/三条事件保持 |
| CREATE_MR/APPLIED、结果前 | 89348 / 89352 | exit=137；投影 HTTP=0、MR POST=1，原 token/三条事件保持 |
| 末尾 MERGE_MR/APPLIED、结果前 | 89472 / 89518 | exit=137；noPendingAtKill=true，投影 HTTP=0，branch POST=0、MR POST=1、MERGE PUT=1，原 token/六条事件保持 |

最后一行的原 Run 为 `fb7e11f7-9256-408b-b530-e0cd657a2f3a`、原 Task 为 `44d8b658-fbf6-44a1-aee5-192c3668bbb3`；强杀时 2 条 operation、6 条 event、无 PENDING，默认 worker 重启发现原末尾候选并补原 TRY/MERGED，沿原事务结束 Run。零 HTTP 限于恢复投影阶段；MR 404、target 重置由受控 HTTP fixture 模拟，不能称为真实 GitLab 删除或重置演练。

### 17.4 同一树本地门禁、TopN 与后端提交

| 验收范围 | 实际结果 | 原始证据 |
|---|---|---|
| API | 3 suites、48 PASS，0 FAIL/ERROR/SKIP | final-gates/api/result.json 与 XML |
| 普通后端 | 58 suites、789 tests = 784 PASS + 5 既有 SKIP，0 FAIL/ERROR | final-gates/ordinary/result.json 与 XML |
| 静态检查 | 六份 fresh SpotBugs XML 的 BugInstance/total_bugs 均 0；typecheck PASS；lint 0 errors/8 既有 fixture warnings | final-gates/static-result.json、static-scan.log、静态报告 20261122-010845 |

上述验证结果路径均以 `.ai/reports/validation/c2-b4/` 为根。API 实际三类为 RunPagedApiTest、RunPlanApiIT、WindowRunApiIT，选择器与清单一致；五项既有 SKIP 仍为 GroupAppServiceTest 1、GroupDeleteTest 2、GroupGetByCodeTest 2。定向、API、普通测试存在重叠，不相加为独立测试总数。

定向前后、API 前后、普通前后及静态后七份快照均为相同的 495 个 backend 文件映射，当前源码逐文件重算一致；结果节点、suite 清单与 hash 独立核对保存在 `local-evidence-audit.json`。frontend 相对 `77f206c5b1610e4b5d8c814b39b72a5f191970da` 零 diff；本轮使用实际 typecheck/lint，不把旧前端 214 项借作本次测试。

静态 TopN 已在 `.ai/reports/static-scan/20261122-010845/summary.md` 逐项说明八项 warning 的实际消费者、保留理由和覆盖边界，未关闭规则或称告警已修复。lint raw 与 B3 字节相同，SHA256 为 `b5496961a4422b7ea5154e6aead19f4c751cd8e76575656cf0b4b92a6fea0faa`；人工处置只更新 ignored summary，未改源码、raw、result.json 或 hash 快照。

后端完整纵向切片已本地提交为 `23950f98f2c7000bc054e74e9a67feb5f892e2dc`，父提交 `77f206c5b1610e4b5d8c814b39b72a5f191970da`，标题 `feat(run): recover confirmed merge results on terminal tasks`，共 13 个 backend 文件。提交树逐文件 hash 与最终 533/48/789/静态验收树一致；此时尚未推送。

上述本地验收完成时，B4 仍为唯一 HEAD，文档提交、分阶段推送及本片固定完整 SHA 的 Full-Link 尚待收口；随后交付事实见 17.5。Full-Link 的后端 API/隔离 GitLab 覆盖不扩写为完整 UI 或更强的 Slice4/5 验收。本片不扩 INTENT/SEND/UNKNOWN、cleanup/version；回滚停派发并保留已发生事实，不撤销 merge。

### 17.5 B4 分阶段推送与固定 SHA 远端验收

后端 `23950f98f2c7000bc054e74e9a67feb5f892e2dc` 与文档 `be1ccbe61a5155ba5ce8582f884d41dbb385a932` 已按阶段推送到 `feature/orchestration-consistency`。`.ai/reports/validation/c2-b4/phase-push.json` 保存推送前后引用：main/develop 仍共同指向 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，远端只有 main、develop、该 feature 三分支，推送完成时工作区干净。

固定完整 `be1ccbe61a5155ba5ce8582f884d41dbb385a932` 的 [Full-Link 44929301318](https://github.com/tongsh6/release-hub/actions/runs/44929301318) 为 completed/success，job `136922204638` 同为 success，watchExitCode=0。原始日志 Passed: 5 / Failed: 0，汇总时间 `2026-11-21T17:28:09.1091859Z`，即北京时间 `2026-11-22 01:28:09`；run 更新完成时间 `2026-11-21T17:28:40Z`。证据为 `.ai/reports/validation/c2-b4/full-link/result.json` 与同目录 `136922204638.log`。

该 CI 绑定 B4 自身已推送 SHA；backend 仍与最终 533/48/789/静态验收树一致，B4 标记 DONE。最初编译失败 0 tests、各自源码树的独立 RED、533/2 FAIL 首轮及仅测试修正历史原样保留，重叠用例不累计。5/5 仍为后端 API 与隔离 GitLab 切片，不等于完整 UI 黄金旅程，不扩大 Slice4/5 既有较弱边界，不为 INTENT/SEND/UNKNOWN 提供本片未做的恢复验收。

## 18. C2-B5：原 MERGE 未发送意图的安全续发

B4 固定 SHA CI 成功后，唯一 HEAD 接续 B5。最初先完成只读职责审查与独立契约，再以旧生产隔离快照运行 RED；随后实现并通过首轮 108/108 定向。相关回归、补充真实 CAS 反例和最终本地门禁现已完成，后端 0642828 已提交推送；文档阶段及固定该后端完整 SHA 的 CI 待完成，B5 不标 DONE；18.1–18.2 保留实施前依据，后续各轮事实见 18.3 起。

### 18.1 独立正反例与源码定位

正例先定义为：原 TRY 完整到期，仅有同 attempt 的 MERGE_MR/INTENT sequence 0、无任何 SEND/自身 Step，原 checkpoint 仍 ready；重启接管应继续原精确请求、原 operation/attempt，并只产生一次 PUT。反例包括旧 SEND 已先提交、已有 owner UNKNOWN/Step、伪装 INTENT 但已有 SEND、错误 guard/checkpoint/request，以及新旧观察版本交错。

最强反例推翻仅允许同 token 再进 SEND 的方案：发送输家被拒后会沿 RunAppService 映射成 UNKNOWN；若仍持同一 Task fence，旧 completeTask 可抢先结束新发送者的任务，随后实际 HTTP 响应可能因任务不再 RUNNING 而丢失。因此需要 Task 当前 owner 轮换，同时约束 sending、finish 与完整结果提交；只修 SEND CAS 不足以证明安全。

源码只读定位还显示：原 operationForUpdate、ack、APPLIED 证明把原 op claim/session 与当前 Task token 等值；HTTP interceptor 当时忽略 begin 返回的 operation ID，原 target 不完整保存 merge_commit_message/should_remove_source_branch，原 body hash 依赖序列化字节。guard 映射保护与非 Task completeSession 存在真实 session 消费者，不能为接管一起轮换原 reservation。上述位置决定最小职责调整；当时仅是源码定位，随后按 18.3 的独立 RED 验证，未将只读推断当作测试结论。

### 18.2 最小契约与验收边界

完整契约集中于原 design、tasks 与 Run delta：RunPort.claimNextTask 保持唯一领取入口；Run→Task→guard 锁内重核并 CAS 只换 Task 当前 token/lease，递增 operation observation version，保留 op 原 claim/session/attempt、guard 原 reservation、startedAt 与实际顺序。sending/finish 显式核实际 caller fence，seq1 记实际 sender；ack/APPLIED 只对本片原 attempt 核该 sender，B2/B3 的原资格不放宽。原 begin 复用并返回 operation ID，由 interceptor 实际消费；无新实体、表、API、DTO、attempt 或 dispatcher。

请求复用原 target 和 hash 能力，保存 sha 文本、原输入 merge_commit_message 文本、should_remove_source_branch=false 布尔三键；自动续发另核消息等于原计划默认值，不将此恢复资格扩为正常非 Task 消费者必须拥有计划；固定次序/类型指纹覆盖原 envelope，不允许 query（含空 ?），未知/缺失/错型/旧 hash 拒绝零写，无兼容回填。历史观察按原 Task 归属和 expectedVersion 核验，接管使旧版本失效；不拿原 op token 当作当前发送权，也不破坏原映射保护。

旧 SEND 先提交时不能接管，其迟到真实响应仍须保存；新接管先提交时旧 sending 与旧 UNKNOWN complete 均拒绝。SEND 已提交但 HTTP 前中断不得再次发送。前置 GET 失败、closed 或 source B 可令 Task UNKNOWN，但 operation 仍 INTENT 且无 SEND/响应，guard 原 reservation/INTENT_PENDING 保持，不能误写 REMOTE_RESULT_UNKNOWN 或引入新状态；前置 GET 已包含 A 或 BLOCKED 不伪造 APPLIED/REJECTED，允许原 completeTask 整事务拒绝回滚并保 RUNNING/INTENT/INTENT_PENDING，这一边界尚不能自动收口，留待后续独立处理。

实施前据此安排先 RED：真 PG 锁两种胜出顺序、两名新 worker、输家 UNKNOWN、显式 fence 与旧/新 version；坏前页 keyset、同仓前序/跨仓进展；跨 JVM 在 INTENT 后强杀、到期后复用原 operation 一次 PUT，并回归 B2/B3/B4。其他 INTENT 动作、SEND/UNKNOWN、cleanup/version 与完整 UI 仍 QUEUED；回滚停派发并保留事实，不撤销远端写。

### 18.3 旧生产冻结树的独立 RED

所有有效初轮 RED 的生产均为 `4a067a8f292f48a5854bc5f4d13d9c28bd54276a`，在 ignored 隔离冻结树执行；provider/HTTP/process 与后续 PG 使用各自测试快照，各阶段内部 sourceStable=true，不合称同一测试树。以下路径以 `.ai/reports/validation/c2-b5/` 为根。

| 独立范围 | 实际结果 | 原始结果路径 |
|---|---|---|
| provider | 25 tests / 21 FAIL / 0 ERROR / 0 SKIP | initial-red-frozen-v2-jdk21/provider/result.json |
| 真实 HTTP 组合 | 7 tests / 7 FAIL / 0 ERROR / 0 SKIP | initial-red-frozen-v2-jdk21/http/result.json |
| PostgreSQL | 71 tests / 17 FAIL / 0 ERROR / 0 SKIP | initial-red-pg-frozen-jdk21/pg/result.json |
| 独立进程 | 3 tests / 2 FAIL / 0 ERROR / 0 SKIP | initial-red-frozen-v2-jdk21/process/result.json |

有效初轮共 106 个新实例。PG 明确复现末尾 INTENT 不入候选、完整未发送意图不可领取、旧/缺失 fence 仍可 sending/finish、未发送 UNKNOWN 被错误标为 REMOTE_RESULT_UNKNOWN。进程两项 INTENT 恢复失败；SEND 已提交后不重发的独立场景在旧生产已通过，不能把保护行为也算成待修缺陷。

最初 `initial-red-frozen-jdk21/provider/result.json` 为 25 tests / 17 FAIL / 4 ERROR / 0 SKIP 且 sourceStable=false；四个 ERROR 为 Mockito 重设 begin 桩时命中空 Command 的夹具错误。该轮单列保留，不算有效业务 RED；随后只修测试夹具，在新冻结快照重跑得到上表 provider 结果，生产未改。后来新增 producer null/empty 两个边界，使首轮实现后定向为 108，未把新增边界冒称已在初轮 106 内执行。

### 18.4 实现职责、发送权与历史事实

RunJpa 仍通过原 claimNextTask 领取：Run→Task 锁后以数据库时间核完整过期、正执行顺序、仓内前序和 no Step，再由唯一实际消费者调用 Journal 的 MANDATORY prepareUnsentMergeClaim。Journal 在原 remote/operation/guard 锁内核唯一原 attempt/INTENT、完整 typed 请求、指纹、检查点和 reservation，使用 PESSIMISTIC_FORCE_INCREMENT 即时提升观察版本；同一物理事务随后按原 token/lease 做 Task CAS，失败抛错回滚。原无证据 CAS 保持，不把 JSON proof 复制进候选 SQL。

Task 当前 token/lease 可以轮换；原 startedAt/实际顺序、operation ID/claim/session/attempt、sequence 0 与 guard session/operation/generation 保持。begin 返回原 ID，interceptor 实际消费；sending/finish 明确接收 caller fence，seq1 保存实际 sender 的 taskId/claimToken。MERGE 的 finish/ack/APPLIED 证明核该 sender，历史观察按 Task 关联与 expectedVersion 判断。旧 API 重载与测试反射桥已删除，无新增实体、表、API、DTO、attempt 或 dispatcher。

正常 producer 保留原消息文本，完整持久 sha、merge_commit_message、should_remove_source_branch=false 的 JSON 类型；固定顺序请求指纹覆盖既有 envelope，无 query（含空 query）。B5 接手额外要求原计划默认消息，不要求非 Task 消费者拥有计划。缺 typed 请求或旧 hash 不能自动续发；历史 MERGE/APPLIED 投影按完整成功响应、原 target method/suffix/SHA 和 seq1 实际 sender 核证明，不要求补齐未参与投影职责的消息/hash。旧 seq1={} 不能投影，也不以原 op token 补造 sender；B2/B3 原资格不普遍放宽。

前置 GET 失败、closed、source B 可使 Task UNKNOWN，但 operation 仍 INTENT、没有 SEND/响应，guard reservation/INTENT_PENDING 保留。读取已包含 A 或 BLOCKED 时，不造 APPLIED/REJECTED，现 completeTask 整事务拒绝回滚并保 RUNNING/INTENT/INTENT_PENDING；此边界尚不能自动收口，仍属后续独立工作。

### 18.5 首轮 108 定向与真实竞争/进程证据

`first-green-jdk21/result.json` 为 5 suites / 108 tests / 0 FAIL / 0 ERROR / 0 SKIP、sourceStable=true：HTTP 7、PG 71、独立进程 3、MR adapter 3、HTTP interceptor 24。PG XML 输出四条 pg_blocking_pids 等待证据，包含双新 worker 的 78→77、79→78 排队关系；旧 SEND 先赢、新接管先赢分别在独立测试中确认实际数据库阻塞，未以同时启动线程代替锁竞争。

| 进程场景 | 原 Run / 进程 | 实际结果 |
|---|---|---|
| INTENT 后强杀、到期续发 | d0de2379-edbd-491a-af5a-2494b351a41c；99310 → 99424，exit=137 | 强杀时无 PENDING、原 MERGE 只有 1 条事件；senderChanged=true、原 provenance 保持，MR POST=1、merge PUT=1、原 attempt 最终 3 条事件 |
| 续发 APPLIED 后再次强杀 | b026284d-a983-4ea8-8b1a-d64ec7026868；99435 → 99446 → 99449，两次 exit=137 | 原 operation/attempt 保持；merge PUT 总数 1，第三 JVM 投影 HTTP=0，原 MERGE 三条事件保持 |
| SEND 已提交、HTTP 前强杀 | bd9ae6bc-fc36-455b-b18a-a97134f5b426；99281 → 99290，exit=137 | 到期后完成 3 轮实际扫描，重启后 HTTP=0、merge PUT=0，原 Task 与 2 条事件保持；这是安全停住，不是 SEND 恢复 |

原始逐行日志在 `first-green-jdk21/xml/releasehub-bootstrap/TEST-io.releasehub.bootstrap.api.RunTaskProcessKillIT.xml`，锁等待证据在同目录 RunTaskPersistenceIT XML。进程使用隔离 PostgreSQL/HTTP fixture 与真实独立 JVM，不冒称真实 GitLab 强杀或完整 UI 旅程。首轮中的事务回滚用例为外层 TransactionTemplate.setRollbackOnly；最终 Task CAS 实际返回 0 的补充验证单列于 18.6。

### 18.6 相关回归与真实 CAS 拒绝补强

`regression-green-jdk21/result.json` 实际为 13 suites / 691 tests / 1 FAIL / 0 ERROR / 0 SKIP、sourceStable=true。唯一失败是 GitWriteSafetyIT 的旧 committedIntentWinsTheLockAndEveryOperationStatePreventsTakeover：它把 INTENT 不可领取当作不可扫描发现。随后仅校正该测试候选断言及名称，继续要求旧人工摘要不可领取、不可投影、零 HTTP；生产不改，不以旧测试期待放宽请求资格。

为独立证明最终 CAS 返回 0 也回滚已准备的 version，新增 mergeIntentClaimCasRejectionRollsBackPreparedVersion：只对原 Task 的 token 变化安装真实 PG BEFORE UPDATE trigger，RETURN NULL 令影响行数为 0；用现有 Clock 在同事务 prepare 后、最终 CAS 前读取已增版本，要求 BusinessException 且所有 projectionFacts 回滚，finally 删除 trigger/function。此项不替代或改名主动 setRollbackOnly 用例。

`cas-red-frozen-jdk21/pg/result.json` 已在旧 HEAD 4a067a8 的原 PG 冻结文件上仅加入该方法，实测 1 test / 1 FAIL / 0 ERROR / 0 SKIP、sourceStable=true；失败是旧领取返回 empty，未发生应有的 CAS 拒绝异常。随后复验前源码变化限于 PG 新增用例与 HTTP 旧发现断言两份测试，生产不改。

最终受影响两套已在 `final-affected-green-jdk21/result.json` 复验为 HTTP 119 + PG 209 = 328 tests / 0 FAIL / 0 ERROR / 0 SKIP，sourceStable=true；新增真实 CAS 返回 0 用例的 XML testcase 无 failure/error/skipped，已确认本事务 version 曾增加、最终抛错后全部事实回滚。原主动 setRollbackOnly 用例继续保留，两类回滚证据不互相替代。

### 18.7 最终本地门禁与交付边界

| 范围 | 实际结果 | 证据（测试结果相对本节验证根目录） |
|---|---|---|
| 相关回归 | 13 suites / 692 PASS / 0 FAIL / 0 ERROR / 0 SKIP | final-related-jdk21/result.json |
| API | 3 suites / 48 PASS / 0 FAIL / 0 ERROR / 0 SKIP | final-gates/api/result.json 与 final-gates/result.json |
| 普通后端 | 58 suites / 816 tests：811 PASS、5 既有 SKIP、0 FAIL / ERROR | final-gates/ordinary/result.json 与 final-gates/result.json |
| 静态 | 六份 fresh SpotBugs XML 均 0；typecheck PASS；lint 0 errors / 8 既有 warnings | final-gates/result.json；.ai/reports/static-scan/20261122-021853/summary.md |

692 是按源码核对的分批汇总，`assembly.singleInvocation=false`：本轮重跑两套 328 项，前次未改的 11 套 364 项保留通过证据。生产未变；前后 495 份 backend 文件仅 PG 新增真实 CAS 用例与 HTTP 旧发现断言两份测试改变，其余 493 份完全一致。首轮 691/1 FAIL、106 独立 RED、108 首轮定向及新增 CAS 的 1/1 RED 均保留原轮次，不与最终结果累加。

最终受影响测试、API、普通与静态的七份源码快照及当前源码均为同一组 495 文件；`local-evidence-audit.json` 保存清单、结果与既有五项 SKIP。API 清单为 RunPagedApiTest、RunPlanApiIT、WindowRunApiIT。TopN 八项既有 fixture 告警逐项说明保留理由，前端没有改动，lint 原始输出与 B4 逐字节相同；规则未关闭，也不扩为完整 UI 验收。

### 18.8 后端阶段提交推送，固定 SHA CI 待验收

后端 14 文件纵向切片已提交为 `064282887ae825255360bcf3b04d0377163219aa`（父提交 `4a067a8f292f48a5854bc5f4d13d9c28bd54276a`），提交说明 `feat(run): safely resume unsent merge intents`。提交的 14 文件逐一匹配最终验收 hash，`backend-commit.json` 保存清单与 validatedSourceMatchesCommit=true；`backend-push.json` 确认第一阶段已推送至 feature/orchestration-consistency。

推送后 main/develop 仍共同指向 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，远端恰为 main、develop 与该 feature 三分支。文档阶段仍待 review/commit/push；本片 CI 固定后端完整 SHA `064282887ae825255360bcf3b04d0377163219aa`，结果待验收，其绑定对象不扩为后续文档提交。

B5 本地验收通过，仍为唯一 HEAD，不借用 B4 CI。前置读取已包含 A/BLOCKED 仍只验证整事务拒绝回滚并保留 RUNNING/INTENT/INTENT_PENDING，不能宣称自动收口；其他动作 INTENT、SEND/UNKNOWN、cleanup/version 和完整 UI 继续独立排队。
