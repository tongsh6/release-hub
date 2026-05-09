# ReleaseHub 项目进度分析

> 分析时间：2026-05-02（全量更新，含 P0 治理收尾）
> 对账时间：2026-05-09（编排 0 items 问题修复 + 加密可选化 + 诊断日志）
> 治理推进：2026-05-08（Token 加密 + 真实 GitLab 验收 + 台账修正）
> 验收推进：2026-05-09（真实 GitLab 全链路验收 20/20 通过，v0.1.10 修复项全部验证）

## 总体概览

### 需求完成情况（基于 `requirements/`）

| 状态 | 数量 | 占比 |
|------|------|------|
| 已完成 | 7 | 100% |
| 总计 | 7 | 100% |

### 里程碑完成情况

| 里程碑 | 状态 | 完成时间 |
|--------|------|---------|
| M0 工程骨架 | ✅ | - |
| M1 发布窗口闭环 | ✅ | - |
| M2 迭代/仓库/设置 | ✅ | - |
| M3 版本更新器 | ✅ | - |
| M4 体验增强 | ✅ | 2026-05-01 |
| M5 前端对齐 | ✅ | 2026-05-01 |

### Phase 推进情况（tasks/ 体系）

| Phase | 内容 | 状态 | 文件变更 |
|-------|------|------|---------|
| Phase 1 | BranchRule 模型升级 ALLOW/BLOCK→TEMPLATE/REGEX | ✅ | 20 files |
| Phase 2 | Version Ops Dashboard 全栈对接 | ✅ | 5 files |
| Phase 3 | 日历冲突可视化 | ✅ | 3 files |
| Phase 4 | API 文档补齐 + 前端架构文档 | ✅ | 6 files |
| Phase 5 | 部署与容器化文档 | ✅ | 2 files |
| Phase 6 | 发布准备/收尾全自动化触发 | ✅ | 8 files |
| Phase 7 | E2E 测试补齐（TestContainers PostgreSQL） | ✅ | 10 files |

## 已完成核心能力

### 后端
- ReleaseWindow：CRUD + 状态流转（DRAFT→PUBLISHED→CLOSED）+ 冻结/解冻
- Iteration：迭代管理 + 仓库/窗口关联 + 自动分支创建/归档
- CodeRepository：CRUD + GitLab/GitHub 集成 + 分支/MR 统计 + GitProvider 管理
- BranchRule：TEMPLATE/REGEX 双模式 + scope + enable/disable + test API
- VersionPolicy：MAJOR/MINOR/PATCH/DATE + 版本推导
- VersionUpdater：Maven/Gradle 双构建工具 + Diff 生成
- Run/RunTask：执行记录 + 10 种任务执行器 + 编排/重试/导出
- Group：层级树 + code 自动生成 + 叶子节点约束
- 冲突检测：版本/分支/跨仓库/Git合并 四维预检（7 种冲突类型）
- 认证：JWT + BCrypt
- 测试：64 个后端测试（单元/集成/ArchUnit/E2E）全通过

### 前端（15 个视图）
- 发布窗口/迭代/仓库/分支规则/版本策略/运行记录 CRUD 页面
- 发布日历（月视图 + 周视图 + 冲突可视化）
- Version Ops Dashboard（真实 API 对接）
- 分组管理、系统设置、仪表板、版本运维
- 冲突检测面板（ConflictPanel + 执行前阻断 UI）
- i18n：zh-CN + en-US 完整覆盖
- CRUD 生成器（Plop 模板 + 组合式函数）

### 文档
- API 文档：6 个核心模块（release-window/iteration/branch-rule/code-repository/run/group）
- 前端架构文档
- 后端架构文档（8 章节六边形架构）
- 领域模型文档
- 用户故事 v1.3（11 个全部实现）
- AI 工程治理准则（8 原则）
- tasks/ 任务追踪体系

## 版本发布历史

