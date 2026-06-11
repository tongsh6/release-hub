# 2026-05-24 本地开发库可重复清理脚本

## 背景

本地开发库已积累大量验收、联调和历史遗留脏数据。一次性 SQL 不可审计，也容易与 SA-002 的生产/验收数据质量复核边界混淆。

## 范围

- 新增 `scripts/dev/cleanup-dev-database.sh` 作为本地开发阶段可重复运行的数据库业务数据清理入口。
- 默认 dry-run，显式 `--execute` 才执行清理。
- 默认目标限定为 Docker 容器 `releasehub-postgres` 和数据库 `release_hub`。
- 默认扫描 `release_hub` 和历史遗留 `public` schema。
- 默认保留 `flyway_schema_history`、`users`、`system_settings`。
- 输出 `.ai/reports/dev-db-cleanup/<timestamp>/summary.md`、`before.tsv` 和 `after.tsv`。

## 非目标

- 不替代 SA-002 dry-run、人工复核队列或处置 case。
- 不清理生产、CI 或未知数据库。
- 不触碰 GitLab 远端仓库、分支或 token。
- 不删除 schema 或 Flyway 迁移元数据。

## 验证

```bash
bash -n scripts/dev/cleanup-dev-database.sh
bash scripts/dev/cleanup-dev-database.sh --report-dir .ai/reports/dev-db-cleanup/manual-verify
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
git diff --check
```

## 结论

- 脚本语法检查通过。
- dry-run 报告已生成：`.ai/reports/dev-db-cleanup/manual-verify/summary.md`。
- 当前本地开发库识别出 27 张候选业务表、5382 行候选清理数据；默认保留 4 行用户/系统设置数据。
- roadmap 检查通过，当前 HEAD 仍指向 SA-002。
- 静态扫描通过：`.ai/reports/static-scan/20260524-194230/summary.md`。
- 本轮未执行 `--execute`，避免在未显式确认执行前静默清空开发库业务数据。
