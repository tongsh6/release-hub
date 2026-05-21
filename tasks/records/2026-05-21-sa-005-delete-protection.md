# Slice: SA-005 删除保护扩展

## 任务

按 `docs/execution-roadmap.md` 当前 HEAD，补齐 SA-005「删除保护扩展」。

## 蓝图归属

- 场景矩阵：`docs/reports/scenario-acceptance-matrix.md` SA-005
- 主线：场景矩阵驱动收口

## 选择理由

SA-005 已完成 Clone URL 保护、版本解析失败修复引导和组织筛选，剩余最直接缺口是删除保护。删除保护属于 Admin Setup 的数据安全边界：仓库或分组一旦被迭代、发布窗口等业务对象引用，直接删除会破坏后续发布计划和审计证据。

## 实现摘要

| 层级 | 处理 |
|---|---|
| 后端 | 保留既有业务约束，并补应用层测试：仓库被迭代引用时 `REPO_011`；分组有子分组时 `GROUP_008`；分组被仓库、迭代或发布窗口引用时 `GROUP_013` |
| 前端 | 新增 `deleteProtectionMessageKey`，仓库列表、分组列表、分组详情对删除保护错误码展示明确阻断提示 |
| 测试 | 补后端应用层单测、前端工具和页面 Vitest |
| 文档 | 同步场景矩阵、项目台账、执行路线图和本任务记录 |

## 验证

```bash
mvn -pl releasehub-application -am -Dtest=CodeRepositoryAppServiceTest,GroupAppServiceValidationTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/utils/__tests__/deleteProtection.spec.ts src/views/repository/__tests__/RepositoryList.spec.ts src/views/group/__tests__/GroupList.spec.ts
```

结果：

- 后端：20 PASS / 0 FAIL / 0 SKIP
- 前端：5 PASS / 0 FAIL / 0 SKIP

## 事后检查

| 检查项 | 结论 |
|---|---|
| 行为闭环 | 仓库/分组删除保护和前端明确提示已闭环 |
| 层级闭环 | Application、Frontend、Test、Docs 均已覆盖 |
| 测试闭环 | 后端应用层单测与前端 Vitest 已覆盖关键路径 |
| 架构闭环 | 不改变 DDD 分层；前端仅映射后端错误码，不推导业务真相 |
| 性能闭环 | 未引入新增查询路径；沿用既有删除前引用检查 |
| 文档闭环 | 场景矩阵、项目台账、路线图和任务记录已同步 |
| 工作区闭环 | 待最终 `git status --short` 和静态扫描确认 |

## 后续

SA-005 删除保护出队，`docs/execution-roadmap.md` 当前 HEAD 切换为 SA-014「版本更新失败重试」。