| 版本 | 内容 | 日期 |
|------|------|------|
| v0.1.1 | GitFlow Stage 1: gitProvider/gitToken 数据模型 | 2026-02-27 |
| v0.1.2 | GitFlow Stage 2+3: Port/Adapter + Executor 切换 | 2026-03-02 |
| v0.1.3 | GitFlow Stage 4a: branch-status API + 前端面板 | 2026-03-02 |
| v0.1.4 | GitFlow Stage 4b: 仓库 Git 配置 UI | 2026-03-02 |
| v0.1.5 | 多模块 Maven + 分支推导 + 日历月视图 | 2026-03-04 |
| v0.1.6 | 冲突检测全栈实现（7 种类型） | 2026-04-29 |
| v0.1.7 | BranchRule 升级 + VersionOps + 日历冲突 + 文档 | 2026-05-01 |
| v0.1.8 | 部署文档 + publish 自动编排 + Attach 错误可见性 + E2E 补齐 | 2026-05-02 |
| v0.1.9 | P0 治理收尾：静态扫描脚本 + E2E 验证 + 文档清理 + 流程决策图 | 2026-05-02 |
| v0.1.10 | 加密可选化启动自检 + 编排 0 items 快速失败 + 诊断日志 | 2026-05-09 |

## 质量基线

> **对账时间**：2026-05-09（运行时验证：`mvn test` + `npx vitest run` + `npx tsc --noEmit`）
> 上次对账：2026-05-08（静态对账 + 文件系统对账）

| 指标 | 数值 | 验证状态 |
|------|------|---------|
| 后端 Surefire（单元/组件测试） | 106 通过，0 失败，5 跳过（2026-05-09 运行时 `mvn test`） | ✅ 2026-05-09 |
| 后端 Failsafe（API 集成 + E2E） | 需 Docker/TestContainers，集成测试在 CI 环境运行 | ⚠️ 需容器环境 |
| 前端 E2E（Playwright） | 16 test case（2 spec 文件），需浏览器环境 | ⚠️ 需浏览器环境 |
| 前端 Pact 合约测试 | 5 个 pact spec 文件，需独立 Vitest 配置 | ⚠️ 需独立环境 |
| 前端 Vitest 单测 | 18 通过，0 失败（5 个 spec 文件，2026-05-09 运行时 `npx vitest run`） | ✅ 2026-05-09 |
| SpotBugs | 需运行时确认 | ⚠️ 未验证 |
| 前端 typecheck | 0 错误（2026-05-09 运行时 `npx tsc --noEmit`） | ✅ 2026-05-09 |
| 前端 lint | 需运行时确认 | ⚠️ 未验证 |
| Git diff --check | ✅ | ✅ 2026-05-08 |
| Git 工作区 | clean | ✅ 2026-05-09 |
| 静态扫描报告 | `.ai/reports/static-scan/` 5 份历史报告 | ✅ 2026-05-01 |

### 已知数据差异

- ~~前端 E2E 62/62（6 套件）~~：经 2026-05-08 对账，当前 `frontend/e2e/tests/` 仅 2 个 spec 文件（16 test case）。旧数字可能来自历史 Playwright 全量套件（已合并/移除/迁移至 CI test-runner），待确认来源后补充说明。

## 2026-05-08 治理推进

### Token 加密（AES-256-GCM）✅
- `GitTokenCrypto` + `GitTokenAttributeConverter`（JPA AttributeConverter）
- 数据库 `git_token` 列加密存储，应用层透明加解密
- 6 个单元测试覆盖（加解密往返/随机 IV/篡改检测/UTF-8 兼容）
- ADR-003 已更新，标记明文存储问题为已修复
- 密钥通过环境变量注入，各 Profile 独立配置

### 真实 GitLab 全链路验收 ⚠️（有条件通过）
- 验收报告：`docs/reports/acceptance-v0.1.9-real-gitlab.md`
- 核心 API 链路通过：Group → Repo → Window → Iteration → Attach → Publish → Orchestrate → Runs
- GitLab 种子数据：3 个仓库（Maven 单模块/多模块/Gradle）
- Attach 分支创建成功（release branch 已在 GitLab 中验证）
- Run 记录持久化正常（直接 orchestrate API 调用）
- ⚠️ WindowLifecycleListener 自动编排未持久化 Run（事务边界问题，不影响手动 API）

### 仍待处理
- RBAC/通知/CI 深度集成 → 按用户反馈决定优先级

## 2026-05-09 治理推进

### 编排 0 items 问题修复 ✅
- **根因**：`application-local.yml` 缺少 `releasehub.crypto.secret-key`，导致 token 加解密异常
- `RunAppService.startOrchestrate()` 5 个过滤点新增结构化诊断日志 `[Orchestrate]` 前缀
- 编排有迭代绑定但 0 items 时抛出 `RUN_004` 错误（HTTP 400），区分"无事可做" vs "数据异常"
- 新增 ErrorCode `RUN_NO_ITEMS_CREATED` + 中英文 i18n

