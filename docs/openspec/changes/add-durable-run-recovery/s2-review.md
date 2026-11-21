# S2 实施范围评审

日期：2026-10-08。源码基线：`5d85b1703f609326aecc14dd21329f9826d3ab99`，已推送到 `feature/initial-admin-bootstrap`。

状态：`COMPLETED_S2`。实施与验收见 [s2-implementation.md](s2-implementation.md)。用户于 2026-10-08 对已呈现的完整范围明确回复“同意”，批准 S2-A 至 S2-D 及新建本机专用 GitLab 项目内的受控 fixture。S3–S5、生产部署和非隔离仓库写入仍未批准。以下保留评审时的源码基线与实施约束。

关联：[完整蓝图](design.md)、[阶段任务](tasks.md)、[需求](../../../requirements/in-progress/SA-013-发布编排中断恢复.md)、[规范增量](specs/run/spec.md)。

## 1. 用户能得到什么

S2 完成后，在受控启用范围内，每次外部写入都有独立操作记录和发送证据。响应丢失时，系统保留“结果未知”和仓库阻断；用户能查看 MR、提交、引用等核对证据。相同仓库的其他内部入口不能绕过阻断继续写。

S1 保存的 LOCAL_ONLY 预览仍不可执行、不被改写。S2 不启用后台发布执行器、自动恢复、补偿或历史 Run 重放；这些仍由 S3–S5 交付。旧同步入口接入证据与门禁，不因此宣称已具备完整流程恢复能力。

## 2. 源码核对与必须修正的前提

| 编号 | 已核实事实 | 直接证据 | 对 S2 的影响 |
|---|---|---|---|
| F1 | 合并检查创建临时 MR/PR 并关闭，具有写副作用 | GitLabGitBranchAdapter:269–305；GitHubGitBranchAdapter:139–181 | 冲突检查也必须登记写操作；reconcile 不能复用它 |
| F2 | GitLab 归档引用创建返回 false 后仍调用删除源分支 | GitLabGitBranchAdapter:336–347 | 优先修复；未确认归档成功不得发送删除请求 |
| F3 | mergeBranch 包含创建 MR、轮询、PUT 合并、405 后再 PUT、关闭 MR，返回值不携带 IID | GitLabGitBranchAdapter:75–153、240–249；GitBranchPort.MergeResult | 拆成可记录的单次副作用；移除隐藏重发和隐藏清理 |
| F4 | 文件更新独立走 GitLabFilePort，返回 boolean；异常折叠为 false，读取 403/其他异常返回空 | RealGitLabFileAdapter:43–94、126–156 | 分支工厂装饰器不能覆盖文件写；空值不能作未执行证据 |
| F5 | 分支列表只请求第一页 100 条且异常返回空；ensureMrInfo 只查 opened/第一条并将异常当不存在 | GitLabGitBranchAdapter:376–401；GitLabAdapter:151–193 | 新核对器需要完整性和查询失败信息；不得复用旧查询结果作安全判定 |
| F6 | GitHub 适配器实际有 Component 装配；PR mergeable=null 当前被视作可合并 | GitHubGitBranchAdapter:26–27、177–190 | GitHub 同样必须纳入门禁；尚未证实的能力保持不支持自动核对 |
| F7 | Run、Attach、Iteration 等写链路包含外层事务，Run 通常在 Git 操作后才保存 | RunAppService、AttachAppService、IterationAppService | 操作证据须独立提交，不能依赖未提交 Run 的外键或大事务 |
| F8 | Maven/Gradle 更新器还可直接写本地文件 | MavenVersionUpdaterAdapter:176–190；GradleVersionUpdaterAdapter:128–141 | 受控范围内无法识别目标的本地模式必须在写前拒绝，不能漏过门禁 |

以上是源码事实，不等同于本轮完成了故障复现或 GitLab 能力实测。F2 是优先处理的安全缺口，S2 实施时先补“归档失败零删除”RED 测试。

## 3. 全入口覆盖清单

以下为当前 main 源码扫描所得的写链路；实施时以实际调用图和架构测试共同锁定，不仅匹配方法名称。

