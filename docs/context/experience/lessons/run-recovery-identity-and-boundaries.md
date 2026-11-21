# Run 恢复设计先核实身份与持久化边界

## 2026-10-05 设计基线

- 仅用类型和毫秒生成 Run ID 不能保证并发唯一；仅用窗口、仓库和迭代生成 Item ID 会让不同 Run 复用数据库主键。
- 真实 PostgreSQL 验证显示，复用 Item ID 保存第二个 Run 后，执行项会转移归属，旧 Run 的列表变空。
- Git 副作用不属于数据库事务；结束时才保存 Run，无法覆盖“远端已执行但本地未记录”的窗口。
- 传输超时不等于确认失败。缺少操作标识和外部证据时，直接重试可能再次发送已接收的请求。
- 数据库任务表、前端任务接口和规范描述，不等于后端任务执行器已经实现；必须核对真实模型和入口。

## 设计原则

先修复身份和历史隔离，再做持久意图、任务和恢复。数据库 fencing 不能撤销已发出的 Git 请求；过期租约只允许先接管核对，不应自动释放未知副作用的仓库保留。终态 Run 的 retry 创建新 Run，未终结状态的 resume 则需要证据和 CAS。

现状证据测试通过，只能证明风险被复现。实施切片时要把错误行为断言替换为目标不变量，不能把此类测试当作恢复功能验收。

证据：`tasks/records/2026-10-05-sa-013-run-recovery-design.md`；完整提案：`docs/openspec/changes/add-durable-run-recovery/`。

## 2026-10-08 S1 实施经验

- 新 Run/Item 使用独立 UUID，旧字符串标识照常读取；跨 Run 归属转移测试已改为历史隔离的目标断言。后续恢复能力仍未实现。
- 幂等性由认证主体与请求键共同限定，事务锁和唯一约束共同兜底；重复请求必须先返回已存快照，再考虑源数据是否变化。请求指纹与内容指纹分别表达请求相同和计划内容相同。
- Run、执行项和快照同事务写入；失败注入应验证三者全部回滚。PostgreSQL 时间字段按微秒精度归一，保证首次响应与重读响应一致。
- 本地 profile 关闭 Flyway 并使用 Hibernate 默认 schema；未限定 schema 的原生表 SQL 不能假定与 JPA 落在同一处。通过实体映射兼容两种建表路径，并在 JDBC search_path 与 Hibernate schema 不同的环境验证。
- `@MapsId` 的新快照应关联已存在的 Run 并使用 `persist`；手动设置共享主键后调用 `merge` 可能被判定为不存在的游离对象。快照仅新增也避免误覆盖历史事实。
- 测试输出目录可能残留旧 XML；汇总必须限定本轮时间和测试集合，复测不可累加为新增覆盖。

证据：`tasks/records/2026-10-08-sa-013-s1-run-plan-preview.md`。

## S2 范围评审经验

2026-10-08 对已推送 `5d85b17` 的源码核对发现：`checkMergeability` 会创建并关闭临时 MR/PR；方法名称不代表无副作用。全入口清单必须追踪到实际 HTTP 和文件写出口，不能只装饰分支工厂，漏掉文件适配器、本地文件模式和配置改向。

旧调用可能尚无已提交 RunTask。操作账本若强依赖这些业务行，外层回滚或未提交外键会破坏写前证据；应独立持久化操作及尝试事件，再由后续任务关联。对租约过期的处理必须考虑“已记录发送但尚未实际发出”的暂停窗口，不能仅靠 CAS 宣称可以安全抢占外部写权。

现有 GitLab 归档在创建返回 false 后仍删除源分支，是 S2-A 的优先修复项。该条目记录源码发现与待验收目标，不表示实现已修复。详细方案见 `docs/openspec/changes/add-durable-run-recovery/s2-review.md`。

关键词：`Run`、`中断恢复`、`幂等`、`RunItemId`、`UNKNOWN`、`事务`、`租约`、`历史隔离`、`MapsId`、`schema`、`persist`、`checkMergeability`、`归档`、`隐藏写入`。

## S2 实施与真实验证经验（2026-10-08）

- 所有内部 HTTP 写出口统一拦截，同时用装配测试检查真实分支和文件适配器；本地文件、未验证 Provider 与无原子前置条件的删除在受控模式拒绝。
- 操作独立提交后，业务回滚也不能删除外部事实。真实页面触发版本更新时，冲突检查的临时 MR 已创建/关闭，业务回滚后保留 NEEDS_REVIEW；只读核对匹配不解除保留。
- Spring Port 装饰代理须保持现有按具体类查询的兼容；CGLIB 代理通过生产加密集成回归。外层事务的同步资源需在挂起/恢复时正确解绑/重绑。
- 本机 GitLab 冷读会超过 3 秒；有限超时不能设得过短，也不能把超时当 NOT_FOUND。已编码的项目/分支路径交给 RestTemplate 时使用 URI，避免再次编码为 `%252F`。
- 运行中 JAR 不应直接使用构建时会替换的 target 文件；采用独立运行副本和 SHA 清单，否则可能出现类加载失败，误判为应用网络故障。
- GitLab 同名分支/tag 会使 pipeline ref 歧义；记录 400 拒绝，并用唯一分支名验证 201/ID。文件 last_commit_id 和 MR 源 SHA 已在 17.11.3 实测，仍不推断目标分支 CAS 或通用幂等。

