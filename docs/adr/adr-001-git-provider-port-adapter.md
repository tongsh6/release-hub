# ADR-001: Port/Adapter 模式统一 Git Provider 抽象

**日期**: 2026-03-02
**状态**: Accepted

## 上下文

ReleaseHub 需要同时支持 GitLab 和 GitHub 两个 Git 托管平台。早期代码中存在两套并行的 Git 操作接口：
- 旧的 `GitLabBranchPort`（6 个操作，仅支持 GitLab）
- 旧的 `GitLabPort`（只读统计，仅支持 GitLab）

随着 GitHub 支持的加入，需要在不修改 Application 层核心编排逻辑的前提下，支持多 Provider 扩展。

## 决策

采用 **Port/Adapter 模式**，创建统一的 `GitBranchPort` 接口：

```java
public interface GitBranchPort {
    boolean supports(GitProvider provider);
    Branch createBranch(String url, String token, String branchName, String fromBranch);
    void deleteBranch(String url, String token, String branchName);
    MergeResult mergeBranch(String url, String token, String source, String target, String message);
    Tag createTag(String url, String token, String tagName, String ref, String message);
    BranchStatus getBranchStatus(String url, String token, String branchName);
    Mergeability checkMergeability(String url, String token, String source, String target);
    void archiveBranch(String url, String token, String branchName, String reason);
    String triggerPipeline(String url, String token, String ref);
}
```

通过 `GitBranchAdapterFactory` 运行时选择具体适配器：
- `GitLabGitBranchAdapter` — GitLab REST API v4
- `GitHubGitBranchAdapter` — GitHub REST API v3
- `MockGitBranchAdapter` — 内存模拟（测试用）

旧的 `GitLabBranchPort` 标记为 `@Deprecated`，逐步迁移后移除。

## 后果

### 正面影响
- Application 层不绑定具体 Git 平台 API，符合开闭原则
- 新增 Git Provider 只需添加新的 Adapter 实现，无需修改编排逻辑
- 测试可通过 Mock Adapter 隔离外部依赖
- Per-repo token 设计允许不同仓库使用不同凭证

### 负面影响
- 多了一层间接性，调试时需要追踪 Factory 路由
- 旧 `GitLabBranchPort` 与新 `GitBranchPort` 暂时并存，增加维护负担

## 备选方案

### 方案 A: Provider 特定的 Port 接口（GitLabBranchPort、GitHubBranchPort）
- 优点: 可针对各平台 API 差异做精确的类型建模
- 缺点: Application 层需要 if-else 分支选择调用哪个 Port，违反开闭原则
- 为何未选择: 新增 Provider 时需要修改 Application 层代码

### 方案 B: 本地 Git CLI 操作（git clone + git 命令）
- 优点: 与平台无关
- 缺点: 服务器磁盘 I/O 和存储压力大，多仓库操作时并发控制复杂
- 为何未选择: 通过 API 操作更轻量，无需维护本地 clone
