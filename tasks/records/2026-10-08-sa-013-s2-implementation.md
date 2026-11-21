# SA-013 S2 操作证据与仓库写入保护

日期：2026-10-08。用户已批准完整 S2 与专用本机 GitLab fixture；S2-A → S2-B → S2-C → S2-D 均已收口。S3–S5 未实施、未批准。

- 蓝图归属：`docs/openspec/changes/add-durable-run-recovery/design.md` 第 6–7 节。
- 实际口径：`docs/openspec/changes/add-durable-run-recovery/s2-implementation.md`。
- 变更：原子 MR 流程、UNKNOWN 语义、归档失败零删除、独立账本 V35、Run 查询索引 V36、CAS/保留、映射保护、只读核对、仓库与 Run 页面。修复旧 GitLab URI 重复编码。
- 行为/层级闭环：应用 Port、真实 HTTP 出口、持久化、API、仓库页/抽屉及 Run 页接通；默认不开启受控模式，无后台恢复。
- 测试闭环：RED/GREEN 涵盖归档、工作流、API、抽屉、URI 编码及不完整远端证据；全量单元/架构 356 PASS / 5 SKIP，定向集成 48 PASS，前端 111 PASS。
- 架构/性能：独立短事务和外部请求分离；仓库/Run 列表有界分页，不逐行请求 GitLab；MR 最多 5 页，网络读取有限超时；同仓串行，不扩展 worker 并发。
- 真实页面：仅新项目 4；UI 创建业务数据、创建分支、挂载并记录 MR；版本更新因冲突回滚产生真实 NEEDS_REVIEW，核对 MR 匹配后保留不解除。不是版本更新或恢复成功旅程。
- 真实 Provider：GitLab 17.11.3 文件旧 revision 400、MR 错 SHA 409、tag SHA 匹配、唯一分支 pipeline 201/359；歧义 ref 400 保留为负向证据。
- 回退：现存 guard 时关闭保护的真实进程拒绝启动。未知保留未删；专用数据库备份后停机保留，GitLab 项目留存审计、专用凭据撤销。
- 静态 TopN：初扫 9 项，7 修复、2 个共享 Spring Port 引用的抑制理由留档；最终复扫 0。前端 lint 0 错误 / 8 存量 warning，typecheck/i18n/build 通过，大 chunk 提示保留。
- 文档/工作区：需求、OpenSpec、路线图、台账、矩阵及经验已同步；本轮没有提交、推送或合并。上次已推送提交仍为 `5d85b17`。
- 未完成项：S3–S5 在 `tasks.md` 保留；硬中断、接管恢复、版本更新成功黄金路径及生产切换未由本轮证明。OpenSpec 仅结构检查，未运行不可用的 strict CLI。

原始脱敏日志、截图、外部请求记录、备份和 TopN 明细统一归档：`/Users/loong/backup-manifest/reports/releasehub-s2-implementation-20261008/summary.md`。
