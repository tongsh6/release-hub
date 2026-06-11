# 2026-05-24 SA-002 BranchCreationMode 独立迁移服务设计

## 背景

SA-002 数据质量治理已把 `BRANCH_CREATION_MODE_MISSING_OR_INVALID` 标记为 `MIGRATION_REQUIRED`，并在处置 case 中禁止直接执行。当前切片把该暂缓项推进为独立迁移服务的设计门禁，避免从复核队列或脚本直接更新历史业务数据。

## 范围

- 新增需求文档：`docs/requirements/in-progress/SA-002-BranchCreationMode独立迁移服务设计.md`。
- 新增 OpenSpec change：`docs/openspec/changes/add-branch-creation-mode-migration-service/`。
- 明确迁移对象：仅历史 `iteration_repo.branch_creation_mode` 缺失、空白、可规范化或非法值。
- 明确排除范围：不修改 `feature_branch`、版本字段、发布窗口、迭代仓库集合、数据质量 case 或 GitLab 远端资源。
- 同步场景矩阵、项目台账、执行路线图和上一条处置审计 change 的遗留任务。

## 设计结论

- 迁移服务分为 dry-run、执行计划、受控执行、复核与回滚阶段。
- 候选分类包括 `SAFE_AUTO_DEFAULTABLE`、`NORMALIZE_LEGAL_VALUE`、`MANUAL_MAPPING_REQUIRED`、`NOT_MIGRATABLE_IN_THIS_SERVICE`。
- 无法仅凭本地数据区分 `NAMED` 与 `EXISTING` 的记录必须人工映射，不能自动写入。
- 当前只完成设计门禁，不实现执行器，不写数据库。

## 验证

```bash
bash scripts/dev/check-roadmap.sh
git diff --check
cd frontend && pnpm run typecheck
cd frontend && pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `bash scripts/dev/check-roadmap.sh` 通过，唯一 HEAD 指向 SA-002 proposal 评审门禁。
- `git diff --check`、`cd frontend && pnpm run typecheck`、`cd frontend && pnpm i18n:lint` 均通过。
- 静态扫描通过：`.ai/reports/static-scan/20260524-160003/summary.md`；SpotBugs 0，frontend lint PASS，frontend typecheck PASS。
- `openspec` CLI 本机 PATH 不可用；按本机策略未安装新工具，OpenSpec 内容采用人工结构校验。

## 结论

BranchCreationMode 独立迁移服务 proposal 已形成。下一步应先做 proposal 人工评审和实现决策；未批准前不进入 dry-run 服务、执行器或数据库迁移实现。
