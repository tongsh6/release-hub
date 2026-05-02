# Architecture Decision Records

ReleaseHub 的架构决策记录（ADR）。每个 ADR 记录一个重要的架构决策，包含上下文、决策、后果和备选方案。

## 索引

| ADR | 标题 | 日期 | 状态 |
|-----|------|------|------|
| [001](adr-001-git-provider-port-adapter.md) | Port/Adapter 模式统一 Git Provider 抽象 | 2026-03-02 | Accepted |
| [002](adr-002-ddd-hexagonal-architecture.md) | DDD 六边形架构分层 | 2025-12-01 | Accepted |
| [003](adr-003-per-repo-token-strategy.md) | Per-repo Token 传递策略 | 2026-03-02 | Accepted |

## 模板

新建 ADR 时使用以下模板：

```markdown
# ADR-NNN: [标题]

**日期**: YYYY-MM-DD
**状态**: [Proposed | Accepted | Deprecated | Superseded]

## 上下文

描述需要做出决策的背景和约束。

## 决策

描述做出的决策及其理由。

## 后果

### 正面影响
- ...

### 负面影响
- ...

## 备选方案

### 方案 A: [描述]
- 优点: ...
- 缺点: ...
- 为何未选择: ...
```
