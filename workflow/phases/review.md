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
5. 输出审查报告（通过 / 需修改 / 驳回）

## 产出物

| 产出 | 来源 | 说明 |
|------|------|------|
| 补充的测试 | Test Agent | 新增的回归用例 |
| 验证结果 | Test Agent | 全量测试通过确认 |
| 审查报告 | Code Review | 合规检查结果 + 建议 |

## 验证标准

- 全量后端测试通过
- 全量前端门禁通过（typecheck / lint / test / build）
- OpenSpec 校验通过（`openspec validate <change-id> --strict`）
- Code Review 无阻塞项（Critical / Blocker）

## 下一步路由

- 审查通过 → **Archive Phase**
- 需修改 → 返回 **Implement Phase**
- 驳回 → 返回 **Proposal Phase**（重新评估方案）