| 入口族 | 调用位置 | 外部副作用 | 接入要求 |
|---|---|---|---|
| 手动编排/失败重试 | RunAppService.startOrchestrate、retry | 创建分支、创建/合并/关闭 MR | 每个原子请求独立 operation/attempt；保留原 Run 来源 |
| 发布与关闭事件 | WindowLifecycleListener、ReleaseWindowAppService.close → RunAppService | 间接触发编排/收尾 | 必须经过相同门禁；发布意图原子受理及回调丢失恢复仍属 S3 |
| 发布收尾 | RunAppService.executeCleanup | 合并、tag、pipeline、两类分支归档 | 全部登记；无法确认的归档删除与 pipeline 不自动重试 |
| 版本更新/批量/重试 | RunAppService.executeVersionUpdate、executeBatchVersionUpdate、retryVersionUpdate | Maven/Gradle 文件 PUT | 每个文件可追踪，部分完成不得被聚合成功掩盖 |
| 迭代创建/修改/增加仓库 | IterationAppService.executeRepoSetup | feature 分支创建 | 包含尚未提交的迭代；证据不能随外层事务回滚 |
| 迭代移除/删除 | IterationAppService 的归档链路 | 归档引用与源引用删除 | 拆开记录；删除必须有已确认归档证据 |
| 迭代版本冲突处理 | IterationAppService.resolveVersionConflict → updateRepoToSystemVersion | 文件版本写回 | USE_SYSTEM 写路径进入相同文件门禁 |
| 挂载/解除/独立分支动作 | AttachAppService.attach、detach、createReleaseBranchForIteration、mergeFeatureToRelease | 创建、合并、归档 | 同一仓库统一标识和保留；单项失败不能释放未知结果 |
| 独立发布分支与合并服务 | ReleaseBranchService.createReleaseBranchAndMerge、CodeMergeService.mergeFeatureToRelease | 创建、合并 | 即使当前调用少也禁止直接注入底层写适配器 |
| 冲突检测 | ConflictDetectionAppService → GitBranchPort.checkMergeability | 临时 MR/PR 创建、关闭 | 用显式 PROBE 操作记录；不能作为只读核对接口调用 |
| 最终文件出口 | GitLabFilePort → RealGitLabFileAdapter；Maven/Gradle 本地文件访问 | HTTP PUT / Files.writeString | 远端适配器校验执行许可；本地无法映射目标时在修改前拒绝 |
| 底层组合操作 | mergeBranch、archiveBranch、triggerPipeline | 自动重发、自动关闭、创建后删除 | 移到可追踪的应用级组合；底层一次调用最多一个写请求 |
| 仓库与连接配置变更 | CodeRepositoryAppService.update/delete、SettingsController.saveGitLab | 改变后续请求目标或凭据来源 | 活跃/未知操作期间保护远端映射；凭据不进入快照 |

DryPlanAppService 使用的 ensureMrInfo 当前仅 GET，但查询语义不足，不升级为恢复证据来源。仓库列表、版本读取等真正只读操作无需占用写许可；权限失败仍须与不存在区分。

## 4. 模块与数据设计收敛

### 操作证据独立于 RunTask

不能要求所有旧入口先创建 RunTask：冲突检查、迭代初始化及部分文件操作没有对应持久 Run，现有 run_task.run_id 又有外键约束。建议 S2 的操作账本独立存在，记录 `ownerType/ownerId`、可选 Run 关联字符串和服务端操作者；S3 通过 task → operation 关联复用同一账本。无对应 Run 的证据通过仓库操作页面查询，不制造可执行的占位 Run。

建议逻辑结构如下，物理迁移号实施时取下一空号，本轮不创建迁移：

| 结构 | 内容与约束 |
|---|---|
| git_operation | operationId、来源、remoteKey、动作、目标/前态摘要、requestHash、当前投影与 stateVersion；来源幂等键唯一，同键异请求拒绝 |
| git_operation_event | append-only 事件；attemptId、事件序号、generation、INTENT_RECORDED/SEND_STARTED/确认结果、时间和脱敏证据；attempt+序号唯一，旧事件不可覆盖 |
| repo_execution_guard | remoteKey 唯一；持有会话、代次、在途 operation、保留原因；只允许 CAS 变更，未知请求不能因租约过期自动释放 |
| git_operation_observation | 核对请求键、操作者、期望版本、观察时间、完整性、远端对象标识与决策；历史观察不覆盖，新结果经 CAS 更新投影 |

