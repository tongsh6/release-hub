# S2 实施与验收口径

日期：2026-10-08。状态：S2-A 至 S2-D 完成；生产默认关闭，S3–S5 未实施。用户已批准完整 S2 和新建本机 GitLab 专用 fixture。基线为已推送的 `5d85b17`，本轮改动尚未提交或推送。

## 已交付行为

| 切片 | 实现与验收结果 |
|---|---|
| S2-A | MR 创建、读取、合并、关闭拆分；移除合并 405 隐式重发；归档创建失败不删除源分支；GitHub mergeable=null 不算成功；引用 SHA、MR 状态与远端 ID 不完整时保留 UNKNOWN |
| S2-B | V35 新增独立操作、事件、观察、仓库保留四表，V36 补齐 Run 分页索引；意图与发送标记独立提交，网络调用在账本事务外；同远端串行、CAS/代次校验、业务回滚和部分成功保留；映射变更与写入准入共用事务锁 |
| S2-C | 仓库详情页和列表抽屉、Run 详情接入同一证据面板；支持有界分页、尝试时间线、操作者、幂等只读核对；观察不触发重放或解除保留 |
| S2-D | GitLab 17.11.3 项目 4、独立 PostgreSQL 与端口完成真实页面和 Provider 能力验证；有保留时关闭保护会拒绝启动；保留真实失败和阻断证据 |

操作账本不依赖未提交 Run 的外键。业务回滚后来源 Run 可能不存在，页面只展示关联标识并解释原因，避免链接到不存在的记录。Run 聚合的 UNKNOWN/resume 统一语义仍在 S3。

## 受控范围与限制

- 配置 `RELEASEHUB_GIT_SAFETY_ENABLED` 默认 `false`。启用前须停止所有实例的新写受理、排空旧调用，统一升级到识别保留的版本，再设置为 `true`。本轮仅在专用环境开启。
- 受控写仅支持与 GitLab Settings 完全一致的服务地址；以 Provider、规范地址、远端项目 ID 形成保留键。无法验证的主机/协议别名拒绝准入，同项目路径别名共享远端保留。
- GitHub 写操作、Mock/InMemory 写适配器、本地文件回退在受控模式拒绝；GitHub 核对返回 UNSUPPORTED。没有验证 GitHub 的真实服务器能力。
- GitLab 分支删除没有已验证的原子 SHA 前置条件，受控模式拒绝自动删除。归档可以留下已创建的归档引用，不能承诺自动完成删除；此限制不能用“先读后删”绕过。
- 有任意活跃保留时，仓库改向/删除、GitLab 服务地址变更被保守阻断。同目标凭据更新不释放保留；核对仍验证项目身份。
- 账本证据不含 Token、文件内容或原始响应；网络错误保存稳定原因。只读核对每次请求超时 10 秒，MR 最多 5 页；列表最多 50 条、详情最多 100 事件及最近 50 观察，超限不推断安全重放。
- UNKNOWN、SEND_STARTED、NEEDS_REVIEW 不因时间流逝自动解除。不混跑旧包，不通过清库、删除 guard 或关闭保护恢复写入。

## API

所有接口要求现有认证，操作者来自服务端身份。`page` 从 0 开始，`size` 为 1–50。

| 方法与路径 | 结果 |
|---|---|
| GET `/api/v1/repositories/{id}/operations` | 按仓库查询操作摘要 |
| GET `/api/v1/runs/{id}/operations` | 按来源 Run 查询摘要 |
| GET `/api/v1/git-operations/{id}` | 操作、事件与观察详情 |
| POST `/api/v1/git-operations/{id}/reconcile` | Header `Idempotency-Key`，正文 `{"expectedStateVersion": 2}`；只发外部 GET，保存观察 |

相同操作/操作者/键及期望版本返回已保存观察；同键不同版本或过时版本返回 409。FOUND 只表示当前证据匹配，不代表可以重发、删除或解除阻断。FORBIDDEN、NOT_FOUND、UNAVAILABLE、INCOMPLETE、MISMATCH、UNSUPPORTED 保持区别。