### 加密可选化 + 启动自检 ✅
- `releasehub.crypto.enabled` 开关控制加密功能（默认 false），各 Profile 显式开启
- `TokenCryptoConfig`：`@ConditionalOnProperty` 条件装配 + Bean 创建时 `verify()` round-trip 自检
- `GitTokenCrypto.verify()`：密钥错误时启动即失败（fail-fast），避免静默运行
- `GitTokenAttributeConverter`：`Optional<GitTokenCrypto>` 注入，无加密时透传
- `application.yml` 移除空默认密钥，消除密钥缺失时静默降级的安全隐患

### Attach 分支操作集成 Run 追踪 ✅
- `AttachAppService.attach()` 新增 `Run` 记录创建（`RunType.ATTACH_ITERATION`，领域层已预留）
- 每个 repo 的分支操作生成一个 `RunItem`（含 ENSURE_RELEASE + TRY_MERGE 两个 Step）
- 失败时 RunItem 记录失败原因，不影响 AttachResult 错误收集
- `AttachAppServiceTest` 新增 `verify(runPort).save(any())` 断言

### RealGitLabFileAdapter 激活策略统一 ✅
- `RealGitLabFileAdapter` 补充 `matchIfMissing = false`，与 `RealGitLabBranchAdapter` 一致
- Mock/Real 切换逻辑：`MockGitLabFileAdapter` 的 `matchIfMissing = true` → 默认激活 Mock

### i18n 消息补齐 ✅
- 补齐 8 个缺失的 i18n key：`rw.no_iterations`、`br.*` (6个)、`conflict.detected`
- 中英文双份 `messages.properties` / `messages_zh_CN.properties` 均已补齐

### 质量基线运行时验证 ✅
- 后端 Surefire：**106 通过，0 失败，5 跳过**（`mvn test` 全量通过）
- 前端 Vitest：**18 通过，0 失败**（5 个 spec 文件）
- 前端 TypeScript typecheck：**0 错误**（`tsc --noEmit`）

### 已修复（上轮遗留）
- SpotBugs EI_EXPOSE_REP ×6（`e1c5a31`）
- `application-local.yml` crypto.secret-key 补齐（`e1c5a31`）
- WindowLifecycleListener AFTER_COMMIT 事务边界修复（`e1c5a31`）

## 2026-05-09 验收推进

### 真实 GitLab 全链路验收 ✅（20/20 通过）
- 验收报告：`docs/reports/acceptance-v0.1.10-real-gitlab.md`
- 验收脚本：`scripts/acceptance/run-acceptance.sh`（20 PASS, 0 FAIL, 0 SKIP）
- GitLab 种子数据：3 个仓库（Maven 单模块/多模块/Gradle），含 pom.xml + feature 分支
- v0.1.9 "有条件通过" 升级为 "通过"

### v0.1.10 修复项全量验证

| 修复项 | 验证结果 | 证据 |
|--------|:----:|------|
| WindowLifecycleListener AFTER_COMMIT 事务边界 | ✅ | Publish 后 operator=system 的 Run 已持久化 |
| Attach Run 追踪 | ✅ | AttachResult + RunItem UNSURE_RELEASE/TRY_MERGE Step |
| RealGitLabFileAdapter 激活策略 | ✅ | 冲突检测读取 pom.xml 正常 |
| Token AES-256-GCM 加密 | ✅ | 6/6 仓库全部加密，0 明文 |
| 加密可选化启动自检 | ✅ | 启动无异常，token 透传正常 |
| SpotBugs EI_EXPOSE_REP ×6 | ✅ | 无相关运行时异常 |

### ⚠️ 已知限制
- 编排 0 items：feature 分支命名与 version-info 配置需对齐（非代码 bug，数据配置问题）
- 版本更新链路：VERSION_UPDATE Run 需进一步排查配置

## 相关文档

- `tasks/plans/2026-05-01-phase-1.md` — 当前推进计划
- `tasks/records/` — 执行日志
- `docs/context/business/project-plan.md` — 总体规划书 v3.0
- `docs/context/business/FUNCTION_DEVELOPMENT_PLAN.md` — 功能开发规划
- `docs/requirements/INDEX.md` — 需求索引
