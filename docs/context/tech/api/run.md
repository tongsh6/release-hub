# Run API 文档

## 概述

运行记录（Run）跟踪发布编排和版本更新的执行过程，包含 RunItem（执行项）和 RunStep（执行步骤）的完整历史。

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
