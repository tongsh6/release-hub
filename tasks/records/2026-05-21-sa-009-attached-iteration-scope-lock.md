# SA-009 已挂窗口后迭代仓库集合锁定

## Slice: 发布计划生成后的迭代范围防漂移

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-009
- **日期**：2026-05-21
- **执行者**：AI
- **状态**：已完成

## 选题理由

SA-009 剩余缺口中，“已挂窗口后的修改限制”直接影响发布计划正确性。迭代一旦挂到发布窗口，系统已经基于当时的仓库集合准备 release 分支、计划项和后续 Run 证据；此时再允许添加或移除仓库，会让窗口计划、版本记录和 Git 分支状态发生漂移。

本轮没有选择纯详情展示或删除保护，是因为仓库集合锁定属于写入边界和数据完整性问题。它必须先在 Application 层收口，前端入口隐藏只作为用户体验约束。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | 通过 | SA-009 创建迭代并选择仓库 |
| 用户价值 | 通过 | 已进入发布计划的迭代仓库范围不会被后续误改 |
| 端到端路径 | 通过 | 迭代详情 -> attached 状态 -> 添加/移除入口隐藏；后端 add/remove/update 写入前拒绝 |
| 单一目标 | 通过 | 只锁定仓库集合和分组，不阻止名称、描述等元数据调整 |
| 可独立验证 | 通过 | 应用层单测、interfaces compile、Vitest、i18n、typecheck、静态扫描 |
| 可回滚 | 通过 | 新增守卫集中在 `IterationAppService`，响应字段向后兼容 |
| 依赖明确 | 通过 | 复用 `ReleaseWindowPort` 和 `WindowIterationPort` 判断挂载状态 |
| 风险收敛 | 通过 | 写入前阻断，不会触发 Git 归档、分支创建、版本记录或迭代保存副作用 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-application/src/main/java/io/releasehub/application/iteration/IterationAppService.java` | 修改 | Application |
| `backend/releasehub-application/src/main/java/io/releasehub/application/iteration/IterationView.java` | 修改 | Application |
| `backend/releasehub-application/src/test/java/io/releasehub/application/iteration/IterationAppServiceTest.java` | 修改 | Test |
| `backend/releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/iteration/IterationController.java` | 修改 | Interfaces |
| `frontend/src/api/iterationApi.ts` | 修改 | Frontend |
| `frontend/src/views/iteration/IterationDetail.vue` | 修改 | Frontend |
| `frontend/src/views/iteration/__tests__/IterationDetail.spec.ts` | 新建 | Test |
| `frontend/src/i18n/messages/zh-CN.ts` | 修改 | Frontend |
| `frontend/src/i18n/messages/en-US.ts` | 修改 | Frontend |
| `docs/project-ledger.md` | 修改 | Docs |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |

## 执行步骤

### Step 1: RED

- 新增 `shouldRejectAddReposWhenIterationAttachedToWindow`，期望已挂窗口后追加仓库抛出 `ITER_002`，且不查询仓库、不创建分支、不保存版本记录、不保存迭代。
- 新增 `shouldRejectRemoveReposWhenIterationAttachedToWindow`，期望已挂窗口后移除仓库抛出 `ITER_002`，且不归档分支、不保存迭代。
- 新增 `shouldRejectUpdateRepoScopeWhenIterationAttachedToWindow`，期望已挂窗口后通过 update 改仓库集合抛出 `ITER_002`。
- 初始运行失败，说明现有实现会继续走仓库校验或直接移除仓库，缺少“已挂窗口”这个业务语义守卫。

### Step 2: GREEN

- `IterationAppService` 新增 `ensureRepoScopeEditable` / `ensureIterationNotAttached`，在 `addRepos`、`removeRepos` 和 `update` 改仓库集合或分组前统一阻断。
- `delete` 复用同一挂载状态判断，减少重复扫描逻辑。
- `IterationView` 增加 `attachedToWindow` 和 `attachedWindowIds`，详情接口返回挂载状态。
- 迭代详情页基于 `attachedToWindow` 隐藏添加/移除仓库入口，并显示仓库集合锁定状态。

### Step 3: VERIFY

- `mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，21 tests。
- `mvn -pl releasehub-interfaces -am -DskipTests compile`：通过。
- `pnpm exec vitest run src/views/iteration/__tests__/AddReposDialog.spec.ts src/views/iteration/__tests__/IterationDetail.spec.ts`：通过，2 tests。
- `pnpm i18n:lint`：通过。
- `pnpm run typecheck`：通过。
- `git diff --check`：通过。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | 通过 | 已挂窗口后仓库集合和分组不可继续变更 |
| 层级闭环 | 通过 | Application 层权威阻断，Interface 层只暴露状态，前端只隐藏入口 |
| 测试闭环 | 通过 | RED/GREEN 覆盖三条写入路径和前端锁定观察 |
| 架构闭环 | 通过 | 复用既有端口判断挂载状态，未引入跨层数据库访问 |
| 安全闭环 | 通过 | 新响应字段只包含挂载状态和窗口 ID，不涉及 token |
| 文档闭环 | 通过 | 台账、矩阵和任务记录已同步 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260521-011123/summary.md`
- **TopN 处理结论**：未发现代码问题；SpotBugs 0 bugs，frontend lint/typecheck 通过。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 移除仓库归档更多真实 GitLab 证据 | 本轮先防止已挂窗口后移除造成计划漂移；未挂窗口移除归档已有单测，真实 GitLab 证据可后续补强 | `docs/reports/scenario-acceptance-matrix.md` SA-009 |
| 删除保护扩展 | 删除保护已有基础后端约束，本轮不扩展关联资源删除治理 | `docs/reports/scenario-acceptance-matrix.md` SA-009 |
| 迭代详情可观察性扩展 | 本轮只新增锁定状态观察，不扩展完整组织路径/窗口列表展示 | `docs/reports/scenario-acceptance-matrix.md` SA-009 |

## 经验沉淀

- 发布计划依赖的业务范围必须在写入边界锁定；前端入口隐藏只能改善用户体验，不能作为唯一约束。
