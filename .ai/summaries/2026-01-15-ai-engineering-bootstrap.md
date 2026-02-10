# 会话摘要：AI 工程化落地初始化（2026-01-15）

## 目标

在 ReleaseHub 仓库中补齐 AI 工程化三层结构（Agent/Skill/Command）并落地最小质量门禁，使“AI 产出可验证、可复用、可审计”。

## 关键变更

- 统一 `.ai` 文档中对经验索引与会话摘要的路径引用为 `.ai/summaries/`
- 新增默认项目摘要：`.ai/summaries/project-context.md`
- 补齐技能与角色文档：
  - Skills：`skill-task-analyzer`、`skill-openspec-gate`、`skill-session-summarizer`
  - Agents：`proposal`、`design`、`implement`、`test`、`archive`
  - Commands：`fix-bug`、`refactor`、`code-review`、`plan-change`
- 补齐经验文档并更新索引：
  - ReleaseWindow 冻结/解冻：`context/experience/lessons/release-window-freeze-pattern.md`
  - 版本策略校验：`context/experience/lessons/version-policy-validation.md`
- 对齐验证门禁：
  - 新增根目录 CI：`.github/workflows/ci.yml`
  - 新增本地一键验证脚本：`scripts/dev/verify-all.sh`
  - 修复前端 `pnpm test` 误跑 e2e：`release-hub-web/vite.config.ts` 排除 `e2e/**`

## 验证结果

- 后端：`mvn -q -B test` 通过
- 前端：`pnpm typecheck`、`pnpm lint`、`pnpm test`、`pnpm build` 通过

## 风险与后续

- e2e 需要显式走 `pnpm test:e2e`，建议在 CI 增加可选 job（依赖后端服务时用条件触发）
- `scripts/dev/verify-all.sh` 如需在 CI 直接使用，可在仓库中标记可执行位
