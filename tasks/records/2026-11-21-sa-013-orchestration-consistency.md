# 2026-11-21 SA-013：分支收敛与唯一编排语义

状态：已交付阶段及其固定 SHA 证据按各节保留。C2-B6 实现 `4e992e9c2406586ec23fa7636292e3d48c163118`、文档 `29ec00586be20730e66cf37da12614e6450d240d` 已分阶段推送，固定实现完整 SHA 的 Full-Link 44940092094 为 5/5，B6 已 DONE，详见19.9；此前本地门禁与历史轮次不变。B7 已完成独立 RED、定向 91/91、单次相关 840/840、API 48/48、普通 831（826 PASS / 5 既有 SKIP）、静态及源码绑定，实现 00def779 已推送；固定实现 SHA 的 Full-Link 44945748427 attempt 2 为 5/5，B7 已 DONE。首轮 Central 429 与最终结果见记录 20.8–20.9，验收文档单独收口。C2-B8 正常 CREATE_MR 完整请求的发送证明闭环已 DONE：独立 RED 与一次明确 PG 契约校准后，125/125 首次 GREEN、单次相关965/965、API48/48、普通877（872 PASS / 5既有SKIP）、静态及最终源码绑定通过；实现 `537588e782c2b3bb2d2e82e21804f2d37fb42fc2` 已推送，固定该 SHA 的 [Full-Link 44972473174](https://github.com/tongsh6/release-hub/actions/runs/44972473174) attempt 1 已 completed/success，五个slice分别PASSED。最终证据与边界见21.11，验收文档单独收口。C2-B9 原 CREATE_MR INTENT 的安全接续与本地收口已 DONE：相关1078/1078、API48/48、普通918（913 PASS / 5既有SKIP）、静态与最终源码审计通过，实现 `666acdc7a60c0922db2f05713cbfefd23eed8265` 已推送，固定该SHA的Full-Link 44978661003 attempt 1为5 PASS / 0 FAIL。证据与边界见记录22.7–22.14，验收文档单独收口。B10「MERGE_MR 精确只读核对证据」已DONE：相关1123/API48/普通942、前端218与静态及源码审计通过，实现 `8dcca5bf8265a3a1f36c16a14f0e3b8b0b35652d` 已推送，固定该SHA的Full-Link44982102777 attempt 1为5 PASS/0 FAIL，证据见记录23.7–23.11。B11「GitLab MR读取响应状态证明」已DONE：实现 `35ca21366ec9124fe9c4d6d2ef162a09c13709fb` 已推送，固定该SHA的Full-Link44984865795 attempt 1为5 PASS/0 FAIL，完整本地与远端证据见记录24.5–24.9。B12「无自身写意图任务的失败与重试闭环」已DONE：实现 `7042f19278ff8d8bfa102a8f27d0c1f1b949539d` 已推送，固定该SHA的Full-Link44989857158 attempt 1为5 PASS/0 FAIL，最终证据见记录25.10。当前唯一 HEAD 为 C2-B13「项目身份响应证明统一」；后续SEND/UNKNOWN窄恢复继续QUEUED，再按原顺序推进SA-002。任务选择以[唯一执行队列](../../docs/execution-roadmap.md)为准，C2 整体未完成。

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

B4 固定 SHA CI 成功后，当时接续实施 B5。先完成只读职责审查与独立契约，再以旧生产隔离快照运行 RED，随后实现并通过首轮 108/108 定向。相关回归、真实 CAS 反例、本地门禁、分阶段推送及固定后端 SHA CI 均已完成，B5 已 DONE，最终证据见 18.9；18.1–18.2 保留实施前依据，后续各轮历史见 18.3 起。B5 对未发送本地结果的保守限制是该片交付边界，B6 才独立负责改变。

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

### 18.8 后端阶段提交推送（当时 CI 尚未结束）

后端 14 文件纵向切片已提交为 `064282887ae825255360bcf3b04d0377163219aa`（父提交 `4a067a8f292f48a5854bc5f4d13d9c28bd54276a`），提交说明 `feat(run): safely resume unsent merge intents`。提交的 14 文件逐一匹配最终验收 hash，`backend-commit.json` 保存清单与 validatedSourceMatchesCommit=true；`backend-push.json` 确认第一阶段已推送至 feature/orchestration-consistency。

该后端首次推送后，main/develop 仍共同指向 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，远端恰为 main、develop 与该 feature 三分支。当时文档阶段与固定后端完整 SHA `064282887ae825255360bcf3b04d0377163219aa` 的 CI 尚待完成；最终事实见 18.9，CI 绑定对象未扩为后续文档提交。

B5 当时的本地验收中，前置读取已包含 A/BLOCKED 只验证整事务拒绝回滚并保留 RUNNING/INTENT/INTENT_PENDING，未宣称自动收口。这一历史结论保留，B6 单独负责未发送意图的本地结果变更；其他动作 INTENT、SEND/UNKNOWN、cleanup/version 和完整 UI 继续独立排队。

### 18.9 文档阶段推送与固定后端 SHA CI 收口

文档 10 文件提交 `62d075c36bd96c35d14bc541957787c5173559f8` 已第二阶段推送，父提交为后端 `064282887ae825255360bcf3b04d0377163219aa`。`local-docs-commit.json` 保留提交时尚未推送的历史状态，随后 `local-doc-push.json` 确认远端 feature 更新、工作区当时干净、main/develop 仍同指 d4534cc，远端恰三分支。

固定后端完整 `064282887ae825255360bcf3b04d0377163219aa` 的 [Full-Link 44935216788](https://github.com/tongsh6/release-hub/actions/runs/44935216788) 为 completed/success，job `136940242878` 同为 success，watchExitCode=0。原始日志 Passed: 5 / Failed: 0，汇总时间 `2026-11-21T18:31:23.7044079Z`，即北京时间 `2026-11-22 02:31:23`；run 创建 `2026-11-21T18:22:58Z`，更新完成 `2026-11-21T18:31:49Z`。证据为 `.ai/reports/validation/c2-b5/full-link/result.json` 与同目录 `136940242878.log`。

后端与本地最终验收树未变，B5 标记 DONE。该 CI 精确绑定后端提交，未将后续文档 SHA 当成该 run 的 headSha；5/5 仍限后端 API 与隔离 GitLab，不等于完整 UI 旅程，不扩大 Slice4/5 的既有边界。全部 RED/GREEN、分批 692 汇总及 B5 保守本地结果限制保留；改变未发送意图的结果收口由 B6 独立承担。

## 19. C2-B6：原 MERGE 未发送意图的本地结果收口

B5 交付后当时队首接续 B6。19.1–19.3 保留先于实现定义的职责、独立正反例和验收出口；19.4–19.8 保留实际 RED、实现、本地门禁、审查及阶段交付历史。详细规范集中于原 design/Run delta，执行清单见 tasks。B6 最终已按19.9完成文档推送与固定实现 SHA CI，标记 DONE；当前 B7 队首见第20节。

### 19.1 第一性原理与首选方案反证

BlockReason 白名单不能证明未发送：unavailable 等 UNKNOWN 也可能具有完整 INTENT 且没有任何 SEND；确定的业务结果也不能覆盖一个已赢得发送权的执行者。最小充分依据是当前 fence、原完整 INTENT、严格唯一 seq0 无 SEND，以及 NOT_SENT 与 Task 终态同物理事务。只释放已证明无在途写的原 reservation，UNKNOWN 的业务不确定性及队列/重试限制保持。

实施前源码定位（9f72670）已核：GitWriteHttpInterceptor 120–122 先 begin/sending，131 才 HTTP；Journal 的 sending 独立提交，当时 completeTask 于 Step/metadata 之后 ack。validUnsentMerge 483–524 已核原完整 request/hash、CREATED/MR_REUSED checkpoint、guard 与 no Step，故应复用并把 ack 前移，而不是复制或放宽资格。validatedPatch 仅负责字段集合/身份，结果与 patch 的语义还须单独核对。V35 的 state/event_type 为字符串且无 enum CHECK，不需要为本片追加迁移。

### 19.2 收口、历史事实与消费者边界

收口本身不要求 lease 再次到期；未过期原 owner 与经 B5 到期换权后的当前 owner 都是正例，expired 只属于换权资格。只扩原 acknowledgeTask 的真实 result patch 消费，在同 completeTask 事务先核当前 fence 与完整未发送证明，再记录 NOT_SENT 状态及原 attempt seq1 本地事件；Task/Step/metadata/仓内队列/绑定/Run/guard 同事务。原 op ID/attempt/seq0/claim/session/身份保持；事件最小字段为 taskId/claimToken/result，evidenceJson.metadata 保存实际 nullable patch，reason 沿 git.blockReason，无则 null，不复制原请求或伪造 HTTP status/响应/requestId，不新增 GitWriteResult.Outcome、实体、表、DTO、API 或 dispatcher。

ALREADY_MERGED 必须对应 patch.mrDisposition=ALREADY_MERGED；UNKNOWN/BLOCKED 不带成功 mergeSha/disposition，null 保留原删除语义，异常路径缺 requestId 键不因此被拒绝。未发送却 MERGED 明确拒绝，FAILED 不扩入。原 target contains A 足以 ALREADY，无额外 MR 读取要求，MR 404 不推翻既有证明；额外 mergeSha contains 规则保持且不补 SHA。MR merged 但 target reset 仍 UNKNOWN；GET 403 仍 BLOCKED/PERMISSION_DENIED，unavailable/closed/sourceChanged 仍 Task UNKNOWN/Run NEEDS_REVIEW、同仓后继停住且不可自动或普通重试。

NOT_SENT 与 SEND_STARTED 互斥；MERGE SEND 先赢且尚无 seq2 响应时，包括同 fence UNKNOWN 在内的任何本地 completeTask 均整笔拒绝，保留迟到真实 finish；op 已 UNKNOWN 且真实 seq2 已存在时仍允许既有 UNKNOWN 确认，不扩展 SEND 恢复。NOT_SENT 先赢时旧/现 worker 迟到 sending/finish/complete 均拒绝。收口使旧 expectedVersion 失效；新 observe 保存原有观察审计时间与版本，不推进执行或改写原事实和 guard。只释放匹配原 session/op/generation 的 guard；Operation.blocked 仍表示 remote 当前任一 guard，历史 NOT_SENT 可因新操作而 blocked=true。mapping/baseURL 的 hasActiveGuards 与 Run/Repo 的 GitOperationsPanel 均为实际消费者；补 zh/en 与组件验收，不冒称完整 UI 旅程。

### 19.3 独立 RED 与交付出口

先验证 ALREADY/各真实 BLOCKED（含 GET 403）/UNKNOWN 零 PUT，ready 沿 B5 一次 PUT；真实锁顺序的 SEND/NOT_SENT 先后、双收口及迟到调用；stale/缺 typed/旧 hash/错 guard/checkpoint/多事件与结果语义污染拒绝；ack 后外层回滚和真实 Task CAS=0 两类完整回滚；旧/新观察与其他 guard 保护。BLOCKED 保留原 FAIL_FAST/CONTINUE 与用户重试复用 MR，UNKNOWN 释放 reservation 不放开队列或重试。

补真实 JVM INTENT 后强杀、默认 worker 到期后本地收口和无 PENDING 末尾发现，回归 B2–B5；现有组件覆盖 NOT_SENT 状态/事件及真实 blocked。完成本片独立 RED→GREEN、相关/API/前端/静态门禁、review、分组提交推送和固定 SHA CI 后再收口。回滚停派发并保留事实，不重发 NOT_SENT、不撤销远端写；其他 INTENT 动作、SEND/UNKNOWN 恢复、cleanup/version 与完整 UI 仍独立排队。

### 19.4 独立旧生产 RED 与测试语义校正

本节证据统一位于 `.ai/reports/validation/c2-b6/`。后端从旧生产完整 HEAD `9f72670e4bc360cde0e3c64ccf82bfe90440b05d` 导出冻结树，只叠加对应新测试；生产未变，前后源码 hash 一致。独立结果如下，三类后端测试不能因断言失败而误记为执行错误：

| 范围 | 独立结果 | 证据 |
|---|---|---|
| PostgreSQL | 41 项：24 FAIL、17 PASS、0 ERROR / SKIP | initial-red-pg-frozen-jdk21/pg/result.json |
| 真实 HTTP fixture | 13 项：13 FAIL、0 ERROR / SKIP | initial-red-http-process-frozen-jdk21/http/result.json |
| 独立 JVM 强杀重启 | 3 项：3 FAIL、0 ERROR / SKIP | initial-red-http-process-frozen-jdk21/process/result.json |
| 原前端新组件用例 | 3 项：3 FAIL、0 pending | ui-initial-red/result.json |

HTTP 的 ALREADY/BLOCKED 原实现因 stale 拒绝；UNKNOWN 保留 INTENT_RECORDED；SEND 已提交且无 seq2 时，提前 UNKNOWN 原本未被拒绝。PG 进一步暴露资格污染可落 UNKNOWN、原终态/版本未推进、真实发送竞争和 ack 后 CAS 证据未形成。三个 JVM 均实际在原 INTENT 后以 exit 137 强杀；重启仍 RUNNING 或 INTENT，失败发生于业务预期而非编译或环境错误。UI 三例失败为显示未翻译的 NOT_SENT key。

独立 RED 保存后，更正测试本身的两类过强预期：UNKNOWN 后继沿既有规则严格 SKIPPED，并验证 token/started_at 为空及零后继 Step；新 observe 允许原有审计更新 operation.updated_at/state_version，除此之外完整执行事实和当前 guard 不变，且版本必须前进。未为这些修正修改生产队列或 observe。原 B5 的 PG 两例与 HTTP 五例均保留场景、更新为 B6 契约；没有删例，旧 RED 文件保持原样。

### 19.5 最小实现与定向 GREEN

生产只改原 Journal Port、GitOperationPersistenceAdapter 与 RunJpaPersistenceAdapter：ack 接收真实 nullable patch，并前移至同一 completeTask 事务的 Step/metadata 改写之前，复用原 validUnsentMerge 完整证明。合法本地结果形成原操作 NOT_SENT 与同 attempt sequence 1 事件；当前 Task、Step、patch、队列、绑定、Run 和匹配 guard 同事务提交。无新实体、表、DTO、API、dispatcher、迁移或兼容写路径，ack 的唯一生产消费者仍是 completeTask。

MERGE/SEND_STARTED 尚无响应时拒绝所有提前本地完成，包括同 fence UNKNOWN，保留真实迟到 finish；已有真实 sequence 2 UNKNOWN 的确认行为保持。未发送证据与业务结果分责，GET 403 的实际 PERMISSION_DENIED 不被原因白名单截断；UNKNOWN 释放原 reservation 后仍 NEEDS_REVIEW，同仓后继停住且不可普通/自动重试。

`first-green-jdk21/result.json` 记录新定向 57/57 PASS（PG 41、HTTP 13、JVM 3），0 FAIL / ERROR / SKIP，源码前后稳定。PG 包含真实锁等待、SEND/NOT_SENT 先后、双收口、外层回滚及真正令 Task UPDATE 返回 0 的 trigger；不是在 ack 之前拒绝而冒充完整回滚。三个独立 JVM 场景恢复阶段各完成 3 次实际扫描，后续独立观察阶段扫描为 4/3/3；MERGE PUT 为 0、观察 HTTP 为 0，原 op/attempt 保留、Task token 换权、同 attempt sequence 1 为 NOT_SENT。

前端只在现有 zh/en 加 NOT_SENT 状态/事件文案，复用 Run/Repo 的 GitOperationsPanel。新三例和原三例 6/6 PASS（ui-first-green），完整 Vitest 217/217 PASS（ui-full-green），源码稳定；同时覆盖业务结果、实际当前 guard 和无新增执行入口，不扩为完整 UI 黄金旅程。

### 19.6 审查与范围

根侧对全部 9 个代码/测试/翻译文件完成 fenced review：生产与 UI 一页、测试七页，冻结 tree `1411b22203cb2219075e5037b506fb07707e51c1`。provider 与持久化两路独立审查均未发现阻断：实际 nullable patch 生产者满足校验，原 CREATE_BRANCH/CREATE_MR/APPLIED 投影不依赖 ack 前尚未写入的结果，MANDATORY 与既有锁顺序保持，历史 NOT_SENT 不获得新发送权。运行验收与源码审查分别留痕，不相互替代。

### 19.7 最终本地门禁与源码绑定

| 范围 | 实际结果 | 证据（相对本节验证根目录） |
|---|---|---|
| 新定向 | 3 suites / 57 PASS / 0 FAIL / ERROR / SKIP | first-green-jdk21/result.json |
| 相关回归 | 单次 Maven 13 suites / 749 PASS / 0 FAIL / ERROR / SKIP | regression-green-jdk21/result.json |
| API | 3 suites / 48 PASS / 0 FAIL / ERROR / SKIP | final-gates/api/result.json |
| 普通后端 | 58 suites / 816 tests：811 PASS、5 既有 SKIP、0 FAIL / ERROR | final-gates/ordinary/result.json |
| 前端 | 定向 6/6，完整 Vitest 217/217 PASS，0 failed / pending | ui-first-green/result.json；ui-full-green/result.json |
| 静态 | 六份 fresh SpotBugs XML 均 0；typecheck PASS；lint 0 errors / 8 既有 fixture warnings | final-gates/result.json；.ai/reports/static-scan/20261122-031351/summary.md |

本次相关回归为单次完整调用，PG 250、HTTP 132、独立 JVM 11 与其余 10 suites 全部通过；不沿用 B5 的分批汇总口径，不将新定向、相关、API 和普通的重叠测试累加。JVM 11/11 覆盖此前 B2–B5 及本片三个新场景。

九份 495 文件 backend 快照一致，五份 222 文件 frontend 快照一致，`local-evidence-audit.json` 记录源码清单与验收一致性，`source-review.json` 留存九文件 review 证据。TopN 八项既有 fixture warning 已逐项说明保留依据；lint 原始输出与 B5 逐字节相同，SHA-256 为 `b5496961a4422b7ea5154e6aead19f4c751cd8e76575656cf0b4b92a6fea0faa`，未关闭规则。

本地验收不替代远端交付：B6 实现已分阶段推送，固定实现 SHA 的 CI 执行中（19.8），仍为唯一 HEAD。其他 INTENT 动作、SEND/UNKNOWN 恢复、cleanup/version 和完整 GitLab/UI 黄金旅程未在本片验收；Slice4/5 的既有较弱覆盖边界不扩大。

### 19.8 实现阶段提交推送与固定 SHA CI（执行中）

实现提交为 `4e992e9c2406586ec23fa7636292e3d48c163118`，父提交 `9f72670e4bc360cde0e3c64ccf82bfe90440b05d`，说明 `feat(run): atomically close unsent merge intents`。9 个后端/测试/翻译文件合计 1355 行新增、38 行删除，validatedSourceMatchesCommit=true；`implementation-commit.json` 保存提交与已验收源码匹配证据，`implementation-push.json` 确认本阶段已推送至 feature/orchestration-consistency。

推送时 main/develop 仍共同指向 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，远端恰为 main、develop 与当前 feature 三分支。此处只确认实现阶段，不把未提交的本地验收文档写成已推送。

固定实现完整 SHA `4e992e9c2406586ec23fa7636292e3d48c163118` 的 [Full-Link 44940092094](https://github.com/tongsh6/release-hub/actions/runs/44940092094) 已实际触发并核对 headSha；创建于 `2026-11-21T19:17:03Z`，即北京时间 `2026-11-22 03:17:03`。本次记录观察状态为 in_progress，尚无完成或通过结论，证据为 `full-link/initial-run.json`。B6 保持 HEAD，待该 run 完成后按实际结论收口；不使用 B5 CI 代替本片验收。

### 19.9 文档阶段推送与固定实现 SHA CI 最终收口

验收文档提交 `29ec00586be20730e66cf37da12614e6450d240d` 已分阶段推送，父提交为实现 `4e992e9c2406586ec23fa7636292e3d48c163118`，说明 `docs: record unsent merge completion validation`。根侧核实时工作区干净，main/develop 仍共同指向 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`。19.7 结尾及 19.8 保留当时的本地验收、CI in_progress 与文档未推送状态，本节记录其后已完成的事实。

固定实现完整 `4e992e9c2406586ec23fa7636292e3d48c163118` 的 [Full-Link 44940092094](https://github.com/tongsh6/release-hub/actions/runs/44940092094) 为 completed/success；job `136955172873` 同为 completed/success，watchExitCode=0。原始汇总 Passed: 5 / Failed: 0，时间 `2026-11-21T19:24:20.4221573Z`，即北京时间 `2026-11-22 03:24:20`；run 创建于 `2026-11-21T19:17:03Z`，更新完成于 `2026-11-21T19:24:36Z`。证据为 `.ai/reports/validation/c2-b6/full-link/result.json` 与同目录 `136955172873.log`。

实现与本地已验收源码保持一致，B6 标记 DONE；该 CI 精确绑定实现 SHA，未将后续文档 29ec005 当作 headSha。19.4–19.7 的独立 RED、测试语义校正、单次相关 749、其余门禁与源码绑定原样保留，重叠测试不累加。5/5 仍限后端 API 与隔离 GitLab，不扩大完整 UI、Slice4/5 或其他未知写恢复的覆盖。唯一 HEAD 接续 B7，独立实施前契约见第 20 节。

## 20. C2-B7：原 CREATE_BRANCH 未发送意图的安全接续与本地收口

20.1–20.4 保留实施前收敛的独立契约：B6 验收完成后，B7 成为唯一 HEAD，契约时基线为 `29ec00586be20730e66cf37da12614e6450d240d`，当时源码和新测试尚未实施、待 RED。以下源码定位与正反例是事前依据，不冒充运行结果；实际 RED、实现和验证自20.5起追加。完整规范集中于原 design/Run delta，执行清单见 tasks，不另建平行计划。

### 20.1 第一性原理、反证与实际定位

已提交的创建意图冻结了项目 I、目标分支和 SHA A；恢复应消费这个命令的值。重新读取已前进或删除的 sourceRef 会改变或丢失原意图，不能把“重新请求当前 base”当作续发。只在 interceptor 写前检查 I 也太晚：provider 可能已按重映射 namespace 读取 J 的分支。因此快照必须经现 TaskClaim/Context 在预检前绑定 I，同时保留实时 DB fence 的发送权检查。

实施前源码已核：GitWriteHttpInterceptor 75–82 对 CREATE_BRANCH 每次解析 ref；99 先构造 body，120–122 的 begin 只返回 ID 后即 sending。RunPort.TaskClaim 79–82 仅有现有任务字段/metadata，GitOperationContext 22–44 从 metadata 取项目且 bindProjectId 重建上下文；它们尚无原 Operation 快照。Journal.prepareUnsentMergeClaim 158–174 仅接收 TRY_MERGE；confirmedAppliedBranchProject 179–196 则独立消费 B2 原 201/name/SHA 响应证明。上述位置是本片职责核对依据，不以当前实现定义正确性。

“所有恢复失败都转 UNKNOWN”也被反例推翻：I→J 由 expected I resolver 明确拒绝，可保留真实 FAILED；完整无 SEND 证明足以原子收口，不依赖业务不确定分类。“已有 target 必须仍等于 A”则混淆了 ENSURE 和本次创建成功：合法目标已经在 B，B≠A 仍可 ALREADY_EXISTS，不回移分支，也不借 201 证明否定已有分支。

### 20.2 最小责任调整与冻结请求

原 prepare 原位重命名并返回 Optional<Operation>；现 TaskClaim 增加 nullable 已有 Operation，Context 提前绑定原 I，bindProjectId 保留快照。metadata/plan 不改，不新建持久对象、DTO、独立 prepare/查询接口。begin 原位返回已有 Operation，实际唯一生产消费者仍为 interceptor，不保留 String 兼容返回；首次/续发 body 都消费 begin 已核准的 typed target。

CREATE_BRANCH 的 method/suffix/branch/ref/sourceRef 与 canonical hash 由现 GitLabGitBranchAdapter 负责，interceptor 和 Journal 为实际消费者。原 sourceRef 仅匹配计划，恢复读取次数为 0；实际 POST 仅 branch/ref，canonical 次序 branch、ref，保留 ref 原始大小写并以 UTF-8 编码、无 query。hash 封套为 `POST|remoteKey|/repository/branches||SHA256(canonicalUtf8Body)|`，外层再 SHA256；不记录 secret，不加新/旧 hash 双轨或历史 INTENT 回填。

快照只提供原值，不承担权限。prepare/begin/sending 仍实时核 DB 当前 fence、原 guard、完整 typed 请求/hash、同 attempt 唯一 seq0、无 SEND/自身 Step；Operation.version 不引入新发送 expectedVersion。仅 observe 增长审计版本而原事实仍合法是正例，DB 意图/fence/guard/target 真正失效才拒绝。原 op/attempt/seq0/owner/session、首次时间和实际顺序保持。

### 20.3 本地结果、发送竞争与 APPLIED 边界

完整未发送 CREATE_BRANCH 的真实 ALREADY_EXISTS/FAILED/UNKNOWN 可沿原结果事务形成 NOT_SENT。接受实际空、仅 git.projectId 或含可选 blockReason 的 patch；project 若提供必须为 I，不能强制合并专属成功字段或补理由。无 SEND 的 CREATED、错项目或身份污染拒绝。NOT_SENT 本地事件、Task/Step/metadata/队列/绑定/Run 与匹配 guard 同事务；后继、普通 retry 与 UNKNOWN 限制不变。

CREATE_BRANCH SEND_STARTED 尚无 seq2 时拒绝提前 UNKNOWN，保留真实 finish；已有真实 seq2 UNKNOWN 继续保留 guard，不自动重发。begin→sending 间换权或收口后，原快照不能让迟到 worker 发出 HTTP。新 owner 续发后 APPLIED 再强杀，第三 JVM 必须按真实 seq1 sender 投影原 Task，而非要求历史 op.claimToken 已改写；只修这项归属检查，不把新 canonical hash 加到 B2 原 201/name/SHA APPLIED 证明，也不建立兼容 hash 分支。

### 20.4 独立 RED 与交付出口

先覆盖两个 branch 动作、same/new owner、40/64 位 SHA 和 AUTO/NAMED/EXISTING，确认无非法创建 INTENT。sourceRef 前进/删除仍发送原 A、零重解析；namespace I→J 时 J branch GET/POST 为 0且保留真实 FAILED；已有目标 B≠A 可本地 ALREADY_EXISTS；空/仅 project/可选 reason patch 与无 SEND 的 CREATED/身份污染分别验证。

真实 PostgreSQL 用例应覆盖完整资格破损、锁等待、begin→SEND 竞争、双领取/收口、旧 worker 迟到、实际 Task CAS=0 和外层回滚，分别证明到达准备/结果阶段后全部相关事实回滚。只读观察仅改变审计 version 的正例也须保留，不能把 snapshot.version 相等新增成发送门禁。

独立 JVM 先在 INTENT 后强杀，默认 worker 换权后按原操作续发 1 次 branch POST，再在 APPLIED 后强杀，第三 JVM 恢复阶段 HTTP=0；另外验证 SEND 后强杀不自动复发。回归 B2–B6、后继/普通 retry 与既有 NOT_SENT UI/API 消费者；完成相关/API/前端/静态 TopN、源码绑定、review、分组提交推送及本片固定 SHA CI 后才收口。

CREATE_MR 原摘要不含完整 title/description/源分支删除选项 请求，interceptor 还会注入随机 operation marker；不能凭“也没有 SEND”宽松重放未知原请求。其 INTENT、SEND/UNKNOWN 与其他动作恢复另片定义。回滚停派发并保留证据，不撤销远端分支、不重发 NOT_SENT 或未知在途操作；不扩大完整 GitLab/UI 验收边界。

### 20.5 独立 RED 与原测试契约保留

20.1–20.4 的事前契约在实施前已随 `8927dcf004ef6ff78e73ce2db663ccfa777b575d` 保存。四组 RED 分别从该完整提交导出冻结生产源码，只覆盖对应的新测试文件后运行；这些反例先于任何 B7 生产修改。验证根目录为 `.ai/reports/validation/c2-b7/`。

| 范围 | 修复前实际结果 | 证据（相对验证根目录） |
|---|---|---|
| Interceptor 单元 | 15 tests / 15 FAIL / 0 ERROR / 0 SKIP | initial-red-unit-frozen-jdk21/unit/result.json |
| 真实 HTTP | 24 tests / 21 FAIL / 3 PASS / 0 ERROR / 0 SKIP | initial-red-http-frozen-jdk21/http/result.json |
| PostgreSQL | 48 tests / 37 FAIL / 11 PASS / 0 ERROR / 0 SKIP | initial-red-pg-frozen-jdk21/pg/result.json |
| 独立 JVM | 4 tests / 3 FAIL / 1 PASS / 0 ERROR / 0 SKIP | initial-red-process-frozen-jdk21/process/result.json |

四组 Maven 均退出 1，sourceStable=true；采集脚本按预期失败完成不代表产品已通过。单元反例覆盖请求类型、query 和 canonical 字段次序；HTTP/PG 复现原分支意图不能接续及 SEND 尚无响应时本地 UNKNOWN 提前结束任务。JVM 三个接续场景在真实 INTENT 提交后以 exit=137 强杀，原代码不能恢复；SEND 不重放控制场景已通过，未通过延长等待或绕过 worker 掩盖边界。

生产签名调整后，仅适配 `begin(...).id()`、TaskClaim 的 nullable Operation、mock Operation 与发送前 canonical 创建夹具。B2 的三字段历史 branchTarget 和任意 hash 的 APPLIED 投影对照保持；未将新请求门禁强加给历史成功响应。`test-contract-preservation.json` 对四组文件逐字逆变换，仅撤销记录的 API/创建夹具调整后均与独立 RED 源完全一致，testsPreserved=true；全部独立断言和场景保留，Process 文件逐字未改。

### 20.6 已实现的责任边界与源码审查

本片修改 7 个生产文件并同步 10 个测试文件，保持原执行链路。

| 既有职责 | 实际变化与消费者 |
|---|---|
| JournalPort / RunPort / Context | begin 直接返回已有 Operation；prepare 原位改为 prepareUnsentTaskClaim 并返回 Optional<Operation>。现 TaskClaim 携带原快照，Context 在首次预检前绑定原项目 I，后续 bindProjectId 保留快照；metadata/plan 不改 |
| GitLabGitBranchAdapter / Interceptor | 严格规范原五字段 target 与 canonical hash。首次和接续均消费 begin 确认的 Operation 重建实际 branch/ref body，保留原 SHA 字节；恢复不再次解析 sourceRef，当前 namespace I→J 在现 resolver 拒绝 |
| JournalPersistence / RunJpa | 完整无 SEND 证明与当前 fence 仍在数据库锁内核对；原 Operation 快照与 Task 换权同事务返回，候选 SQL 不变。原 ALREADY_EXISTS/FAILED/UNKNOWN 经既有结果事务形成 NOT_SENT/本地事件并释放匹配 guard |
| 原发送与 APPLIED 投影 | CREATE_BRANCH 的 sending、finish、ack 和成功投影按真实 seq1 sender 核当前发送者；原 owner/session/attempt/seq0 保留。SEND 无 seq2 时拒绝提前本地完成，真实迟到 finish 保持；B2 APPLIED 仍使用既有 201/name/SHA 证明 |

未新增实体、表、schema 迁移、执行器、查询 Port、用户执行入口或兼容路径。新增规范化方法均由现 interceptor/Journal 消费；快照只供原值，不成为独立权限。真实 UNKNOWN 继续阻断同仓后继和普通/自动重试，NOT_SENT 不获得再次发送权。

根侧已完整审阅 7 个生产文件的两页 fenced diff、四组独立测试和其余六份旧 API 夹具，冻结 tree 为 `3e7da169c275f61039f2f9536b201bec44ae68d4`。`source-review.json` 记录 17 文件 hash、完整 review 和两路独立跨链审查；未发现阻断性问题。特别核对了原 I/A 在预检前生效、无 sourceRef 重读、当前发送权与历史归属分离、迟到响应保护及第二次 APPLIED 强杀后的纯 DB 投影。源码审查与运行验证分别留证。

### 20.7 最终本地验收与源码绑定

| 范围 | 当前实际结果 | 证据（相对验证根目录） |
|---|---|---|
| 新定向 | 单元 15、HTTP 24、PG 48、JVM 4，共 91/91，0 FAIL/ERROR/SKIP；sourceStable=true | first-green-jdk21/result.json |
| 相关回归 | 单次 Maven 13 suites/840/840，0 FAIL/ERROR/SKIP；sourceStable=true | regression-green-jdk21/result.json |
| API | 48/48，0 FAIL/ERROR/SKIP；sourceStable=true | final-gates/api/result.json |
| 普通后端 | 831 tests = 826 PASS / 5 既有 SKIP，0 FAIL/ERROR；SKIP 身份与 B6 一致，sourceStable=true | final-gates/ordinary/result.json |
| 静态 TopN | 六份 fresh SpotBugs XML 全 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings；已逐项读取源码并记录保留理由 | final-gates/static-result.json；静态报告见下文 |
| 最终源码绑定 | 九份 backend 快照的 495 文件完全一致，绑定以上各轮结果和实现提交 | local-evidence-audit.json、implementation-commit.json |
| 前端 | 222 文件与 B6 已验 217 的源码树一致，本片未重跑 UI 套件；本片 lint/typecheck 已重新通过 | local-evidence-audit.json；217 仅为 B6 的历史验收 |

相关回归实际包括 GitMergeWorkflow 48、RunOrchestration 29、Journal 8、HTTP 156、PG 298、独立 JVM 15、BranchAdapter 55、MergeRequestAdapter 108、ReleaseEvidence 42、BeanDecorator 6、Interceptor 65、RepositoryScope 3、Worker 7；新定向、相关和普通/API 的重叠测试不累加。

静态报告为 `.ai/reports/static-scan/20261122-041826/summary.md`。8 条既有测试 fixture warning 全部保留并逐项记录原因，未关闭 lint 规则；本片 lint 原始输出与 B6 逐字节相同，SHA256 为 `b5496961a4422b7ea5154e6aead19f4c751cd8e76575656cf0b4b92a6fea0faa`，比较证据为 `static-warning-comparison.json`。前端未改，与 B6 已验 217 的源码树一致，不把历史结果算作本片新运行，也不将隔离 fixture 验收扩大为完整 UI 旅程。

定向 JVM 已验证 feature/release 两种原意图在 exit=137 后由默认 worker 接续，实际 branch POST 各 1 次、ref 仍为原 A，原 owner/session/attempt/seq0、Task 首次时间与顺序保持。feature 在 APPLIED 后再次 exit=137，第三 JVM 按 seq1 当前 sender 补原结果，投影 HTTP=0，当前 Task token 与三条 HTTP 事件保持。SEND 控制场景在到期后完成 4 次真实扫描，额外 HTTP=0、POST=0，原事实保持；对应 trace 保留在 first-green-jdk21/maven.log。

### 20.8 实现推送、远端首轮 429 与同 SHA 第二次运行

实现已按一个可评审提交 `00def779f82d4b10b13c547bacda08fd91706fc9`（`feat(run): resume original unsent branch intents`）保存并推送，父提交为 `8927dcf004ef6ff78e73ce2db663ccfa777b575d`，包含 17 个 backend 文件，1905 行新增、94 行删除。提交内容与最终已验源码一致，证据见 `implementation-commit.json`、`implementation-push.json`；本节文档尚待单独提交收口。

推送后远端仍仅 main、develop、feature/orchestration-consistency 三条分支；main/develop 同指 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，feature 指向实现提交。未将未完成的 B7 合入基线。

[Full-Link 44945748427](https://github.com/tongsh6/release-hub/actions/runs/44945748427) 已实际 dispatch，headSha 固定为完整 `00def779f82d4b10b13c547bacda08fd91706fc9`，创建时间为 2026-11-21T20:20:24Z（北京时间 2026-11-22 04:20:24）。attempt 1 已 completed/failure，job `136972559468` 的 e2e 失败、watchExitCode=1，updatedAt 为 2026-11-21T20:22:34Z，summaryLines 为空。实际失败在 backend Docker 的 `RUN mvn dependency:go-offline`：Maven Central 对 Spring Boot 3.4.1 与 Testcontainers 1.20.4 的 BOM 下载返回 429；后端尚未启动，`Run vertical slice tests` 被跳过。原结果、日志和实际步骤分析保留在 `full-link/attempt-1/result.json`、`full-link/attempt-1/136972559468.log`、`full-link/attempt-1/failure-analysis.json`，不能归类为业务断言失败。

已仅对同一 run/SHA 重跑失败 job 一次；`full-link/attempt-2/rerun.json` 记录 rerunAccepted=true、failedJobsOnly=true，`attempt-2/initial-run.json` 记录 runAttempt=2、status=queued、updatedAt=2026-11-21T20:25:10Z。第二次运行待实际结果，结论应独立记录于 `attempt-2/result.json`，不覆盖首轮失败事实，也不以本地 GREEN 或 B6 的 CI 替代本片远端验收。

B7 保持唯一 HEAD，待同 SHA 第二次 CI 的实际结果与文档收口，满足本片验收后才出队。CREATE_MR INTENT、已发送未知结果的受证据约束恢复、其他动作及完整 GitLab/UI 黄金旅程仍独立排队，C2 整体未完成。

### 20.9 固定实现 SHA 的远端通过与本片收口

20.1–20.8 保留各自发生时的事实，包含事前契约、本地验收、首轮 Central 429 及第二次运行待结果的记录。最终结果追加于本节，不改写这些历史。

[Full-Link 44945748427](https://github.com/tongsh6/release-hub/actions/runs/44945748427) attempt 2 已实际 completed/success，headSha 精确为 `00def779f82d4b10b13c547bacda08fd91706fc9`，watchExitCode=0；e2e job `136973868908` 为 success。业务摘要在 2026-11-21T20:33:36.1202917Z 记录 **Passed: 5 / Failed: 0**，运行 updatedAt 为 2026-11-21T20:34:01Z（北京时间 2026-11-22 04:34:01）。精确结果见 `.ai/reports/validation/c2-b7/full-link/attempt-2/result.json`；首轮结果和失败分析仍独立保留在 attempt-1，没有覆盖或重分类。

B7 已完成独立 RED→GREEN、91/91、单次840/840、API48/48、普通831（826 PASS/5既有SKIP）、静态、源码绑定、实现审阅/推送及本片固定完整SHA远端验收，标记 **DONE**。实现提交已推送；记录本节时，验收文档尚未提交；main/develop 仍同 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`。本片 UI 未改，前端217是B6已验源码树的历史结果，本片仅重跑lint/typecheck。Full-Link 5/5限于后端API与隔离GitLab切片，不扩大完整UI黄金旅程，也不扩写Slice4/5的清理或真实冲突覆盖。

下一唯一 HEAD 为 **C2-B8 正常 CREATE_MR 完整请求的发送证明闭环**，独立契约与正反例已收敛，源码及新测试尚未修改、待 RED。当前消费者涵盖 ENSURE_MR 及已有非 Task cleanup，需贯通 producer、Journal核准、Operation重建HTTP及B3/原结果消费者；CREATE_MR恢复保持关闭，历史B3 APPLIED不加新请求/hash门禁。B9原INTENT、SEND/UNKNOWN、其他动作和完整UI继续分别排队，SA-002顺序不变，C2整体未完成。

## 21. C2-B8：正常 CREATE_MR 完整请求的发送证明闭环

21.1–21.5 保留实施前独立契约：定义时生产基线为 B7 已验并推送的 `00def779f82d4b10b13c547bacda08fd91706fc9`，B8 为当时唯一 HEAD；根侧与独立 boundary 已按 producer、Journal、Operation、observer 及数据/API消费者收敛契约。记录这些定位、正反例和出口时，源码与新测试尚未修改、待独立 RED，不冒充运行结果。该契约随后随 `68b4fe1b2c77951ebde755c2645d3fc2f676f972` 提交；实际 RED、校准、实现和验证自21.6起追加。完整规范仍集中在原 design/Run delta/tasks，不另建计划。

### 21.1 根因与当前真实消费者

当前 GitLabMergeRequestAdapter.create 仅产生 source_branch/target_branch/title/remove_source_branch 四字段正文；interceptor 为新鲜opId追加description marker，但 CREATE_MR target 没有保存完整 title/description/删除源选项。begin 已返回 Operation，现只有 branch 消费返回target重建body；CREATE_MR 的核准值与真实发送体仍未形成完整证明。只增加存储字段，不能证明真实副作用来自已接纳命令。

| 现对象或路径 | 本片职责与实际消费者 |
|---|---|
| GitLabMergeRequestAdapter.create | 原位补description空字符串；同类typed canonical规则供interceptor/Journal两个真实消费者使用 |
| ENSURE_MR → adapter → interceptor → Journal → HTTP | 正常Task立即消费完整命令，B3响应继续交原Task检查点与后继 |
| executeCleanup → executeCleanupItem → executeMerge → GitLab mergeBranch → GitMergeWorkflow | 同一个create的现非Task消费者；taskFence=null仍须合法创建，不能无条件套Task专属约束 |
| Journal.begin/sending与现实体/session/guard | 承担实时typed请求/hash/marker和发送权，Operation只给核准值，不扩展示字段 |
| observer.findMergeRequest | description.lines().anyMatch(marker::equals)整行精确匹配，不是substring contains；FOUND不授权发送或完成 |

具体投影依据为已存在的 `.ai/reports/validation/c2-b7/regression-green-jdk21/xml/releasehub-bootstrap/TEST-io.releasehub.bootstrap.api.RunTaskProcessKillIT.xml`：`restartingAfterAppliedWriteProjectsWithoutAnotherHttpRequest(ActionType)[2]` 为 PASS。对应 `PROCESS_APPLIED_MR_RESUME` trace 的关键字段为 `exit=137 projectionHttp=0 branchPost=0 mrPost=1 mergePut=1 preservedToken=true preservedEvents=3 noPendingAtKill=false`。这是 B7 已完成回归中的 APPLIED 投影证据，不证明 CREATE_MR INTENT 恢复，也不是 B8 新运行的测试。

GitLab 17.11 条件依据沿用 `.ai/reports/validation/c2-b3/baseline/gitlab-17-11-mr-producer.json` 中保存的[官方 merge_requests.rb](https://gitlab.com/gitlab-org/gitlab/-/raw/v17.11.0-ee/lib/api/merge_requests.rb)，38180 bytes，SHA256 `42bf362c4196f6d6629a0962ec29c935fcad4badb1db4a12d237382efb225a0f`；本轮根侧/boundary 还只读复核 257–288、343–365、744/749 及 EE 扩展。该定位支持创建 POST 与 merge PUT 的 SHA 前置条件区别，不新增外部写或运行验收。

### 21.2 完整命令、marker与唯一实际发送体

入站严格五字段且typed：source_branch/target_branch/title非空白字符串（保持原值、不 trim），description为字符串、可为空字符串但不能null，remove_source_branch为布尔false。false来自共享feature不能擅删；原producer显式补description=""，缺/错/多字段拒绝，不保留四字段旧路径。canonical UTF-8 body按上述五字段固定次序，普通Unicode/引号/换行按原值保留。

入站description含reserved `ReleaseHub-Operation:`前缀即拒绝，不静默清洗或去重。现interceptor对新鲜UUID opId追加唯一独立末行 `\nReleaseHub-Operation: <id>`；opId非空白且不含CR/LF，保持原值、不trim，recorded marker完整独立、精确对应本opId且前缀只出现一次，与observer整行匹配一致。完整target严格八字段：method=POST、suffix=/merge_requests、五正文域、sha。sha严格40/64位hex且保留字节与大小写；任何query包括空?均拒绝。

`requestHash = SHA256("POST|" + remoteKey + "|/merge_requests||" + SHA256(canonicalBodyUtf8) + "|" + expectedSha)`

begin继续返回已有Operation；interceptor核候选id/action/repo/remote/provider/base/project/target/run/operator、INTENT_RECORDED/null reason，再从返回target重建真实HTTP body。Operation没有hash/session/fence，真实校验留在Journal当前实体与锁内；不为比较或展示增字段，不加expectedVersion发送门禁，快照不授权。MERGE既有hash规则不变。

### 21.3 实时发送权、恢复禁区与最强反例

begin与当前owner sending均核typed请求/hash/marker；sending还须op.reason=null、GITLAB/canonical base/正整数project与remoteKey自洽、真实session/guard。Task追加原ENSURE_MR计划source/target、consistentProject、当前fence、原历史owner、同attempt唯一seq0、无SEND/自身Step及匹配INTENT_PENDING guard。非Task cleanup同样核完整请求与session/guard，但不套Task专属限制。begin后当前owner记录被破坏也须实时拒绝，guard合法不替代实体证明；复用现发送事务，无额外GET、无网络持锁。

Journal admission/sending自身事务失败时，仅回滚该未提交事务内的新变化，零非法SEND/POST；sending反例必须真正经过seq1/guard暂写后抛错，不以前置拒绝冒充回滚。已通过REQUIRES_NEW独立提交的INTENT/SEND/响应事实，不随外层业务回滚消失；Task沿既有结果确认/APPLIED投影边界，非Task沿既有会话收口保持必要guard。已知APPLIED不能改说远端UNKNOWN，也不能因回滚自动重放。

prepareUnsentTaskClaim、validUnsentOperation恢复白名单、RunJpa接管、Context resume快照、NOT_SENT均不扩大到CREATE_MR。完整INTENT到期仍须0 token换权/0 SEND/0 Step/0 POST；B9另行定义workflow按原A决策及真实本地结果，不能提前授权恢复。

最强反例是本地完整记录source A，但创建POST时source已前进到B。GitLab17.11创建POST无sha-CAS，merge PUT才有SHA前置条件；本片sha是本地意图与响应证明。响应B仍UNKNOWN、保留guard、不建CREATED检查点、不启动TRY。历史B3三字段target/任意hash的APPLIED仍只沿原201/三处project身份/source-target/SHA/IID/URL响应证明纯DB投影，禁止追加新完整请求/hash门禁。

### 21.4 数据、API与既有测试契约变化

现 Task RunAppService 和非Task cleanup 的title都固定生成 `Merge <source> into <target>`；唯一GitLab MR POST没有description参数，本片显式空字符串加marker，没有新增自由正文输入。既有target_json TEXT可承载完整支持的请求。GitOperationController的repo/run列表及详情直接序列化targetJson，因此现字段将返回完整受支持命令，不能称仅DB内部或API数据完全不变；正常查询需认证但没有仓库级/操作者级过滤，不声称只有创建者可见，不在B8扩RBAC。GitOperationsPanel当前不渲染targetJson，没有新增UI入口。

旧 GitWriteHttpInterceptorTest.shouldCorrelateRequestWithoutPersistingTokenOrBodyContent（基线558–579行）曾断言command不含MR标题；本片必须有依据地改为完整请求可审计，同时保留token/认证header/任意无关正文及响应证据隐私约束，不泛化保存任意body或secret。不能声称所有旧测试仅机械迁移或所有断言不变；独立RED一旦冻结仍须保留其正反例契约。这是现数据/测试契约调整，不引入新实体/API入口。

### 21.5 独立正反例与验收出口

| 事前场景 | 预期出口 |
|---|---|
| Task ENSURE_MR与taskFence=null cleanup真实HTTP/Journal | target/hash/body/marker一致，B3/原结果下游继续工作，非Task不误拒绝 |
| 字段缺错多、错误hash、任意query | 零SEND/POST，不默补、不保留四字段旧路径 |
| foreign/重复marker、opId空白或含CR/LF、错误末行；合法Unicode/引号/换行 | 非法拒绝，合法原值保留且能被observer整行匹配 |
| Journal返回错误id/target/其他可见身份 | interceptor零SEND/POST，不信任错误返回 |
| begin后当前owner记录破坏、旧fence/owner、错误session/guard/历史归属 | sending锁内实时拒绝，快照或guard合法不掩盖实体错误 |
| 真实PG锁竞争、Journal admission/sending自身事务失败、事件/自身Step污染 | 零非法SEND/POST；仅回滚当前未提交事务的新变化，sending实际经过seq1/guard暂写后抛错，不以前置拒绝冒充 |
| 外层业务回滚且INTENT/SEND/响应已由REQUIRES_NEW独立提交 | 保留原事实；Task沿既有结果确认/APPLIED投影，非Task会话收口保留必要guard，不把已知APPLIED改说远端UNKNOWN，不自动重放 |
| 完整CREATE_MR INTENT到期 | PG验证无接管、无token换权、无SEND/Step/POST |
| source A→响应B；历史B3 APPLIED/现独立进程trace | UNKNOWN/guard/无TRY和原纯DB成功投影同时保持 |
| targetJson API与隐私断言 | 返回完整支持命令，认证/secret/无关正文和响应隐私约束保留，无新UI入口 |

先独立RED再改生产；完成真实HTTP/PG、现非Task消费者、既有APPLIED进程回归、相关/API/静态TopN、源码绑定、review，再分组提交推送并以本片固定SHA CI验收。无新实体/表/schema/DTO/Port/执行器/缓存/用户入口。回滚停派发、保留原事实，不清数据、不重发未知，不扩大完整UI；B9、SEND/UNKNOWN、其他动作和完整UI仍独立排队，SA-002顺序不变。

### 21.6 冻结生产基线的独立 RED 与一次契约校准

21.1–21.5 是实施前契约；实际 RED 从完整提交 `68b4fe1b2c77951ebde755c2645d3fc2f676f972` 导出冻结 archive，仅覆盖对应独立测试文件。生产修改在独立反例运行之后进行，验证根目录为 `.ai/reports/validation/c2-b8/`。

| 范围 | 修复前实际结果 | 证据（相对验证根目录） |
|---|---|---|
| Interceptor 单元 | 46 tests / 44 FAIL / 2 PASS / 0 ERROR / 0 SKIP | red-unit-frozen-jdk21/result.json |
| 真实 Spring/HTTP/PG | 9 tests / 7 FAIL / 2 PASS / 0 ERROR / 0 SKIP | red-http-frozen-jdk21-r3/result.json |
| PostgreSQL 初始契约 | 69 tests / 57 FAIL / 12 PASS / 0 ERROR / 0 SKIP | red-pg-frozen-jdk21/result.json |
| PostgreSQL 校准后契约 | 70 tests / 56 FAIL / 14 PASS / 0 ERROR / 0 SKIP | red-pg-calibrated-frozen-jdk21/result.json |

四份结果均为 Maven exitCode=1、sourceStable=true、rootProductionStable=true。校准后有效独立集合为 **125 tests / 107 FAIL / 18 PASS**，0 ERROR/SKIP；原 PG 69 项不再与校准后 70 项重复累计。HTTP 前两次尝试因新增测试的 MergeStatus 类型限定错误未进入用例，原日志保留在 red-http-frozen-jdk21/maven.log 和 red-http-frozen-jdk21-r2/maven.log，不算产品 RED；r3 才取得上述 9 项业务断言结果。

PG 初始一个新反例把“完整 CREATE_MR INTENT 不自动恢复”扩大为“原合法 owner 也不得明确上报 UNKNOWN”，这个假设过强。经根侧授权，拆开自动扫描与 owner 的显式结果报告：到期自动扫描仍不换 token、不 SEND、不写 Step、不 POST；当前原 owner 可明确记录 UNKNOWN Step，同时原 INTENT/attempt/HTTP 事件不变，guard 保留 REMOTE_RESULT_UNKNOWN，后继及自动重发继续阻断。这不是 NOT_SENT 收口，不释放 guard，也不赋予接续权。

该次新 RED 契约校准已独立留在 `pg-contract-calibration.patch` / `pg-contract-calibration.json`，校准时生产代码未变，patch SHA256 为 `e14ebd558c8b6e6952b4ad2fb67978e72f1c7db719b9d0d629306283ebfd725c`。不能称全部最初的新断言未改变；后续以校准后的 125 项契约为基准。`test-contract-preservation.json` 已逐字核对单元 216 行、HTTP 241 行和校准 PG 604 行独立插入块全部保留，原 69 项失败证据没有覆盖。

### 21.7 已实现的正常发送证明与保留边界

本片修改 3 个生产文件及 3 个测试文件；根侧已完成独立完整源码审阅。实际责任仍在原生产链，不增加实体、表、schema、DTO、Port、执行器或用户入口。

| 既有职责 | 当前实现及消费者 |
|---|---|
| GitLabMergeRequestAdapter | 原 producer 显式给出 description 空串；同类定义严格五字段 typed canonical 请求、八字段完整 target、唯一 marker 和指纹规则，由现 interceptor/Journal 共同消费 |
| GitWriteHttpInterceptor | 拒绝缺错多字段、任意 query 和入站 reserved marker；核 begin 返回的原候选身份、状态及完整 target，按返回 Operation 重建实际 POST，保留原 SHA 字节与正文值 |
| GitOperationPersistenceAdapter | begin 与当前 owner sending 均在现有锁/事务内核完整请求、hash、marker、实体身份、session/guard；Task 再核原计划、当前 fence/历史 owner、唯一 seq0、无 SEND/自身 Step，非 Task 不套 Task metadata |
| 原 Task 与非 Task 消费者 | ENSURE_MR 接原 CREATED 检查点和 TRY；现 cleanup 的 mergeBranch 组合入口在 taskFence=null 时同样完整发送，继续消费 B3 响应与原 MergeResult |

完整请求中的 sha 仍是本地意图和响应证明，不是创建 POST 的原子 SHA 条件。读 A 而 201 返回 B，Task 与非 Task 实际 HTTP 对照均保持 UNKNOWN/guard，不建立 CREATED 检查点、不进入后继 TRY。原有历史 B3 APPLIED 按完整响应投影的资格不增加新完整请求/hash 限制。

CREATE_MR 的 prepare/takeover/Context resume 和 NOT_SENT 范围仍关闭。当前 owner 明确报告 UNKNOWN 可保留原 INTENT 和 guard，不能误写成自动恢复或本地安全收口。Journal 自身事务失败只回滚该未提交事务的新变化；PG 发送回滚实际经过 seq1/guard 暂写后抛错。REQUIRES_NEW 已独立提交的 INTENT/SEND/响应不随外层业务回滚消失，已知 APPLIED 不改说远端 UNKNOWN，也不能自动重放。

当前 Task/cleanup 的 title 仍为分支名模板，description 仍为空串加受控 marker，无自由正文入口。既有 targetJson 查询字段会返回完整受支持请求；正常查询需认证，但现有 Controller/service 没有仓库级或操作者级过滤，本片不增加 RBAC。UI 不新增 targetJson 展示。token、认证 header、任意额外正文及响应隐私约束保留，不泛化保存任意请求。

### 21.8 首次 GREEN、旧夹具迁移及相关回归

`first-green-jdk21/result.json` 已实际通过 **125/125**：单元 46、真实 HTTP 9、PG 70，0 FAIL/ERROR/SKIP；495 个 backend 文件在该次运行前后不变。HTTP 正常 Task 与现非 Task mergeBranch 各覆盖 40/64 位 SHA，独立计算 remoteKey/body/hash 并核实际 POST，真实 CREATED 检查点与后继 MERGE 一并执行；两条 A→B 对照不靠 mock adapter 替代。

旧 Interceptor 用例的合法发送夹具原位补完整五字段，保持 admission/sending/丢失响应/不完整响应原断言可达。旧“标题不存”断言按21.4的事前决定改为完整支持请求可审计，token、内部 header、无关正文和响应证据隐私断言保留；这不是全部旧断言仅机械迁移，详情见 `unit-contract-preservation.json`。

首次 GREEN 之后，唯一 backend 差异是 RunTaskPersistenceIT 中旧 `recordAppliedCreatedMr` fixture：先经合法完整新请求的 begin/sending/finish 形成 APPLIED，再只在测试 SQL 中恢复历史三字段 target 和旧 hash。这样既不让新发送绕过门禁，也继续验证 B3 历史 APPLIED 投影不依赖新请求/hash；生产没有新增兼容分支。该调整没有改动任何校准后独立测试块。

随后 `regression-green-jdk21/result.json` 单次 Maven 的 **13 suites / 965 tests 全部通过**，0 FAIL/ERROR/SKIP；495 文件 before/after 稳定，包含全部 125 个新用例再次通过。分布为 Workflow48、RunOrchestration29、Journal8、HTTP165、PG368、独立 JVM15、BranchAdapter55、MergeRequestAdapter108、ReleaseEvidence42、BeanDecorator6、Interceptor111、RepositoryScope3、Worker7。独立 JVM 是既有进程场景回归，不把 APPLIED 纯 DB 投影扩写为 CREATE_MR INTENT 恢复；定向、相关、API 和普通套件存在重叠，不累加为互异场景数。

首次 GREEN 与最终回归树存在上述一个旧 fixture 文件差异，各次运行自身源码稳定；不能写成所有阶段的源码快照完全相同。

### 21.9 本地后续门禁与当前交付状态

| 门禁 | 本次记录时已发生的结果 | 证据（相对验证根目录） |
|---|---|---|
| API | 48/48，0 FAIL/ERROR/SKIP，495 文件 sourceStable=true | final-gates/api/result.json |
| 普通后端 | 沿 B7 既有 58-suite inventory 运行 877 项：872 PASS、5 既有 SKIP，0 FAIL/ERROR，495 文件 sourceStable=true；58-suite inventory 与 5 个 SKIP 身份均与 B7 一致 | final-gates/ordinary/result.json |
| 静态 TopN | 六份本轮新鲜 SpotBugs XML 均0、typecheck PASS、lint 0 errors/8既有fixture warnings，逐项核源码并保留理由 | `.ai/reports/static-scan/20261122-093043/summary.md` |
| 最终源码绑定 | 相关/API/普通/静态前后八份backend快照的495文件完全一致；相关965中抽取125新例全部PASS | local-evidence-audit.json |
| 实现提交/推送、固定 SHA CI | 尚待实际证据，未以 B7 的提交或 CI 代替 | 待本片收口 |

静态 TopN 已读取两份现有 stub 并逐项记录8条 warning保留原因，未关闭规则；原始 lint 与 B7 逐字节一致，SHA256 为 `b5496961a4422b7ea5154e6aead19f4c751cd8e76575656cf0b4b92a6fea0faa`。前端222文件与B7相同，B7已绑定B6已验217的源码树；本片未重复运行UI套件，仅新跑lint/typecheck。

本节记录时 **B8 仍为唯一 HEAD，尚未 DONE**。本地门禁与源码绑定已完成，分组提交/推送和本片固定 SHA CI 仍待实际结果，完整 GitLab/UI 黄金旅程未在本片新增验收；B9 原 CREATE_MR INTENT、SEND/UNKNOWN、其他动作和完整 UI 按原队列继续，SA-002 顺序不变。

### 21.10 实现分组推送与固定 SHA 远端验收启动

21.9 保留本地门禁完成时尚未取得提交/推送/CI证据的事实；本节追加随后实际发生的交付步骤，不覆盖前面的历史记录。

实现已提交为 `537588e782c2b3bb2d2e82e21804f2d37fb42fc2`（`feat(run): prove complete MR creation commands before sending`），parent 为 `68b4fe1b2c77951ebde755c2645d3fc2f676f972`。本提交仅含3个生产与3个测试文件，共1216行新增、28行删除，已验证代码与提交一致；验收文档单独收口。精确绑定见 `implementation-commit.json`。

首次 push 在默认 osxkeychain 凭据链路等待90秒后超时。根侧先核远端 feature 仍为旧 `68b4fe1`、无残余 git 进程，再用现有已认证的 tongsh6 gh credential helper 仅覆盖本次命令重试，3.8秒成功；没有修改 hooks 或持久凭据配置。首次超时与核实/重试证据分别保留于 `push-timeout-recovery.json`、`push-auth-hook-probes.json`、`implementation-push.json`，不把首次无结果当作已推送。

成功后 ls-remote 明确确认远端仅 main、develop、feature/orchestration-consistency 三条分支；main/develop 仍同 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`，feature 精确指向 `537588e782c2b3bb2d2e82e21804f2d37fb42fc2`，未将本片提前合入基线。

[Full-Link 44972473174](https://github.com/tongsh6/release-hub/actions/runs/44972473174) 已实际 dispatch，headSha 固定为完整 `537588e782c2b3bb2d2e82e21804f2d37fb42fc2`，createdAt为2026-11-22T01:39:32Z（北京时间09:39:32）。`full-link/dispatch.json` 与 `full-link/run.json` 记录此次触发及初始 in_progress 状态，既有 watch 正在观察该运行；本节记录时尚未取得最终业务摘要或通过结果。

B8继续保持唯一HEAD，等待本片固定SHA远端结果与验收文档收口，不以本地GREEN或B7的5/5提前标DONE。B9仅开展独立只读边界核对，生产未因B9修改；CREATE_MR原INTENT、SEND/UNKNOWN及完整UI仍按原队列分别验收。

### 21.11 固定实现 SHA 的最终远端验收与 B8 收口

21.1–21.10 保留各自发生时的事实，包括独立契约、一次PG校准、各轮本地证据、首次push超时及CI待结果状态；最终结果追加于本节，不覆盖21.9/21.10的当时记录。

[Full-Link 44972473174](https://github.com/tongsh6/release-hub/actions/runs/44972473174) attempt 1 已实际 **completed/success**，headSha 精确为 `537588e782c2b3bb2d2e82e21804f2d37fb42fc2`，watchExitCode=0。运行 updatedAt 为2026-11-22T01:49:21Z（北京时间09:49:21），e2e job `137055046607` 及其全部步骤均为success。精确状态、步骤和artifact元数据见 `.ai/reports/validation/c2-b8/full-link/result.json`。

`full-link/business-result.json` 从已保存的真实 `full-link/job.log` 提取出以下业务结果；Results摘要同样列出五项✅，并在2026-11-22T01:48:59.3389516Z明确输出 `✅ Passed: 5 ❌ Failed: 0`。业务证据已记录passedSlices=5、failedSlices=0及sliceIds 1–5，不是只凭GitHub job绿色判断业务通过。

| 现有切片 | 日志通过时间（2026-11-22，UTC） | 业务结果 |
|---|---|---|
| Slice 1: Group + Window Lifecycle | 01:47:10 | PASSED |
| Slice 2: Repo + Iter + BranchRule | 01:47:29 | PASSED |
| Slice 3: Release Orchestration | 01:48:17 | PASSED |
| Slice 4: Post-Release Cleanup | 01:48:37 | PASSED |
| Slice 5: Conflict Detection | 01:48:59 | PASSED |

留存的 `job.log` 为489215 bytes，SHA256为 `897b9d95c60ff6f6aa48486d4cc6ba7152d3938d396e6b5f278e124511db6530`，与业务提取证据一致。运行返回的artifact元数据为 `14734481532 / e2e-logs / 682051 bytes`。本次远端验收限于后端API＋隔离GitLab五个既有切片，不扩大为完整UI黄金旅程，也不从Slice4/5的名称推断完整真实收尾或合并冲突覆盖。

B8已完成独立RED与明确PG校准、125/125、单次965/965、API48/48、普通877（872 PASS/5既有SKIP）、静态、最终源码绑定、审阅、实现分组提交/推送及本片固定完整SHA远端验收，标记 **DONE**。验收文档单独收口；main/develop仍同 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`。首次GREEN与最终树的一处旧fixture差异、8条既有warning、前端217为B6历史结果等边界继续按21.8–21.9保留。

下一唯一HEAD为 **C2-B9 原 CREATE_MR INTENT 的安全接续与本地收口**。截至本节仅开展独立只读边界分析，B9源码和新测试尚未修改，不把B8完整正常请求证明当作恢复授权。当前prepare/takeover/Context resume/NOT_SENT仍关闭CREATE_MR，历史B3 APPLIED投影不追加新请求/hash门禁；B9需独立定义原A决策、原意图资格与真实本地结果。SEND/UNKNOWN、其他动作和完整UI继续按原队列独立验收，SA-002顺序不变，C2整体未完成。

## 22. C2-B9：原 CREATE_MR INTENT 的安全接续与本地收口

### 22.1 独立目标、基线与反例

本节记录B9的事前契约，不是测试运行或实现完成证据。B8代码 `537588e782c2b3bb2d2e82e21804f2d37fb42fc2` 与验收文档 `8cfc294c58f0ae53817a44565801936888a03bb1` 已分阶段推送；本片以8cfc294为定位基线，仍是唯一HEAD。详细规范与待验清单同步到既有design、Run spec、tasks，实际RED、必要校准及验收结果另行追加。

首要问题是续接“原项目I、期望SHA A的命令”，不能把当前source已为B重新解释成原意图。反过来，source前进或删除也不能一概阻断：target已包含A时无需再查source，唯一opened MR的详情仍为原I/source/target/A时可以复用该检查点。最强反例仍是GitLab创建POST无sha-CAS：即使预检读A，实际响应B也只能UNKNOWN/保留guard、不能CREATED或进入TRY。快照完整不等于发送权，当前fence和数据库内完整事实须分别成立。

### 22.2 已有职责与定位依据

| 既有位置 | 核实后的责任与本片消费者 |
|---|---|
| RunJpa领取、Journal.prepareUnsentTaskClaim、现TaskClaim/Context | 原Run→Task→remote锁序内核资格并给原Task换权；已有Operation快照只传值，无新候选扫描/执行器 |
| GitMergeRequestPort.SourceRevision；GitMergeWorkflow.ensureMergeRequest与merge；GitLabGitBranchAdapter.ensureMergeRequest | 已有SourceRevision(projectId,sha)承载原I/A；现workflow.merge和adapter.ensure两个真实调用者直接迁移到新增第4个nullable参数，fresh/cleanup传null |
| GitLabMergeRequestAdapter.read / GitWriteHttpInterceptor | 原始三个项目身份须在映射前核验；prepared复用原id/body/marker/hash，实际POST从begin返回Operation重建 |
| Journal.sending/finish/ack、原Task结果事务与RunPort metadata | 实时发送权与真实seq1 sender由Journal承责；原检查点、TRY、队列消费结果，git.requestId继续表示已有远端请求证据 |

定位已读现workflow中merge调用和ensure声明（25–38）、adapter调用（105–107）、SourceRevision定义（11）及RunPort写git.requestId的现消费者（40、66）；根侧另行独立核对RunJpa、Context与Journal方法边界。这里列的是契约来源，不声称已运行B9测试。

### 22.3 完整资格、换权与历史事实

仅原ENSURE_MR对应的CREATE_MR进入本片：同Task恰一operation、同operation/attempt唯一合法seq0、INTENT_RECORDED且reason=null、无SEND和自身Step；B8严格八字段typed target/canonical hash/唯一原marker，以及原repo/remote/GITLAB/base/正整数项目I、run/operator、历史owner/session/claimToken/generation、原计划source-target/consistentProject与INTENT_PENDING guard全部成立。旧摘要、缺损、额外事实或不匹配guard不得自动补齐。

lease只决定换权；当前fence由原锁序下的实体事实核准。接管只换Task当前token/lease与op观察version，保留原id/attempt/session/claimToken/generation/seq0、首次时间和已有实际顺序。seq1记真实新sender，finish/ack/APPLIED投影核seq1；不能改写历史owner，也不能用snapshot.version或一次准备替代sending实时核准。

### 22.4 原I/A预检、发送和本地结果

adapter必须先固定原I，再传已有SourceRevision(I,A)。prepared顺序为：contains(target,A)确认true即idless ALREADY_MERGED且零source/list/detail/POST；确认false才看完整opened列表，唯一候选详情I/I/I、原source/target、A、OPENED才MR_REUSED；只有确空列表才readSource，真实I/A才续发原POST。列表不完整、多候选、详情不符、source B/删除/读取未知均零POST，权限按既有blocked分类。重用A的MR不要求当前source仍为A，TRY独立重读并以A约束merge。

read在映射现MergeRequest前核原始三个数值项目身份；prepared失败context保原I/source/target/A，不能借B或foreign的iid/url/mergeSha/disposition。SHA比较ignoreCase，但新prepared检查点保原A字面，原target/hash/body字节不改。fresh/cleanup原mergeSha补充与B3已SEND后failedWrite的部分IID诊断保留。interceptor复用原id和唯一marker，从begin返回Operation重建正文；snapshot不授权，无网络持锁。

完整noSEND可按实际MR_REUSED/ALREADY_MERGED/FAILED/MERGE_BLOCKED/UNKNOWN同事务NOT_SENT：MR_REUSED须真实MR检查点，ALREADY_MERGED允许无IID/URL，UNKNOWN允许现消费者最小blockReason patch，任何已提供身份不得矛盾；CREATED或非空git.requestId拒绝本地收口。NOT_SENT event、Task/Step/metadata、队列/绑定/Run及匹配guard释放共同提交/回滚。Task UNKNOWN仍禁后继和普通retry；SEND无seq2不得提前本地UNKNOWN，真实seq2 UNKNOWN保guard。REQUIRES_NEW已提交的INTENT/SEND/响应不随外层回滚消失，不把已知APPLIED改成远端UNKNOWN。

历史B3三字段target/任意hash APPLIED继续原201/三处project/source-target/SHA/IID/URL响应投影，不新增完整请求/hash门禁。原投影经ignoreCase验证后保留响应SHA字面，不强制改成原A；真实新sender仍须seq1证明。非Task cleanup保留B8正常发送能力，不扩大接管。

### 22.5 独立正反例与明确迁移

| 正反例 | 事前判据 |
|---|---|
| 原I/A，target已含A；source已B或删除 | idless ALREADY_MERGED、原A检查点、source/list/detail/POST均0，不为补字段增加查询 |
| target不含A，唯一opened详情I/I/I/source-target/A/OPENED；当前source已B | MR_REUSED、原A与有效IID/URL；创建POST0，TRY独立重读并sha=A |
| 完整空列表后真实source I/A；I→J、A→B/删除/未知或权限对照 | 仅I/A可原POST；其他零POST，真实分类/原context保持，不把失败当空 |
| 完整资格逐项损坏、旧摘要、错owner/fence/guard、额外op/event/自身Step | 零非法换权/SEND/POST，不补造历史；begin后的破坏由sending重核 |
| noSEND五种真实结果、最小UNKNOWN patch、错误身份/CREATED/requestId | 合法形状NOT_SENT，非法形状拒绝；Task UNKNOWN继续阻断后继和普通retry |
| PG锁竞争、真实CAS=0、结果事务实际写入后抛错、SEND与NOT_SENT竞争 | 不双赢、不留半条收口；当前未提交变化回滚，独立提交历史保留 |
| INTENT kill→新owner SEND→APPLIED再次kill→第三JVM投影 | 原A/body/marker保持，MR POST总数1；APPLIED投影HTTP0，seq1记录真实sender；本地NOT_SENT独立场景POST0 |
| 历史B3 APPLIED、B8非Task、GET→POST响应B | 原响应投影与正常非Task仍成立；响应B仍UNKNOWN/guard/无TRY，不扩大恢复或UI结论 |

B8两条阶段负控必须有依据迁移：合法完整INTENT到期由“不接管”转为安全接管正例；原owner显式noSEND UNKNOWN由“保留INTENT/guard”转为NOT_SENT原子结果。损坏、旧摘要、SEND/UNKNOWN禁区继续保持。记录21的历史结果不改写，也不声称所有旧断言只是机械迁移。

### 22.6 独立RED的源码准备与验收边界

workflow测试如需新增参数才能编译，仅在8cfc294冻结导出树做三处无行为API适配：ensure声明增加第4个nullable SourceRevision参数而旧逻辑忽略它，两个现有生产调用补null；原工作区生产不因此修改，不留兼容overload。该RED树不是纯测试overlay，必须保留精确适配patch、基线和源码绑定；PG/HTTP可直接用原冻结生产。编译失败不算行为RED，实际运行结果和必要校准以后另记。

后续须完成独立RED→实现GREEN、workflow/真实HTTP/PG/多JVM、原检查点/TRY/队列/retry/API回归，以及相关/API/普通后端/静态TopN、源码绑定和审阅，再分组commit/push、固定完整SHA CI验收。契约本身不代表这些步骤通过，B9在实际证据齐备前保持HEAD。

无新entity/table/schema/DTO/Port/executor/cache/UI或自由正文入口，复用既有targetJson与结果消费者。回滚停派发并保留原事实，不清数据、不重发SEND未知；S3整体、后续SEND/UNKNOWN/其他动作与完整UI仍需独立验收，SA-002顺序不变。

### 22.7 六层冻结RED及其证据边界

实际证据见 `.ai/reports/validation/c2-b9/red-evidence-audit-final.json`。下列六层均针对 `8cfc294c58f0ae53817a44565801936888a03bb1` 冻结生产基线验行为，合计106例：81 FAIL、25正控PASS，最终有效轮次均0 ERROR/0 SKIP。workflow导出树包含22.6约定的三处无行为签名适配，旧逻辑忽略新增参数；其他五层没有生产适配，不把整个RED树称作纯测试overlay。

| 层与证据目录（c2-b9下） | 例数 | assertion FAIL | 正控PASS |
|---|---:|---:|---:|
| workflow / red-workflow-detail-frozen-jdk21 | 19 | 17 | 2 |
| detail / red-detail-frozen-jdk21 | 10 | 8 | 2 |
| interceptor / red-interceptor-frozen-jdk21 | 12 | 7 | 5 |
| HTTP / red-http-frozen-jdk21 | 14 | 14 | 0 |
| PostgreSQL / red-pg-frozen-jdk21-delimiter-fixed | 45 | 31 | 14 |
| 多JVM / red-process-frozen-jdk21 | 6 | 4 | 2 |

首次workflow包装器期望跨application/infrastructure共29例，但Maven reactor fail-fast实际只执行19个workflow例并跳过下游模块，原expectedRed=false保留；另一次独立detail运行补齐10例。首次PG运行45例为29 assertion FAIL、2 SQL fixture ERROR、14正控PASS；仅修复两个触发器fixture的四个美元定界字符后，在同一冻结基线得到31 FAIL/14 PASS。不能将首轮SQL错误算成产品RED或覆盖失败日志。多JVM四个FAIL是原ENSURE_MR仍RUNNING、TRY仍PENDING，已经通过启动及前序分支任务，不是启动错误；两个SEND/UNKNOWN正控PASS。

### 22.8 分类校准、首次核心GREEN与消费者核对

按实际RunAppService.ensureTaskMergeRequest消费者，FAILED只接受blockReason为null或UNKNOWN，MERGE_BLOCKED必须为合法且非UNKNOWN的枚举，UNKNOWN必须为UNKNOWN。PG一条FAILED正例的reason由PERMISSION_DENIED校准为UNKNOWN；这是明确的契约样本修正，不宣称所有新断言未变。随后独立新增FAILED/PERMISSION_DENIED、MERGE_BLOCKED/UNKNOWN、MERGE_BLOCKED/null三个反例，在初版B9宽松实现上得到3 FAIL/0 ERROR/0 SKIP（`red-classification-refinement-jdk21`）。该增量RED针对当时B9实现及其source-before快照，不能混称为8cfc294生产上的第107–109例。

首次名为 `green-independent-100-jdk21` 的尝试实际因workflow替换残留额外右花括号而编译失败，执行0例，不能算GREEN；只去掉该括号，保留原失败证据。分类收紧后的 `green-independent-103-jdk21/result.json` 实际103/103 PASS、0 FAIL/ERROR/SKIP：workflow19、detail10、interceptor12、HTTP14、PG48；该次495文件前后快照稳定。103例不包括后续多JVM六例，不把不同阶段源码说成完全相同。

审计记录workflow、detail、interceptor、HTTP、process五份独立测试文件的RED与当前hash相同；PG文件有已记载的定界修复、分类正例校准及三个增量反例。B8两项阶段负控在B9独立GREEN后按22.5契约迁移：完整到期INTENT安全接管、原owner真实noSEND UNKNOWN原子NOT_SENT，Task仍UNKNOWN。历史结果和其他损坏/SEND/UNKNOWN禁区保留。

ENSURE_MR本地结果消费者复用了原Task结果事务；真实minimal failure patch及显式null删除值得到区分，非空requestId/mergeSha、失败中的非空IID/URL/disposition和外来身份不被接受。对22.4中“队列/绑定/Run”的表述补充动作边界：ENSURE_MR不新增binding写入，验收核的是前序binding事实保持；本片不声称制造了ENSURE_MR binding写入再验证其回滚。实际原子写入范围是NOT_SENT event、Task/Step/metadata、队列/Run及匹配guard，既有REQUIRES_NEW事实仍独立保留。

### 22.9 首次相关回归1074与步骤顺序验收阻断

`.ai/reports/validation/c2-b9/regression-green-jdk21/result.json` 虽沿用该目录名，实际单次13 suites为1074例、1073 PASS/1 FAIL、0 ERROR/SKIP，passed=false；495文件前后快照稳定。RunTaskProcessKillIT的21例全部PASS，包含六个B9新增强杀/重启例；这不等于整组相关回归通过。

唯一失败为 `GitWriteSafetyIT.createMrIntentResumesOriginalPostAndRecordsCurrentSender(int)[2]`（64位SHA），共享helper按steps位置2期望CREATED却读到ALREADY_EXISTS。源码核实RunItemJpaEntity.steps是未声明排序的ElementCollection，RunJpaPersistenceAdapter.toDomainItem直接沿集合顺序映射。真实消费者BlockBoard.vue:100取最后step的message作为阻塞原因，RunDrawer/RunDetail按steps顺序展示，因此这是生产读取/投影责任缺失，不能仅改变测试为按action检索或重试到偶然通过。

截至本节，排序修复尚未验收，API/普通后端/静态/实现提交推送与固定SHA CI也没有本片新的完成结果；B9继续HEAD，不出队。首次1074失败、103核心GREEN及冻结RED分别保留。

### 22.10 步骤顺序的追加事前契约

先查重再确定责任：V10的run_step没有id，不引入假定存在的只读自增id映射；现RunStepJpaEmbeddable已有taskId，RunTaskJpaEntity已有taskOrder。原Run持久化映射统一承担排序，既有API的steps数组顺序有意纠正，字段不变，BlockBoard/RunDrawer/RunDetail及原结果返回立即消费；无新entity/schema/字段/DTO/API或前端排序分支。

每批Runs先汇集关联taskId，复用现JpaRepository.findAllById一次读取；无关联id则零额外query，局部Map仅用于本次映射，不缓存。先核Task与Step的run、targetType=RUN_ITEM、targetId及actionType/taskType一致，核准关联步骤按taskOrder在前；无关联、关联缺失或身份不符的步骤保留在后，按startAt/endAt作有限退化。completeTask复用已加载ordered任务列表，不制造逐Run/Item/Step查询。

最强反例是两个关联步骤K1/K2的taskOrder正序、时间戳逆序，另一个无关联步骤U的时间戳居中：“双方有关联比taskOrder，否则比时间”会形成K1<K2<U<K1的比较环。必须先分组再排序，外来run/item/action的Task不得参与顺序证明。无Task且时间戳相同的历史步骤无法据现有数据还原真实先后，明确保留这一边界，不添字段制造假事实。

独立验收限定四个已有职责内的正反例：关联taskOrder胜过集合/时间序，归属污染不能借序，混合与无Task退化不丢步骤且比较传递，批量读取/零关联/completeTask复用不产生N+1。先冻结这些判据与RED，再改生产排序；此处仅是追加契约，不是排序GREEN或1074已修复的结论。

### 22.11 步骤顺序的独立RED、编译修正与GREEN

本节续接22.9–22.10，不改写首次1074失败或当时的事前契约。`.ai/reports/validation/c2-b9/red-step-order-frozen-jdk21/result.json` 固定8cfc294生产、仅overlay既有PG测试文件，实际4例均为断言FAIL、0 ERROR/SKIP，14.8秒；导出树及根工作区生产源码稳定，没有生产适配缝或先改实现再回填RED。

首次 `green-step-order-jdk21/result.json` 实际执行0例、exitCode=1：lambda参数task遮蔽了completeTask已有局部变量，属于编译失败，不能计GREEN或产品RED。保留该尝试后，只把该参数改为value；`green-step-order-compile-fixed-jdk21/result.json` 为4/4 PASS、0 FAIL/ERROR/SKIP，13.3秒，495文件前后快照稳定。

四个展开例分别是 `runStepOrderUsesTaskOrderDespiteControlledLoadedPermutationAndResultTimes` 的SAME_TIME/REVERSED_TIME两例、`runStepOrderRetainsRepeatedCleanupActionsWithoutInventingTasks`、`runStepOrderKeepsForeignLinkedFactsAfterKnownTasksWithoutBorrowingTheirOrder`。它们经真实PG写入、重新加载原Step/Task事实，在回滚事务内控制已加载集合的排列，分别调用实际findById/findAll/findPaged，核完整steps列表和末项，再核持久事实未被该控制排列改写。它们证明已关联taskOrder优先、无Task的重复cleanup动作不丢失、foreign身份不借序及混合排序边界；不声称PostgreSQL物理返回了人为控制的行序。

批量与性能约束另外由源码复核承担：RunJpaPersistenceAdapter.toDomains汇集本批次非空taskId，只在集合非空时调用一次findAllById，以局部Map服务整批映射；taskOrderForStep核run、RUN_ITEM、targetId及actionType/taskType；toDomainItem按核准taskOrder（无效关联放后）、startAt、endAt组成完整比较器。completeTask复用原findByRunIdOrderByTaskOrderAsc已加载的ordered列表，不新增逐Item/Step排序查询。四个PG用例没有SQL计数或completeTask返回顺序的专属断言，不能将源码证据写成这些测试的运行覆盖。读取入口还包括findByWindowKey；它的共用映射接线经源码复核，不扩称为四例直接覆盖。最终该生产文件SHA256为 `b6af9f0445fffd92a14009fcaa662a330ec0216076cc561bfa66dfd3330066a1`。

原steps数组的顺序得到纠正，数据字段/API形状不变。BlockBoard末项阻塞原因、RunDrawer/RunDetail列表以及原结果返回仍直接消费同一顺序，无新增前端排序分支、entity/schema/字段/DTO/API。无Task且时间戳相同的历史事实仍不能还原真实先后，不能用这次GREEN消除该数据边界；ENSURE_MR仍只保持前序binding事实。

### 22.12 最终本地门禁、源码绑定与覆盖边界

最终结果在同一已冻结源码上完成，下列测试数量具有重叠，不能相加为独立覆盖数。1078已包含113个新增例：workflow19、MR详情10、interceptor12、HTTP14、PG48、多JVM6、步骤排序4（核心109加排序4）。

| 门禁与证据（`.ai/reports/validation/c2-b9/` 下） | 实际结果 |
|---|---|
| `regression-final-jdk21/result.json` | 单次13 suites，1078/1078 PASS、0 FAIL/ERROR/SKIP，347.3秒；含PG420、HTTP179、多JVM21 |
| `api-final-jdk21/result.json` | 48/48 PASS、0 FAIL/ERROR/SKIP，47.3秒 |
| `ordinary-final-jdk21/result.json` | 918总例=913 PASS+5既有SKIP，0 FAIL/ERROR，14.1秒；五个SKIP身份与B8一致 |
| `.ai/reports/static-scan/20261122-105808/summary.md`（仓库根路径） | 六份fresh SpotBugs XML均零问题；typecheck PASS；lint 0 errors/8既有fixture warnings，逐项读源码并记录保留理由，未关闭规则 |
| `local-evidence-audit.json` | 最终十份backend快照495文件完全一致，均匹配最终工作区；113新例全部在最终1078 XML内PASS |

首次1074之后的backend源码只在RunJpaPersistenceAdapter与RunTaskPersistenceIT两个路径变化，不把首次回归与最终树说成完全相同。步骤排序修正后的相关/API/普通/静态前后及排序4/4的十份快照则完全相同；实现完整审阅、排序复审和跨链独立审查均已记录在local-evidence-audit.json，测试结果与源码接线证据分别保留。

普通后端五个既有SKIP仍是GroupAppServiceTest的一例、GroupDeleteTest的两例、GroupGetByCodeTest的两例，与B8逐项身份一致。八条lint warning仍来自既有GroupTreeSelect.spec.ts/GroupDialog.spec.ts的fixture定义，根侧重读两文件实际定义并在静态ignored summary逐项保留理由；raw lint输出与B8逐字节一致，SHA256为 `b5496961a4422b7ea5154e6aead19f4c751cd8e76575656cf0b4b92a6fea0faa`。frontend222文件与B8相同；217个前端测试是已绑定的历史结果，本片没有重跑UI套件，只有typecheck/lint本次新跑。

本地验收涵盖原I/A预检、完整原意图接续、真实HTTP/PG竞争和原子NOT_SENT，以及强杀后恢复/第三JVM的APPLIED纯DB投影。SEND无seq2与真实UNKNOWN仍保留写入保护；历史B3投影资格、非Task正常B8链、最小失败patch及失败分类边界保持。新能力没有扩大到非Task接管、其他动作、SEND/UNKNOWN自动重放或完整UI；S3/C2整体继续独立推进。本地门禁通过不代替固定实现SHA的远端验收。

### 22.13 实现分组推送与固定SHA CI待验

实现已由根侧单独提交并推送：`666acdc7a60c0922db2f05713cbfefd23eed8265`，父提交为 `8cfc294c58f0ae53817a44565801936888a03bb1`，提交信息 `feat(run): resume original unsent MR intents safely`。本组13个backend文件（7生产、6测试），2143行新增/130行删除；implementation-commit.json确认提交源码与最终已验证源码及495文件快照匹配，文档另行收口。

`.ai/reports/validation/c2-b9/implementation-push.json` 已核远端feature/orchestration-consistency为该完整SHA；远端仅main、develop和此feature三个分支，main/develop仍同 `d4534ccc34fedb9bbf394ced89b4726f43ec197c`。推送使用现有认证账号的本命令credential helper，hooks未变；不将本次推送描述为main/develop已集成B9。

根侧已派发[Full-Link 44978661003](https://github.com/tongsh6/release-hub/actions/runs/44978661003)，创建于2026-11-22T03:00:55Z；按branch ref发起后核headSha为上述完整实现SHA。记录本节时运行尚待最终验收，B9仍为唯一HEAD，不标DONE，不提前提升下一片。后续必须追加真实终态和业务摘要；该CI的既有边界是后端API与隔离GitLab，不能扩写完整UI覆盖。

### 22.14 固定实现SHA CI最终验收与B9出队

[Full-Link 44978661003](https://github.com/tongsh6/release-hub/actions/runs/44978661003)的attempt 1已completed/success，固定headSha为 `666acdc7a60c0922db2f05713cbfefd23eed8265`，watchExitCode=0。最终updatedAt为2026-11-22T03:10:09Z（北京时间11:10:09）；job137074110296的14个步骤全部success，包括实际Run vertical slice tests。此终态追加在22.13当时待验记录之后，不覆盖此前失败、编译修正、校准或pending事实。

| 远端证据（`.ai/reports/validation/c2-b9/full-link/` 下） | 已核事实 |
|---|---|
| `result.json` | run、attempt、完整headSha、job与全部步骤终态一致；completed/success |
| `business-result.json` | Slice1..5分别PASSED，passedSlices=5、failedSlices=0；实际完成时间依次为03:08:27、03:08:41、03:09:15、03:09:29、03:09:43Z |
| `job.log` | 原始日志SHA256为 `3af924df2ef5fc165c64d73126f3592e0f7baa884470c7879cc9eb5b497acf27`，保留业务输出而非仅凭workflow绿色图标验收 |
| GitHub artifact14736604004 | e2e-logs，666214 bytes；取证时expired=false |

本片独立RED、已说明的校准、核心与排序GREEN、最终相关/API/普通/静态、源码绑定和独立审阅，以及分组实现推送与固定SHA远端验收已完成，B9标DONE并出队，验收文档单独收口。相关1078中的113个新增例、五个既有SKIP、八条既有fixture warnings及217前端测试仅为历史结果的边界保持。Full-Link只证明既有后端API与隔离GitLab链路，不扩称完整UI或提升Slice4/5既有较弱验收边界；main/develop尚未因此集成本片。

当前唯一HEAD顺延为C2-B10「MERGE_MR 精确只读核对证据」，目标仅为I/IID/A对象当前merged及typed观察事实。FOUND不代表原PUT成功或原业务计划满足，实际source/target变化照实保留；不改Task/Step/guard/seq2。B10详细契约与新测试留待下一阶段，本节不宣称已实现。后续SEND/UNKNOWN受证据约束恢复继续QUEUED，之后仍为SA-002；C2/S3整体与完整UI没有随B9一并完成。

## 23. C2-B10：MERGE_MR精确只读核对证据（独立事前契约）

### 23.1 定位基线与单一目标

B9验收文档`d51a0b7efd20cdcf005aa165330cf3de6312de05`已提交推送；生产实现仍为`666acdc7a60c0922db2f05713cbfefd23eed8265`，固定SHA Full-Link44978661003首轮5/5。第22节封存；23.1–23.6为独立事前契约，不能当作运行结果，实际RED/GREEN从23.7另记。项目仍按确认的development/system_refactoring及SA-013完整蓝图推进，B10为唯一HEAD。

用户价值是让原MERGE_MR未知或在途写入具备精确、可解释的当前对象观察，为后续受证据约束的判断提供事实。端到端路径沿现Controller.reconcile → AppService在事务外GET → GitRemoteEvidenceAdapter → 原confirmedMergedMr → Journal.observe → 现Observation详情/UI，单一目标为核对原I/IID/A对象当前merged，不确认原PUT因果、不补原业务计划执行结论。

### 23.2 查重、实际消费者与定位依据

在23.1所列事前基线上已只读核实：GitRemoteEvidenceAdapter.java:34–63将MERGE_MR和UPDATE_MR放在同一宽松state/sha判断中，未把MR GET实际status与完整原始对象一起交给B4证明；:139–142的通用summary会把scalar值转文本。B10只收敛MERGE_MR的观察规则，不扩展其他动作。

GitLabMergeRequestAdapter.java:230–251的confirmedMergedMr已具备HTTP200、原始三个正整数项目身份、精确IID、合法且语义等同A的SHA、merged状态、分支/URL及merge_commit_sha完整证明。GitOperationAppService.java:22–33已有key/version幂等核对、事务外evidence.read与Journal.observe接线；GitOperationPersistenceAdapter.java:423–435只写Observation和op.version/updatedAt，:140–149的真实finish按当前状态及seq1 sender核验。原对象可承责，不为此次观察扩Operation/Task关联接口或增加另一份证明模型。

现GitOperationController.java:26–36暴露详情及reconcile，GitOperationsPanel.vue:42–47/96–115展示observation.status/evidenceJson并在核对后重取详情。完整证据有立即消费方，不是为未来新增无消费者存储。文件分别位于既有infrastructure/git、infrastructure/persistence/gitoperation、application/gitoperation、interfaces/api/run与frontend/src/components/run层，不新增实体、表、schema、DTO、Port、缓存、执行器或UI入口。

### 23.3 精确locator、完整证明与typed证据

MERGE_MR原target必须提供文本method=PUT、完整匹配`/merge_requests/[1-9][0-9]*/merge`的文本suffix、文本且满足validSha的A（40/64位hex）。非法locator返回INCOMPLETE、MR GET=0；该计数不排除现namespace身份GET。原namespace若由I指向J，沿既有身份检查先阻止J业务GET，不用当前J替换原项目。历史三字段与当前五字段target使用同一规则，不新增完整请求hash门禁或兼容层，不补齐旧正文。

| 独立输入 | 应有结果 |
|---|---|
| 缺失/错误类型/非法method、IID、suffix或SHA | INCOMPLETE；业务MR GET为0，不猜测或宽松截断locator |
| absent、JSON null、array或其他非object正文 | INCOMPLETE |
| HTTP200 object含空对象，但三项目/IID/A、typed字段、state/URL或merge_commit_sha证明不完整 | MISMATCH |
| HTTP201加完整merged正文 | MISMATCH；须使用实际status，不能固定传200 |
| 实际200且confirmedMergedMr(actualStatus,raw,I,IID,A)完整成立 | FOUND，仅为该对象当前merged |
| 403/404/读取不可用 | 原FORBIDDEN/NOT_FOUND/UNAVAILABLE；不得引出重放或释放保护 |

观察证据仅取十字段：project_id、source_project_id、target_project_id、iid、source_branch、target_branch、sha、web_url、state、merge_commit_sha。已有scalar/null保留原JSON类型与值；缺失不制造null，嵌套object/array省略，错误scalar类型保留作诊断而不能骗过完整证明。token、认证header、merge_commit_message及任意额外正文不记录。API形状不变，现evidenceJson的可见内容更完整，UI继续原格式化展示。

最强反例：I/IID/A当前已merged，但由另一操作者完成，或实际source/target已改变。FOUND可以成立，实际分支必须如实保留，仍不能推出原PUT成功或原计划target满足；不从merge_commit_message文本推导计划，不通过新接口向Task索要另一套绑定。本片只读事实与后续恢复授权保持分责。

### 23.4 持久副作用与双向竞争边界

reconcile成功提交只新增现Observation并推进op.version/updatedAt；Task、Step、Run、metadata、guard、events、attempt及op原执行state/reason保持。UNKNOWN之后再得到FOUND仍为UNKNOWN/NEEDS_REVIEW并保留guard，不建seq2、不完成原Task、不启动重试或后继。相同actor/key与原expectedVersion仍走现幂等返回，不新增网络写或并行查询入口。

真实HTTP/PG须覆盖两种提交顺序：观察先commit后，原seq1 sender的真实PUT响应仍可按现fence/状态资格finish，不能因观察版本增加丢掉真实响应；真实finish先commit后，持旧version的GET观察必须STALE且零Observation/业务写入，原真实seq2保留。GET仍在账本事务外，观察version仅防止陈旧观察提交，不充当发送权或远端Git锁。

### 23.5 先验正反例与预定验收

预定27个backend展开例（24 adapter + 3真实Spring/HTTP/PG）及1个现UI基线正控；这是测试准备范围，不是执行结果。根侧先冻结d51a0b7生产树，在生产修改前得到独立行为RED；不把编译失败或fixture错误算作产品RED，后续必要校准保留原证据和理由。

| 验收层 | 正例与最强反例 |
|---|---|
| adapter新增24例 | 固定展开3（40/64位typed证明和retargeted对象）+响应矩阵15+非法locator6；精确展开见23.7，不把namespace I→J或全部通用错误归入新24 |
| 既有adapter18例与源码边界 | pipeline/tag、MR分页/关联和403等旧回归独立保留；namespace I→J及其余401/404/UNAVAILABLE沿既有身份/异常分支核源码，不宣称新24或旧18逐项覆盖全部错误 |
| 真实HTTP/PG 3例计划 | UNKNOWN后FOUND保持全部执行保护；observe先commit仍允许原sender真实finish；finish先commit使旧观察STALE且零写。使用实际HTTP计数、数据库事实与受控并发，不用前置拒绝替代竞争 |
| UI 1例基线正控计划 | 现GitOperationsPanel消费FOUND证据时仍展示UNKNOWN/阻塞事实，不产生恢复动作；只证明组件消费，不扩称完整UI旅程 |

相关/API/普通后端、静态TopN逐项处理、源码绑定及独立源码审阅均为后续门禁；实现按可评审粒度分组提交推送，并以本片固定完整SHA CI验收才出队。新的测试展开数、RED/GREEN、源码变化与CI结果须另行追加，不能借B9历史测试替代本片行为验证。

### 23.6 数据、回滚与后续范围

复用已有Observation/evidenceJson存储和现查询/UI，不改schema或接口形状，不新建实体或Task关联API。新证据字段受白名单和typed保真约束，不泛化保存任意远端正文。未验收前停止交付，发现缺陷以修复提交处理；已有Observation、原执行和guard事实保留，不清数据或重发未知请求。本片未引入dispatcher或独立开关，不把回滚描述为操作一个不存在的新入口。

后续SEND/UNKNOWN受证据约束恢复仍QUEUED，只有独立设计与验收才能授予动作；FOUND本身没有该权限。真实远端观察完整旅程、完整UI及C2/S3整体继续保留未验边界，SA-002次序不变。

### 23.7 冻结生产的独立RED与UI基线正控

冻结基线为`d51a0b7efd20cdcf005aa165330cf3de6312de05`。两组backend验证只overlay指定测试文件，signatureSeamPaths均为空；导出树与根工作区生产前后稳定，没有签名适配或生产行为修改。所有RED完成后才修改生产，测试契约无校准。

| 证据（`.ai/reports/validation/c2-b10/` 下） | 实际展开与结果 | 时间 |
|---|---|---|
| `red-adapter-frozen-jdk21/result.json` | 24=22断言FAIL+2正控PASS，0 ERROR/SKIP | 5.3秒 |
| `red-http-frozen-jdk21/result.json` | 3=1断言FAIL+2正控PASS，0 ERROR/SKIP | 15.3秒 |
| 两组独立RED合计 | 27=23 FAIL+4正控PASS，不含UI基线 | 分组运行，不合并成单次 |
| `ui-baseline-control/result.json` | 既有3例加新1正控，共4/4 PASS，0 failed/pending | 1.2秒 |

两个真实HTTP竞争正控在旧生产上已经PASS；第三条先通过UNKNOWN/NEEDS_REVIEW、Task/Step/metadata/guard/events/attempt保持的断言，再在typed证据聚合断言FAIL。因此RED定位的是观察证明/证据内容缺口，不能描述为旧UNKNOWN会被观察解除，也不能把已有并发保护写成此次才引入。

实际新增adapter24为固定展开3（40/64位、旧三字段/当前五字段、null merge SHA与retargeted对象）、响应矩阵15、非法locator6。响应15对应WRONG_IID、MISSING_PROJECT_ID、FOREIGN_SOURCE_PROJECT、TEXT_TARGET_PROJECT、SOURCE_SHA_MOVED、STILL_OPENED、MISSING_SOURCE_BRANCH、EMPTY_TARGET_BRANCH、RELATIVE_URL、MISSING_MERGE_SHA、NESTED_MERGE_SHA、EMPTY_OBJECT、EMPTY_BODY、STATUS_201、NON_OBJECT；locator6对应MISSING_SHA、INVALID_SHA、WRONG_METHOD、ZERO_IID、QUERY_IN_SUFFIX、NON_TEXT_SUFFIX。既有18例继续回归，不能加进新增24或把全部契约输入都称作新增测试覆盖。

同键幂等也须区分证据层：新三条HTTP没有same-key重复零GET断言。旧GitWriteSafetyIT:1911–1920直接核同key返回同一Observation且请求均为GET；GitOperationJournalIT:116–125核同key幂等、版本冲突及UNKNOWN/guard保持，:56–79核认证和同key原版本API响应；既有GitOperationsPanel.spec.ts:32–45核响应丢失时重用key/version。零额外evidence.read的依据是GitOperationAppService:24–27先返回、:32才read的源码接线，不将这些证据写成新三例的运行覆盖。

### 23.8 独立GREEN、前端回归与未完成门禁

全部独立RED之后，生产仅修改GitRemoteEvidenceAdapter.java，SHA256为`e6ace6fd364349a3e66be86080ef9b500884c8b8c736ec678ad10fb1e5b6ff22`；独立只读源码审查未见阻断。实现复用confirmedMergedMr完整证明、原read/reconcile/observe与现UI消费，不新增实体或接口，不改变原执行/恢复权限。

`green-independent-jdk21/result.json`实际27/27 PASS（adapter24、真实HTTP/PG3），0 FAIL/ERROR/SKIP，14.6秒；495个backend源码文件在该轮前后稳定。没有测试校准或源码seam，未以改断言取得GREEN。`frontend-final/result.json`实际218/218 PASS、0 failed/pending，含coverage，2.7秒；本片新UI正控包含在218内，与基线4/4不相加。前端生产未改，基线与完整回归的222文件快照一致；本片有一次实际完整前端套件运行，不借用B9的217历史计数。

`red-evidence-audit.json`已PASS：27个RED身份与27个GREEN逐项一致，原三个测试文件全部旧行保留，新测试hash冻结不变。首次审计器错误比较Git symlink blob SHA与文件内容SHA，失败记录保留为`red-evidence-audit-attempt-1.json`；只修正取证器，未改生产/测试或重跑Maven。实际`backend/tests/e2e/test_utils.sh`为mode120000，原link文本`../utils/test-utils.sh`及已跟踪目标内容hash在两棵冻结树中均与d51a0b7一致；该次取证修正不是测试失败或源码偏移。

记录本节时，完整1123相关回归尚待最终结果，API/普通后端/静态、实现提交推送与固定SHA CI尚未验收。B10保持HEAD，出口不勾选；后续结果须另行追加，不用27独立GREEN或218前端通过代替完整交付门禁。

### 23.9 最终本地门禁与交付源码绑定

本节追加23.8当时待验事项的实际结果，23.1–23.8原文保留。完整相关回归为单次14 suites，不把独立27例和其中重复覆盖相加。

| 门禁与证据（`.ai/reports/validation/c2-b10/` 下） | 实际结果 |
|---|---|
| `regression-final-jdk21/result.json` | 1123/1123 PASS，0 FAIL/ERROR/SKIP，350.5秒；HTTP182、PG420、多JVM21、adapter42，全部27新增已包含在1123内 |
| `api-final-jdk21/result.json` | 48/48 PASS，0 FAIL/ERROR/SKIP，47.3秒 |
| `ordinary-final-jdk21/result.json` | 942总例=937 PASS+5既有SKIP，0 FAIL/ERROR，14.2秒 |
| `frontend-final/result.json` | 218/218 PASS，含coverage，2.7秒；包含新增1个UI基线正控，不与基线4/4重复累计 |
| `local-evidence-audit.json` | PASS；最终10份backend495文件快照及6份frontend222文件快照分别完全一致，27新增均在1123内PASS |

五个既有SKIP身份与B9一致：GroupAppServiceTest一例、GroupDeleteTest两例、GroupGetByCodeTest两例，精确身份保留在local-evidence-audit.json。前端仅一个既有GitOperationsPanel测试文件变化，没有前端生产改动；218为本片实际新跑的完整套件，不借用B9的217结果。

静态报告为`.ai/reports/static-scan/20261122-114305/summary.md`。六份fresh SpotBugs XML均零问题，typecheck PASS，lint 0 errors/8条既有fixture warnings。根侧重读GroupTreeSelect.spec.ts、GroupDialog.spec.ts两份fixture实际定义，逐项写明保留理由，未关闭规则；raw lint输出与B9逐字节相同，SHA256为`b5496961a4422b7ea5154e6aead19f4c751cd8e76575656cf0b4b92a6fea0faa`。

最终审计将四个实现文件绑定到已验证源码；其中唯一生产文件GitRemoteEvidenceAdapter的SHA256仍为`e6ace6fd364349a3e66be86080ef9b500884c8b8c736ec678ad10fb1e5b6ff22`。8页完整review的代码树绑定与独立源码/测试审阅已在local-evidence-audit.json留证，无阻断；该本地通过不替代固定实现SHA的远端CI终态。

### 23.10 实现分组推送与固定SHA CI待验

实现已单独提交并推送为`8dcca5bf8265a3a1f36c16a14f0e3b8b0b35652d`，父提交`d51a0b7efd20cdcf005aa165330cf3de6312de05`，提交信息`fix(git): require precise merge observation evidence`。本组4文件、499行新增/3行删除：1个生产适配器、2个backend测试文件和1个前端组件测试；验收文档单独收口。`implementation-commit.json`确认提交与495个backend、222个frontend最终验证源码相符。

`implementation-push.json`已核feature/orchestration-consistency远端指向该完整SHA；远端仍只有main、develop和此feature三个分支，main/develop均为`d4534ccc34fedb9bbf394ced89b4726f43ec197c`。hooks保持，未将main/develop描述为已集成本片。

[Full-Link44982102777](https://github.com/tongsh6/release-hub/actions/runs/44982102777) attempt 1已实际派发，创建于2026-11-22T03:45:59Z（北京时间11:45:59），`full-link/dispatch.json`及`full-link/run.json`已核headSha为上述完整实现SHA。记录本节时status=in_progress，尚无最终验收结论；B10继续唯一HEAD，不提前写5/5或DONE，不提升尚未确定名称的下一片。后续需追加真实终态与业务摘要，后端API/隔离GitLab、完整UI和真实远端观察完整旅程的证据边界保持。

### 23.11 固定实现SHA CI最终验收与B10出队

[Full-Link44982102777](https://github.com/tongsh6/release-hub/actions/runs/44982102777) attempt 1已completed/success，headSha精确为`8dcca5bf8265a3a1f36c16a14f0e3b8b0b35652d`，watchExitCode=0。最终updatedAt为2026-11-22T03:55:55Z（北京时间11:55:55）；job137084628173的14个步骤全部success，包括实际Run vertical slice tests。本节追加终态，23.1–23.10的事前合同、独立验证与当时pending事实保持，不回写为当时已经通过。

| 远端证据（`.ai/reports/validation/c2-b10/full-link/` 下） | 已核事实 |
|---|---|
| `result.json` | 固定完整SHA、attempt 1、job及14个步骤均已completed/success |
| `business-result.json`与原始`job.log` | Slice1..5分别PASSED，passedSlices=5、failedSlices=0；实际完成时间依次为03:54:01、03:54:16、03:54:54、03:55:11、03:55:28Z |
| `job.log` | SHA256为`b1d046f752c1b2733249c5c35ff10880dbe223a9e5f1953ff1f2b21ec2e17770`，已直接核原始五条PASSED输出 |
| GitHub artifact14738561654 | e2e-logs，660838 bytes；取证时expired=false |

独立RED→GREEN、完整本地相关/API/普通/前端/静态、源码绑定与审阅、实现分组推送和固定SHA远端验收均已完成，B10标DONE并出队，验收文档单独收口。27新增包含于1123、UI新增正控包含于218，以及五个原SKIP、八条既有fixture warning的边界保持。该CI只是已有backend API与隔离GitLab五切片，不代表新MERGE_MR观察已完成真实远端或完整UI旅程，也不提升Slice4/5既有较弱覆盖。

当前唯一HEAD为C2-B11「GitLab MR读取响应状态证明」。已只读定位GitLabMergeRequestAdapter.getMap/readPages未核actualHTTP，readSource/read/find/contains四个现消费者可能把201/206当作FOUND或完整缺席；下一片只收敛原GET成功status=200，实际返回而未抛异常的非200为INCOMPLETE，保留既有403/404/network语义，无新实体、接口或target快照。记录本节时B11测试仅独立内存准备，未落盘、未运行，详细合同下一阶段补齐；SEND/UNKNOWN窄恢复仍QUEUED，不能随B10一并宣称交付，SA-002顺序保持。

## 24. C2-B11：GitLab MR读取响应状态证明

### 24.1 基线与独立目标

B10十文档已提交推送为`62f21035a8631dfabeffb2384eb1120bde4bd4ff`，`.ai/reports/validation/c2-b11/baseline.json`冻结backend495/frontend222，生产在独立RED前未变。记录23全文保持。本片先按目标与实际消费者定义下列契约，再核冻结生产的行为；合同和预计计数不是实现或验收结果，实际RED另列24.5。

### 24.2 查重、责任与定位依据

现GitLabMergeRequestAdapter的getMap（296–303）服务readSource/read，readPages（305–325）服务find/contains；两者均在收到正文后直接走既有证明，缺少actual HTTP状态资格。复用这两个出口及375–380的incomplete/readError，无新实体、schema、DTO、Port、target快照或用户入口。GitMergeWorkflow的contains在58/77/148/160/195五处立即消费；既有readSource/find/read也服务fresh/prepared ENSURE_MR、TRY_MERGE；ConflictDetectionAppService.checkMergeability（205）经GitLabGitBranchAdapter（274）调用只读probe，后者在GitMergeWorkflow 227/230/236消费readSource/find/read，不另建消费路径。

原GET只有实际200才进入既有正文/身份/SHA/URL/分页校验，200不单独保证FOUND。未抛异常的非200直接INCOMPLETE/null，先于正文和分页头消费；抛出的401/403、404、网络等异常保留原FORBIDDEN、NOT_FOUND、UNAVAILABLE分类。后页失败丢弃累计匹配、无部分值且停止后续请求，分页上限与next页规则不变；不改POST201、merge PUT或B10观察资格。

### 24.3 独立正反例与影响

| 独立样本 | 期望 |
|---|---|
| 四个读取消费者的合法200正文；完整多页200 | 继续原证明并保留原结果，空正文/身份不符仍按既有规则拒绝 |
| 201/206带有效对象、匹配列表或空列表 | INCOMPLETE/null，不确认FOUND或完整缺席 |
| 前页200已匹配，后页206带完整正文和结束头 | 丢弃此前匹配，零后续页读取；这是反驳提前成功和接受2xx家族的最强反例 |
| 后页抛401/403、404或网络异常 | 保留既有分类，丢弃部分证据且不再请求随后页 |
| fresh列表、prepared包含关系、TRY详情的真实HTTP失败 | 由现workflow处理不完整结果，不据此创建MR、确认已合并或发送merge PUT |

不完整读取先污染复用/包含/缺席判断，再可能形成错误检查点或重复写入，最终影响后继队列和审计可信度。两处统一状态门禁避免四个消费者各自补丁；继续复用现结果分类及事务边界，不增加GET、重试机制或SEND/UNKNOWN权力。

### 24.4 事前验证与交付计划

新增25 adapter+3真实Spring/HTTP/PG例；adapter另选5旧正控，共选择30例，事前预期16 FAIL/14 PASS；HTTP3事前预期FAIL。28新增与5旧正控分开计数，编译/fixture错误不能当行为RED。根侧统一冻结生产、执行Maven并在RED齐后只实现两处状态检查，其余agent只改分配文件。

预期相关1151、普通967（962+5旧SKIP）不是已通过计数；后续还需独立GREEN、API、静态TopN、源码绑定/审阅、分组提交推送及固定SHA CI。前端222未改，B10的218仅作为历史已验树沿用，不重跑组件套件；typecheck/lint仍新跑。B11保持唯一HEAD，SEND/UNKNOWN后续恢复仍QUEUED，SA-002顺序保持。未验收前不交付，缺陷以修复提交处理；既有执行/观察/guard事实保留，不清数据或重发未知请求。

### 24.5 实际独立RED及fixture修正

下列结果均来自`.ai/reports/validation/c2-b11/`，冻结生产为62f21035a8631dfabeffb2384eb1120bde4bd4ff，仅overlay对应测试，无签名适配；每轮sourceStable和rootProductionStable均为true，0 ERROR/SKIP。

| 实际阶段 | 选择结果 | 耗时 |
|---|---|---|
| `red-adapter-frozen-jdk21/result.json` | 30=16 FAIL+14正控PASS（新增25+既有5） | 4.9s |
| `red-http-frozen-jdk21/result.json` | 3 FAIL | 14.9s |
| `red-http-fixture-fixed-jdk21/result.json` | 同3例再次3 FAIL | 14.8s |

首轮HTTP已在目标业务断言失败；交叉审查又发现prepared用例两处快照SQL使用run_step不存在的id排序，会在后续路径形成fixture错误。仅把两处查询排序字段改为既有task_id，业务合同和断言未改，保留首轮并在生产仍冻结时复跑。不能描述为所有测试完全未调整，也不将潜在SQL错误作为产品RED。

最终独立选择合计33=19 FAIL/14正控PASS，共28新增+5旧正控，不重复计算首轮HTTP。读取正文的旧语义被目标断言否证；记录本节时尚无实现GREEN或完整门禁/提交/CI结果，B11不出队。

### 24.6 两处实现与独立GREEN

独立RED齐后，仅在GitLabMergeRequestAdapter.getMap/readPages各增加一处actual status!=200时返回incomplete的检查，共2行生产变更；保留原消费者、正文验证和异常分类，没有生产seam。`green-independent-jdk21/result.json`实测33/33通过（adapter30+真实HTTP3），13.7s，0 FAIL/ERROR/SKIP，backend495文件before/after稳定。

`red-evidence-audit.json`已逐项核33个RED/GREEN身份一致，28新增与5旧正控边界明确，全部旧测试行保留；唯一测试修正为24.5所述两处新SQL排序字段，业务断言不变，首轮证据保留。frontend222未变，未重跑或冒称本片通过前端组件套件。记录本节时完整相关回归已启动但尚无终态，API/普通/静态、提交推送及固定SHA CI仍待完成；独立GREEN不等于B11交付，仍唯一HEAD。

### 24.7 完整本地验收与最终源码审计

以下均为`.ai/reports/validation/c2-b11/`下的实际结果，追加本节不回写24.1–24.6的事前计划、RED和当时待验事实。

| 门禁与证据 | 实际结果 |
|---|---|
| `regression-final-jdk21/result.json` | 单次14 suites，1151/1151 PASS，349.4s；含HTTP185、PG420、多JVM21、MR adapter143，0 FAIL/ERROR/SKIP |
| `api-final-jdk21/result.json` | 48/48 PASS，48.1s，0 FAIL/ERROR/SKIP |
| `ordinary-final-jdk21/result.json` | 原58 suites，967=962 PASS+5原SKIP，14.7s，0 FAIL/ERROR；五个skip身份与B10一致 |
| `final-gates/static-result.json` | PASS；六份fresh SpotBugs XML均0，typecheck通过，lint 0 errors/8既有fixture warnings |

静态报告`.ai/reports/static-scan/20261122-122142/summary.md`已逐项注明八条warning的保留理由，实际定义已复核，未关闭规则；lint原始输出SHA256为`b5496961a4422b7ea5154e6aead19f4c751cd8e76575656cf0b4b92a6fea0faa`，与B10逐字节一致。

`local-evidence-audit.json`为PASS：10份backend495文件快照完全一致，全部28个新增用例包含于1151并通过；33个独立选择例含5个旧正控，不另算为33新增。两份frontend222文件快照一致且等于B10已验树，本片仅新跑typecheck/lint，218/218为B10历史，未重复组件套件。代码与两页完整fenced review树一致，独立源码审阅及测试交叉审查无新增阻断。唯一生产文件GitLabMergeRequestAdapter的SHA256为`e8887c1ee402d62bdbff6e72b0008a21c73df08cdb6ebbd7ee86502ff921b160`；上述本地通过不替代固定实现SHA的远端CI终态。

### 24.8 实现分组推送与固定SHA CI待验

实现已单独commit/push为`35ca21366ec9124fe9c4d6d2ef162a09c13709fb`，父提交`62f21035a8631dfabeffb2384eb1120bde4bd4ff`，信息`fix(git): require HTTP 200 for MR read evidence`。本组3文件、305行新增：现适配器增加2行状态检查，两个既有测试文件增加303行；无新实体、接口或schema。验收文档单独收口，`implementation-commit.json`已绑定提交与最终495个backend、222个frontend验证源码。

`implementation-push.json`核实feature/orchestration-consistency远端为上述完整SHA；远端仍恰main、develop、feature三个heads，main/develop均为`d4534ccc34fedb9bbf394ced89b4726f43ec197c`，未将它们描述为已集成本片。推送沿现已认证凭据，hooks保持。

[Full-Link44984865795](https://github.com/tongsh6/release-hub/actions/runs/44984865795)已实际dispatch，创建于2026-11-22T04:23:50Z（北京时间12:23:50）；`full-link/dispatch.json`与`full-link/run.json`核定headSha精确为上述实现SHA。记录本节时status=in_progress、conclusion为空，尚无最终验收结论；B11仍唯一HEAD，不勾CI或出队，不提前命名下一片。CI仍只按既有backend API与隔离GitLab五切片验收，不扩大完整UI旅程或SEND/UNKNOWN恢复权限。

### 24.9 固定实现SHA CI最终验收与B11出队

[Full-Link44984865795](https://github.com/tongsh6/release-hub/actions/runs/44984865795) attempt 1已completed/success，headSha精确为`35ca21366ec9124fe9c4d6d2ef162a09c13709fb`，watchExitCode=0。最终updatedAt为2026-11-22T04:32:45Z（北京时间12:32:45）；job137093115383全部14步骤success，包括实际Run vertical slice tests。本节追加终态，24.1–24.8原文及当时待验事实保持，不回写成当时已通过。

| 证据（`.ai/reports/validation/c2-b11/full-link/`下） | 最终事实 |
|---|---|
| `result.json` | 固定完整SHA、attempt 1、job及14步骤均completed/success，watch退出0 |
| `business-result.json`与原始`job.log` | Slice1..5均PASSED，5 PASS/0 FAIL；完成时间依次为04:31:07、04:31:19、04:31:52、04:32:05、04:32:19Z |
| `job.log` | SHA256为`4132a34ed305bba23c197b004c1b57bdb5d8b2ff7de00e2ed5a40bc3c25a63d2`；04:32:19.5731550Z原始摘要为Passed: 5 / Failed: 0 |
| artifact14739970538 | e2e-logs，663034 bytes，取证时expired=false |

独立RED→GREEN、完整本地相关/API/普通/静态、源码绑定及审阅、实现分组推送和固定SHA CI均完成，B11标DONE并出队，验收文档单独收口。28新增包含于1151、五个旧SKIP和八条原fixture warning的边界保持；前端218仅B10历史，本片未重跑组件套件。该CI只验已有backend API与隔离GitLab五切片，不扩大完整UI旅程、Slice4/5既有覆盖或SEND/UNKNOWN恢复权限。

下一唯一HEAD为C2-B12「无自身写意图任务的失败与重试闭环」。当前Run/Task锁与token核准下，首次结果提交前无任何本Task operation的UNKNOWN拟按FAILED收口，保留原message/metadata，由现新Run retry直接消费。有任何operation或历史UNKNOWN不在本片，不以FOUND直接恢复；拟由原ack返回现RunItemResult供唯一completeTask消费者使用，不新增DTO或Port方法。独立HTTP/PG测试仍仅内存准备，代码未落盘、测试未运行，详细合同后续补齐。SEND/UNKNOWN窄恢复继续QUEUED，之后仍按SA-002顺序推进。

## 25. C2-B12：无自身写意图任务的失败与重试闭环

### 25.1 基线与独立目标

B11十文档已提交推送为`59f6311d811616d9b990e714d60ce70274dc749b`，`.ai/reports/validation/c2-b12/baseline.json`冻结backend495/frontend222，productionUnchangedBeforeIndependentRed=true。记录24全文封存；B11的本地和固定SHA CI结果不是B12验证。本片目标是把当前Task尚未形成自身写意图的首次失败接入现有安全重试，同时保留真正远端未知的保护。

### 25.2 独立合同与真实消费者

当前WINDOW协议的RUNNING Task经原Run→Task锁、当前token和结果输入核准后，实时确认没有任何自身operation且没有自身Step，才将提议UNKNOWN归为FAILED；非UNKNOWN保持。原message、metadata含git.blockReason=UNKNOWN诊断、startAt/endAt、action与Task/token身份不改。有任意自身operation均沿原协议，已有Step、旧token、非当前协议及历史UNKNOWN不取得该首次转换资格；其他Task的APPLIED或其他仓库guard不冒充当前写意图。

原ack返回既有RunItemResult，唯一completeTask消费者用同一结果贯穿Step/Item/Task/skip/binding/Run。失败不新增成功binding，ENSURE_MR只保留前序绑定。现RunPlanAppService在278–289拒绝NEEDS_REVIEW/RUNNING/UNKNOWN，在311/435–437只接纳失败、阻塞或因阻塞跳过项；copyRetryCheckpoint 404–421区分ENSURE前无检查点与TRY继承I/IID/A，RunJpa inheritedPrefix 179–209继承成功前缀。因此ENSURE失败后的新Run可重新读当前B，TRY失败后的新Run须继续原I/IID/A，不改历史Run或UNKNOWN。

Run.java 75–93按Step/Item结果汇总，不读取git.blockReason作为UNKNOWN判据，保留诊断不应重新阻断FAILED。现前端runPresentation.ts 18/35–37已有当前WINDOW v3失败重试判定，RunList.vue 103和RunDetail.vue 259/287为实际消费者；本片不修改它们，不将源码接线描述成真实UI点击验收。

### 25.3 责任查重、最强反例与影响

定位依据：`GitOperationPersistenceAdapter.acknowledgeTask` 335–340现已锁定Task并按taskId查询操作，空集却直接void返回；`lockTask/lockTaskOwner` 486–506复用Run→Task顺序、协议与token校验。现`GitOperationJournalPort` 42只有该既有方法，唯一生产调用在`RunJpaPersistenceAdapter.completeTask` 399–461；其后Step/Item、skipAfterFailure、binding分支、Task CAS和Run汇总仍消费原step.result。返回已有结果即可让真正持有账本事实的一方裁定，再由原结果事务一致落库，不新增DTO、Port方法、状态、实体、schema、额外网络调用或第二套重试判定。

| 独立正反例 | 事前期望 |
|---|---|
| 无自身op/Step的首次UNKNOWN；非UNKNOWN正控；其他Task已APPLIED或别仓有guard | 只将合格UNKNOWN归FAILED并保留诊断/身份；其他事实及非UNKNOWN保持 |
| 自身任意op、已有Step、旧token、错误协议、历史UNKNOWN | 不进入无意图转换；原资格/保护协议不放宽 |
| begin先commit，结果后取得真实PG锁 | 必须看到自身op并沿原协议，不使用锁外空快照 |
| 结果先持锁并commit，晚begin真正等待 | Task失败后晚begin拒绝，无新意图/写请求 |
| 结果在Step/metadata等暂写后CAS失败或外层rollback | 本事务新增变化整体不提交；晚begin此后可合法继续，原独立事实保持 |
| ENSURE读取A后无意图失败，retry时源B；TRY已有I/IID/A后无意图失败 | 新ENSURE读B；新TRY继承I/IID/A，不重建MR或更换原SHA |

最强反例是“结果事务暂时看不到operation，但并发begin仍可能取得资格”：只有真实共享锁与提交顺序能关闭竞争窗口，顺序测试或锁外计数不足；结果回滚不能被说成永久失权。二阶影响是FAILED让现retry和失败传播可用，三阶风险是各层若继续读取旧UNKNOWN，会把Step、队列、binding和Run汇总写成矛盾事实；同一个核准结果必须贯穿原事务，其他真实未知仍可使整Run保持NEEDS_REVIEW。

### 25.4 事前验收与旧测试调整计划

PG20预计9 FAIL/11 PASS，真实HTTP3预计2 FAIL/1 PASS；这些仅是独立反例设计与预计展开，本节不将计划作为运行结果。先冻结59f6311生产，由根侧统一执行独立RED，不能将编译/fixture错误计为产品RED；再实现上述结果传递与资格检查并验证GREEN。

新RED后，旧5个PG fixture计划补真实write UNKNOWN以保持原保护目的；HTTP5个既有方法的12个期望需按新合同单独校准。保留原证据与校准补丁，说明为何改变，不能称所有旧断言原样保留，也不能先改旧期望掩盖新RED。

后续门禁为真实PG竞争/回滚、真实HTTP与原重试闭环、完整相关/API/普通后端、静态TopN逐项处理、源码绑定和独立审阅，以及分组提交推送与固定SHA CI。现前端消费者保持，完整UI旅程仍另行验收；当前B12唯一HEAD，不提前写GREEN/DONE或推动下一片。未验收不交付，缺陷以修复提交处理；历史执行与guard保留，不清数据、不重发未知请求，SEND/UNKNOWN恢复与SA-002顺序保持。

### 25.5 实际独立RED与实现前证据冻结

25.1–25.4的事前记录保持原文。以下两组均固定`59f6311d811616d9b990e714d60ce70274dc749b`生产，分别只overlay对应新测试，无生产签名seam；每组sourceStable/rootProductionStable均为true，0 ERROR/SKIP。证据目录为`.ai/reports/validation/c2-b12/`。

| 实际阶段 | 结果 | 耗时 |
|---|---|---|
| `red-pg-frozen-jdk21/result.json` | 20新增=9 FAIL/11正控PASS | 15.4s |
| `red-http-frozen-jdk21/result.json` | 3新增=2 FAIL/1正控PASS | 15.7s |
| 两组独立新增合计 | 23=11 FAIL/12正控PASS；不是实现GREEN | 不以重复运行或重叠套件累加 |

PG九个失败逐项对应目标行为：四种action的Step预期FAILED而实际UNKNOWN；结果先提交用例的Run预期FAILED而实际NEEDS_REVIEW；外层回滚用例的事务内投影预期FAILED而实际UNKNOWN；CAS用例因旧UNKNOWN未命中针对FAILED的触发条件，实际未抛出预期异常；两种仓库策略的当前Task仍UNKNOWN而不是FAILED。均为断言失败，不是编译或fixture ERROR。

HTTP两个失败分别为新ENSURE与TRY链中的Step预期FAILED、实际UNKNOWN（冻结文件884/999行）；真实PUT未知保护正控PASS。失败先定位于结果投影，尚不能据此宣称其后新Run retry、晚begin或回滚后置断言已通过。

首次PG RED执行前，补齐了新用例首注释“仅首次UNKNOWN转换”的范围，并在outer rollback后加入实际beginCreateMrIntent，以检查原token仍能合法取得INTENT；这属于首轮执行前的用例完备化，20例数量不变。冻结PG新增块502行、HTTP新增块285行，独立RED时旧测试全文仍保留。外层用例在本轮先于该后置验证失败，其完整回滚/late begin结论须待GREEN，不能将存在断言写成已验通过。

事前合同的retry措辞已精确为：本转换只确定当前Task FAILED，现retry仍要求源Run结束、finishedAt非空、无RUNNING/UNKNOWN Task及原窗口/范围/guard门禁，其他仓仍执行或存在其他UNKNOWN时不立即取得资格；这是原受理条件的明确化，不扩大范围。25.3中“GitOperationJournalPort 42”只指该行的既有声明；规划时全仓生产源码的acknowledgeTask共声明、实现、唯一completeTask调用三处，不表示整个Port只有一个方法。

独立RED齐后才进入生产修改，并授权旧5个PG fixture补真实write UNKNOWN、HTTP5方法12期望按新合同单独校准，实际补丁和验证后续追加。记录本节时尚无GREEN，未完成本地全门禁/提交/CI；B12仍唯一HEAD，不提前验收或出队。

### 25.6 三处生产接线、旧测试校准与独立GREEN

独立RED之后的生产变化仅在三个既有文件，无新DTO、Port方法、状态或实体/schema。实际源码与独立只读审查均确认原结果事务的责任边界：

| 既有生产责任 | 实际实现 |
|---|---|
| GitOperationJournalPort 41–42 | acknowledgeTask原位返回已有RunItemResult，不增加方法、重载或兼容路径 |
| GitOperationPersistenceAdapter 334–396 | 保持MANDATORY及原Run→Task/token核准，复用taskOperations按本Task查询；无自身operation且提议UNKNOWN时查自身Step，存在则拒绝、无则返回FAILED；非UNKNOWN原样返回，任意自身operation继续原协议并返回原结果 |
| RunJpaPersistenceAdapter 423–459 | 唯一completeTask调用获取核准result，同一值进入Step、Item、skipAfterFailure、binding分支、Task CAS及Run汇总；原action/message/时间/token和validated metadata patch继续使用 |

归FAILED的路径没有新增成功binding，也未创建Git操作/事件/guard。ENSURE_MR的前序绑定保持；原token与协议核验沿现lockTask责任保留。源码独立审查无阻断，不能把它扩大成未运行的完整回归结论。

`old-test-calibration/result.json`为PASS，附两份独立patch。HTTP旧5方法只校准12行期望（+12/-12）；PG旧5方法共六处编辑（+23/-1），为原未知保护场景补真实begin→sending→finish(UNKNOWN)，并将一处claimLastTask改为`claimMergedMrTask("MR_REUSED", 40)`，使用独立project避免遗留guard干扰。两个独立新增块与对应冻结RED逐字节一致：HTTP285行、PG502行，newIndependentBlockUnchanged=true，且oldFilePreservedBeforeRed=true。这里证明调整边界，不表示旧5个方法已由本次新例选择器运行；其完整执行仍待相关回归。

`green-independent-jdk21/result.json`已实测23/23 PASS（真实HTTP3+PG20），19.1s，0 FAIL/ERROR/SKIP、exitCode=0，495源码before/after稳定。已核对应XML：双向真实Run→Task锁竞争、FAILED投影触发的CAS回滚、外层回滚后原token实际late begin、新ENSURE重读B、TRY继承I/IID/A及真实write UNKNOWN保护全部通过；25.5中未越过失败位置的后置断言现已完成，不回写原RED结果。

记录本节时完整相关回归已启动但尚无终态，API/普通/静态及最终源码总审计、代码commit/push与固定SHA CI均未完成。25.5及此前历史保持，B12仍唯一HEAD；独立GREEN不等于最终交付或完整UI旅程验收。

### 25.7 API旧fixture补证与本地回归

以下实际结果在`.ai/reports/validation/c2-b12/`下；25.6及此前的当时状态保持，不回写失败或待验历史。

| 阶段与证据 | 实际结果 |
|---|---|
| `regression-final-jdk21/result.json` | 单次14 suites，1174/1174 PASS，347.0s；含HTTP188、PG440、多JVM21，0 FAIL/ERROR/SKIP |
| `api-final-jdk21/result.json` | 首轮48=47 PASS/1 FAIL，48.2s，0 ERROR/SKIP；原失败保留 |
| `api-verified-jdk21/result.json` | fixture补证后48/48 PASS，51.1s，0 FAIL/ERROR/SKIP |
| `ordinary-final-jdk21/result.json` | 原58 suites，967=962 PASS+5既有SKIP，14.2s，0 FAIL/ERROR |

API首轮唯一失败为RunPlanApiIT.unresolvedOrchestrationPreventsCloseBeforeAnyCleanupGit：旧fixture未为本Task建立任何operation，却直接提交UNKNOWN并期待Run NEEDS_REVIEW，实际按B12契约成为FAILED。该例原目的仍是“真实未决写必须阻止close及cleanup”，因此补方法局部真实Journal CREATE_BRANCH→sending→finish(UNKNOWN)事实，再执行原结果提交与close断言；不把期望409改为成功，也不修改生产规则迎合旧fixture。

`api-fixture-calibration/result.json`与`RunPlanApiIT.patch`已审计：仅该测试fixture变化，+45/-10；原方法语句按序保留，原close409、Task UNKNOWN、窗口PUBLISHED、零cleanup计划和零Git交互等断言不变，删除行只是try内缩进调整。原setup与PreciseClock保持，finally按原配置存在与否精确恢复raw settings，并断言GLOBAL整行与原快照相等；逆变换可恢复原文。API复验已实际执行这一路径并通过。

当前待交付代码共6文件（3生产+3测试）。API补证只改变RunPlanApiIT，该文件不在已通过的独立23和相关1174选择器内；`api-fixture-calibration/result.json`核定其余494个backend文件与GREEN/related一致。因此前两轮实际执行的生产及选中测试未变，无需机械重跑无关套件。各运行内部的495快照前后稳定不等于跨阶段所有495文件相同：API补证前后的唯一差异必须在最终源码总审计中明确保留，不能写成十份495快照完全一致。

记录本节时静态任务仍执行，最终源码总审计、代码commit/push及固定SHA CI尚未完成；B12继续唯一HEAD，不提前出队或扩大完整UI覆盖。旧PG/HTTP校准用例已纳入1174相关回归通过，API首轮失败与本次补证为另一个独立证据链。

### 25.8 完整本地门禁与跨阶段源码审计

`final-gates/static-result.json`已PASS，22.3s；本次报告为`.ai/reports/static-scan/20261122-132559/summary.md`。六模块fresh SpotBugs XML各0，typecheck PASS，lint 0 errors/8既有fixture warnings。根侧已重读两份fixture实际定义，八条逐项保留理由写入报告，未关闭规则；raw lint SHA256为`b5496961a4422b7ea5154e6aead19f4c751cd8e76575656cf0b4b92a6fea0faa`，与B11逐字节一致。

`local-evidence-audit.json`为PASS，明确区分同次运行内部稳定与API补证前后的跨阶段差异：

| 审计范围 | 实际结论 |
|---|---|
| 通过阶段的10份backend快照，每份495文件 | 后6份495文件完全相同；前4份仅未被独立/相关选择器选中的RunPlanApiIT不同 |
| 全部通过阶段共享的其余494文件 | 全部一致；已执行生产及独立23/相关1174所选测试未漂移 |
| 本片23个独立新增用例 | 全部包含于1174相关回归并通过，不把23再次加到1174 |
| 2份frontend222快照 | 相同且与B11一致；218/218仅B10历史，本片未重跑组件套件，typecheck/lint为本次运行 |
| 五个普通后端SKIP与API首轮失败 | SKIP身份保持，`api-final-jdk21/result.json`原失败保留，校准另有patch与审计 |

六代码文件（3生产+3测试）的审阅由首批五文件与API fixture增量组成，绑定tree `bfb133f73461a8fbc26ad4e15f2d5d71d960aa86`；原五文件哈希与首次review树`9652a4560d0e0b46a64075697bc05a2dfedd0cb3`一致。独立生产审查、测试交叉核验及API校准补证无阻断。不能将该结论改写成十份495源码完全相同，或声称为API fixture变更重跑了无关的完整相关套件。

至此独立23、单次相关1174、API复验48、普通967（962 PASS+5旧SKIP）、静态TopN及最终源码审计均通过。25.7及此前记录保持原时态；记录本节时实现代码尚待commit/push与固定SHA CI，B12仍唯一HEAD，最终交付出口未完成。

### 25.9 实现分组推送与固定SHA CI待验

实现已单独commit/push为`7042f19278ff8d8bfa102a8f27d0c1f1b949539d`，父提交`59f6311d811616d9b990e714d60ce70274dc749b`，6文件（3生产+3测试）、+889/-39。验收文档单独收口；`implementation-commit.json`核定validatedCodeMatchesCommit=true，当前495个backend及222个frontend文件均匹配最终验证树。25.8所列早期API fixture差异仍保留，不将此提交绑定解释为所有历史快照相同。

`implementation-push.json`已核远端恰三heads：feature/orchestration-consistency为上述完整SHA，main/develop仍同`d4534ccc34fedb9bbf394ced89b4726f43ec197c`，未将main/develop描述为已集成B12。提交前冻结文档的`implementation-doc-gates/result.json`为PASS，包含roadmap/OpenSpec strict、EOF/diff及495/222源码稳定；hooks保持。

[Full-Link44989857158](https://github.com/tongsh6/release-hub/actions/runs/44989857158)已实际dispatch，创建于2026-11-22T05:32:38Z（北京时间13:32:38）。`full-link/dispatch.json`与`full-link/run.json`核到headSha精确为`7042f19278ff8d8bfa102a8f27d0c1f1b949539d`，记录本节时status=in_progress、conclusion为空，尚无最终CI结果。B12仍唯一HEAD，提交推送项完成、最终CI出口未勾选；25.1–25.8历史保持，不提前出队或扩大backend API/隔离GitLab之外的完整UI验收。

### 25.10 固定实现SHA CI最终验收与B12出队

[Full-Link44989857158](https://github.com/tongsh6/release-hub/actions/runs/44989857158) attempt 1已completed/success，headSha精确为`7042f19278ff8d8bfa102a8f27d0c1f1b949539d`，watchExitCode=0。最终updatedAt为2026-11-22T05:43:06Z（北京时间13:43:06）；job137108323800全部14步骤success。本节追加终态，25.1–25.9原文保持，尤其不将25.9当时in_progress回写为已成功。

| 证据（`.ai/reports/validation/c2-b12/full-link/`下） | 最终事实 |
|---|---|
| `result.json` | 固定完整SHA、attempt 1、job及14步骤均completed/success，watch退出0 |
| `business-result.json`及原始`job.log` | Slice1..5分别PASSED，5 PASS/0 FAIL；完成时间依次为05:40:58、05:41:18、05:42:00、05:42:21、05:42:43Z |
| `job.log` | SHA256为`176eb3f3f2e2f14fdde340cb7f850d64fa040387e69655e9351cdee8d1274966`；05:42:43.5114338Z原始摘要为Passed: 5 / Failed: 0 |
| artifact14741706332 | e2e-logs，633066 bytes，取证时expired=false |

独立RED→23 GREEN、单次相关1174、API补证后48、普通967、静态与最终源码审计、六代码文件分组推送及固定SHA CI均完成，B12标DONE并出队，验收文档单独收口。API首轮失败、旧fixture校准和10份快照中前4份仅API fixture差异等边界均保留；前端218仅B10历史，本片未重跑。CI只验已有backend API与隔离GitLab五切片，不扩大完整UI旅程或Slice4/5既有覆盖，也不赋予真实SEND/UNKNOWN新的恢复权限。

下一唯一HEAD为C2-B13「项目身份响应证明统一」。拟由Resolver.resolve、Evidence.read的namespace前置及GitLabAdapter.resolveProjectId三个现入口复用现Resolver，统一actual200、object和positive integral JSON id的纯证明，不新增实体或Port。各入口定位、token与真实异常政策保持；仅long消费者另核上界，字符串身份不附加long上限。第三入口正常不可信/越界沿既有GIT_OP_004；Evidence正常非200为INCOMPLETE、200身份不符为MISMATCH。当前仅只读核对与内存测试准备，尚无新增测试落盘或运行，详细事前合同待切片基线后记录26；metadata、其他业务GET、SEND/UNKNOWN和完整UI仍独立待验。后续任务仍按既定SA-002顺序推进。
