# 2026-05-23 SA-016 种子分支清理执行保护证据

## 背景

SA-016 关闭窗口和 GitLab 收尾证据已闭环，但本地验收 GitLab 的三个种子仓库长期累积 release、feature 和 archive 分支，会影响 clean-room 复现和后续真实 GitLab 验收效率。已有 `reset-gitlab-seed-branches.sh` 能 dry-run 或 `--execute`，但缺少执行前候选、执行后状态、种子分支保护和重复执行幂等的结构化证据。

## 范围

- 强化 `scripts/e2e/reset-gitlab-seed-branches.sh`，默认生成审计报告。
- 固定清理范围为三个种子仓库：`seed-repo-1-maven`、`seed-repo-2-maven-multi`、`seed-repo-3-gradle`。
- 使用脚本内置 allowlist 保护 `main` 和 seed feature 分支。
- 通过 dry-run、execute 和 repeat execute 留下真实本地 GitLab 证据。

## 变更

- 脚本新增 `--report-dir`，每轮输出：
  - `summary.md`
  - `branches.md`
  - `branches.jsonl`
- 每条分支事件记录仓库、Project ID、分支、动作、HTTP 状态和保护原因。
- dry-run 记录 `KEEP` / `WOULD_DELETE`。
- execute 记录 `KEEP` / `DELETE`，并在删除后复核 `POST_KEEP` / `POST_REMOVED`。
- 如果 execute 后仍有非种子分支存在，脚本以失败退出。

## 验证

```bash
bash -n scripts/e2e/reset-gitlab-seed-branches.sh
scripts/e2e/reset-gitlab-seed-branches.sh --help
scripts/e2e/reset-gitlab-seed-branches.sh --report-dir .ai/reports/gitlab-seed-branch-reset/sa016-dry-run-before
scripts/e2e/reset-gitlab-seed-branches.sh --execute --report-dir .ai/reports/gitlab-seed-branch-reset/sa016-execute
scripts/e2e/reset-gitlab-seed-branches.sh --execute --report-dir .ai/reports/gitlab-seed-branch-reset/sa016-execute-repeat
python3 - <<'PY'
import json
for path in [
    ".ai/reports/gitlab-seed-branch-reset/sa016-dry-run-before/branches.jsonl",
    ".ai/reports/gitlab-seed-branch-reset/sa016-execute/branches.jsonl",
    ".ai/reports/gitlab-seed-branch-reset/sa016-execute-repeat/branches.jsonl",
]:
    with open(path) as f:
        for line in f:
            json.loads(line)
PY
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- dry-run：发现 799 个非种子候选；保留 7 个种子分支；缺失种子分支 0。
- execute：删除 799 个非种子分支；执行后 `POST_REMOVED=799`；7 个种子分支 `POST_KEEP`；缺失种子分支 0。
- repeat execute：删除 0 个分支；7 个种子分支继续 `POST_KEEP`。
- 三份 JSONL 均按行合法，未出现 `DELETE_FAILED`、`POST_UNEXPECTED_PRESENT` 或 `POST_MISSING_SEED`。
- 路线图检查通过，唯一 HEAD 指向 SA-014。
- 静态扫描通过，报告：`.ai/reports/static-scan/20260523-154248/summary.md`。

## 非目标

- 不把脚本扩展为通用生产分支清理工具。
- 不删除默认分支、seed feature 分支或非固定种子仓库中的分支。
- 不读取或修改 ReleaseHub 数据库。
- 不改变 SA-016 关闭窗口、归档、tag、CI 或 retry 语义。

## 结论

SA-016 release 分支累积冲突清理已从“脚本存在”推进到“受控执行证据闭环”。当前本地 GitLab 种子仓库已恢复为 clean-room 状态；下一路线图 HEAD 转向 SA-014 空仓库版本解析真实 GitLab 证据。
