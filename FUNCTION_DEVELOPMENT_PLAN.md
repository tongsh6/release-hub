# ReleaseHub 功能开发规划

> 生成时间：2025-01-27  
> 基于项目扫描和代码分析

## 一、项目现状分析

### 1.1 已实现功能（✅）

#### 核心领域模型
- ✅ **ReleaseWindow（发布窗口）**
  - CRUD 操作
  - 状态流转：DRAFT → PUBLISHED → RELEASED → CLOSED
  - 时间窗口配置（startAt/endAt）
  - 冻结/解冻机制
  - 窗口与迭代关联（WindowIteration）

- ✅ **Iteration（迭代）**
  - 迭代管理（CRUD）
  - 迭代与仓库关联
  - 窗口关联管理（Attach/Detach）

- ✅ **CodeRepository（代码仓库）**
  - 仓库 CRUD
  - GitLab 集成（分支/MR 统计）
  - 仓库同步功能
  - 健康状态检查

- ✅ **Project（项目）**
  - 项目管理（CRUD）

- ✅ **Group（分组）**
  - 分组层级管理
  - 父子关系维护

- ✅ **Run（运行记录）**
  - Run 实体（runId、runType、operator、status）
  - RunItem（执行项）
  - RunStep（执行步骤）
  - 运行记录查询

#### 编排与执行
- ✅ **Plan（计划编排）**
  - 计划生成（PlanAppService）
  - 干运行计划（DryPlanAppService）

- ✅ **Attach/Detach（关联管理）**
  - 窗口与迭代关联
  - 关联/解关联操作

- ✅ **Orchestrate（编排执行）**
  - 编排执行服务
  - 执行流程控制

#### 规则与配置
- ✅ **BranchRule（分支规则）**
  - 前端页面已存在（BranchRuleList.vue）
  - 需要确认后端完整实现

- ✅ **VersionPolicy（版本策略）**
  - 前端页面已存在（VersionPolicyList.vue）
  - 需要确认后端完整实现

- ✅ **Settings（系统设置）**
  - GitLab 设置
  - 命名规则设置
  - 阻塞策略设置
  - 引用设置

#### 认证与权限
- ✅ **Auth（认证）**
  - JWT 认证
  - 用户登录
  - 密码加密（BCrypt）

#### 前端页面
- ✅ 发布窗口列表/详情
- ✅ 迭代列表/详情
- ✅ 仓库列表/详情
- ✅ 运行记录列表/详情
- ✅ 分支规则列表
- ✅ 版本策略列表
- ✅ 设置页面
- ✅ 仪表板
- ✅ 分组管理

### 1.2 部分实现/待完善功能（⚠️）

#### 版本管理核心功能
- ⚠️ **版本号自动更新（VersionUpdater）**
  - 总体规划书中明确为核心功能
  - 代码中未发现 VersionUpdater 实现
  - **优先级：P0（最高）**

- ⚠️ **Maven 版本更新**
  - pom.xml 版本更新逻辑
  - 多模块版本一致性处理
  - Diff 生成

- ⚠️ **Gradle 版本更新**
  - gradle.properties 版本更新
  - build.gradle 版本更新（可选）

- ⚠️ **版本更新执行记录**
  - 版本更新 Run 的详细实现
  - Diff 查看功能
  - 执行结果展示

#### 子项目管理
- ⚠️ **SubProject（子项目）**
  - 领域模型中提及但未实现
  - Maven module / Gradle subproject 管理

#### 规则中心后端
- ⚠️ **BranchRule 后端实现**
  - 前端页面存在，需确认后端 API 完整性

- ⚠️ **VersionPolicy 后端实现**
  - 前端页面存在，需确认后端 API 完整性

### 1.3 未实现功能（❌）

#### 核心 MVP 功能
- ❌ **版本更新执行流程**
  - ReleaseWindow → 绑定仓库 → 执行版本更新 → 查看结果
  - 这是总体规划书中的核心闭环

