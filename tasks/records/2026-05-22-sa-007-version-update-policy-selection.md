# SA-007 版本更新入口策略选择

## 目标

- 补齐 SA-007 “版本更新入口按组织/仓库范围选取默认策略”的缺口。
- 让版本更新弹窗基于所选仓库的组织和仓库范围加载可继承版本策略，并用当前版本推导目标版本。

## 变更

- `releaseWindowApi` 新增 `validateVersion` 前端封装。
- `VersionUpdateDialog.vue` 新增版本策略选择器：
  - 按所选仓库 `groupCode + repoId` 调用 `versionPolicyApi.applicable`。
  - 默认选择 applicable 返回的第一项，即后端已排序的最具体策略。
  - 调用 `repositoryApi.getInitialVersion` 获取当前版本。
  - 调用 `releaseWindowApi.validateVersion` 推导并填充目标版本。
  - 用户切换策略后重新推导目标版本。
- `VersionUpdateDialog.spec.ts` 覆盖 scoped applicable 查询、当前版本读取、默认策略推导和切换策略后重新推导。

## 验证

```bash
pnpm exec vitest run src/views/release-window/__tests__/VersionUpdateDialog.spec.ts
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-004855/summary.md`

## 结论

- SA-007 版本更新入口已按组织/仓库范围选取默认策略并推导目标版本；后续剩余重点是外部 Playwright 真实页面场景验收。
