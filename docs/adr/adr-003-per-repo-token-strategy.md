# ADR-003: Per-repo Token 传递策略

**日期**: 2026-03-02
**状态**: Accepted
**补充**: 2026-05-08 — Token 明文存储问题已通过 AES-256-GCM JPA AttributeConverter 修复（`GitTokenCrypto` + `GitTokenAttributeConverter`）

## 上下文

ReleaseHub 管理的多个 Git 仓库可能属于不同的 GitLab Group / GitHub Organization，需要不同的访问令牌。早期设计中存在全局 Git 配置（GitSettings），但无法满足多仓库多凭证的场景需求。需要在以下两种策略中选择：

- **全局 Token**：系统级配置一个 GitLab/Token，所有仓库操作共用
- **Per-repo Token**：每个仓库存储自己的 GitProvider、accessToken

## 决策

采用 **Per-repo Token 传递策略**：每个 `CodeRepository` 实体存储其 `gitProvider` 和 `gitAccessToken`，Git 操作的每次调用都通过方法参数传入 token，不依赖全局状态。

```java
// GitBranchPort 的方法签名示例
Branch createBranch(String repoCloneUrl, String token, String branchName, String fromBranch);
```

`GitBranchAdapterFactory` 根据 `gitProvider` 选择合适的适配器，token 作为方法参数透传至 API 调用。

## 后果

### 正面影响
- 不同仓库可使用不同 Git Provider 和 Token，无互斥问题
- Token 生命周期可独立管理（按仓库粒度轮换）
- 适配器方法为纯函数，无隐藏的全局状态依赖
- 跨组织/跨平台的混合管理场景得到原生支持

### 负面影响
- Token 在方法参数链中透传，增加参数数量
- ~~数据库中存储 accessToken 需要加密保护（当前为明文）~~。**已于 v0.1.10 修复**：通过 AES-256-GCM `GitTokenCrypto` + JPA `GitTokenAttributeConverter` 实现数据库列级透明加解密，密钥通过环境变量注入。加密可通过 `releasehub.crypto.enabled` 开关控制
- 无法使用全局 Token 缓存优化多仓库共享同一 Token 的场景

## 备选方案

### 方案 A: 全局 Token 配置（GitSettings）
- 优点: 配置简单，所有仓库共享一个 Token
- 缺点: 不支持跨组织仓库管理，Token 轮换影响全局
- 为何未选择: 无法满足多仓库管理场景的基本需求

### 方案 B: Token Vault 集中管理
- 优点: 统一管理凭证，支持自动轮换，安全性更高
- 缺点: 引入额外基础设施依赖，MVP 阶段过度设计
- 为何未选择: 可在后续版本中引入 HashiCorp Vault 或云原生 Secret Manager，当前 Per-repo 存储已满足需求
