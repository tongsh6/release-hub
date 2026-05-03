# ReleaseHub 功能开发规划

> **📦 历史参考文档**：本文档撰写于 2026-01-28，2026-05-01~02 通过 Phase 1-7 将其中所有 P1 任务全部完成（v0.1.8）。
>
> **当前计划请查看**：
> - 推进计划：`tasks/plans/`
> - 执行记录：`tasks/records/`
> - 项目进度：`docs/PROJECT_PROGRESS.md`
> - 任务规范：`tasks/QUICK_START.md` 和 `tasks/CONSTRAINTS.md`
>
> 本文档保留作为功能演进历史参考，不再作为当前任务的权威来源。
>
> ---
>
> 最后更新：2026-01-28（2026-05-01 同步实际完成状态）
> 基于项目扫描和代码分析
> ⚠️ 本文档中标记为 P1 的多项功能已于后续迭代中完成，详见项目现状分析

## 一、项目现状分析

### 1.1 已实现功能（✅）

#### 核心领域模型
- ✅ **ReleaseWindow（发布窗口）**
  - CRUD 操作
  - 状态流转：DRAFT → PUBLISHED → CLOSED（文案：待发布/已发布/已关闭）
  - 上线时间配置（plannedReleaseAt）
  - 冻结/解冻机制
  - 窗口与迭代关联（WindowIteration）

- ✅ **Iteration（迭代）**
  - 迭代管理（CRUD）
  - 迭代与仓库关联（Set<RepoId>）
  - 窗口关联管理（Attach/Detach）

- ✅ **CodeRepository（代码仓库）**
  - 仓库 CRUD
  - GitLab 集成（分支/MR 统计）
  - 仓库同步功能
  - 健康状态检查

- ✅ **Group（分组）**
  - 分组层级管理
  - 父子关系维护

- ✅ **Run（运行记录）**
  - Run 实体（runId、runType、operator、status）
  - RunItem（执行项）
  - RunStep（执行步骤）
  - 运行记录查询、导出、重试

#### 版本管理核心功能（MVP 已完成）
> 说明：以下能力已实现，但当前业务主线以发布准备/收尾为主，不强制依赖。
- ✅ **VersionUpdater（版本更新器）**
  - VersionUpdater Port 接口（application 层）
  - VersionUpdateRequest / VersionUpdateResult 模型
  - 版本更新工厂模式（根据 BuildTool 选择更新器）

- ✅ **MavenVersionUpdater**
  - pom.xml 解析（DOM）
  - 单模块版本更新逻辑
  - Diff 生成（更新前后对比）
  - 单元测试覆盖

- ✅ **GradleVersionUpdater**
  - gradle.properties 版本更新
  - build.gradle 不支持时的明确提示
  - Diff 生成
  - 单元测试覆盖

- ✅ **版本更新执行服务**
  - VersionUpdateAppService（application 层）
  - 集成到 Run 执行流程（RunAppService）
  - 批量版本更新支持
  - 执行记录存储（RunItem、RunStep）

- ✅ **版本校验功能**
  - VersionValidationAppService
  - 版本推导服务（根据 VersionPolicy）
  - 版本号格式校验
  - 校验 API（`POST /api/v1/release-windows/{id}/validate`）

- ✅ **版本更新 API**
  - `POST /api/v1/release-windows/{id}/execute/version-update`
  - `POST /api/v1/release-windows/{id}/execute/batch-version-update`
  - API 文档（Swagger）完整

#### 规则与配置
- ✅ **VersionPolicy（版本策略）**
  - 领域模型实现
  - 持久化适配器（VersionPolicyPersistenceAdapter）
  - 数据初始化器（MAJOR, MINOR, PATCH, DATE 四种策略）
  - 前端页面（VersionPolicyList.vue）

 - ✅ **BranchRule（分支规则）**
   - 领域模型、Port、适配器、Controller 已实现
   - 支持规则 CRUD 与合规校验（glob 模式匹配）

