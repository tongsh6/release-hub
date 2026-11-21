# 2026-11-21 SA-013：分支收敛与唯一编排语义

状态：S3-A/B1、S3-B2 已完成目标验收、交叉审查、静态扫描及分阶段推送，当前进入 S3-C1 持久四动作与安全中断。提交前发现的业务 UNKNOWN 会话收口和最终写 URI 两个组合缺陷均已用真实 RED→GREEN 修复。本文只记录本轮实际结果，历史 GitLab 证据不视为本轮重跑。

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

## 8. 未完成边界与下一切片

- 当前 HEAD 为 S3-C1：复用已有 run_task，唯一 WINDOW_ORCHESTRATION 路径按四动作持久提交；请求 key、MR 分段、任务/Item CAS、guard 事务确认与进程中断安全是同一纵向切片。
- Run 现有结果保存仍为聚合保存；仅增加 @Version 不能解决已在领域递增版本和多仓覆盖。C1 必须定向提交并从数据库事实汇总。
- C1 请求线程消费持久任务，目标为中断后可查询且安全停住；C2 才引入同一执行器的后台 worker、202/轮询、数据库扫描、核对与继续，不保留双轨。
- UNKNOWN 的只读核对不会自动解除仓库保留；普通 retry 不接受 NEEDS_REVIEW。SEND_STARTED 之后不能用租约过期或一次 404 证明允许重发。
- 原子删除分支没有已核实的 provider 前置条件，真实 HTTP 删除继续拒绝。cleanup 的自动恢复属于 S4，B2 不宣称具备。
- 实际 HTTP X-Request-Id 尚未向上采集，字段保持 null，不能用 MR IID 代替。
- 本轮尚未执行真实 GitLab、浏览器全旅程或独立进程 kill/restart；后续按蓝图完成对应验收。
