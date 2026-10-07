# SA-013 发布编排中断恢复设计交付

## 本轮结果

完成当前 HEAD 的设计与复现范围。需求及 OpenSpec 提案已互链，设计覆盖计划、身份、事务、任务状态、外部核对、并发门禁、API/UI、迁移、回滚和 S1–S5。生产执行逻辑、新迁移、恢复 API 和 worker 均未实现。

提案：`docs/openspec/changes/add-durable-run-recovery/`。首批建议只批准 S1：身份隔离与可查询持久计划预览，零 Git 写入。`review.md` 状态为 READY_FOR_REVIEW，不代表用户批准。

## 现状证据

| 证据 | 观察 |
|---|---|
| 创建分支后读取失败 | 模拟分支已创建，RunPort.save 未调用 |
| 远端成功但响应丢失 | 被记为 FAILED，当前 retry 再次发送合并请求；不宣称真实 Git 已合并两次 |
| Run 保存失败 | 模拟合并已发生，持久化调用随后失败 |
| 身份碰撞 | 同类型同毫秒 Run ID 相同；同范围不同运行的 Item ID 相同 |
| PostgreSQL 跨运行关联 | 第二次保存后旧 Run 的执行项数为 0，新 Run 为 1 |
| PostgreSQL 事务回滚 | 非空约束导致 Run 未落库，事务外测试对象状态仍保留 |

新增 6 项测试用于固化本轮观察；它们不是目标恢复行为。S1/S3 实现时必须替换成身份不碰撞、历史不丢项、意图先持久化等正确断言。

## 验证

- RunAppService 定向回归：17 PASS，其中 4 项为新增证据。
- ArchitectureRulesTest：4 PASS。
- RunRecoveryPersistenceEvidenceIT：2 PASS，使用隔离 PostgreSQL 18.1，无真实 GitLab 写入。
- SpotBugs、前端 typecheck、diff、roadmap 通过；ESLint 0 error / 8 条既有测试警告。
- 11 个 Requirement、27 个 Scenario，MODIFIED 名称、本地链接和需求互链校验通过。
- OpenSpec CLI 本机不可用，未安装；未声称官方 strict validate 通过。

归档报告：`/Users/loong/backup-manifest/reports/releasehub-run-recovery-design-20261005/summary.md`。

## 范围与剩余项

未执行真实 kill -9 / 重启恢复、真实 GitLab 写入或历史数据迁移；当前生产风险仍然存在。GitLab 当前官方文档不能代替仓库使用的 17.11.3 fixture 验证，Provider 能力在 S2 验收。

S0 已完成；提案及 S1 待用户评审，S1–S5 全部保留在 tasks.md，未删除、未提前标完成。下一 HEAD 是提案评审与 S1 范围确认；明确批准后可直接进入 S1，不重复询问。

## 2026-10-08 类型检查结论更正

此前 `pnpm typecheck` 的成功退出来自仅含 references、files 为空的根配置，实际检查文件数为 0，不能代表应用源码类型检查通过。显式 `vue-tsc --noEmit -p tsconfig.app.json` 对当前代码报告 44 个存量错误，HEAD 基线 45 个，S1 新增 0 个。类型门禁已重新列为 SA-018 队首；原测试和构建结果不受此更正影响。详见 `tasks/records/2026-10-08-sa-013-s1-run-plan-preview.md`。
