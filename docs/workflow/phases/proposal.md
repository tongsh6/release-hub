# Proposal Phase

## 触发条件

- 新功能开发（添加新行为或新接口）
- 破坏性变更（修改现有 API 契约、删除功能）
- 架构调整（引入新依赖、模块拆分、基础设施变更）
- 安全/性能语义变化

## 跳过条件

- 纯 Bug 修复（恢复既有行为，不引入新行为）
- 文档/格式调整
- 测试补齐（不改变业务逻辑）
- 重构（不改变外部接口）

## 执行 Agent

→ [`.ai/agents/proposal.md`](../../.ai/agents/proposal.md)

## 执行步骤

1. **检索现有 change**：在 `openspec/changes/` 中查找是否已有相关提案，避免重复
2. **生成 change-id**：使用 verb-led 命名（如 `add-freeze-feature`）
3. **创建提案结构**：
   - `openspec/changes/<change-id>/proposal.md` — 变更动机、范围、影响
   - `openspec/changes/<change-id>/tasks.md` — 可执行的任务清单
   - `openspec/changes/<change-id>/design.md` — 可选，复杂变更才需要
4. **完整目标蓝图**：`proposal.md` 必须说明最终目标、完整范围、非目标、影响面、风险、分阶段交付策略和未完成项追踪方式
5. **垂直切片事前约束**：`tasks.md` 必须按完整蓝图拆分 Slice，每个 Slice 说明蓝图归属、目标、涉及层、依赖、非目标、后续项和验收
6. **编写 delta specs**：在 `specs/<capability>/spec.md` 中标注 ADDED/MODIFIED/REMOVED
7. **校验**：运行 `openspec validate <change-id> --strict`

## 产出物

| 文件 | 必须 | 说明 |
|------|------|------|
| `proposal.md` | 是 | 变更提案 |
| `tasks.md` | 是 | 任务分解 |
| `design.md` | 否 | 复杂变更补充 |
| `specs/*/spec.md` | 是 | Delta 规范 |

## 验证标准

- `openspec validate <change-id> --strict` 通过
- `proposal.md` 包含反向引用到需求文档（如有关联需求）
- `proposal.md` 包含完整目标蓝图；不得只描述本次准备实现的局部范围
- `tasks.md` 中每个任务可独立实现和验证
- `tasks.md` 中每个垂直切片都有明确蓝图归属、用户价值、端到端路径、依赖关系、后续项和验收方式
- 不允许只列横向技术铺垫；必要技术前置必须被标注为后续 Slice 的依赖

## 下一步路由

- 复杂变更（跨模块/迁移/安全性能）→ **Design Phase**
- 简单变更 → **Implement Phase**
