# 经验：发布窗口生命周期设计

> 记录于：2026-01-29

## 问题背景

设计发布窗口的状态流转时，需要考虑：
- 状态数量和转换规则
- 各状态下允许的操作
- 冻结机制的引入时机

## 解决方案

### 三态流转模型

```
DRAFT（待发布）→ PUBLISHED（已发布）→ CLOSED（已关闭）
```

**设计理由**：
- **DRAFT**：规划阶段，可调整配置、挂载迭代
- **PUBLISHED**：执行阶段，可执行版本更新，但配置基本稳定
- **CLOSED**：归档阶段，只读状态

### 冻结机制（横切关注点）

冻结是独立于状态的横切机制：
- 任何状态下都可冻结
- 冻结后禁止：版本更新、配置变更、挂载调整
- 可解冻恢复正常操作

**使用场景**：
- 紧急情况暂停发布
- 等待外部依赖
- 发现问题需要暂停

### 状态 vs 冻结

| 维度 | 状态 | 冻结 |
|------|------|------|
| 生命周期 | 单向流转 | 可逆切换 |
| 语义 | 阶段推进 | 临时暂停 |
| 操作限制 | 按阶段限制 | 全面锁定 |

## 关键代码

```java
// 状态流转
public void publish() {
    if (this.status != ReleaseWindowStatus.DRAFT) {
        throw new IllegalStateException("Only DRAFT window can be published");
    }
    this.status = ReleaseWindowStatus.PUBLISHED;
    this.publishedAt = Instant.now();
}

// 冻结检查
public void freeze() {
    this.frozen = true;
}

public void ensureNotFrozen() {
    if (this.frozen) {
        throw new IllegalStateException("Window is frozen");
    }
}
```

## 踩坑记录

1. **状态和冻结混淆**：最初把冻结设计成一个状态，导致状态机复杂化
2. **冻结时机**：冻结应在操作入口统一检查，而非散落在各处

## 适用场景

- 需要生命周期管理的业务实体
- 需要临时暂停机制的流程
- 状态流转 + 横切控制的组合模式
