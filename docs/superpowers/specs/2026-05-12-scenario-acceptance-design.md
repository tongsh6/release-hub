# 场景化验收完善设计

> 日期：2026-05-12 | 状态：Draft for Review

## 一、目标

把现有验收从“技术链路可用”升级为“业务场景可解释、覆盖缺口可追踪、自动化可持续补齐”的体系。

当前 `scripts/acceptance/run-acceptance.sh` 已能验证真实 GitLab、PostgreSQL、后端 API、Run 明细、分支模式、冲突检测与干净路径。下一步不重写它，而是为它建立场景矩阵，并补齐第一批最影响发布信心的自动化缺口。

## 二、最终行为

验收体系最终包含三层：

1. **场景矩阵**：按角色、用户故事、场景类型和验证层级描述“为什么验收、验收什么、由什么自动化覆盖”。
2. **后端真实 GitLab 验收**：继续以 `run-acceptance.sh` 为主入口，验证 API、数据库状态、GitLab 实物状态和 Run 记录一致。
3. **前端 Playwright 旅程验收**：补齐用户能否从 UI 完成关键观察与判断，包括窗口状态、冲突报告、Run 详情和历史数据可见性。

## 三、范围

### 本轮范围

- 新增场景化验收矩阵文档。
- 将现有 `run-acceptance.sh`、后端 E2E、前端 Playwright 映射到矩阵。
- 第一批自动化只补 3 类缺口：
  - 干净窗口黄金路径：`0 冲突 -> Publish -> Orchestrate SUCCESS -> RunItem > 0`。
  - 冲突恢复闭环：发现冲突、解决冲突、重新扫描为 0、重试成功。
  - 前端可观察性：用户能从页面看到发布窗口、冲突/状态、Run 详情。
- 保留现有验收脚本“不 DROP、不 DELETE、不破坏历史”的本地持久化原则。

### 非目标

- 不实现 RBAC，也不把角色映射为真实权限控制。
- 不做大规模并发、极限数据量、性能压测。
- 不重写现有 E2E 框架。
- 不把所有边界场景一次性自动化。

## 四、场景维度

### 角色维度

| 角色 | 核心验收关注点 |
|---|---|
| 系统管理员 | GitLab Settings、分组、仓库、分支规则是否配置正确且持久化 |
| 发布经理 | 发布窗口生命周期、挂载、发布、关闭、失败后的重试路径是否闭环 |
| 技术负责人 | 仓库导入、迭代关联、分支创建模式、版本冲突解决是否可靠 |
| 测试人员 | 冲突报告、发布状态、Run 执行明细是否可观察、可复核 |

### 场景类型

| 类型 | 本轮处理方式 |
|---|---|
| 正常路径 | 作为 P0 覆盖，必须自动化 |
| 异常恢复 | 选择冲突恢复作为第一批自动化 |
| 边界场景 | 本轮只记录缺口，不批量实现 |
| 并发场景 | Phase 2 处理 |

### 验证层级

| 层级 | 验证对象 | 主入口 |
|---|---|---|
| API/数据/GitLab | API 响应、数据库状态、GitLab 分支/Commit、Run 明细 | `scripts/acceptance/run-acceptance.sh` |
| 后端 E2E | Spring Boot 用例、真实或 Mock GitProvider 行为 | `backend/releasehub-bootstrap/src/test/java/.../e2e` |
| 前端 UI | 页面是否可完成观察与操作 | `frontend/e2e/tests/*.spec.ts` |
| 文档矩阵 | 场景覆盖、缺口、优先级、验证方式 | 新增验收矩阵文档 |

## 五、验收矩阵草案

