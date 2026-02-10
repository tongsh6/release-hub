# 经验：分支命名与归档策略

> 记录于：2026-01-29

## 问题背景

多仓库发布场景下，分支命名需要：
- 与业务实体（迭代、发布窗口）关联
- 支持自动化创建和归档
- 便于追溯和管理

## 解决方案

### 分支命名约定

```
feature/<iterationKey>   # 迭代开发分支
hotfix/<iterationKey>    # 修复分支
release/<windowKey>      # 发布分支
```

**设计理由**：
- **类型前缀**：区分分支用途（feature/hotfix/release）
- **业务 Key 后缀**：关联到具体业务实体，便于追溯
- **Key 由系统生成**：避免命名冲突，保证唯一性

### 归档策略

```
archive/<reason>/<original>
```

- `<reason>`：归档原因
  - `unpublished`：未发布就解除挂载
  - `released`：发布完成后归档
- `<original>`：原分支名（不含空格）

**示例**：
- `feature/IT-001` → `archive/released/feature/IT-001`
- `release/RW-001` → `archive/released/release/RW-001`

### 自动化触发

| 操作 | 分支动作 |
|------|----------|
| 仓库挂载到迭代 | 创建 feature 或 hotfix 分支 |
| 迭代挂载到窗口 | 创建 release 分支 |
| 仓库从迭代解除 | 归档分支（reason=unpublished） |
| 发布收尾完成 | 归档分支（reason=released） |

## 踩坑记录

1. **分支名包含空格**：Git 分支名不能包含空格，需要校验
2. **归档分支过多**：考虑定期清理策略或设置保留时间
3. **Key 格式一致性**：iterationKey 和 windowKey 格式要统一，便于解析

## 关键代码

```java
// 分支名生成
public String buildFeatureBranchName(IterationKey key) {
    return "feature/" + key.getValue();
}

public String buildArchiveBranchName(String reason, String original) {
    return "archive/" + reason + "/" + original;
}
```

## 适用场景

- 多仓库协同发布
- 需要分支生命周期管理
- 自动化 CI/CD 流程
