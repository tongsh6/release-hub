# SA-004 GitLab Settings 连接测试闭环

## Slice 2: 真实 GitLab 连接测试与前端反馈

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-004
- **日期**：2026-05-21
- **执行者**：AI
- **状态**：已完成

## 选题理由

上一切片已完成系统级 Settings token 加密和审计闭环，SA-004 剩余最直接的 P1 缺口是“测试连接”按钮缺少真实 GitLab 验证。原实现后端固定返回 `true`，用户无法区分 token 无效、GitLab 不可达或配置缺失，前端成功提示也复用通用文案，无法作为可靠验收证据。

本轮没有选择 SA-014 版本更新失败重试，原因是该任务会横跨 Run 重试模型、执行器幂等、GitLab 写回和前端入口，适合作为后续独立纵切面；SA-004 连接测试与刚完成的 token 安全属于同一管理员配置链路，能以更小范围补齐真实 API 可用性证据，降低后续所有 GitLab 操作的前置配置风险。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | 通过 | SA-004 管理员配置 GitLab 连接 |
| 用户价值 | 通过 | 管理员保存配置后可立即验证真实 GitLab API 可用 |
| 端到端路径 | 通过 | Settings 页面按钮 -> Settings API -> GitLabPort -> GitLab `/api/v4/user` |
| 单一目标 | 通过 | 只处理连接测试真实性和前端成功/失败反馈 |
| 可独立验证 | 通过 | MockMvc、WireMock、Vitest、i18n lint、typecheck |
| 可回滚 | 通过 | 不改变 Settings 保存结构，不引入新外部状态 |
| 依赖明确 | 通过 | 复用既有 SettingsPort、GitLabAdapter、统一 BusinessException |
| 风险收敛 | 通过 | 错误返回不泄露 token，仅暴露状态类原因 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-application/src/main/java/io/releasehub/application/gitlab/GitLabPort.java` | 修改 | Application |
| `backend/releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/gitlab/GitLabAdapter.java` | 修改 | Infrastructure |
| `backend/releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/settings/SettingsController.java` | 修改 | Interfaces |
| `backend/releasehub-common/src/main/java/io/releasehub/common/exception/ErrorCode.java` | 修改 | Common |
| `backend/releasehub-common/src/main/java/io/releasehub/common/exception/BusinessException.java` | 修改 | Common |
| `backend/releasehub-common/src/main/resources/i18n/messages.properties` | 修改 | Common |
| `backend/releasehub-common/src/main/resources/i18n/messages_zh_CN.properties` | 修改 | Common |
| `backend/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/SettingsApiTest.java` | 修改 | Test |
| `backend/releasehub-infrastructure/src/test/java/io/releasehub/infrastructure/gitlab/GitLabAdapterTest.java` | 新建 | Test |
| `frontend/src/api/settingsApi.ts` | 修改 | Frontend |
| `frontend/src/views/settings/Settings.vue` | 修改 | Frontend |
| `frontend/src/views/settings/__tests__/Settings.spec.ts` | 新建 | Test |
| `frontend/src/i18n/messages/zh-CN.ts` | 修改 | Frontend |
| `frontend/src/i18n/messages/en-US.ts` | 修改 | Frontend |
| `docs/project-ledger.md` | 修改 | Docs |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |

## 执行步骤

### Step 1: RED

- 后端先补 `SettingsApiTest`，期望 `/api/v1/settings/gitlab/test` 委托 `GitLabPort.testConnection()`；初始失败为缺少端口方法和连接失败业务错误。
- 前端先补 `Settings.spec.ts`，期望成功时显示 `settings.messages.connectionSuccess`；初始失败为旧实现只显示 `common.success`。

### Step 2: GREEN

- `GitLabPort` 增加 `testConnection()`，由 `GitLabAdapter` 调用 GitLab `/api/v4/user`。
- 成功条件为 2xx 且响应体包含当前用户 `id`。
- 配置缺失沿用 `GITLAB_001`；无效 token、非预期响应和不可达统一返回 `GITLAB_003`。
- Settings API 不再固定返回 `true`，改为实际委托 GitLabPort。
- 前端 `settingsApi.testGitLab()` 返回业务布尔值，Settings 页面成功显示专用 i18n 文案，失败走统一 `handleError`。

### Step 3: VERIFY

- `mvn -pl releasehub-bootstrap -am -Dtest=SettingsApiTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `mvn -pl releasehub-infrastructure -am -Dtest=GitLabAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `pnpm exec vitest run src/views/settings/__tests__/Settings.spec.ts`：通过。
- `pnpm i18n:lint`：通过。
- `pnpm run typecheck`：通过。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | 通过 | 用户点击测试连接会触发真实 GitLab 当前用户 API |
| 层级闭环 | 通过 | Controller 仅编排端口，GitLab 远程调用保留在 Infrastructure |
| 测试闭环 | 通过 | API 委托、远程成功/401/配置缺失、前端成功/失败均有自动化证据 |
| 架构闭环 | 通过 | 新能力挂在既有 GitLabPort，不让 SettingsController 直接依赖远程调用细节 |
| 安全闭环 | 通过 | 失败原因不输出 token，成功/失败路径都不回显明文 token |
| 文档闭环 | 通过 | 台账和场景矩阵同步标记 SA-004 已覆盖 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260521-003427/summary.md`
- **TopN 处理结论**：未发现代码问题；SpotBugs 0 bugs，frontend lint/typecheck 通过。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 细分 token 无效、权限不足、网络不可达诊断展示 | 当前 P0/P1 目标是证明真实连接测试和统一失败出口；更细诊断属于 P2 体验增强 | `docs/reports/scenario-acceptance-matrix.md` SA-004 |

## 经验沉淀

- 不新增经验文档；本轮继续沿用既有 TDD、静态扫描和场景矩阵闭环方式。
