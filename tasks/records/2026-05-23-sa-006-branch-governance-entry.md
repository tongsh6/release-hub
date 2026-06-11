# SA-006 历史不合规分支治理入口

## 完整目标蓝图

### 最终行为

- 管理员配置分支规则后，不仅能在仓库同步统计中看到不合规数量，还能在仓库详情中看到活跃历史不合规分支清单。
- 治理入口明确动作边界：本阶段只做可见性和安全引导，不自动重命名、删除或归档历史分支。
- 归档分支、默认分支和基础分支不进入历史不合规治理清单，避免把生命周期归档结果误报为治理风险。

### 范围

- 后端：新增仓库维度只读治理读模型，复用 BranchRule 作用域合规判断。
- Git 适配：允许 `GitBranchPort.listBranches(..., "")` 表达列出全部分支。
- API：新增 `GET /api/v1/repositories/{id}/branch-governance/noncompliant`。
- 前端：仓库详情抽屉和详情页展示不合规分支清单与人工治理边界。
- 文档：同步场景矩阵、台账、路线图和 OpenSpec。

### 非目标

- 不做历史分支自动重命名、删除或归档。
- 不做跨仓批量修复任务。
- 不改变 BranchRule 匹配语义。
- 不改变仓库同步统计的既有归档排除规则。

## Slice：仓库维度只读治理入口

- 蓝图归属：完整目标中的“历史不合规分支可见性 + 安全引导”。
- 目标：管理员能从仓库详情看到活跃历史不合规分支，而不是只看到统计数量。
- 涉及层：Application、Infrastructure、API、Frontend、Test、Docs。
- 依赖：既有 BranchRule scoped compliance、GitBranchPort、仓库详情页面。
- 后续：SA-006 后续保持回归；下一队首转向 SA-013。

## 变更

- 新增 `BranchGovernanceAppService`，按仓库读取远端分支并使用 `repo.groupCode` + `repoId` 作为 BranchRule 作用域上下文判断合规性。
- 治理清单排除 `archive/...`、默认分支、`main`、`master`、`develop`，只返回需要人工复核的活跃历史分支。
- 新增仓库治理 API，并在仓库详情抽屉和详情页展示分支名、作用域和安全处理建议。
- 将治理展示收敛到 `BranchGovernancePanel.vue`，避免抽屉和详情页列定义漂移。

## 验证

```bash
mvn -q -pl releasehub-application -am -Dtest=BranchGovernanceAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-bootstrap -am -Dtest=RepositorySyncApiTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-infrastructure -am -Dtest=GitLabGitBranchAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/repository/__tests__/RepositoryDrawer.spec.ts src/views/repository/__tests__/RepositoryDetail.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `BranchGovernanceAppServiceTest` 覆盖活跃不合规分支识别、作用域参数传递、归档分支排除和基础分支排除。
- `RepositorySyncApiTest` 覆盖治理 API 响应契约和 `MANUAL_REVIEW_ONLY` 动作边界。
- `GitLabGitBranchAdapterTest` 覆盖空前缀列出全部分支，支持治理读模型读取历史分支。
- 前端仓库详情抽屉/详情页专项通过，确认页面内可见历史不合规分支和人工治理边界。
- 前端 typecheck、i18n lint、路线图检查和静态扫描均通过；静态扫描报告：`.ai/reports/static-scan/20260523-005017/summary.md`，TopN 未发现代码问题。

## 结论

- SA-006 历史不合规分支治理入口已补齐，后续保持回归。
- 下一步按路线图推进 SA-013 发布编排结果复核与失败 Run 观察。
