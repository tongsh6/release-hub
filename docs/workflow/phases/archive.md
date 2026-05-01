# Archive Phase

> ReleaseHub 扩展阶段，AIEF 标准无此阶段。用于将完成的变更归档为可复用的知识资产。

## 触发条件

- Review Phase 通过
- 代码已合并到主分支 / 已部署

## 跳过条件

- 无 OpenSpec change 的微小修改（Bug 修复、测试补齐等直接跳过归档）

## 执行 Agent

→ [`.ai/agents/archive.md`](../../.ai/agents/archive.md)

## 执行步骤

1. **归档 OpenSpec change**：
   - 将 `openspec/changes/<change-id>/` 迁移到 `openspec/changes/archive/<date>-<change-id>/`
   - 修复归档后的互链（proposal ↔ 需求文档）
2. **归档需求**（如有关联需求）：
   - 将需求从 `requirements/in-progress/` 迁移到 `requirements/completed/`
   - 更新 `requirements/INDEX.md`
3. **沉淀经验**（如有值得记录的经验）：
   - 在 `context/experience/lessons/` 创建经验文档
   - 在 `context/experience/INDEX.md` 添加索引条目
4. **校验**：
   - `openspec validate` 全局校验通过
   - 需求门禁 `scripts/dev/validate-requirements-gate.sh` 通过

## 产出物

| 产出 | 必须 | 说明 |
|------|------|------|
| 归档的 change | 是 | `archive/<date>-<change-id>/` |
| 更新的需求索引 | 条件 | 有关联需求时 |
| 经验文档 | 否 | 有价值经验时 |
| 会话摘要 | 否 | 复杂任务时（`skill-session-summarizer`） |

## 验证标准

- `openspec validate` 全局校验通过
- 归档目录结构完整（proposal + tasks + specs）
- 互链正确（归档 proposal 指向 completed 需求，需求指向归档 proposal）
- 需求索引已更新

## 下一步路由

→ 流程结束（等待下一个任务）
