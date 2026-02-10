---
name: releasehub-ai-toolkit
description: Tools and workflow helpers for the ReleaseHub repo (.ai/context/openspec). Use when analyzing a task, deciding whether OpenSpec is required, finding relevant docs/experiences, or generating a “what to read next” context set before implementing changes.
---

# ReleaseHub AI Toolkit

Use the bundled script to deterministically:
- Classify a task (`feature`/`bugfix`/`refactor`/`optimization`/`docs`/`chore`)
- Extract domain + keywords
- Decide whether the task likely requires OpenSpec
- Suggest the most relevant context docs and historical experiences

## Quick usage (recommended)

Run from the ReleaseHub repo (script auto-detects repo root):

```bash
python3 ~/.codex/skills/releasehub-ai-toolkit/scripts/rh_ai.py bundle --task "添加 ReleaseWindow 冻结/解冻接口，并限制冻结后不可修改配置"
```

## Subcommands

- `analyze`: Print task analysis JSON
- `experience`: Search `.ai/summaries/experience-index.md` and print top matches
- `context`: Suggest which docs to read next (based on analysis + keyword search)
- `bundle`: Combined output (analyze + experience + context)

Examples:

```bash
python3 ~/.codex/skills/releasehub-ai-toolkit/scripts/rh_ai.py analyze --task "修复 XXX 接口返回不符合预期"
python3 ~/.codex/skills/releasehub-ai-toolkit/scripts/rh_ai.py experience --task "版本号格式验证边界情况" --top 5
python3 ~/.codex/skills/releasehub-ai-toolkit/scripts/rh_ai.py context --task "重构 ReleaseWindowAppService 方法命名，保持行为不变" --top 12
```

## Notes

- Experience search reads `.ai/summaries/experience-index.md`; keep it updated for better results.
- Context suggestions prioritize: task-direct docs > experiences > conventions > generic project context.
