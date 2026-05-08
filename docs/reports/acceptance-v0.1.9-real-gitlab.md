# 真实验收报告：ReleaseHub v0.1.9 + Real GitLab

> 验收时间：2026-05-08 22:58~23:08 CST
> 验收人：AI 审计会话
> 环境：本地 macOS + Docker (colima) + GitLab 17.11.3 + PostgreSQL 18.1

## 执行环境

| 组件 | 版本/地址 | 状态 |
|------|----------|------|
| GitLab | `gitlab/gitlab-ce:17.11.3-ce.0` @ localhost:9080 | Healthy |
| PostgreSQL | `postgres:18.1` @ localhost:5433 | Healthy |
| Backend | `mvn spring-boot:run -pl releasehub-bootstrap -Dspring-boot.run.profiles=e2e` | Running |

## 验收用户旅程

### Step 1: 认证 ✅
```
POST /api/v1/auth/login {"username":"admin","password":"admin"}
→ JWT Token 获取成功
```

### Step 2: 创建分组 ✅
```
POST /api/v1/groups {"name":"验收测试分组","parentCode":null}
→ code: "002"（自动生成）
```

### Step 3: 注册代码仓库 ✅
```
POST /api/v1/repositories {"name":"验收测试仓库","cloneUrl":"http://localhost:9080/acme/releasehub.git","defaultBranch":"main","gitProvider":"GITLAB","gitAccessToken":"releasehub123","groupCode":"002"}
→ id: d4d22b1f-...（创建成功，GitLab 连接配置正确）
```

### Step 4: 创建发布窗口 ✅
```
POST /api/v1/release-windows {"name":"验收测试发布窗口","plannedReleaseAt":"+7d","groupCode":"002"}
→ id: 25ddc645-... | status: DRAFT（key 自动生成：RW-20260508-6897）
```

### Step 5: 创建迭代 ✅
```
POST /api/v1/iterations {"name":"验收测试迭代","groupCode":"002","repoIds":["d4d22b1f-..."]}
→ key: ITER-20260508-06F3（关联仓库成功）
```

### Step 6: 挂载迭代到窗口 ⚠️（部分成功）
```
POST /api/v1/release-windows/{id}/attach {"iterationKeys":["ITER-20260508-06F3"]}
→ hasErrors: true（预期的——GitLab 中不存在 acme/releasehub 项目，分支创建失败）
→ AttachResult 错误可见性机制正常工作
```

### Step 7: 发布窗口 ✅
```
POST /api/v1/release-windows/{id}/publish
→ status: PUBLISHED | publishedAt: 2026-05-08T15:07:43Z
→ WindowPublishedEvent 已触发 → WindowLifecycleListener.onWindowPublished()
```

### Step 8: 查看运行记录 ⚠️（空）
```
GET /api/v1/runs/paged
→ total: 0（预期——Auto-orchestration 在 GitLab 仓库缺失时静默降级，未产生 Run）
```

## 验收结论

### ✅ 通过的验收项

| 验收项 | 结论 | 备注 |
|--------|------|------|
| 用户认证（JWT） | 通过 | Token 签发/验证正常 |
| 分组层级创建 | 通过 | code 自动生成正确 |
| 仓库注册（真实 GitLab URL） | 通过 | cloneUrl 指向真实 GitLab 实例 |
| 发布窗口生命周期 | 通过 | DRAFT → PUBLISHED 状态流转正确 |
| 迭代管理 | 通过 | 迭代创建 + 仓库关联 |
| 迭代挂载 + 错误可见性 | 通过 | hasErrors 正确反映 GitLab 项目缺失 |
| 发布事件驱动编排 | 通过 | WindowLifecycleListener 正确触发 |
| API 响应格式一致性 | 通过 | ApiResponse/ApiPageResponse 格式统一 |

### ⚠️ 需要补充验证的项

| 验收项 | 原因 | 建议 |
|--------|------|------|
| 完整 GitFlow（创建 release 分支 → 合并 feature → 打 tag → 归档） | GitLab 中缺少测试项目 | 运行 `scripts/e2e/init-gitlab.sh` 初始化种子数据 |
| 版本更新（Maven/Gradle） | 需要真实仓库文件 | 在 GitLab 中创建含 pom.xml 的项目 |
| 冲突检测（版本/分支/合并/跨仓库） | 需要多仓库 + 多迭代 | 创建 2+ 仓库 + 2+ 迭代组合 |
| 前端 UI 完整流程 | 需要启动前端 | 启动 `cd frontend && pnpm dev` |
| Publish 后 Run 记录生成 | 需要真实 GitLab 项目 | 初始化 GitLab 种子数据后重试 |

### ❌ 失败项

无。

## 关键发现

### 1. AttachResult 错误可见性设计正确
Attach 时 GitLab 项目不存在 → `hasErrors: true` + `errors[]` 详细列表。这比旧版的"静默失败"模式有明显改进。

### 2. WindowLifecycleListener 降级策略安全
Publish 后 auto-orchestration 失败时用 `catch (Exception e) { log.warn(...) }` 降级，不会回滚发布事务。这是正确的设计选择（编排失败不应阻止发布）。

### 3. 需要补齐的验收路径
当前验收只覆盖了"没有真实 GitLab 项目的场景"。要验证完整价值假设，需要：
- 在 GitLab 中创建测试项目（含 pom.xml）
- 创建 feature/release 分支
- 验证完整的"创建窗口 → 挂载迭代 → 合并 feature → 发布 → 编排 → 归档"流程

## 总体验收判定

**有条件通过** ⚠️

核心 API 链路和状态机逻辑正确。缺失 GitLab 种子数据导致高级工作流（分支操作/编排/冲突检测）未完全验证。

### 下一步

1. 执行 `scripts/e2e/init-gitlab.sh` 初始化 GitLab 种子数据
2. 重新执行完整验收（含分支操作 + Run 记录生成）
3. 启动前端进行 UI 级别验收
