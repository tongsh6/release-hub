# Design: GitFlow 分支生命周期管理

## Context

现有平台已实现：
- `ReleaseBranchService`：创建 release 分支、合并 feature → release
- `RunTask` 框架：异步执行各阶段任务
- 各 `*Executor`：任务执行单元（全为 Mock）

缺失：
- 真实 Git 操作（无 GitHub/GitLab API 集成）
- 分支状态可视化

## Goals / Non-Goals

**Goals:**
- 将 Mock 分支操作替换为真实 GitHub/GitLab API 调用
- 每个 `CodeRepository` 可独立配置 Provider 和 Token
- 发布窗口页提供分支状态面板

**Non-Goals:**
- 不支持 SSH 认证（仅 Personal Access Token）
- 不支持 Bitbucket（可后续扩展）
- 不实现 CI/CD 触发（已有独立 Executor 框架，不在本次范围）

## Architecture Decisions

### Decision 1: Port/Adapter 模式

新增 `GitBranchPort`（Application 层接口），Infrastructure 层提供 `GitHubBranchAdapter`、`GitLabBranchAdapter`、`MockGitBranchAdapter`。`GitBranchAdapterFactory` 根据仓库的 `gitProvider` 动态选择。

**优点**：符合现有 DDD 架构约束，Domain 层不感知 Git 实现。

### Decision 2: Token 存储

`gitToken` 存储在 `code_repository` 表。当前使用数据库级别存储（明文 + 应用层访问控制）。后续可迁移到 Secret Manager，不影响接口。

**风险**：Token 明文存储存在安全风险。
**缓解**：API 响应中脱敏展示，仅 HTTPS 传输。

### Decision 3: 分支状态聚合

`GET /release-windows/{id}/branch-status` 聚合调用各仓库 Git API，返回分支快照。不持久化分支状态（每次实时获取），避免状态不一致问题。

**权衡**：实时拉取存在延迟，但分支状态变化频繁，缓存反而增加复杂度。可后续加 TTL 缓存优化。

## API 设计

```
GET /api/v1/release-windows/{id}/branch-status

Response:
{
  "windowId": "...",
  "windowKey": "WINTER-2026",
  "repos": [
    {
      "repoId": "...",
      "repoName": "backend-service",
      "featureBranches": [
        {
          "branchName": "feature/iter-Q1",
          "exists": true,
          "latestCommit": "abc1234",
          "mergeStatus": "MERGED"   // PENDING | MERGED | CONFLICT | UNKNOWN
        }
      ],
      "releaseBranch": {
        "branchName": "release/v0.3.0",
        "exists": true,
        "latestCommit": "def5678"
      }
    }
  ]
}
```

## Data Model Changes

```sql
ALTER TABLE code_repository
  ADD COLUMN git_provider VARCHAR(20) NOT NULL DEFAULT 'MOCK',
  ADD COLUMN git_token    VARCHAR(500);
```

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| GitHub/GitLab API Rate Limit | 分支状态查询加 60s 本地缓存（后续优化） |
| Token 泄露 | 响应脱敏；仅 HTTPS；不写入日志 |
| 网络超时导致 RunTask 失败 | 现有 RetryPolicy 覆盖；超时设 10s |

## Migration Plan

1. 新增 Flyway 脚本（DEFAULT 'MOCK'），无现有数据破坏
2. 现有仓库默认使用 `MockGitBranchAdapter`，行为与之前完全一致
3. 用户可按需在仓库配置中填写 Provider + Token，逐步切换到真实 API

## Open Questions

- [ ] Token 是否需要 per-environment（开发环境用 Mock，生产用真实）？建议通过 `gitProvider` 配置区分
- [ ] 分支冲突时是否需要通知（Email / 站内消息）？暂不实现，后续加
