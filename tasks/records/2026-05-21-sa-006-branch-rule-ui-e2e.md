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

- 新增 `frontend/e2e/tests/branch-rule.spec.ts`，覆盖 SA-006 分支规则管理真实页面候选旅程。
- 复用现有 Playwright 登录、i18n label 和强制点击 helper。
- 场景只验证前端用户旅程；真实 GitLab 分支创建被规则约束的强证据仍由后续全链路环境补充。

## 验证

- `pnpm exec playwright test branch-rule.spec.ts --list`
  - 识别 1 个 spec / 3 个 test；该结果只证明可发现，不作为验收通过证据。
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

- 不启动 Docker Desktop 或重建全链路验收环境，因此本记录不声明场景化验收通过。
- 不补真实 GitLab feature/release 分支创建证据。
- 不改变 BranchRule 后端业务模型。

## 2026-05-22 复验更新

- 外部 Playwright 已在本地真实前端、真实后端和 GitLab 环境下跑通本 spec 的 3 个测试。
- 页面旅程覆盖项目级规则创建、缺失项目 ID 阻止、列表搜索复核、规则测试、禁用/启用切换和测试数据清理。
- 复验期间补齐列表 scope 明细展示，管理员可在列表中直接复核项目级/子项目级规则落点。
- 复验期间修复开关成功后页面刷新误用 `reload` 的真实页面问题，避免禁用/启用成功后列表状态不重新拉取。
- 本记录的剩余非目标保持不变：真实 GitLab feature/release 分支创建被规则约束的强证据仍需继续补齐。
