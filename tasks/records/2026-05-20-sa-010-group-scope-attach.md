# Slice: SA-010 挂载同分组范围约束

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-010 发布经理挂载迭代到发布窗口
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

- 场景矩阵将“只能挂载同分组范围内迭代”列为 SA-010 P1 缺口。
- 该约束属于发布计划生成前的业务边界：跨分组迭代一旦进入窗口，会污染发布计划、编排 Run 和 release 分支准备范围。
- 本切片范围窄：只补挂载入口的后端强约束、前端禁选提示面和回归证据，不改变已有 release 分支创建、冲突检测或解除挂载流程。

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-common/src/main/java/io/releasehub/common/exception/ErrorCode.java` | 修改 | Common |
| `backend/releasehub-common/src/main/java/io/releasehub/common/exception/BusinessException.java` | 修改 | Common |
| `backend/releasehub-common/src/main/resources/i18n/messages*.properties` | 修改 | Common |
| `backend/releasehub-application/src/main/java/io/releasehub/application/window/AttachAppService.java` | 修改 | Application |
| `backend/releasehub-application/src/test/java/io/releasehub/application/window/AttachAppServiceTest.java` | 修改 | Test |
| `frontend/src/api/iterationApi.ts` | 修改 | Frontend API |
| `frontend/src/api/modules/releaseWindow.ts` | 修改 | Frontend API |
| `frontend/src/views/release-window/AttachIterationsDialog.vue` | 修改 | Frontend UI |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |

## 执行步骤

### Step 1: RED

- 新增 `AttachAppServiceTest.shouldRejectAttachWhenIterationGroupMismatch`。
- 断言发布窗口分组为 `G001`、迭代分组为 `G002` 时，`attach` 拒绝并返回 `RW_013`。

### Step 2: GREEN

- 新增错误码 `RW_ITERATION_GROUP_MISMATCH` 和中英文消息。
- `AttachAppService.attach` 在写入 `WindowIteration`、创建 release 分支和保存 Run 前检查 `releaseWindow.groupCode == iteration.groupCode`。
- 前端 `AttachIterationsDialog` 打开时读取发布窗口分组，迭代列表展示 `groupCode`，非同分组行通过 Element Plus selection `selectable` 禁选。

### Step 3: VERIFY

| 命令 | 结果 |
|------|------|
| `mvn -pl releasehub-application -Dtest=AttachAppServiceTest test` | FAIL：未带 `-am` 时复用本地旧 `releasehub-common`，找不到新错误工厂方法 |
| `mvn -pl releasehub-application -am -Dtest=AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS：7 passed |
| `pnpm run typecheck` | PASS |
| `pnpm i18n:lint` | PASS |
| `bash scripts/dev/static-scan-topn.sh 10` | PASS |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | OK | 跨分组迭代不能进入发布窗口发布计划 |
| 层级闭环 | OK | 后端强约束和前端挂载弹窗禁选同时补齐 |
| 测试闭环 | OK | 后端单测覆盖拒绝路径，前端 typecheck/i18n lint 覆盖契约变更 |
| 架构闭环 | OK | 约束位于 Application 服务入口，未把业务真相交给前端推导 |
| 性能闭环 | OK | 只增加常量级 groupCode 比较和弹窗打开时一次窗口详情读取 |
| 文档闭环 | OK | 已同步场景矩阵、项目台账和本执行记录 |
| 工作区闭环 | OK | 已检查 diff，静态扫描已执行并留痕 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260520-010558/summary.md`
- **TopN 处理结论**：未发现代码问题；git diff check、backend SpotBugs、frontend ESLint、frontend typecheck 均通过。

## 经验沉淀

- [x] 不新增经验文档。本轮只沉淀到 SA-010 执行记录。
