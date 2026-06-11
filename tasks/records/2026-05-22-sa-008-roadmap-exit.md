# SA-008 路线图出队与队首纠偏

## 背景

- `docs/execution-roadmap.md` 仍将 SA-008 标为 `HEAD`。
- `docs/reports/scenario-acceptance-matrix.md` 已将 SA-008 标为“已覆盖”，并记录发布窗口组织路径、组织筛选、冻结限制和删除保护均已补齐。
- `tasks/records/2026-05-21-sa-008-release-window-group-filter.md`、`2026-05-21-sa-008-freeze-ui-guard.md`、`2026-05-21-sa-008-release-window-delete-protection.md` 已分别留下实现和验证证据。

## 本轮变更

- SA-008 从执行路线图出队，不继续占用 `HEAD`。
- `docs/execution-roadmap.md` 新 `HEAD` 调整为 SA-006「分支规则真实端到端证据收口」。
- `docs/project-ledger.md` 的当前推进项和 Top Priority 同步调整为 SA-006。
- `docs/reports/scenario-acceptance-matrix.md` 的 Phase 2 缺口池补入 SA-006 P1，避免总览“部分覆盖”与缺口池断裂。

## 验收

```bash
bash scripts/dev/check-roadmap.sh
```

## 后续

- 下一切片应围绕 SA-006 真实 GitLab scoped rule 证据推进。
- 不在本轮声明 SA-006 完成；本轮只是修正队列指针和文档事实漂移。
