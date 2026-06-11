# 2026-05-23 SA-002 存量清理人工复核入口

## 背景

SA-002 已能通过 `scripts/acceptance/sa002-safe-cleanup.sh` 生成 dry-run 清理报告，但动作清单仍停留在报告层，缺少进入应用层入口、人工复核输入和越权拒绝证据。路线图要求不直接改库、不自动删除 GitLab 资源，并让每条动作保留执行前检查和执行后复核口径。

## 范围

- 扩展 SA-002 dry-run 动作契约：资源类型、资源 ID、风险类型、应用入口、建议动作、执行前检查、执行后复核、人工复核决策和已执行标记。
- 新增应用层人工复核服务，登记受支持资源/风险组合，并统一拒绝直接执行、不完整动作、未知风险和已执行动作。
- 新增接口 `POST /api/v1/data-quality/cleanup-review`，供人工复核后的动作清单进入应用层入口。
- 保持脚本 `--execute` 拒绝，不修改数据库，不触碰远端 GitLab 资源。

## 变更

- `DataQualityCleanupReviewAppService` 按资源/风险组合返回权威应用入口、执行前检查和执行后复核口径。
- `DataQualityCleanupController` 提供复核 API，返回 `ACCEPTED/PENDING/REJECTED` 统计和逐动作结果，且 `executionPermitted=false`。
- `sa002-safe-cleanup.sh` 的 `actions.md` / `actions.jsonl` 输出新增人工复核字段，并修正 BranchCreationMode 审计为当前合法值 `AUTO/NAMED/EXISTING`。
- OpenSpec、场景矩阵、项目台账、脚本索引和路线图同步 SA-002 当前状态。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=DataQualityCleanupApiTest -Dsurefire.failIfNoSpecifiedTests=false test
bash -n scripts/acceptance/sa002-safe-cleanup.sh
scripts/acceptance/sa002-safe-cleanup.sh --report-dir .ai/reports/sa002-safe-cleanup/manual-review
while IFS= read -r line; do printf '%s\n' "$line" | python3 -m json.tool >/dev/null || exit 1; done < .ai/reports/sa002-safe-cleanup/manual-review/actions.jsonl
scripts/acceptance/sa002-safe-cleanup.sh --execute
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 应用层单测：6 PASS / 0 FAIL / 0 SKIP。
- API 测试：1 PASS / 0 FAIL / 0 SKIP。
- dry-run 报告生成 3 条待复核动作：2 个 DRAFT 发布窗口残留、1 个 `window_iteration.branch_created=false`。
- `actions.jsonl` 按行 JSON 校验通过。
- `--execute` 按设计拒绝。
- 路线图检查通过，唯一 HEAD 指向 SA-016。
- 静态扫描通过，报告：`.ai/reports/static-scan/20260523-152446/summary.md`。

## 非目标

- 不做自动批量删除。
- 不绕过发布窗口、迭代、仓库等应用层不变量。
- 不新增 RBAC、通知或全局依赖。
- 不把人工复核入口变成清理执行入口。

## 结论

SA-002 已从“独立 dry-run 清理计划”推进到“人工复核入口闭环”。动作清单可以进入应用层复核，越权执行和不完整动作被拒绝，脚本仍保持只读 dry-run 边界。下一路线图 HEAD 转向 SA-016 release 分支累积冲突清理执行保护证据。
