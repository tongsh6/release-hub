# Slice 3: WireMock 实现

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 3 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | WireMock 替代 Spring MockRestServiceServer |
| 用户价值 | ✅ | 真实 HTTP stub，可录制/复用 GitLab API 响应，复用到 CI |
| 端到端路径 | ✅ | Infrastructure test → WireMock stub → Adapter |
| 单一目标 | ✅ | 只改写 infrastructure 层测试 |
| 可独立验证 | ✅ | 全部 infrastructure 测试通过，无 MockRestServiceServer 残留 |
| 可回滚 | ✅ | git revert |
| 依赖明确 | ✅ | 依赖 Slice 2（wiremock-standalone dep） |
| 风险收敛 | ✅ | 只改测试代码 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `GitLabGitBranchAdapterTest.java` | 修改：`@WireMockTest(httpPort = 0)` | Infra test |
| `GitHubGitBranchAdapterTest.java` | 修改：`@WireMockTest(httpPort = 0)` | Infra test |
| `GitBranchAdapterFactoryImplTest.java` | 检查：纯逻辑，不需 HTTP | Infra test |
| `GradleVersionUpdaterTest.java` | 检查：纯逻辑，不需 HTTP | Infra test |
| `MavenVersionUpdaterTest.java` | 检查：纯逻辑，不需 HTTP | Infra test |
| `MockGitLabBranchAdapterTest.java` | 已删除/合并 | Infra test |

## 执行步骤

### Step 1: 改写 GitLabGitBranchAdapterTest
- `@WireMockTest(httpPort = 0)` + `WireMockRuntimeInfo` 注入
- stub GitLab API（create branch / merge / branch status / check mergeability）
- 7 个测试方法覆盖正常路径和错误路径

### Step 2: 改写 GitHubGitBranchAdapterTest
- `@WireMockTest(httpPort = 0)` + `WireMockRuntimeInfo` 注入
- stub GitHub API（create branch / merge / get branch / check mergeability）
- 6 个测试方法

### Step 3: 其他文件检查
- `GitBranchAdapterFactoryImplTest`：纯工厂逻辑，不需要 HTTP stub
- `GradleVersionUpdaterTest` / `MavenVersionUpdaterTest`：纯版本号处理逻辑

### Step 4: VERIFY
- `mvn test -pl releasehub-infrastructure` 全部通过
- `grep "MockRestServiceServer"` 在 infra test 中 0 引用 ✅

## 静态扫描

```bash
scripts/dev/static-scan-topn.sh 10
```
**报告路径**：
**TopN 处理结论**：
**未解决风险**：

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | WireMock 替代完成，infra 测试全部通过 |
| 层级闭环 | ✅ | 只涉及 infrastructure 测试层 |
| 测试闭环 | ✅ | 全部 infra 测试通过 |
| 架构闭环 | ✅ | 与 Slice 2 WireMock 依赖对齐 |
| 性能闭环 | ✅ | 独立端口 stub，不依赖外部 GitLab |
| 文档闭环 | ✅ | 设计文档已标记完成 |
| 工作区闭环 | ✅ | 无残留 MockRestServiceServer |
