# SA-006 分支规则页面用户旅程 E2E

日期：2026-05-21

## 用户旅行图

1. 系统管理员进入分支规则页面。
2. 管理员创建项目级分支规则；缺少项目 ID 时页面直接阻止保存并显示校验错误。
3. 管理员补齐项目 ID 后保存规则，并通过搜索在列表中复核规则作用域。
4. 管理员在规则行内测试 `feature/SA-006` 是否匹配 `feature/{key}`。
5. 管理员切换规则禁用/启用状态，确认命名规范可被运维开关控制。
6. 测试结束后清理本轮创建的规则，避免污染后续验收数据。

## 实现范围

- 新增 `frontend/e2e/tests/branch-rule.spec.ts`，覆盖 SA-006 分支规则管理页面旅程。
- 复用现有 Playwright 登录、i18n label 和强制点击 helper。
- 场景只验证前端用户旅程；真实 GitLab 分支创建被规则约束的强证据仍由后续全链路环境补充。

## 验证

- `pnpm exec playwright test branch-rule.spec.ts --list`
  - 识别 1 个 spec / 3 个 test。
- `pnpm exec playwright test branch-rule.spec.ts`
  - BLOCKED：本机 `localhost:5173` 未启动，首个 `page.goto('/')` 返回 `ERR_CONNECTION_REFUSED`；`scripts/dev/start-local-env.sh status` 显示 Docker、PostgreSQL、GitLab、后端和前端均未就绪。
- `pnpm run typecheck`
  - PASS
- `pnpm run lint`
  - PASS
- `pnpm exec vitest run src/views/branch-rule/__tests__/BranchRuleList.spec.ts`
  - 4 PASS / 0 FAIL
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260521-233416/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不启动 Docker Desktop 或重建全链路验收环境。
- 不补真实 GitLab feature/release 分支创建证据。
- 不改变 BranchRule 后端业务模型。
