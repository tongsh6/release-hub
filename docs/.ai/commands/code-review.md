# Command: Code Review

## 用法

```
/code-review [范围描述或文件/模块]
```

## 功能

生成代码审查报告，关注：DDD 分层、可测试性、安全、性能与可维护性，并输出可执行的改进建议。

代码审查必须检查实现后静态扫码证据：

- 是否存在 `.ai/reports/static-scan/<timestamp>/summary.md`
- TopN 是否已逐项处理或记录不处理原因
- 是否有复扫证据
- 是否仍存在安全、架构或阻塞级风险

## 产物

- `.ai/reports/code-review/YYYY-MM-DD-<topic>.md`
