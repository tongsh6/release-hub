# 2026-05-23 SA-001 场景矩阵清账与下一阶段候选排序

## 背景

SA-014 空仓库版本解析真实 GitLab 证据闭环后，`docs/reports/scenario-acceptance-matrix.md` 的 Phase 2 缺口池仍保留多条历史缺口。继续从这些 stale 条目中挑任务，会把项目推进变成局部脚本堆叠，而不是产品级收口。

## 范围

- 复核 Phase 2 缺口池，把已闭环、暂缓和下一阶段任务分开。
- 同步 `docs/project-ledger.md` 的 Top Priority 和关键证据索引。
- 同步 `docs/execution-roadmap.md` 的唯一 HEAD。
- 不做业务代码修改。

## 清账结果

- GitLab token 异常、GitLab 不可达、仓库 URL/版本解析异常、历史不合规分支、版本策略继承、冲突制造与解决、Run retry、多窗口并行、空仓库和大规模迭代均已由近期 focused slices 补齐或进入后续保持回归。
- 批量组织重构、批量资源迁移向导继续暂缓，不进入当前阶段。
- 下一阶段不直接挑新的局部功能，而是先执行全量场景验收基线复跑，确认近期 focused slices 在集成链路中仍成立。

## 验证

```bash
bash scripts/dev/check-roadmap.sh
git diff --check
```

结果：

- 路线图唯一 HEAD 指向 SA-001。
- Markdown diff 无尾随空白。

## 结论

Phase 2 存量缺口池已按产品状态清账。当前路线图 HEAD 转向 SA-001 全量场景验收基线复跑与发布候选判定。
