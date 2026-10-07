# Run API 文档

## 概述

运行记录（Run）跟踪发布编排和版本更新的执行过程，包含 RunItem（执行项）和 RunStep（执行步骤）的完整历史。

新 Run 和 RunItem 使用独立 UUID，旧字符串 ID 仍可查询。S1 的持久计划预览使用 `executionVersion=2` 和 `PLANNED` 状态，不能通过重试接口执行。它只记录本地快照，不代表已检查远端 Git 状态。

## 持久计划预览（S1）

从窗口编排面板进入“已保存计划预览”，点击“保存当前范围预览”。打开弹窗和切换已保存计划只查询数据，不自动创建或执行。

### 保存预览

- `POST /api/v1/release-windows/{id}/run-plans`
- 必须登录，并提供 `Idempotency-Key`（8–128 位字母、数字或 `._:-`）。操作者取认证上下文，请求体中的 operator 不可信也不使用。
- 请求示例：

```json
{"repoIds":["repo-id"],"iterationKeys":["iteration-key"],"failFast":true}
```

仓库集合必须非空；迭代为空时选取该窗口已挂载迭代。服务端规范化集合并拒绝跨分组、未挂载迭代、无匹配仓库、缺少本地 feature 分支信息、关闭窗口及超过 1000 个执行项的请求。

响应为 `ApiResponse<PlanView>`，包含 runId、windowId、operator、createdAt、planHash、snapshot，以及固定的 `status=PLANNED`、`executionVersion=2`、`evidenceLevel=LOCAL_ONLY`、`executionPermitted=false`。

同主体、同 key、同规范化请求返回原计划，即使业务数据后来变化；同 key 不同请求返回 `409 / RUN_005`。输入范围无效返回 `400 / RUN_006`。前端在响应丢失后保留 key，下一次重试不会重复创建。

### 查询预览

- `GET /api/v1/release-windows/{id}/run-plans`：最近 20 条摘要，不加载全部快照。
- `GET /api/v1/runs/{id}/plan`：按 Run ID 查询完整快照。
- Run 分页支持 `status=PLANNED`，预览不会被误列为 RUNNING。
- Run 详情页按 PLANNED 状态加载快照，不查询执行任务或显示重试入口。对预览调用旧 retry 接口返回 `409 / RUN_007`。

快照持久化本次读取并校验的本地窗口、仓库、迭代、顺序、分支、版本和预览步骤；不保存 Token 或 clone URL。没有 worker、Git 调用、自动迁移或直接激活预览的接口。后续执行必须另建受控计划。

### 数据与回退

V34 只增加 Run 协议字段和 `run_plan`，不回填历史 Run、不重写 ID。JPA 映射同时支持既有 local 的 Hibernate 建表及默认 schema。创建 Run、Item 和快照在同一事务中完成，快照插入失败则整体回滚。

回退时保留新增数据结构与记录，停用新预览创建入口；不要删除历史快照。旧版本不理解 PLANNED 状态，应使用兼容该读模型的版本查看预览，不得将其当作可执行 Run。

## 接口列表

### 1. 获取运行记录详情
- **URL**: `GET /api/v1/runs/{id}`
- **Response**: `RunView`

### 2. 列表查询
- **URL**: `GET /api/v1/runs`
- **Response**: `List<RunView>`

### 3. 分页列表
- **URL**: `GET /api/v1/runs/paged?page=1&size=20&runType=VERSION_UPDATE&status=SUCCESS`
- **Response**: `ApiPageResponse<RunView[]>`

### 4. 重试
- **URL**: `POST /api/v1/runs/{id}/retry`
- **Request**: `{ "items": ["windowKey::repoId::iterationKey", ...] }`
- **Behavior**: 重新执行失败的 RunItem

### 5. 导出
- **URL**: `GET /api/v1/runs/{id}/export`
- **Response**: JSON 格式运行数据

## 版本运维接口（精简版）

### 6. 版本运维运行记录（分页）
- **URL**: `GET /api/v1/version-ops/runs/paged?page=1&size=20&status=SUCCESS`
- **Response**: `RunSummaryView` — 仅返回 VERSION_UPDATE 类型运行记录

### 7. 版本运维详情
- **URL**: `GET /api/v1/version-ops/runs/{runId}`
- **Response**: `RunDetailView` — 包含 Item 和 Step 详情

### 8. 版本运维日志
- **URL**: `GET /api/v1/version-ops/runs/{runId}/logs`
- **Response**: `{ "runId": "...", "lines": ["[INFO] ...", "[ERROR] ..."] }`

## RunView 结构

```json
{
  "id": "uuid",
  "runType": "WINDOW_ORCHESTRATION",
  "status": "SUCCESS",
  "startedAt": "2026-01-15T10:00:00Z",
  "finishedAt": "2026-01-15T10:02:30Z",
  "operator": "admin"
}
```

## 运行类型 (RunType)

| 类型 | 说明 |
|------|------|
| `WINDOW_ORCHESTRATION` | 窗口编排（提测合并 + 收尾） |
| `VERSION_UPDATE` | 版本更新（Maven/Gradle） |

## 动作类型 (ActionType)

| 动作 | 说明 |
|------|------|
| `ENSURE_FEATURE` | 确保 feature/hotfix 分支存在 |
| `ENSURE_RELEASE` | 确保 release 分支创建 |
| `ENSURE_MR` | 确保分支可合并 |
| `TRY_MERGE` | 尝试合并 |
| `MERGE_TO_MASTER` | 合并到 master（收尾） |
| `ARCHIVE_BRANCH` | 归档分支 |
| `CREATE_TAG` | 创建标签 |
| `TRIGGER_CI` | 触发 CI |
| `UPDATE_VERSION` | 更新版本号 |