- ❌ **版本校验（Validate）**
  - 根据 BranchRule/VersionPolicy 推导 plannedBranch/Version
  - 校验接口实现

- ❌ **版本更新器工厂**
  - Maven/Gradle 更新器策略模式
  - 可插拔的更新器接口

#### 增强功能
- ❌ **发布窗口日历视图**
  - 可视化展示发布窗口时间线

- ❌ **冲突检测**
  - 发布窗口时间冲突检测
  - 版本号冲突检测

- ❌ **规则预览**
  - 分支规则预览
  - 版本策略预览

#### 未来功能（Roadmap）
- ❌ GitOps（自动建分支、打 tag、生成 changelog）
- ❌ CI/CD 集成（Jenkins/GHA）
- ❌ 通知（飞书/钉钉/邮件）
- ❌ RBAC 权限体系
- ❌ 多租户支持

## 二、功能开发优先级

### P0 - 核心 MVP 功能（必须实现）

#### 2.1 版本号自动更新核心功能
**目标**：实现从 UI 创建 Release Window → 绑定仓库 → 一键执行版本更新 → 可看到执行结果与差异的完整闭环。

**任务清单**：
1. **VersionUpdater 接口设计**
   - 定义 VersionUpdater Port 接口
   - 支持 Maven/Gradle 两种实现
   - 定义更新结果（成功/失败、diff、错误信息）

2. **Maven VersionUpdater 实现**
   - pom.xml 解析（使用 DOM/SAX）
   - 版本号更新逻辑
   - 多模块版本一致性处理
   - Diff 生成（更新前后对比）

3. **Gradle VersionUpdater 实现**
   - gradle.properties 版本更新
   - build.gradle 版本更新（基础实现）

4. **版本更新执行服务**
   - 集成 VersionUpdater 到 Run 执行流程
   - 执行记录存储
   - 错误处理和回滚机制

5. **版本更新 API**
   - `POST /api/v1/release-windows/{id}/execute/version-update`
   - 返回 runId 和执行状态

6. **版本更新结果查看**
   - Run 详情页展示 diff
   - 执行步骤详情
   - 错误信息展示

**预估工作量**：7-10 天

#### 2.2 版本校验功能
**目标**：在执行版本更新前，根据 BranchRule/VersionPolicy 推导并校验 plannedBranch/Version。

**任务清单**：
1. **版本推导服务**
   - 根据 VersionPolicy 推导目标版本
   - 根据 BranchRule 推导分支名

2. **校验服务**
   - 版本号格式校验
   - 分支名格式校验
   - 冲突检测（版本号冲突、分支冲突）

3. **校验 API**
   - `POST /api/v1/release-windows/{id}/validate`
   - 返回校验结果（OK/ERROR + message）

**预估工作量**：3-5 天

#### 2.3 BranchRule/VersionPolicy 后端完善
**目标**：确保规则中心功能完整可用。

**任务清单**：
1. **BranchRule 后端实现**
   - 领域模型（BranchRule 聚合根）
   - CRUD API
   - 规则模板和正则校验

2. **VersionPolicy 后端实现**
   - 领域模型（VersionPolicy 聚合根）
   - CRUD API
   - 版本策略（SemVer、日期版本等）

3. **规则应用**
   - 规则与项目/仓库的关联
   - 规则优先级

**预估工作量**：3-5 天

### P1 - 重要增强功能（建议实现）

#### 2.4 发布窗口日历视图
**目标**：可视化展示发布窗口时间线，提升用户体验。

**任务清单**：
1. 日历组件集成（如 FullCalendar）
2. 发布窗口时间线展示
3. 冲突可视化提示

**预估工作量**：2-3 天

#### 2.5 子项目管理
**目标**：支持 Maven module / Gradle subproject 管理。

**任务清单**：
1. SubProject 领域模型
2. SubProject CRUD API
3. 子项目与仓库关联
4. 子项目版本更新支持

**预估工作量**：5-7 天

#### 2.6 冲突检测增强
**目标**：自动检测发布窗口时间冲突、版本号冲突。

