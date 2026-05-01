# Review Phase

> ReleaseHub 采用 **Test + Code Review 双重审查机制**，由独立的 Agent 和 Command 分别执行。

## 触发条件

- 代码变更完成（Implement Phase 产出就绪）
- 变更涉及代码修改（非纯文档调整）

## 跳过条件

- 纯文档/格式调整
- CI 自动验证已覆盖的微小修改

## 执行 Agent / Command

| 审查类型 | 执行者 | 职责 |
|---------|--------|------|
| 测试审查 | [`.ai/agents/test.md`](../../.ai/agents/test.md) | 补齐测试覆盖、构建回归用例 |
| 代码审查 | [`.ai/commands/code-review.md`](../../.ai/commands/code-review.md) | 规范合规、设计合理性、安全检查 |

**注意**：Test Agent 和 Code Review Command 保持独立，不合并。Test 聚焦于补齐验证，Code Review 聚焦于质量与规范。

## 执行步骤

### 测试审查（Test Agent）

1. 分析变更范围，识别未覆盖的测试场景
2. 补充单测 / 集成测试 / e2e（按变更复杂度）
3. 构建回归用例，防止未来退化
4. 运行全量验证确保绿色

### 代码审查（Code Review Command）

1. 检查 DDD 分层合规性（Domain 层隔离）
2. 验证命名规范、API 契约一致性
3. 检查安全性（注入、越权、敏感数据暴露）
4. 评估变更与 proposal/tasks 的一致性
5. 检查完整蓝图与垂直切片闭环：最终目标是否完整记录，当前实现是否回连蓝图，未完成项是否有追踪位置，行为、层级、测试、架构、性能、文档、工作区是否完整
6. 检查静态扫码报告是否存在，TopN 是否逐项记录处理方式、处理结果和复扫证据
7. 输出审查报告（通过 / 需修改 / 驳回）

## 产出物

| 产出 | 来源 | 说明 |
|------|------|------|
| 补充的测试 | Test Agent | 新增的回归用例 |
| 验证结果 | Test Agent | 全量测试通过确认 |
| 静态扫码报告 | Implement / Review | 扫描结果 + TopN 处理记录 + 复扫证据 |
| 审查报告 | Code Review | 合规检查结果 + 建议 |

## 验证标准

- 全量后端测试通过
- 全量前端门禁通过（typecheck / lint / test / build）
- 静态扫描报告存在，TopN 已修复或有明确不修复原因，复扫证据可追溯
- OpenSpec 校验通过（`openspec validate <change-id> --strict`）
- Code Review 无阻塞项（Critical / Blocker）
- 所有已实现 Slice 通过事后检查；未实现 Slice 必须仍在完整规划中可追踪，若有例外，必须明确记录为已知风险或返回 Implement Phase

## 下一步路由

- 审查通过 → **Archive Phase**
- 需修改 → 返回 **Implement Phase**
- 驳回 → 返回 **Proposal Phase**（重新评估方案）
