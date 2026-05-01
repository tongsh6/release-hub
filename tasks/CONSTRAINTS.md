# 任务规划硬约束

> 源自 [AI 工程治理准则](../docs/context/tech/architecture/ai-engineering-governance.md)，提炼为 7 条不可跳过的任务规划约束。任何 AI 在规划或执行 ReleaseHub 任务时必须遵守。

## 1. 完整蓝图先行

跨模块任务（涉及 2 个及以上 Maven 模块或前后端跨越）必须先写出完整目标蓝图，再拆成任务 DAG。

**完整蓝图必须包含：**
- 最终用户/系统行为
- 完整范围：后端、前端、数据、API、权限、审计、测试、文档
- 非目标：本轮明确不做但不应被误解为已完成的内容
- 架构形态：关键模块、Port/Adapter、策略、状态机
- 阶段计划：哪些本次做，哪些后续做
- 验收矩阵：完整能力的验收标准
- 风险与回滚：迁移、兼容、安全、性能、回滚路径

**禁止行为：**
- 只规划眼前最小部分，不记录完整目标
- 用"DAG"作为缩小目标的理由

> 模板：[blueprint-template.md](templates/blueprint-template.md)

## 2. 垂直切片不可绕过

每个任务切片必须是贯穿以下必要层的端到端可交付单元：

```
确认设计 → Domain → Application → Infrastructure → API → Frontend → Test → 文档
```

**一个合格垂直切片的检查清单：**

| 约束 | 必须满足 |
|------|---------|
| 蓝图归属 | 该切片属于完整目标蓝图的哪一部分 |
| 用户价值 | 切片完成后用户/调用方能观察到具体行为变化 |
| 端到端路径 | 至少贯穿 Domain/Application/API/Frontend/Test 的必要层 |
| 单一目标 | 一个切片只解决一个明确业务结果，不混入无关重构 |
| 可独立验证 | 有独立验收标准和自动化验证方式 |
| 可回滚 | 失败时能定位影响范围，必要时可单独回退 |
| 依赖明确 | 若依赖其他切片，在 DAG 中显式标出 |
| 风险收敛 | 性能、迁移、安全、外部系统副作用已在切片内声明 |

**禁止两类偏差：**
- 只做横向技术铺垫（"新增表""新增 Port""新增组件壳"），长期无用户可验证结果
- 只实现"最小闭环"，但不记录完整目标、剩余切片和后续推进路径

## 3. 依赖显式化：DAG 标注

切片间依赖必须以 DAG 形式标注。DAG 是推进顺序，不是缩小目标的理由。

**格式要求：**
- 每个切片标注前置依赖
- 可并行的切片标出并行边
- 串行切片说明依赖原因

**示例：**
```
确认设计
  → Slice 1: Domain（无依赖，模型基础）
  → Slice 2: Application + Port（依赖 Slice 1）
  → Slice 3: Infrastructure + API（依赖 Slice 2）
  → Slice 4: Frontend（依赖 Slice 3）
  → Slice 5: E2E + 文档（依赖 Slice 4）
```

## 4. TDD 强制

实现代码必须遵循 RED → GREEN → REFACTOR → VERIFY 顺序。

- **RED**：先写失败测试或复现用例
- **GREEN**：写刚好满足当前测试且不偏离完整规划的实现
- **REFACTOR**：消除重复、收敛命名、强化边界
- **VERIFY**：运行覆盖相关变更范围的必要测试

所有新增业务逻辑必须有对应的自动化测试覆盖。工具代码（getter/setter/DTO）可豁免。

## 5. 静态扫描留痕

代码实现完成后、进入最终交付前，必须执行静态代码扫描并保留证据。

```bash
scripts/dev/static-scan-topn.sh 10
```

**强制流程：**
1. **扫描**：生成 `.ai/reports/static-scan/<timestamp>/summary.md`，保留 raw 日志
2. **排序**：提取 TopN，按安全漏洞、错误、架构违规、可维护性风险排序
3. **修复**：优先修复 TopN；跳过任一项必须写明原因
4. **复扫**：修复后必须复扫，覆盖被修复项所在模块或文件
5. **留痕**：在报告中记录每项处理方式、处理结果、复扫证据和未解决风险

TopN 默认值为 10。任务体量较小时可降为 5，但必须在报告中说明。安全或架构类问题不得因 TopN 截断而忽略。

## 6. 事后检查：切片级复盘

每个切片完成后，必须做 7 项检查再进入下一个切片：

| 检查项 | 判定标准 |
|--------|---------|
| 行为闭环 | 需求中的可观察行为已经实现 |
| 层级闭环 | 需要触达的层都已接通，无悬空 DTO/空 API/未接 UI |
| 测试闭环 | RED/GREEN 证据存在，关键路径至少一种测试覆盖 |
| 架构闭环 | 未违反 DDD 分层、Port/Adapter、深模块、正交性和切面化约束 |
| 性能闭环 | 多仓库/批量/远程调用场景无新增 N+1、串行瓶颈 |
| 文档闭环 | tasks、需求、OpenSpec、上下文或经验已按实际结果同步 |
| 工作区闭环 | `git status --short` 已检查，临时产物已归档或说明 |

## 7. 经验沉淀

任务结束后检查：

- 是否有可复用的操作模式需要记录
- 是否踩了值得记录的坑
- 如果有：在 `docs/context/experience/lessons/` 创建经验文档，更新 `INDEX.md`
- 是否有临时文件、截图、报告需要归档或清理

## 架构约束速查

| 变化轴 | 应放置位置 | 禁止行为 |
|--------|-----------|---------|
| 发布窗口生命周期 | `domain/releasewindow` | 在 Controller 或 UI 中硬编码状态流转 |
| Git 操作 | `application/port/out` + `infrastructure/git` | Application 绑定具体 Git API |
| 版本语义 | `domain/version` + `application/version` | Maven/Gradle 文件细节进入领域层 |
| 冲突检测 | `domain/conflict` + `application/conflict` | 多类冲突堆在一个巨型方法中 |
| 执行记录与任务 | `domain/run` + `application/run` | 用一次性脚本替代可恢复任务 |
| UI 展示 | `frontend/src/views` | 前端推导后端业务真相 |

新增变化点优先走 **Port/Strategy/Registry** 扩展：新 Git Provider、新构建工具、新冲突检测类型、新 RunTask 执行器、新分支规则类型。
