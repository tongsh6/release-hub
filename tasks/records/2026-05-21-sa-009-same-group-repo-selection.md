# SA-009 同分组仓库选择与写入保护

## Slice: 迭代添加仓库分组边界收口

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-009
- **日期**：2026-05-21
- **执行者**：AI
- **状态**：已完成

## 选题理由

SA-009 要求技术负责人在迭代中只能选择同分组已纳管仓库。当前 SA-010 已经补过发布窗口挂载同分组约束，如果 SA-009 仍允许跨分组仓库进入迭代，会让错误数据过早落库，并把问题延迟到发布窗口阶段才暴露。

本轮选择这个任务，是因为它是 SA-005 仓库分组可观察性之后的直接业务边界：前端需要减少错误候选，后端必须在写入边界做权威校验。相比继续推进迭代详情展示或删除保护，这个缺口更接近数据完整性和后续发布计划正确性。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | 通过 | SA-009 创建迭代并选择已纳管仓库 |
| 用户价值 | 通过 | 技术负责人只会看到同分组候选，错误选择被源头拦截 |
| 端到端路径 | 通过 | 迭代详情添加仓库弹窗 -> `IterationAppService.addRepos` 写入边界 |
| 单一目标 | 通过 | 只处理同分组选择与写入保护，不扩展迭代删除/归档规则 |
| 可独立验证 | 通过 | 应用层单测、Vitest、i18n、typecheck、静态扫描 |
| 可回滚 | 通过 | 新增错误码和校验逻辑局部集中，前端过滤不改变 API 契约 |
| 依赖明确 | 通过 | 复用仓库 `groupCode` 和迭代 `groupCode` |
| 风险收敛 | 通过 | 后端先校验再创建分支、保存版本记录或保存迭代 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-application/src/main/java/io/releasehub/application/iteration/IterationAppService.java` | 修改 | Application |
| `backend/releasehub-application/src/test/java/io/releasehub/application/iteration/IterationAppServiceTest.java` | 修改 | Test |
| `backend/releasehub-common/src/main/java/io/releasehub/common/exception/ErrorCode.java` | 修改 | Common |
| `backend/releasehub-common/src/main/java/io/releasehub/common/exception/BusinessException.java` | 修改 | Common |
| `backend/releasehub-common/src/main/resources/i18n/messages.properties` | 修改 | Common |
| `backend/releasehub-common/src/main/resources/i18n/messages_zh_CN.properties` | 修改 | Common |
| `frontend/src/views/iteration/AddReposDialog.vue` | 修改 | Frontend |
| `frontend/src/views/iteration/IterationDetail.vue` | 修改 | Frontend |
| `frontend/src/views/iteration/__tests__/AddReposDialog.spec.ts` | 新建 | Test |
| `docs/project-ledger.md` | 修改 | Docs |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |

## 执行步骤

### Step 1: RED

- 后端新增 `shouldRejectAddReposWhenRepositoryBelongsToDifferentGroup`，期望跨分组仓库追加时抛出 `ITER_005`，且不调用 Git adapter、`iterationRepoPort.saveWithVersion` 或 `iterationPort.save`。
- 前端新增 `AddReposDialog.spec.ts`，期望打开添加仓库弹窗时只展示当前迭代分组下的仓库；初始实现会展示全量仓库。

### Step 2: GREEN

- 新增 `ITERATION_REPO_GROUP_MISMATCH` 错误码和中英文消息。
- `IterationAppService` 新增 `ensureReposBelongToGroup`，并在 `create`、`update`、`addRepos` 写入前统一校验仓库归属。
- `AddReposDialog.open` 接收当前迭代 `groupCode`，加载仓库后按分组过滤候选。
- `IterationDetail.openAddRepos` 传入当前迭代分组，保证页面入口和后端约束一致。

### Step 3: VERIFY

- `mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，18 tests。
- `pnpm exec vitest run src/views/iteration/__tests__/AddReposDialog.spec.ts`：通过，1 test。
- `pnpm i18n:lint`：通过。
- `pnpm run typecheck`：通过。
- `git diff --check`：通过。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | 通过 | 前端候选过滤和后端写入拒绝共同覆盖 SA-009 同分组选择 |
| 层级闭环 | 通过 | 权威规则在 Application 层执行，前端只做用户体验过滤 |
| 测试闭环 | 通过 | RED/GREEN 覆盖后端副作用阻断和前端候选范围 |
| 架构闭环 | 通过 | 复用既有 `CodeRepositoryPort.findById`，没有绕过端口或引入新查询路径 |
| 安全闭环 | 通过 | 新逻辑不暴露 token，不新增敏感字段 |
| 文档闭环 | 通过 | 台账、矩阵和任务记录已同步 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260521-010100/summary.md`
- **TopN 处理结论**：未发现代码问题；SpotBugs 0 bugs，frontend lint/typecheck 通过。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 移除仓库归档 | 需要定义从迭代移除仓库后的 Git 分支归档与审计口径，本轮只处理跨分组写入保护 | `docs/reports/scenario-acceptance-matrix.md` SA-009 |
| 已挂窗口后的迭代仓库修改限制 | 需要结合发布窗口状态、WindowIteration 关联和发布计划冻结策略统一设计 | `docs/reports/scenario-acceptance-matrix.md` SA-009 |
| 删除保护和迭代详情可观察性 | 属于 P1 展示与治理扩展，不影响本轮同分组边界闭环 | `docs/reports/scenario-acceptance-matrix.md` SA-009 |

## 经验沉淀

- 业务边界不能只靠前端过滤；前端负责减少误选，Application 层负责阻断所有写入路径。
