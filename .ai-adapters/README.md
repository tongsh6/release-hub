# AI 工具适配层

> 多 AI 工具的桥接引用层。业务逻辑和规范定义在 [AGENTS.md](../AGENTS.md)（权威入口），适配器只做指向。

## 设计原则

1. **保留原生位置**：各工具的配置文件留在其识别路径（`.github/copilot-instructions.md`、`.claude/` 等），不移动
2. **适配器只做桥接**：指向 `AGENTS.md` 作为权威入口，不重复业务逻辑
3. **权威入口唯一**：`AGENTS.md` → `context/` / `openspec/` / `.ai/` 是知识流向

## 适配器清单

| 工具 | 原生配置位置 | 适配器 | 状态 |
|------|-------------|--------|------|
| GitHub Copilot | `.github/copilot-instructions.md` | [copilot/](copilot/README.md) | 桥接引用 |
| Cursor AI | `.cursor/` | [cursor/rules/project.mdc](cursor/rules/project.mdc) | 规则摘要 |
| Claude Code | `.claude/` | [claude-code/](claude-code/README.md) | 桥接引用 |
| Amazon Q | `.amazonq/prompts/` | — | 保留原样（OpenSpec 提示） |

## 新增适配器指南

1. 在 `.ai-adapters/<tool-name>/` 下创建目录
2. 编写 README 或配置文件，引用 `AGENTS.md` 和相关 `context/` 文档
3. 在本文件的适配器清单中登记
4. 工具的原生配置文件保留在其识别路径
