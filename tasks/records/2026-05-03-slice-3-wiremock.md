# Slice 3: WireMock 实现

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 3 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

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
| `GitLabGitBranchAdapterTest.java` | 修改 | Infra test |
| `MockGitLabBranchAdapterTest.java` | 修改 | Infra test |
| `GitHubGitBranchAdapterTest.java` | 修改 | Infra test |
| `GitBranchAdapterFactoryImplTest.java` | 检查 | Infra test |
| `GradleVersionUpdaterTest.java` | 检查 | Infra test |
| `MavenVersionUpdaterTest.java` | 检查 | Infra test |

## 执行步骤

### Step 1: 改写 GitLabGitBranchAdapterTest
```java
@WireMockTest(httpPort = 0)
class GitLabGitBranchAdapterTest {

    @Test
    void createBranch_success(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/api/v4/projects/1/repository/branches"))
            .willReturn(aResponse().withStatus(201)
                .withBody("{\"name\":\"feature/test\"}")));

        // adapter 连 wm.getHttpBaseUrl()
        verify(postRequestedFor(urlPathEqualTo("/api/v4/projects/1/repository/branches")));
    }
}
```

### Step 2: 改写其余 adapter 测试
- MockGitLabBranchAdapterTest → WireMock stub
- GitHubGitBranchAdapterTest → WireMock stub
- GradleVersionUpdaterTest / MavenVersionUpdaterTest → 纯逻辑不需要 HTTP

### Step 3: VERIFY
- `mvn test -pl releasehub-infrastructure` 全部通过
- `grep "MockRestServiceServer"` 在 infra test 中 0 引用

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ⬜ | |
| 层级闭环 | ⬜ | |
| 测试闭环 | ⬜ | |
| 架构闭环 | ⬜ | |
| 性能闭环 | ⬜ | |
| 文档闭环 | ⬜ | |
| 工作区闭环 | ⬜ | |
