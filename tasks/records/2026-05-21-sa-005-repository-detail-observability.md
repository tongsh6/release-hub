# SA-005 仓库详情组织路径和版本状态

## Slice: 仓库纳管详情可观察性补强

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-005
- **日期**：2026-05-21
- **执行者**：AI
- **状态**：已完成

## 选题理由

SA-005 是 Admin Setup 中最靠前的部分覆盖项。管理员纳管仓库后，如果详情页看不到组织路径和版本解析状态，后续技术负责人创建迭代、选择同分组仓库、判断版本前置条件时都缺少可复核依据。

本轮没有选择 SA-006 完整分支规则管理或 SA-014 版本更新失败重试，原因是它们会触达更大的规则作用域、执行器幂等和重试模型；SA-005 详情可观察性已有后端存储基础，只缺少 API 字段和前端呈现，能以小切片补齐关键用户判断信息。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | 通过 | SA-005 管理员纳管代码仓库 |
| 用户价值 | 通过 | 管理员能在仓库详情确认归属分组路径和版本解析来源 |
| 端到端路径 | 通过 | 仓库详情抽屉/路由详情 -> 初始版本 API -> 持久化 versionSource |
| 单一目标 | 通过 | 只补详情可观察性，不扩展仓库规则体系 |
| 可独立验证 | 通过 | MockMvc、Vitest、i18n lint、typecheck、静态扫描 |
| 可回滚 | 通过 | 新增响应字段兼容旧客户端，前端仅新增展示 |
| 依赖明确 | 通过 | 复用既有 `initial_version/version_source` 和分组树接口 |
| 风险收敛 | 通过 | token 不参与新增展示，失败时保留 groupCode 和未设置状态兜底 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-application/src/main/java/io/releasehub/application/repo/CodeRepositoryPort.java` | 修改 | Application |
| `backend/releasehub-application/src/main/java/io/releasehub/application/repo/CodeRepositoryAppService.java` | 修改 | Application |
| `backend/releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/repo/CodeRepositoryPersistenceAdapter.java` | 修改 | Infrastructure |
| `backend/releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/repo/InitialVersionView.java` | 修改 | Interfaces |
| `backend/releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/repo/CodeRepositoryController.java` | 修改 | Interfaces |
| `backend/releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/settings/SettingsAdapter.java` | 修改 | Infrastructure |
| `backend/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/RepositorySyncApiTest.java` | 修改 | Test |
| `backend/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/RepositoryE2ETest.java` | 修改 | Test |
| `frontend/src/api/repositoryApi.ts` | 修改 | Frontend |
| `frontend/src/views/repository/RepositoryDrawer.vue` | 修改 | Frontend |
| `frontend/src/views/repository/RepositoryDetail.vue` | 修改 | Frontend |
| `frontend/src/views/repository/__tests__/RepositoryDrawer.spec.ts` | 新建 | Test |
| `frontend/src/utils/groupPath.ts` | 新建 | Frontend |
| `frontend/src/i18n/messages/zh-CN.ts` | 修改 | Frontend |
| `frontend/src/i18n/messages/en-US.ts` | 修改 | Frontend |
| `docs/project-ledger.md` | 修改 | Docs |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |

## 执行步骤

### Step 1: RED

- 后端新增独立 API 用例，期望 `/repositories/{id}/initial-version` 返回 `versionSource=MANUAL`；初始实现只返回 `version`。
- 前端新增 `RepositoryDrawer.spec.ts`，期望详情抽屉加载分组树、展示组织路径、初始版本和版本来源；初始实现不会调用 `getInitialVersion` 和 `groupApi.listTree`。

### Step 2: GREEN

- `CodeRepositoryPort` 增加初始版本来源读取能力，JPA adapter 从 `version_source` 返回来源。
- `CodeRepositoryAppService` 增加 `InitialVersionInfo`，接口层 `InitialVersionView` 增加 `versionSource`。
- 仓库详情抽屉和路由详情页并行加载仓库详情、版本信息和分组树，展示组织路径、初始版本和来源标签。
- 修正 `SettingsAdapter.saveGitLab(null)` 清空语义，确保测试和业务都能把空 GitLab Settings 识别为缺失配置。

### Step 3: VERIFY

- `mvn -pl releasehub-bootstrap -am -Dtest=RepositorySyncApiTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `mvn -pl releasehub-bootstrap -am -Dtest=RepositoryE2ETest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `mvn -pl releasehub-bootstrap -am -Dtest=SettingsApiTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `pnpm exec vitest run src/views/repository/__tests__/RepositoryDrawer.spec.ts`：通过。
- `pnpm i18n:lint`：通过。
- `pnpm run typecheck`：通过。
- `git diff --check`：通过。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | 通过 | 管理员可在仓库详情复核组织路径和版本来源 |
| 层级闭环 | 通过 | 持久化字段通过 Port/Application/Interface 暴露，前端不直连数据库语义 |
| 测试闭环 | 通过 | RED/GREEN 覆盖后端字段和前端可观察性 |
| 架构闭环 | 通过 | 新字段向后兼容，未改变仓库创建/更新语义 |
| 安全闭环 | 通过 | 详情新增信息不包含 Git token |
| 文档闭环 | 通过 | 台账、矩阵和任务记录已同步 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260521-005204/summary.md`
- **TopN 处理结论**：未发现代码问题；SpotBugs 0 bugs，frontend lint/typecheck 通过。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 按组织筛选、重复仓库、错误 URL 和删除保护体验 | 超出本轮详情可观察性切片，仍属 SA-005 P1/P2 后续项 | `docs/reports/scenario-acceptance-matrix.md` SA-005 |
| 版本解析失败后的具体修复引导 | 需要结合真实解析失败原因设计用户提示，本轮仅暴露来源状态 | `docs/reports/scenario-acceptance-matrix.md` SA-005 |

## 经验沉淀

- 不新增经验文档；本轮沿用既有 TDD、i18n lint 和静态扫描闭环方式。
