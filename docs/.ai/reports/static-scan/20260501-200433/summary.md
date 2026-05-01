# 静态代码扫描与 TopN 处理报告

- 时间：2026-05-01 20:04:33 +0800
- 仓库：`/Users/loong/workspace/code/github/releasehub`
- TopN：10

## 变更范围

- `.DS_Store`
- `.ai-adapters/README.md`
- `.ai-adapters/claude-code/README.md`
- `.ai-adapters/cursor/rules/project.mdc`
- `.ai/QUICK_REFERENCE.md`
- `.ai/README.md`
- `.ai/agents/implement.md`
- `.ai/agents/test.md`
- `.ai/commands/code-review.md`
- `.ai/summaries/experience-index.md`
- `.github/copilot-instructions.md`
- `AGENTS.md`
- `context/experience/INDEX.md`
- `context/tech/architecture/ai-engineering-governance.md`
- `workflow/INDEX.md`
- `workflow/phases/design.md`
- `workflow/phases/implement.md`
- `workflow/phases/proposal.md`
- `workflow/phases/review.md`

## 扫描命令

- git-diff-check: `git diff --check`
  - 状态：PASS
- backend-static-scan: `true`
  - 状态：SKIPPED，目录不存在
- frontend-static-scan: `true`
  - 状态：SKIPPED，目录不存在

## TopN 问题清单

| # | 扫描来源 | 问题摘要 | 优先级依据 | 处理方式 | 处理结果 | 复扫证据 |
|---|----------|----------|------------|----------|----------|----------|
| 1 | 全部扫描 | 未提取到错误/警告/安全类问题 | 无 | 无需处理 | PASS | 本报告 raw 日志 |

## TopN 处理要求

- AI 必须优先处理上表 TopN；若跳过某项，必须在“处理方式”中写明原因。
- 修复后必须再次运行本脚本或对应最小静态扫描命令。
- 最终交付必须引用本报告路径，并说明 TopN 中每项的处理结果。
- 原始扫描日志保留在 `raw/` 目录。
