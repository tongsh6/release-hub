# SA-007 版本策略前端管理

## 目标

- 补强 SA-007 “管理员在版本策略页配置 scoped policy”的前端入口。
- 让管理员能在版本策略页创建和删除 GLOBAL / PROJECT / SUB_PROJECT 范围的策略。

## 变更

- `versionPolicyApi` 已提供 scoped policy create/remove/applicable 调用和 scope 类型。
- `VersionPolicyList.vue` 新增：
  - 创建策略按钮和弹窗。
  - scheme、bumpRule、scopeLevel、scopeProjectId、scopeSubProjectId 表单字段。
  - PROJECT / SUB_PROJECT 作用域必填校验。
  - 列表 scope 展示和删除入口。
- i18n 补充版本策略 scope 文案和通用删除确认文案。
- 新增 `VersionPolicyList.spec.ts`，覆盖项目作用域必填校验、作用域切换清理、子项目 scoped create payload 和删除后 reload。

## 验证

```bash
pnpm exec vitest run src/views/version-policy/__tests__/VersionPolicyList.spec.ts
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-004119/summary.md`

## 结论

- SA-007 前端已具备 scoped policy 创建/删除基础管理能力；后续剩余重点是完整 UI E2E、编辑入口、版本更新入口按组织/仓库范围选取默认策略。