- ✅ **Settings（系统设置）**
  - GitLab 设置（业务范围内）
  - 命名规则/阻塞策略设置（代码存在但不在当前业务范围）

#### 认证与权限
- ✅ **Auth（认证）**
  - JWT 认证
  - 用户登录（admin/admin）
  - 密码加密（BCrypt）

#### 前端页面
- ✅ 发布窗口列表/详情
- ✅ 迭代列表/详情
- ✅ 仓库列表/详情
- ✅ 运行记录列表/详情
- ✅ 版本策略列表
- ✅ 分支规则列表（前端）
- ✅ 设置页面
- ✅ 仪表板
- ✅ 分组管理
- ✅ 版本更新对话框（VersionUpdateDialog）
- ✅ Diff 查看组件（DiffViewer）

#### 测试覆盖（2026-05-02 更新）
- ✅ 后端测试：134/134 通过（52 单元/集成 + 82 E2E TestContainers）
- ✅ 前端 E2E：62/62 通过（Playwright, 6 套件）
- ✅ ArchUnit 架构测试（11 个测试通过）
- ✅ 前端 typecheck / lint 通过

### 1.2 部分实现/待完善功能（⚠️）

#### 前端对齐
- ✅ **BranchRule 前端升级**
  - 现状：已升级为 TEMPLATE/REGEX，支持 scope/启用/禁用/测试
  - **2026-05-01 完成**（Phase 1，20 文件变更）

- ✅ **Version Ops Dashboard 对接**
  - 现状：已对接真实 API（runs/logs），替换硬编码数据
  - **2026-05-01 完成**（Phase 2，新增 VersionOpsController）

#### 规则与分组对齐
- ✅ **分组 code 自动生成**
  - 目标口径：仅输入名称，code 按 001/001001... 自动生成
  - 现状：`GroupAppService.create()` 已实现自动生成逻辑（v0.1.x 完成）

- ✅ **groupCode 关联到发布窗口/迭代/仓库**
  - 目标口径：创建/修改时必选末端分组并提交 groupCode
  - 现状：ReleaseWindow/Iteration/CodeRepository 均包含 groupCode + 叶子节点校验（v0.1.x 完成）

#### 发布准备与收尾自动化
- ✅ **提测合并与收尾编排自动化**（Phase 6, 2026-05-02）
  - Attach 错误可见性：`AttachResult` 值对象 + `hasErrors`/`errors` 字段
  - Publish 自动编排：`WindowPublishedEvent` → `WindowLifecycleListener`（AFTER_COMMIT）
  - 剩余缺口：Attach 分支操作集成 Run 追踪（技术债务，择期处理）

### 1.3 已完成增强功能（✅，原列在"未实现"中）

- ✅ **发布窗口日历视图**（2026-05-01，Phase 3）
  - 月视图 + 周视图 + 同日冲突可视化
- ✅ **冲突检测增强**（2026-04-29）
  - 版本号冲突 / 分支冲突 / 跨仓库一致性 / Git 合并冲突四维预检（7 种冲突类型）

### 1.4 未来功能（Roadmap）
- ❌ GitOps（自动建分支、打 tag、生成 changelog）
- ❌ CI/CD 集成（Jenkins/GHA）
- ❌ 通知（飞书/钉钉/邮件）
- ❌ RBAC 权限体系
- ❌ 多租户支持

---

## 二、领域模型关系