| ID | 角色旅程 | 场景 | 类型 | 当前覆盖 | 本轮动作 |
|---|---|---|---|---|---|
| SA-001 | 管理员配置基础环境 | GitLab Settings 配置并重启后仍存在 | 正常 | `run-acceptance.sh` 3.2.1 / 3.7 | 矩阵登记 |
| SA-002 | 管理员治理数据质量 | 存量数据审计：Token 加密、BranchCreationMode、featureBranch、cloneUrl | 正常 | `run-acceptance.sh` 1.x | 矩阵登记 |
| SA-003 | 发布经理创建发布窗口 | 新建窗口、创建迭代、注册 3 个真实仓库 | 正常 | `run-acceptance.sh` 3.x | 矩阵登记 |
| SA-004 | 技术负责人准备迭代分支 | AUTO/NAMED/EXISTING 分支创建模式和非法分支拒绝 | 正常/边界 | `run-acceptance.sh` 10.x | 矩阵登记 |
| SA-005 | 发布经理挂载迭代 | Attach 后 GitLab release 分支真实存在，WindowIteration 状态正确 | 正常 | `run-acceptance.sh` 4.x | 矩阵登记 |
| SA-006 | 测试人员检查发布风险 | 冲突检测返回数量和类型分布 | 正常 | `run-acceptance.sh` 5.x | 矩阵登记 |
| SA-007 | 发布经理执行干净发布 | 干净窗口 0 冲突后 Publish + Orchestrate SUCCESS，RunItem > 0 | 正常 | `run-acceptance.sh` 5.2 已有雏形 | 固化为正式验收项 |
| SA-008 | 技术负责人解决冲突 | 版本冲突解决后重新扫描为 0，再发布成功 | 异常恢复 | 部分覆盖 | 补齐脚本断言 |
| SA-009 | 测试人员复核执行明细 | Run 详情含 RunItem 和 RunStep 分布 | 正常 | `run-acceptance.sh` 7.x | 补前端观察路径 |
| SA-010 | 发布经理处理业务阻断 | 未解决冲突时 Orchestrate 返回 `CONFLICT_001`，被判定为业务正确拒绝 | 异常 | `run-acceptance.sh` 6.x | 矩阵登记 |
| SA-011 | 技术负责人执行版本更新 | 版本更新 Run 成功，GitLab 远程 Commit 可验证 | 正常 | `run-acceptance.sh` 8.x，当前可能被冲突阻断 | 绑定干净窗口重验 |
| SA-012 | 测试人员通过 UI 观察发布状态 | 前端可查看窗口、Run、日历、仓库历史 | 正常 | `frontend/e2e/tests/slice-2-full-flow.spec.ts` 冒烟 | 升级关键断言 |
| SA-013 | 测试人员通过 UI 复核冲突/Run | UI 能进入发布窗口详情和 Run 详情，看到执行结果 | 正常 | 缺口 | 补 Playwright |

## 六、第一批自动化切片

### Slice A：验收矩阵文档

产出一份稳定入口文档，建议路径：

`docs/reports/scenario-acceptance-matrix.md`

内容包括：

- 当前基线。
- 场景矩阵。
- 每个场景对应脚本/测试文件。
- 已覆盖、部分覆盖、未覆盖状态。
- Phase 2 缺口池。

### Slice B：后端真实 GitLab 验收脚本增强

在 `scripts/acceptance/run-acceptance.sh` 上做小步增强：

- 为场景输出增加 SA 编号，方便报告和矩阵追踪。
- 将 5.2 干净路径的 `items > 0`、RunStep 分布、最终状态作为硬断言。
- 将版本更新优先绑定到干净窗口，避免主窗口冲突导致 SA-011 长期 SKIP。
- 明确冲突恢复场景：解决后重新扫描，断言 total=0。

### Slice C：前端 Playwright 观察路径

在现有 `frontend/e2e/tests/` 中补最小旅程：

- 从发布窗口列表进入详情。
- 打开运行记录页，定位最近 Run。
- 进入 Run 详情，断言能看到状态、类型、步骤或明细区域。
- 保留现有冒烟测试，不要求一次覆盖所有操作。

## 七、风险与处理

| 风险 | 影响 | 处理 |
|---|---|---|
| `run-acceptance.sh` 继续膨胀 | 可维护性下降 | 本轮只做编号、断言、干净路径绑定，不引入复杂框架 |
| 本地持久化数据累积导致冲突 | 干净路径不稳定 | 使用唯一窗口、唯一 NAMED feature 分支，并记录清理脚本为 Phase 2 |
| 前端 E2E 受历史数据和分页影响 | 测试脆弱 | 通过唯一名称搜索或仅断言稳定入口和详情区域 |
| 角色尚未真实权限化 | 矩阵角色可能被误解为权限测试 | 文档明确角色是验收视角，不是 RBAC 验收 |

## 八、Phase 2 缺口池

- GitLab token 过期/无效后的提示与恢复。
- GitLab 不可达时的错误展示和恢复。
- release 分支累积冲突的一键清理脚本。
- 合并冲突制造、解决和 Run retry。
- 多窗口并行发布。
- 空仓库、无 `pom.xml` / `gradle.properties`、异常版本号。
- 100 个迭代或多仓库批量窗口。

## 九、完成标准

本轮完成后应满足：

- 有一份可维护的场景化验收矩阵。
- 每个 P0 场景都能找到对应自动化入口或明确缺口。
- 干净黄金路径不再只作为脚本中间段存在，而是正式 PASS/FAIL 验收项。
- 冲突恢复路径具备可重复断言。
- 前端至少覆盖“查看发布状态和执行证据”的关键用户观察路径。