原蓝图中的 run_task_attempt 收敛为上述 operation/event 的尝试读模型，S3 不再另建第二份同义尝试账本。任务调度、依赖和 Run 聚合状态仍属于 S3，不能因 S2 引入账本就提前放行执行器。

remoteKey 使用 Provider + 服务端实例标识 + 经验证的远端项目稳定 ID；不能只使用本地 repoId、分支名或简单小写 clone URL。同一远端的别名必须落同一保留；无法验证身份则阻断受控写入。分支目标、文件路径和前态哈希固定在操作记录中，不保存 Token、请求头或任意响应体。

### 单一写出口与事务

应用层新增窄接口：操作账本、仓库保留、只读证据查询和原子 Git 写入。基础设施实现 PostgreSQL CAS 与 Provider 适配；真实传输适配器不直接向旧业务服务暴露。工厂不得返回绕过门禁的原始适配器，GitLabFilePort 与本地文件路径也须受控。用 ArchUnit 和 Spring 装配测试校验生产/测试 profile 的唯一出口。

顺序：独立短事务保存意图及保留 → 独立短事务 CAS 写入 SEND_STARTED → 事务外执行一次写请求 → 短事务追加结果事件。使用独立 Bean 的事务边界，不能依赖同类调用。原事务中未提交的业务行不能成为账本插入的强外键前提。

在外层业务事务尚未结束时，外部写成功不代表业务投影已提交。业务回滚、提交后进程退出或结果提交失败，都须保留可查询的操作证据和待复核保留；不能仅靠 afterCompletion 回调作为解除阻断的唯一证据。S2 先提供诊断，不承诺自动修复业务投影。

代次 CAS 只能拒绝旧执行者更新数据库和发起后续步骤，不能撤回已发出的 HTTP 请求。SEND_STARTED 到实际发送之间进程暂停，也按可能在途处理；新持有者只接管核对，不能因超时抢到写许可。未决证据不足时继续阻断，而非宣称 exactly-once。

## 5. 结果、核对与页面契约

- 写结果区分确认成功、确认未生效、UNKNOWN；连接中断、响应体损坏或 5xx 不自动等于确认失败。
- 读结果区分 MATCHED、NOT_FOUND、FORBIDDEN、UNAVAILABLE、INCOMPLETE、UNSUPPORTED。即使 NOT_FOUND，也必须考虑原请求在途和完整分页，不能直接允许重放。
- “目标状态满足”与“证实本操作完成”分开。例如已有同名同 SHA 分支不赋予本操作删除所有权。
- 复合归档拆成创建归档引用、核对引用、删除源引用；创建失败或无法确认成功时不发删除。外部并发漂移和删除原子前置能力未证实的情况进入人工复核，不声称一次查询能消除竞争窗口。
- GitHub 暂不承诺与 GitLab 等价的自动核对；具备通用证据/阻断，Provider 未支持的决策明确返回 UNSUPPORTED。

拟新增接口（路径为评审草案，不是已实现 API）：

| 接口 | 行为 |
|---|---|
| GET /api/v1/repositories/{repoId}/operations | 有界分页的本仓库操作摘要，含 Run 来源与阻断状态 |
| GET /api/v1/git-operations/{operationId} | 尝试时间线、关联 MR/SHA、脱敏结果及观察记录 |
| POST /api/v1/git-operations/{operationId}/reconcile | Idempotency-Key + expectedStateVersion；只发外部 GET/HEAD，记录观察；绝不创建/合并/关闭 MR、写文件或触发 CI |

操作者来自认证上下文；沿用现有访问控制，不新增 RBAC 产品范围。相同核对键返回原结果，异内容返回 409；过时观察不能覆盖更新的状态。核对分页、超时和次数有上限，超限返回 INCOMPLETE/UNAVAILABLE，不转成成功。查询按仓库/操作索引，摘要不加载全部历史事件，也不在列表请求中逐项远程核对。

仓库详情显示“操作记录/写入受阻”；有关联 Run 时从 Run 页面链接到同一证据。只展示核对按钮、时间及阻断原因，不展示“强制解除”“继续执行”“自动回滚”。S1 预览仍显示不可执行。

