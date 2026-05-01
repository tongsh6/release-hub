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
2. **完整蓝图复核**：确认最终目标、完整范围、阶段计划、当前 Slice 归属、未完成项追踪位置已经明确
3. **事前约束复核**：确认当前 Slice 的蓝图归属、目标、涉及层、依赖、非目标、后续项、验收标准已经明确
4. **逐条实现**：按 `tasks.md` 顺序实现，每完成一条标记 `[x]`；未完成项保持未勾选并写清下一步
5. **TDD 执行**：
   - RED：先写失败测试
   - GREEN：写刚好满足当前测试且不偏离完整规划的实现
   - REFACTOR：优化保持绿色
6. **切片事后检查**：完成一个 Slice 后，检查与完整蓝图一致性、行为闭环、层级闭环、测试闭环、架构闭环、性能闭环、文档闭环、工作区闭环
7. **验证**：
   - 后端：`./mvnw -q clean test`（至少相关模块）
   - 前端：`pnpm typecheck && pnpm lint && pnpm test`（如涉及）
8. **静态扫码与 TopN 修复**：
   - 执行 `scripts/dev/static-scan-topn.sh 10` 或等价静态扫描命令
   - 阅读 `.ai/reports/static-scan/<timestamp>/summary.md`
   - 优先修复 TopN；跳过项必须记录原因
   - 修复后复扫，并在同一报告中补充处理方式、处理结果和复扫证据
9. **输出变更摘要**：列出修改的文件、新增的测试、验证结果、静态扫描报告路径、TopN 处理结论、完整蓝图状态、未完成项追踪位置和每个 Slice 的事后检查结论

## 产出物

| 产出 | 必须 | 说明 |
|------|------|------|
| 代码变更 | 是 | 符合 DDD 分层和规范的实现 |
| 单元测试 | 是 | 覆盖新增/修改逻辑 |
| tasks.md（更新） | 是 | 标记已完成的任务 |
| 静态扫描报告 | 是 | `.ai/reports/static-scan/<timestamp>/summary.md`，含 TopN 处理记录 |
| 变更摘要 | 是 | 修改文件列表 + 验证结果 |

## 验证标准

- 后端测试全量通过
- 前端 typecheck / lint / test 通过（如涉及前端）
- 静态扫描已执行，TopN 已修复或明确记录跳过原因，并有复扫证据
- DDD 分层约束未被破坏（Domain 层无外部依赖）
- 代码变更与 tasks.md 一致（不多做、不少做）
- 每个已完成 Slice 都有事后检查结论；未完成项必须保留在规划中并说明原因、下一步和追踪位置
- 不允许存在未接入的悬空实现：例如无人调用的 API、未注册的 Adapter、未接 UI 的前端入口、无测试覆盖的核心路径

## 下一步路由

→ **Review Phase**
