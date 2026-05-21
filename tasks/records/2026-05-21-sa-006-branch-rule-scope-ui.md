# SA-006 分支规则作用域表单校验

日期：2026-05-21

## 用户旅行图

1. 系统管理员进入分支规则页面，新增或编辑一条命名规范。
2. 管理员选择全局、项目级或子项目级作用域。
3. 项目级规则必须填写项目 ID；子项目级规则必须同时填写项目 ID 和子项目 ID。
4. 管理员可在页面内用目标分支名测试当前规则，确认命名规范是否匹配。

## 实现范围

- BranchRule 页面增加作用域字段动态校验，避免项目级/子项目级规则缺失必要 ID。
- 切换作用域时清理不再适用的旧 ID，避免 GLOBAL/PROJECT 请求携带脏 scope 字段。
- 补齐中英文 i18n 校验文案。
- 新增 BranchRuleList Vitest，覆盖作用域校验、切换清理、创建请求参数和规则测试入口。

## 验证

- `pnpm exec vitest run src/views/branch-rule/__tests__/BranchRuleList.spec.ts`
  - 4 PASS / 0 FAIL
- `pnpm run typecheck`
  - PASS
- `pnpm run i18n:lint`
  - PASS
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260521-232649/summary.md`
  - `git diff --check` PASS
  - `frontend lint` 仅报告既有 `ConflictPanel.spec.ts` 3 个 warning，非本切片新增文件
  - `frontend typecheck` PASS
  - `backend SpotBugs` 原始日志为 Maven reactor 依赖解析问题；补跑 `mvn -pl releasehub-domain -am -DskipTests spotbugs:check` 后仍因项目未配置 `spotbugs` prefix / Maven metadata 异常失败，非本前端切片引入代码告警

## 非目标

- 不改变 BranchRule 后端作用域模型。
- 不新增真实 GitLab 分支创建 E2E；SA-006 仍需后续补全“规则配置 → 分支创建被规则约束”的端到端证据。