## 6. 实施顺序与验收出口

依赖顺序：S2-A → S2-B → S2-C → S2-D。它们共同完成 S2；任何单项完成均不能标记全部 S2 已验收。

| 切片 | 用户价值与范围 | 独立验收 | 回退边界 |
|---|---|---|---|
| S2-A 原子操作与结果语义 | 修复归档失败仍删除；分拆 MR 创建/合并/关闭；统一写 UNKNOWN 与只读查询结果 | Mock HTTP 验证每个原子方法最多一个写请求；归档失败零 DELETE；超时不重发；403/分页缺失不当作不存在 | 新执行保持关闭；保留安全修复，不恢复危险的自动删除 |
| S2-B 持久证据与全入口门禁 | 新增账本/CAS/保留；接通第 3 节所有入口和映射保护 | PostgreSQL 同仓并发、不同仓独立、旧代次、业务回滚、结果提交失败；架构与 profile 装配测试 | 保留账本与阻断；不能退回不识别 guard 的旧二进制 |
| S2-C 可查询证据与只读核对 | 仓库/Run 页面、分页 API、幂等核对与脱敏 | 认证/幂等/版本冲突 API、组件测试；reconcile 的 mock HTTP 断言零写请求 | 可关闭核对入口，已有证据与保留仍可查询 |
| S2-D 隔离 Provider 验证与受控放行 | 在本机 GitLab 17.11.3 专用 fixture 验证能力及并发边界 | 版本化能力矩阵、外部请求账本、故障注入；业务场景验收由页面触发并事后核对 | 停止新受理/派发，核对在途请求；不删历史证据或未知保留 |

S2-D 拟仅使用新建、带本次测试标识的专用 GitLab 项目。测试资源记录清单和恢复/清理方案，不重置现有 seed 或业务仓库。真实项目创建、MR/文件/引用修改等 fixture 操作须包含在明确批准的隔离范围内；本轮未执行这些操作。

### 必须通过的负向验收

1. 两个本地 repoId 指向同一远端，仅一个内部写会话获准；不同远端不互相阻塞。
2. INTENT、SEND_STARTED 或结果事件落库失败时，不产生无证据的后续外部写。
3. 外部成功后业务事务回滚，证据仍存在；UNKNOWN 不能被其他旧入口绕过。
4. 租约过期、旧执行者延迟返回与并发核对，不释放未知请求保留，不覆盖新代次。
5. 创建 MR 响应丢失、合并超时、归档创建失败/目标漂移、文件部分成功、pipeline 响应丢失分别有独立证据和阻断断言。
6. 冲突检查的临时 MR/PR 创建和关闭均被记录；只读核对零 POST/PUT/PATCH/DELETE。
7. 401/403、404 无访问权限证据、分页未读完、重复操作关联标识、能力缺失，均不能证明安全重放。
8. 活跃或未知期间改 cloneUrl/provider、删除仓库或改变连接映射不能转移保留；凭据变更后仍需验证同一目标，核对失败继续阻断。
9. 原 S1 幂等预览、旧 Run 查询/导出、类型门禁与双端契约不回归。
10. UI 能解释受阻原因和最近核对结论；无绕过保护的执行按钮。

## 7. 启用、回退与评审决定

新执行器始终关闭。门禁的首次受控启用必须先停止新受理并排空旧同步调用，所有实例运行可识别保留的版本，核对基线与 Provider 能力后再开放旧业务写入口。不能混跑不识别 guard 的旧版本。

停用新写派发不等于停用保护：已经存在的活跃/未知保留必须继续拦截。回退只允许停止写入并保留证据，待外部状态与本地业务状态均核实后按可审计流程处置。不能用删除 guard 行、清库或切回旧包解除阻断。

建议批准 S2-A 至 S2-D 的完整实施范围，按序验收；批准不涵盖 S3 worker/resume、生产部署、历史数据重放或非隔离仓库写入。若仅批准 S2-A，也必须在任务清单保留 S2-B/C/D，不能把底层接口拆分当作门禁完成。

实施授权已满足：用户于范围评审完成后明确同意完整 S2，按 S2-A → S2-B → S2-C → S2-D 推进；不重复请求同范围批准。
