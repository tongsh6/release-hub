# ReleaseWindow 冻结/解冻：领域事件 + 状态机模式

## 问题

ReleaseWindow 的生命周期往往需要 “冻结/解冻” 来控制配置变更、纳入仓库、执行 Run 等操作。常见风险是把冻结逻辑散落在应用服务或控制器里，导致状态判断重复、规则不一致、并发下出现非法状态跳转。

## 结论

用 ReleaseWindow 聚合根内的状态机表达 “允许的状态跳转”，并通过领域事件描述状态变更语义；应用层只编排用例与事务边界，不直接修改状态细节。

## 建议建模

- 状态：`DRAFT` / `OPEN` / `FROZEN` / `CLOSED`（示例，按项目实际定义）
- 命令：`freeze(reason, operator)`、`unfreeze(operator)`、`configure(...)`、`close(...)`
- 领域事件：`ReleaseWindowFrozen`、`ReleaseWindowUnfrozen`

## 规则（示例）

- 只有 `OPEN` 才能冻结
- 只有 `FROZEN` 才能解冻
- `FROZEN` 状态禁止执行配置类命令（如修改范围、时间窗口、策略等）
- `CLOSED` 禁止任何变更

## 实现要点（DDD）

- 把规则放入聚合根方法中，失败用业务异常表达（而不是返回 boolean）
- 状态跳转与事件产生在同一个原子操作中（同一事务内持久化）
- 并发场景用乐观锁或版本号防止 “重复冻结/解冻” 导致的写覆盖

## 测试清单（TDD）

- 冻结成功：OPEN → FROZEN，事件产生
- 冻结失败：DRAFT/CLOSED 冻结抛出业务异常
- 解冻成功：FROZEN → OPEN，事件产生
- 解冻失败：非 FROZEN 解冻抛出业务异常
- 冻结后禁止配置：调用 configure 抛出业务异常

## 常见坑

- 只在 Controller 校验状态，绕过其他入口导致规则失效
- 用 if/else 在多个服务里堆状态判断，新增状态后容易漏改
- 忽略并发导致 “最后一次写入获胜”，状态回退或事件错乱