证据：`tasks/records/2026-10-08-sa-013-s2-implementation.md`。未做 S3 worker、硬中断恢复或历史重放。

- 启动前保护检查须早于 WebServer 生命周期启动。ApplicationRunner 在 Tomcat 开放端口后才运行，真实日志确认有短暂窗口；使用 SmartInitializingSingleton 在单例初始化完成、服务启动前检查保留。

## S3-A/B1 组合边界经验（2026-11-21）

- 传输层 APPLIED 与业务结果已确认不是同一事实。创建 MR 的 201 只有 iid，或 merge 的 200 缺少项目/分支/SHA，都可能已产生副作用但不足以确认正确目标。装饰器不能只在异常时标记失败；正常 UNKNOWN 返回也必须进入现有会话的未决收口。无写入的只读 UNKNOWN 不制造 guard，明确审批 BLOCKED 不误冻结。
- 日志与锁使用稳定项目 ID，还必须确保真实派发 URI 也使用该 ID。namespace 可能在读取后改名或被重用；用 numeric project ID 固定最终目标，保留原 raw suffix/query，避免第二次 URL 编码。
- 独立单测全部绿色不证明组件组合安全。上述两个缺陷在完整 Spring、HTTP fixture、PostgreSQL 下先复现了实际 APPLIED/blocked=false，再验证 NEEDS_REVIEW 和后继请求零发送；测试必须观察数据库事实和远端请求计数。
- 文件 CAS 使用业务最初读取的 last_commit_id。发送前读取只用于校验，不能把新 revision 填回旧内容请求，否则 A→B→A′ 会被误认为安全写入。
- BranchStatus 只表达分支存在与 SHA，不从 release 分支存在推断 MERGED。合并结果来自 Run/Item/MR 事实，API/UI 不维持第二套推断口径。
- 导出的独立提交验证树放在已核实忽略的 `.ai/reports/validation/`，保持源工作区与 index 不变；提交前比对候选 tree、精确路径和工作区内容，不能把全工作区绿色当作部分提交可编译的证据。

证据：`tasks/records/2026-11-21-sa-013-orchestration-consistency.md`。当前仍无 worker/resume 或本轮真实 GitLab 硬中断验收。

## S3-B2 仓库级收尾与生命周期竞态（2026-11-21）

- 领域 close 的幂等不等于外部副作用幂等。CLOSED、Run、不可变计划须在同一短事务提交；重复请求只读原 receipt，不能用当前绑定重建收尾。计划指纹按 snapshot itemId 匹配来源数据，不能依赖 JPA 集合的物理读序。
- 关闭窗口的行锁必须与 Attach/Detach、Publish、Freeze/Unfreeze、Delete、Orchestrate/retry 受理共同使用。只串行 close 仍会出现“编排已受理但尚未合并，先关闭并发布旧 release”的竞态；已受理 RUNNING/NEEDS_REVIEW 意图应阻断首次 close，PLANNED 预览不能误阻断。
- 收尾按 (window, repository) 承责；用 null iterationKey 和真实参与者集合表达仓库级身份。必须核查 JPA、列表、详情、CSV/Markdown、VersionOps、OpenAPI、生成类型和组件的实际消费者，不能借第一个迭代或假迭代名掩盖范围。
- Tag 使用确认的 mergeSha，不猜默认分支 tip；Pipeline 的 APPLIED 需要 project/id/ref/SHA/url。相同 ref 不是相同执行，恢复读取也必须共享身份判定；不同大小写 SHA 判据不能一端放行、另一端再报 UNKNOWN。GitLab Pipeline API 不提供 expected SHA 原子创建条件，响应不匹配时保留未知结果，不宣称完全避免远端副作用。
- 检查点保存错误必须在 Git 异常转换之外传播，后继 Tag/CI 零派发。单个窗口不关闭共享全局 Iteration，不创建 archive copy 或删除原分支；明确记录 RETAIN。
- 测试应按权威计划识别实际 Item。同批 attachAt 相同时由稳定迭代键决定顺序，随机键的创建顺序不等于计划顺序；mock、重试选择和精确次数断言必须使用同一真实身份。
- Mockito 测试不能代替实际模拟适配器的契约验证。InMemory 是 test profile 的实际实现，必须给出合法且内部一致的合成提交和 MR 证据；不能为让替身通过而放宽真实写入验证。
- 生成的 OpenAPI 类型也是真实消费者。2.6.0 与 Boot 3.4.1 的 ABI 不匹配，2.8.17 又触发旧 Spring 的 PathPattern 缺陷；本轮实测锁定 2.8.14，不隐藏 Advice 或改变 MVC 匹配。OpenAPI 3.1 用 type union 表达 null，生成物由当前接口描述得到，不手工补另一份类型事实。

依赖定位依据：[springdoc 兼容表](https://springdoc.org/v2/)、[上游路径匹配问题 #3210](https://github.com/springdoc/springdoc-openapi/issues/3210)。实际 RED/GREEN、隔离 PostgreSQL/API 和前端结果见本轮任务记录。C1/C2 恢复执行仍单独验收，现有 run_task 表和只读 observation 不能证明 worker/resume 已存在。