### 核心实体关系图

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           ReleaseHub 核心领域模型                         │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────────┐        N:N         ┌─────────────┐                     │
│   │ ReleaseWindow│◄─────────────────►│  Iteration  │                     │
│   │ (发布窗口)   │  WindowIteration   │  (迭代)     │                     │
│   └─────────────┘                    └──────┬──────┘                     │
│         │                                   │                            │
│         │ 发布编排/执行                       │ N:N (包含)                  │
│         ▼                                   ▼                            │
│   ┌─────────────┐                    ┌─────────────┐                     │
│   │    Run      │                    │CodeRepository│                    │
│   │ (运行记录)   │                    │ (代码仓库)   │                    │
│   └─────────────┘                    └─────────────┘                     │
└──────────────────────────────────────────────────────────────────────────┘
```

### 关系说明

| 关系 | 类型 | 说明 |
|------|------|------|
| ReleaseWindow ↔ Iteration | N:N | 通过 WindowIteration 关联，一个窗口可关联多个迭代 |
| Iteration → CodeRepository | N:N | 迭代包含多个仓库（Set<RepoId>） |
| ReleaseWindow → Run | 1:N | 发布准备/收尾编排产生运行记录 |

---

## 三、功能开发优先级

### P0 - 阻塞功能使用（必须完成）

#### 3.1 BranchRule 后端 API 实现（✅ 已完成）
**目标**：完善规则中心，使分支规则功能可用。

**任务清单**：
- [x] 创建 BranchRule 领域模型（聚合根）
- [x] 创建 BranchRulePort 接口（application 层）
- [x] 实现 BranchRulePersistenceAdapter（infrastructure 层）
- [x] 创建 BranchRuleController CRUD API
- [x] 实现规则校验逻辑（模板 + 正则）

**预估工作量**：已完成

### P1 - 重要增强功能

#### 3.2 分组 code 自动生成 ✅
**目标**：仅输入名称即可生成分组 code。

**任务清单**：
- [x] 自动生成规则：001、001001、002001...
- [x] 保持更新/删除不影响既有码

**预估工作量**：已完成（v0.1.x）

#### 3.3 groupCode 关联到发布窗口/迭代/仓库 ✅
**目标**：创建/修改时必选分组末端节点并提交 groupCode。

**任务清单**：
- [x] 请求体补齐 groupCode
- [x] 末端节点校验

**预估工作量**：已完成（v0.1.x）

#### 3.4 发布准备与收尾自动化对齐 ✅
**目标**：提测合并与收尾编排按规则自动触发。

**任务清单**：
- [x] 挂载迭代自动创建 release 分支并合并 feature/hotfix
- [x] 解除挂载归档分支，归档原因为 unpublished
- [x] 收尾编排归档分支与迭代，归档原因为 released
- [x] Attach 错误可见性（`AttachResult`，Phase 6）
- [x] Publish 自动编排（`WindowPublishedEvent` → `WindowLifecycleListener`，Phase 6）

**预估工作量**：已完成（2026-05-02 Phase 6），剩余 Attach Run 追踪集成为技术债务

#### 3.5 BranchRule 前端升级 ✅
**目标**：前端对接新 API，支持 scope/启用/禁用/测试。

**任务清单**：
- [x] 替换旧模型（ALLOW/BLOCK）为 TEMPLATE/REGEX
- [x] 对接新 API：enable/disable/test
- [x] 补齐表单字段与校验

**预估工作量**：已完成（2026-05-01 Phase 1）

#### 3.6 Version Ops Dashboard 对接 ✅
**目标**：对接真实 API，替换硬编码数据。

**任务清单**：
- [x] 接入 runs/logs API
- [x] 补齐运行详情与日志展示
- [x] 完善加载与错误提示

**预估工作量**：已完成（2026-05-01 Phase 2）

#### 3.7 发布窗口日历视图 ✅
**目标**：以迭代与发布窗口视角展示统计。

**任务清单**：
- [x] 月视图与周视图
- [x] 迭代/发布窗口时间线展示
- [x] 冲突可视化提示

**预估工作量**：已完成（2026-05-01 Phase 3）

### P2 - 体验优化功能

- ✅ 冲突检测增强（2026-04-29 完成）
- 规则预览
- 仪表板增强

---

## 四、开发计划建议

> **📦 历史参考**：以下为 2026-01-28 制定的 3 周计划。实际执行于 2026-05-01~02 以 7 个 Phase 在 2 天内完成（详见 `tasks/records/`）。以下内容保留作为规划方法参考，不代表实际执行时序。

### Phase 1：流程对齐（1 周）

**本周目标**：分组规则与发布准备/收尾自动化对齐。

| 天数 | 任务 |
|------|------|
| Day 1-2 | 分组 code 自动生成与末端节点校验 |
| Day 3 | groupCode 关联到发布窗口/迭代/仓库 |
| Day 4-5 | 提测合并与收尾编排自动化对齐 |

### Phase 2：前端对齐（1 周）

**本周目标**：完成 BranchRule 前端升级与 Version Ops Dashboard 对接。

| 天数 | 任务 |
|------|------|
| Day 1-2 | BranchRule 前端升级 |
| Day 3-4 | Version Ops Dashboard 对接 |
| Day 5 | 回归测试和文档更新 |

### Phase 3：体验增强（1 周）

**本周目标**：完成日历视图与冲突可视化。

| 天数 | 任务 |
|------|------|
| Day 1-2 | 日历组件集成与时间线展示 |
| Day 3 | 冲突可视化提示 |
| Day 4-5 | 集成测试与优化 |

---

## 五、验收标准

### 5.1 核心 MVP 功能验收（✅ 已完成）
- ✅ 可以从 UI 创建 Release Window
- ✅ 可以绑定仓库/迭代
- ✅ 提测合并与收尾编排可用
- ✅ 可查看运行记录与任务结果
- ✅ 版本校验功能可用

### 5.2 规则中心验收（✅ 已完成）
- ✅ BranchRule CRUD API 可用
- ✅ 分支规则可应用到仓库
- ✅ 规则校验功能可用

### 5.3 信息一致性
- ✅ 同一发布窗口的"上线时间、关联迭代、关联仓库、状态"可追踪
- ✅ 有审计记录（createdBy/createdAt/updatedAt）

### 5.4 可扩展性
- ✅ 版本更新器可插拔（Port/Adapter 模式）
- ✅ 支持后续扩展到 CI、通知、权限

---

## 六、技术债务和风险

### 6.1 已解决的技术债务
- ✅ 版本更新器实现（Maven/Gradle）
- ✅ 数据库字段类型（message 字段改为 TEXT）
- ✅ 错误处理改进（BizException + 404 状态码）

### 6.2 已解决的技术债务（2026-05-02 更新）
1. ~~**BranchRule 前端升级**~~ → ✅ Phase 1
2. ~~**Version Ops Dashboard 未对接**~~ → ✅ Phase 2
3. ~~**分组 code 自动生成缺口**~~ → ✅ v0.1.x
4. ~~**groupCode 关联缺口**~~ → ✅ v0.1.x
5. ~~**发布准备/收尾自动化缺口**~~ → ✅ Phase 6（Attach 错误可见性 + Publish 自动编排）

### 6.3 待解决的技术债务
1. **Attach 分支操作集成 Run 追踪**（Phase 6 遗留）
   - AttachAppService 和 RunAppService 存在并行实现，统一两者需要较大架构调整
2. **预存 SpotBugs EI_EXPOSE_REP × 5**（`releasehub-common`）
   - PageResult / ApiPageResponse 可变对象暴露，全局影响

### 6.4 风险与对策（2026-05-02：以下风险均已解除）

1. ~~**前端与后端模型不一致**~~ → ✅ Phase 1 已统一
2. ~~**前端日历组件集成**~~ → ✅ Phase 3 已完成（FullCalendar + 冲突可视化）

当前无活跃风险项。

---

## 附录：相关文件参考

- **项目总体规划书**: `release-hub/release_hub_项目总体规划书.md`
- **OpenSpec 工作流**: `openspec/AGENTS.md`
- **后端结构报告**: `release-hub/BACKEND_STRUCTURE_REPORT.md`
- **API 文档**: `release-hub/docs/RELEASE_WINDOW_API.md`
- **控制台 IA 规格**: `release-hub/docs/releasehub-console-ia-v1.2.md`
- **下一步任务清单**: `NEXT_STEPS_TASKS.md`
- **集成测试报告**: `INTEGRATION_TEST_REPORT.md`