## 真实页面与外部能力证据

专用项目：`root/releasehub-s2-20261008-0504-c8cc38`（ID 4）；没有重置 seed 或修改既有业务项目。

1. 页面登录、创建组 `001`、保存专用连接、测试连接、纳管仓库、创建迭代 `ITER-20261008-4AA1` 并添加仓库。真实 feature 分支创建，账本 APPLIED；页面只读核对匹配基线 SHA `fc91bf310319b3d9db01cf2533b1a8d9a39f25ce`。
2. 页面创建窗口 `RW-20261008-1689` 并关联迭代。真实 release 分支和 MR 创建/关闭被记录，挂载 Run `921760f4-ac46-4126-8da6-aea1911b1e46` 完成。
3. 页面提交版本更新时，冲突检查创建/关闭临时 MR，随后业务因冲突回滚。4 条操作进入 NEEDS_REVIEW、保留仍在；没有把这次版本更新写成成功旅程。
4. 页面核对 MR !3，确认 `state=closed`、IID 和 SHA 匹配，阻断仍然存在。截图记录 operation `c0644ee8-fd3e-4795-932e-75fd290d35f1` 的远端确认、业务待复核和匹配观察。
5. 外部 fixture 的独立能力分支验证：文件正确 revision 更新成功，过期 `last_commit_id` 返回 400 且提交不变；错误 MR 源 SHA 返回 409；tag 指向准确 SHA；唯一名称分支创建 pipeline 返回 201、ID 359。同名 branch/tag 的 pipeline 返回 400“Ref is ambiguous”，没有将拒绝当作成功。
6. 真实进程以关闭保护的配置连接该测试库时退出码 1，明确拒绝现存保留；最终检查在服务端口开放前执行，避免 ApplicationRunner 的短暂启动窗口。没有删除保留，也没有做恢复重放。

页面由当前桌面 CUA/Playwright 接口驱动。不是既有 `frontend/e2e` CLI 全套复跑，不增加历史 E2E PASS 数。仓库既有全量验收脚本会准备 seed/业务资源，本轮使用批准的隔离 fixture 路径，避免扩大资源范围。外部能力 API 测试与 ReleaseHub 页面旅程分别统计。

## 验证结果与已知边界

- 后端全量单元/架构：356 PASS、5 SKIP（361 项，按本轮 XML 统计）；S2、计划预览、历史隔离、初始管理员、生产加密和 Pact 定向集成：48 PASS。
- 前端 34 文件 / 111 PASS；证据组件在最后文案调整后再次通过 3 项定向检查；typecheck、i18n、lint、生产构建通过。lint 仍有 8 个存量 warning，构建保留大 chunk 提示。
- SpotBugs 初扫 9 项：修复 7 项，2 项共享 Spring Port 引用暴露按不可复制依赖记录抑制理由；复扫 0 项。没有升级工具或依赖。
- 新增回归覆盖写前提交失败、发送标记失败、结果提交失败、丢响应、同仓别名竞争、旧代次、回滚、文件部分成功、合并未知不关闭/重发、pipeline 缺失 ID、权限/分页/重复关联标识及核对幂等。
- 本机 GitLab 冷读超过原 3 秒超时曾产生 UNAVAILABLE；调整为有界 10 秒后核对成功。旧 GitLabAdapter 路径重复编码也通过 RED/GREEN 修复。
- 没有执行硬杀进程恢复、双 worker 接管、生产切换、历史重放或完整版本更新成功旅程；这些不能由 S2 的 mock HTTP 或只读核对替代。
- OpenSpec CLI 不可用且未安装；只完成结构/引用和路线图检查，不声称 strict validate。

报告及静态扫描证据：`/Users/loong/backup-manifest/reports/releasehub-s2-implementation-20261008/summary.md`。任务记录：`tasks/records/2026-10-08-sa-013-s2-implementation.md`。下一 HEAD 为 S3 实施范围评审，实施仍需独立批准。
