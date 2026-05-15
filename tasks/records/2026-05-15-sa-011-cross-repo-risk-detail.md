# 2026-05-15 SA-011 Cross Repo Risk Detail

## 任务

按项目台账当前 P1 队列，补 SA-010/SA-011「更多真实冲突类型详情」中的 `CROSS_REPO_VERSION_MISMATCH` 前端观察路径。

## 变更

- `frontend/e2e/tests/slice-2-full-flow.spec.ts` 新增 Slice-2 serial 场景：在 UI 创建出的发布窗口、迭代和仓库上下文中模拟 `CROSS_REPO_VERSION_MISMATCH` + `MISMATCH` 风险报告。
- 新场景断言冲突面板展示跨仓库版本冲突类型分布、系统/仓库版本差异、阻断级别和建议处理方式。
- 新场景确认跨仓库版本冲突不会展示应用内“同步版本”按钮。
- 顺手收窄冲突类型筛选测试选择器，直接点击对应 radio input，避免中文类型名包含关系导致误匹配。
- `docs/reports/scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 已同步 SA-011 最新证据。

## 验证

- `pnpm --dir frontend run typecheck`：通过。
- `pnpm --dir frontend i18n:lint`：通过。
- `pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"`：通过，9 tests。
- `mvn install -DskipTests`（backend）：通过，用于补齐本地 SNAPSHOT 依赖后复跑静态扫描。
- `scripts/dev/static-scan-topn.sh 10`：通过，报告 `.ai/reports/static-scan/20260515-145053/summary.md`；TopN 未发现代码问题。

## 后续

- SA-010/SA-011 仍保留更多真实冲突类型后端/GitLab 强证据和部分失败重试。
- SA-015 仍保留冲突详情和部分失败复核旅程。
