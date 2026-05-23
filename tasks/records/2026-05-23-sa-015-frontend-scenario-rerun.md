# 2026-05-23 SA-015 前端场景复跑与证据边界更新

## 背景

SA-015 当前要求复核 Run/窗口详情、数据质量复核队列和发布候选评审入口在新增页面后仍符合真实页面旅程原则。本轮复跑发现历史 Slice-2 失败 Run 旅程依赖 `MOCK` 仓库按本地路径执行版本更新，但当前领域枚举只保留 `GITHUB/GITLAB`，持久化层会把历史 `MOCK` 降级为 `GITLAB`，导致版本更新走真实 GitLab 预检并被冲突阻断。

## 范围

- 恢复 `MOCK` Git Provider 的领域语义，避免 mock 仓库被错误降级为 GitLab。
- 为 `MOCK` provider 提供本地分支适配器，使 UI 创建仓库、迭代挂载、发布和编排旅程仍能在本地闭环。
- 前端仓库表单补回 `MOCK` provider 选项，Slice-2 真实页面旅程明确选择 `MOCK`。
- 复跑完整 Slice-2，确认 SA-015 可由 UI 真实触发失败版本更新 Run，并从 Run 列表按 `windowKey + group + FAILED` 复核失败步骤。
- 修正 SA-011 route-stub 断言：`REPO_AHEAD` 展示“接受仓库版本”，`SYSTEM_AHEAD` 展示“同步版本”。

## 证据边界

- 真实页面旅程：Slice-2 serial 用例通过 UI 创建分组、仓库、迭代、发布窗口、挂载关系，发布窗口详情触发编排和版本更新，并由后端真实创建 `VERSION_UPDATE_FAILED` Run。
- route-level stub：冲突类型分布、Git 访问异常、部分失败 Run 详情和 SA-014 版本更新请求契约仍作为前端观察/请求语义回归，不记作真实 GitLab 强证据。
- 后端/GitLab 强证据：仍由既有 `run-acceptance.sh` 场景承担；本轮不触碰 GitLab 远端资源，不扩大功能范围。

## 实现

- `GitProvider` 恢复 `MOCK` 枚举值。
- 新增 `MockGitBranchAdapter`，支持本地分支创建、状态读取、合并、归档、标签、pipeline id 和分支列表。
- `RepositoryEdit.vue`、`repositoryApi` 和中英文 i18n 增加 `MOCK` provider。
- Slice-2 UI 创建仓库时选择 `MOCK`，并收紧发布状态、编排按钮和版本更新失败 Run 断言。
- 持久化适配器测试更新为：空 provider 仍兼容为 `GITLAB`，`MOCK` provider 必须保留为 `MOCK`。

## 验证

```bash
mvn -pl releasehub-domain,releasehub-infrastructure -am -Dtest=CodeRepositoryTest,CodeRepositoryPersistenceAdapterTest,GitBranchAdapterFactoryImplTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm run typecheck
pnpm i18n:lint
pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts
pnpm exec vitest run src/views/run/__tests__/RunDetail.spec.ts src/views/data-quality/__tests__/DataQualityReviewQueue.spec.ts src/views/release-governance/__tests__/ReleaseCandidateReview.spec.ts src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
bash scripts/dev/check-roadmap.sh
git diff --check
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 后端领域/基础设施专项测试：12 PASS / 0 FAIL / 0 SKIP。
- 前端 typecheck 通过。
- i18n lint 通过。
- Slice-2 完整复跑：23 PASS / 0 FAIL；其中 SA-013 和 SA-015 真实 UI 触发段均通过。
- 相关组件回归：17 PASS / 0 FAIL。
- roadmap 检查通过：唯一 HEAD 指向 SA-001。
- `git diff --check` 通过。
- 静态扫描通过：`.ai/reports/static-scan/20260523-212419/summary.md`；SpotBugs 0，frontend lint PASS，typecheck PASS。

## 结论

SA-015 更完整前端场景复跑已恢复并更新证据边界。`MOCK` provider 再次作为本地验收 provider 保留，失败版本更新 Run 不再被误导到真实 GitLab 冲突预检路径；当前队列可转入发布候选 dogfood/staging 验证。
