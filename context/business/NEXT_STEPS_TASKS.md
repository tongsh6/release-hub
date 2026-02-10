# ReleaseHub 下一步任务清单

> 最后更新：2026-02-06
> 基于集成测试报告和项目现状分析

## 优先级说明
- **P0**: 必须完成，阻塞功能使用
- **P1**: 重要，影响用户体验
- **P2**: 优化，提升质量

---

## ✅ 已完成任务汇总

### Phase 1: 修复关键问题（P0）✅
- [x] 1.1 改进版本更新 API 错误处理
- [x] 1.2 编译项目并修复单元测试

### Phase 2: 测试数据准备（P0）✅
- [x] 2.1 创建测试数据种子脚本

### Phase 3: 完整功能测试（P1）✅
- [x] 3.1 版本更新功能完整测试
- [x] 3.2 版本校验 API 测试
- [x] 3.3 批量版本更新测试
- [x] 3.4 Diff 展示功能测试

### Phase 4: 前端 UI 端到端测试（P1）✅
- [x] 4.1 前端版本更新流程测试

### Phase 5: API 文档和代码质量（P2）✅
- [x] 5.1 更新 API 文档
- [x] 5.2 代码质量检查

### Phase 6: 集成测试完善（P1）✅
- [x] 6.1 编写集成测试（13 个测试用例通过）

### Phase 7: 规则中心完善（P0）✅
- [x] 7.1 BranchRule 后端 API 完整实现

### Phase 8.1-8.2: 分组与 groupCode（P1）✅
- [x] 8.1 分组 code 自动生成
  - [x] 8.1.1 后端已实现 generateCode() 方法（001, 001001 格式）
  - [x] 8.1.2 前端 code 字段变为可选，添加提示
  - [x] 8.1.3 添加单元测试验证自动生成
- [x] 8.2 groupCode 关联与末端节点校验
  - [x] 8.2.1 后端已有 ensureLeafGroup() 校验
  - [x] 8.2.2 创建 GroupTreeSelect 组件（树形选择，叶子节点可选）
  - [x] 8.2.3 ReleaseWindow、Iteration、Repository 前端添加 groupCode 必填字段
  - [x] 8.2.4 国际化文本更新

---

## 📋 当前待办任务

### Phase 8: 规则对齐与自动化（P1）

#### 任务 8.3: 提测合并与收尾编排自动化
**问题**: 自动创建/合并/归档规则未形成自动触发
**目标**: 按用户故事规则自动触发提测合并与收尾编排

**子任务**:
- [ ] 8.3.1 挂载迭代自动创建 release 分支并合并 feature/hotfix
- [ ] 8.3.2 解除挂载归档分支（unpublished）
- [ ] 8.3.3 收尾编排归档分支与迭代（released）

**验收标准**:
- 自动触发符合规则
- Run/RunTask 可追踪结果

**预估时间**: 2-3 天

---

#### 任务 8.4: BranchRule 前端升级
**问题**: 前端使用旧 API 模型（ALLOW/BLOCK），后端新 API 使用 TEMPLATE/REGEX
**目标**: 对接新 API，支持 scope/启用/禁用/测试

**子任务**:
- [ ] 8.4.1 切换到新 API（modules/branchRule.ts）
- [ ] 8.4.2 补齐表单字段与校验
- [ ] 8.4.3 支持规则测试与启用/禁用

**验收标准**:
- 前端与后端模型一致
- 规则测试与启用/禁用可用

**预估时间**: 1-2 天

---

#### 任务 8.5: Version Ops Dashboard 对接
**问题**: 页面存在但数据硬编码
**目标**: 对接真实 API（scan/update/runs）

**子任务**:
- [ ] 8.5.1 对接 runs 列表与详情
- [ ] 8.5.2 对接 scan/update 入口
- [ ] 8.5.3 完善加载与错误提示

**验收标准**:
- Dashboard 展示真实数据
- 详情与日志可查看

**预估时间**: 2-3 天

---

#### 任务 8.6: 迭代/发布窗口日历视图
**问题**: 缺少可视化统计视图
**目标**: 以迭代、发布窗口为视角进行统计展示

**子任务**:
- [ ] 8.6.1 选择日历组件（推荐 FullCalendar 或 vue-cal）
- [ ] 8.6.2 实现时间线展示
- [ ] 8.6.3 添加冲突可视化提示

**验收标准**:
- 日历视图正常展示迭代/发布窗口
- 冲突可视化提示可用

**预估时间**: 2-3 天

---

### Phase 9: OpenSpec 归档（P2）

#### 任务 9.1: 归档 add-version-updater 变更
**问题**: 版本更新功能已完成，需要归档变更记录
**目标**: 保持 OpenSpec 工作流整洁

**子任务**:
- [ ] 9.1.1 更新 tasks.md 状态（✅ 已完成）
- [ ] 9.1.2 运行 `openspec validate add-version-updater --strict`
- [ ] 9.1.3 执行 `openspec archive add-version-updater --yes`
- [ ] 9.1.4 更新 `openspec/specs/` 目录

**验收标准**:
- 变更记录成功归档
- OpenSpec 验证通过

**预估时间**: 0.5 天

---

## 📊 执行顺序建议

### 本周重点（Week 1）
```
Day 1-2: 任务 8.1 分组 code 自动生成
Day 3:   任务 8.2 groupCode 关联与末端节点校验
Day 4-5: 任务 8.3 提测合并与收尾编排自动化
```

### 下周计划（Week 2）
```
Day 1-2: 任务 8.4 BranchRule 前端升级
Day 3-4: 任务 8.5 Version Ops Dashboard 对接
Day 5:   任务 8.6 迭代/发布窗口日历视图
```

---

## 📁 相关文件参考

| 文件 | 描述 |
|------|------|
| `FUNCTION_DEVELOPMENT_PLAN.md` | 功能开发规划（已更新） |
| `INTEGRATION_TEST_REPORT.md` | 集成测试报告 |
| `release-hub/docs/DOMAIN_MODEL.md` | 领域模型文档（新增） |
| `openspec/changes/add-version-updater/tasks.md` | 版本更新任务清单（已更新） |
| `release-hub/release_hub_项目总体规划书.md` | 项目总体规划书 |

---

## 💡 技术决策记录

### 已做出的决策
1. **版本更新器实现**：使用 Port/Adapter 模式，支持 Maven 和 Gradle
2. **Diff 存储**：存储在 RunStep.message 字段（TEXT 类型）
3. **错误处理**：使用 BizException + GlobalExceptionHandler

### 待决策事项
1. **日历组件选择**：FullCalendar vs vue-cal vs 自定义

---

## 🎯 验收检查清单

### 核心 MVP 功能（✅ 已完成）
- [x] 可以从 UI 创建 Release Window
- [x] 可以绑定仓库/迭代
- [x] 提测合并与收尾编排可用
- [x] 可查看运行记录与任务结果
- [x] 版本校验功能可用

### 规则中心功能（✅ 已完成）
- [x] BranchRule CRUD API 可用
- [x] 分支规则可应用到仓库
- [x] 规则校验功能可用

### 用户体验（待完成）
- [ ] BranchRule 前端升级完成
- [ ] Version Ops Dashboard 对接完成
- [ ] 迭代/发布窗口日历视图可用

### 规则与分组对齐（待完成）
- [ ] 分组 code 自动生成可用
- [ ] groupCode 关联与末端节点校验可用
- [ ] 提测合并与收尾编排自动化可用
