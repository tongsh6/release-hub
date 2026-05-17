# Slice: SA-016 发布窗口报告导出

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` 第七节当前推进队列，P1「SA-016 收尾扩展」
- **日期**：2026-05-17
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | 通过 | 属于 SA-016 发布后收尾证据沉淀的报告导出扩展 |
| 用户价值 | 通过 | 发布经理可从发布窗口详情导出窗口级执行报告 |
| 端到端路径 | 通过 | 覆盖 Application、Infrastructure、API、Frontend、Test、文档 |
| 单一目标 | 通过 | 只补窗口报告导出，不扩展 PDF、制品上传或 CI 真实触发 |
| 可独立验证 | 通过 | MockMvc、Vitest、typecheck、i18n lint 可独立验证 |
| 可回滚 | 通过 | 新增 API/入口和文档，可单独回退 |
| 依赖明确 | 通过 | 复用 ReleaseWindow、Run、RunItem、RunStep 既有数据 |
| 风险收敛 | 通过 | 无新表和迁移；CSV 做转义，避免消息中的逗号/换行破坏格式 |

## 变更

- 新增 `ReleaseWindowReportController`，提供：
  - `GET /api/v1/release-windows/{id}/report.json`
  - `GET /api/v1/release-windows/{id}/report.csv`
- 新增 `ReleaseWindowReportView`，按窗口汇总 window 基本信息、Run、RunItem、RunStep、结果分布。
- `RunPort`/JPA 增加 `findByWindowKey`，避免报告导出依赖分页大小。
- `ExportAppService` 复用 Run 导出能力，并补齐 CSV 标准转义。
- 发布窗口详情页新增“导出报告”按钮，打开窗口级 CSV 报告。
- `docs/context/tech/api/release-window.md`、`docs/project-ledger.md` 和 `docs/reports/scenario-acceptance-matrix.md` 已同步 SA-016 最新状态。

## 验证

```bash
mvn -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
mvn -q -B -DskipTests install
mvn -q -B -DskipTests com.github.spotbugs:spotbugs-maven-plugin:4.9.8.3:check
```

结果：

- MockMvc 通过，覆盖窗口报告 JSON/CSV。
- ReleaseWindowDetail Vitest 通过，覆盖 CSV 导出入口。
- 前端 typecheck 和 i18n lint 通过。
- 静态扫描报告：`.ai/reports/static-scan/20260517-225202/summary.md`，TopN 未发现代码问题。
- 静态扫描脚本 raw 中的 SpotBugs 命令存在本地 reactor 快照解析噪音，已补跑 `mvn install` 后的 SpotBugs check，结果通过。
- `pnpm run test -- src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` 的目标用例全部通过，但该脚本默认启用全局 coverage，窄范围运行因全局函数覆盖率阈值返回失败；目标用例已由 `pnpm exec vitest run ...` 复核通过。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | 通过 | 发布窗口详情页可导出窗口级 CSV，后端支持 JSON/CSV |
| 层级闭环 | 通过 | Application、Infrastructure、API、Frontend 均已接通 |
| 测试闭环 | 通过 | 先补失败 API 契约，再实现并通过目标测试 |
| 架构闭环 | 通过 | Domain 不依赖框架，Application 通过 Port 查询 Run，Interfaces 仅做导出控制器 |
| 性能闭环 | 通过 | 使用窗口维度查询，不用分页拉全量；报告按已存在 Run 明细流式组装字符串 |
| 文档闭环 | 通过 | 台账、场景矩阵、任务记录已同步 |
| 工作区闭环 | 通过 | 已检查 `git status --short`，无无关用户改动 |

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| PDF/制品归档报告 | 超出本切片 | `docs/reports/scenario-acceptance-matrix.md` SA-016 P2 |
| CI pipeline 真实触发 | 超出本切片 | `docs/reports/scenario-acceptance-matrix.md` SA-016 P2 |

## 经验沉淀

- [x] 不需要，本次复用既有报告导出、MockMvc 和静态扫描流程。
