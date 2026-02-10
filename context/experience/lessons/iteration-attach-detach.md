# 经验：迭代挂载与解除设计

> 记录于：2026-01-29

## 问题背景

发布范围需要动态调整：
- 迭代可以关联到发布窗口
- 同一迭代可挂载多个仓库
- 需要支持中途调整（添加/移除）

## 解决方案

### 两层挂载模型

```
ReleaseWindow ←N:N→ Iteration ←N:N→ CodeRepository
              (WindowIteration)    (迭代包含仓库)
```

**设计理由**：
- **发布窗口-迭代**：N:N 关系，同一迭代可被多个窗口复用
- **迭代-仓库**：N:N 关系，迭代作为仓库的组织单元
- **解耦**：仓库不直接关联到窗口，通过迭代间接关联

### 挂载语义

```java
// 迭代挂载到发布窗口
WindowIteration.attach(windowId, iterationKey)
// 自动包含迭代的所有仓库
// 自动创建 release 分支

// 仓库挂载到迭代
Iteration.addRepo(repoId, type)  // type: FEATURE | HOTFIX
// 自动创建 feature/<iterationKey> 或 hotfix/<iterationKey> 分支
```

### 解除语义

```java
// 迭代从发布窗口解除
WindowIteration.detach(windowId, iterationKey)
// 自动归档 release 分支（reason=unpublished）

// 仓库从迭代解除
Iteration.removeRepo(repoId)
// 自动归档 feature/hotfix 分支（reason=unpublished）
```

### 约束规则

| 操作 | 约束 |
|------|------|
| 迭代首次挂载到窗口 | 窗口必须是 DRAFT 状态 |
| 后续调整挂载 | 窗口未冻结即可 |
| 删除迭代 | 未关联窗口且未挂载仓库 |
| 删除仓库 | 未关联到任何迭代或窗口 |

## 踩坑记录

1. **级联影响**：解除挂载时要考虑分支归档等副作用
2. **状态一致性**：窗口冻结时禁止挂载调整
3. **顺序问题**：挂载时需要记录 attachAt，用于执行顺序排序

## 关键代码

```java
// WindowIteration 设计
public class WindowIteration extends BaseEntity<String> {
    private final ReleaseWindowId windowId;
    private final IterationKey iterationKey;
    private final Instant attachAt;  // 用于排序

    // ID = windowId::iterationKey
    public String getId() {
        return windowId.getValue() + "::" + iterationKey.getValue();
    }
}
```

## 适用场景

- 多层级关联关系
- 需要动态调整的业务范围
- 需要副作用管理（如分支操作）
