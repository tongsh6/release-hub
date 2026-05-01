# Claude Code 适配

Claude Code 的配置位于其原生识别路径：

- **命令目录**：[`/.claude/commands/`](../../.claude/commands/) — Agent/Skill/Command 的 Claude Code 入口
- **项目设置**：`/.claude/` 目录

Claude Code 通过 `AGENTS.md` 自动加载项目上下文，命令定义与 `.ai/commands/` 保持同步。

代码实现完成后，Claude Code 也必须遵循统一静态扫码流程：运行 `scripts/dev/static-scan-topn.sh 10` 或等价命令，保留 `.ai/reports/static-scan/` 报告，并记录 TopN 处理方式、处理结果和复扫证据。

如需更新，请参考 [AGENTS.md](../../AGENTS.md) 作为权威入口。