**任务清单**：
1. 时间冲突检测算法
2. 版本号冲突检测
3. 冲突提示和解决建议

**预估工作量**：3-5 天

### P2 - 体验优化功能（可选）

#### 2.7 规则预览
- 分支规则预览
- 版本策略预览

#### 2.8 执行记录增强
- 执行历史对比
- 批量重试
- 导出功能完善

#### 2.9 仪表板增强
- 发布窗口统计
- 执行成功率统计
- 仓库健康度统计

## 三、开发计划建议

### Phase 1：核心 MVP 闭环（2-3 周）

**Week 1：版本更新核心功能**
- Day 1-2: VersionUpdater 接口设计和 Maven 实现
- Day 3-4: Gradle 实现和集成测试
- Day 5: 版本更新执行服务集成

**Week 2：校验和规则完善**
- Day 1-2: 版本校验功能实现
- Day 3-4: BranchRule/VersionPolicy 后端完善
- Day 5: 前端集成和测试

**Week 3：前端完善和测试**
- Day 1-2: 版本更新 UI 完善
- Day 3-4: 端到端测试
- Day 5: Bug 修复和文档

### Phase 2：增强功能（1-2 周）

- 发布窗口日历视图
- 子项目管理
- 冲突检测增强

### Phase 3：体验优化（持续迭代）

- 规则预览
- 执行记录增强
- 仪表板增强

## 四、技术债务和风险

### 4.1 技术债务
1. **版本更新器实现复杂度**
   - 多模块 Maven 项目版本一致性处理
   - Gradle 不同配置方式的兼容性

2. **Git 操作接口预留**
   - 当前只做版本文件改写，Git 操作（commit、push）需后续实现

3. **测试覆盖**
   - 版本更新器需要充分的单元测试和集成测试

### 4.2 风险与对策
1. **版本改写工程差异巨大**
   - 对策：MVP 先支持标准场景，复杂场景提供明确错误提示

2. **规则中心泛化过度**
   - 对策：先实现模板+正则，策略工厂后续渐进

3. **前端耗时失控**
   - 对策：优先功能可用，美观度后续优化

## 五、验收标准

### 5.1 核心 MVP 功能验收
- ✅ 可以从 UI 创建 Release Window
- ✅ 可以绑定仓库/迭代
- ✅ 可以执行版本更新（Maven/Gradle）
- ✅ 可以查看执行结果和 diff
- ✅ 版本校验功能可用
- ✅ 规则中心功能完整

### 5.2 信息一致性
- ✅ 同一发布窗口的"目标版本、关联仓库、状态"可追踪
- ✅ 有审计记录（createdBy/createdAt/updatedAt）

### 5.3 可操作性
- ✅ 从 UI 到执行的完整闭环可用
- ✅ 执行结果可查看和追溯

### 5.4 可扩展性
- ✅ 版本更新器可插拔（Port/Adapter 模式）
- ✅ 支持后续扩展到 CI、通知、权限

## 六、下一步行动

### 立即开始（P0）
1. **创建版本更新功能提案**
   - 使用 OpenSpec 创建变更提案
   - 定义详细的需求和场景

2. **设计 VersionUpdater 接口**
   - 定义 Port 接口
   - 设计更新结果模型

3. **实现 Maven VersionUpdater**
   - 先实现单模块场景
   - 再扩展到多模块场景

### 并行进行
- BranchRule/VersionPolicy 后端实现
- 版本校验功能设计

### 后续跟进
- 前端 UI 完善
- 端到端测试
- 文档更新

---

## 附录：相关文件参考

- **项目总体规划书**: `release-hub/release_hub_项目总体规划书.md`
- **OpenSpec 工作流**: `openspec/AGENTS.md`
- **后端结构报告**: `release-hub/BACKEND_STRUCTURE_REPORT.md`
- **API 文档**: `release-hub/docs/RELEASE_WINDOW_API.md`
- **控制台 IA 规格**: `release-hub/docs/releasehub-console-ia-v1.2.md`
