# Implement Phase

## 触发条件

- `tasks.md` 就绪（来自 Proposal 或 Design 阶段）
- 或：跳过提案的任务类型（Bug 修复、测试补齐、重构）

## 跳过条件

- 纯文档/格式调整（无代码变更）

## 执行 Agent

→ [`.ai/agents/implement.md`](../../.ai/agents/implement.md)

## 执行步骤

1. **加载上下文**：
   - 阅读 proposal/tasks/design（如存在）
   - 加载相关开发规范（`context/tech/conventions/`）
   - 检索相关经验（`context/experience/INDEX.md`）
2. **逐条实现**：按 `tasks.md` 顺序实现，每完成一条标记 `[x]`
3. **TDD 执行**：
   - RED：先写失败测试
   - GREEN：最小实现通过
   - REFACTOR：优化保持绿色
4. **最小验证**：
   - 后端：`./mvnw -q clean test`（至少相关模块）
   - 前端：`pnpm typecheck && pnpm lint && pnpm test`（如涉及）
5. **输出变更摘要**：列出修改的文件、新增的测试、验证结果

## 产出物

| 产出 | 必须 | 说明 |
|------|------|------|
| 代码变更 | 是 | 符合 DDD 分层和规范的实现 |
| 单元测试 | 是 | 覆盖新增/修改逻辑 |
| tasks.md（更新） | 是 | 标记已完成的任务 |
| 变更摘要 | 是 | 修改文件列表 + 验证结果 |

## 验证标准

- 后端测试全量通过
- 前端 typecheck / lint / test 通过（如涉及前端）
- DDD 分层约束未被破坏（Domain 层无外部依赖）
- 代码变更与 tasks.md 一致（不多做、不少做）

## 下一步路由

→ **Review Phase**
