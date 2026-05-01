# Command: Static Scan TopN

## 用法

```bash
scripts/dev/static-scan-topn.sh [TopN]
```

默认 TopN 为 `10`，也可通过环境变量指定：

```bash
TOP_N=5 scripts/dev/static-scan-topn.sh
```

## 触发时机

任何 AI 工具完成代码实现后，进入最终交付前必须执行本命令或等价静态扫描命令。

## 工作要求

1. 运行静态扫描并生成 `.ai/reports/static-scan/<timestamp>/summary.md`。
2. 阅读报告中的 TopN 问题清单。
3. 优先修复 TopN；不能修复或不应修复的项，必须记录原因。
4. 修复后复扫，并把处理方式、处理结果、复扫证据补回同一份报告。
5. 最终回复必须给出报告路径、TopN 处理结论和未解决风险。

## 报告留痕

- 汇总报告：`.ai/reports/static-scan/<timestamp>/summary.md`
- 原始日志：`.ai/reports/static-scan/<timestamp>/raw/*.txt`

报告是交付证据，不得删除。
