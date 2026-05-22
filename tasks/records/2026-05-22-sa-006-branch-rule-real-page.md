# SA-006 分支规则真实页面管理旅程

日期：2026-05-22

## 背景

- 路线图 HEAD 已回到 SA-006，目标是收口“规则配置 -> 分支创建被规则约束”的端到端证据。
- 既有 Playwright 只完成候选 spec 可发现性；本轮先把分支规则管理页从候选旅程推进到真实页面可操作证据。
- 本地环境启动时遇到历史 schema 漂移，另以 `2026-05-22-local-schema-scope-default.md` 记录本地自恢复修复。

## 产品验收范围

1. 管理员打开分支规则页。
2. 创建项目级规则时，缺少项目 ID 会被页面直接阻止。
3. 补齐项目 ID 后保存规则，并在列表中看到作用域标签和项目 ID 明细。
4. 在规则行内测试 `feature/SA-006` 命中 `feature/{key}`。
5. 禁用规则后看到成功提示，再启用规则并看到成功提示。
6. 测试结束后删除本轮规则，避免污染后续验收数据。

## 实现

- 分支规则列表展示 scope 细节：项目级显示 `projectId`，子项目级显示 `projectId / subProjectId`。
- 修复列表页开关成功后调用不存在的 `reload`，改为使用 `useListPage` 暴露的 `fetch` 重新加载列表。
- Playwright 选择项目级 radio 时使用精确文本匹配，避免 `项目级` 误匹配到 `子项目级`。
- 单测补充 `scopeDetail` 行为，保证列表复核信息不会退化。

## 验证

```bash
mvn -q -pl releasehub-infrastructure -am -DskipTests compile
pnpm exec vitest run src/views/branch-rule/__tests__/BranchRuleList.spec.ts
pnpm exec playwright test e2e/tests/branch-rule.spec.ts
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

结果：

- 后端 infrastructure 编译通过。
- 分支规则列表单测通过。
- SA-006 Playwright 真实页面旅程 3/0/0 通过。
- 前端 typecheck、lint 和静态扫描通过；静态扫描报告：`.ai/reports/static-scan/20260522-231950/summary.md`。

## 剩余缺口

- 本轮证明分支规则管理页真实可用，不声明 SA-006 完成。
- 下一步仍需用真实后端和真实 GitLab 证明最具体规则会约束 feature/release 分支创建：合规分支可创建，不合规 `NAMED` 分支会在写入前拒绝。
