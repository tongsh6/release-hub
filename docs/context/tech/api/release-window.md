# Release Window API 文档

本文档描述 Release Window 聚合的 REST API 接口。

## 状态机流转

`DRAFT` (草稿) -> `PUBLISHED` (已发布) -> `CLOSED` (已关闭)

其他状态：
- `FROZEN` (冻结)：布尔标记（非独立状态），冻结后不可修改时间窗口。

## 接口列表

### 1. 创建发布窗口 (Create)
- **URL**: `POST /api/v1/release-windows`
- **Request**:
  ```json
  {
    "name": "2024-W01"
  }
  ```
- **Response**: `ReleaseWindowView`

### 2. 配置时间窗口 (Configure)
- **URL**: `PUT /api/v1/release-windows/{id}/window`
- **Request**:
  ```json
  {
    "startAt": "2024-01-01T10:00:00Z",
    "endAt": "2024-01-03T18:00:00Z"
  }
  ```
- **Response**: `ReleaseWindowView`

### 3. 冻结窗口 (Freeze)
- **URL**: `POST /api/v1/release-windows/{id}/freeze`
- **Request**: `{}` (Empty JSON)
- **Response**: `ReleaseWindowView` (frozen=true)

### 4. 发布窗口 (Publish)
- **URL**: `POST /api/v1/release-windows/{id}/publish`
- **Pre-condition**: 必须先配置时间窗口 (StartAt/EndAt)。
- **Request**: `{}` (Empty JSON)
- **Response**: `ReleaseWindowView` (status=PUBLISHED, publishedAt set)

### 5. 解冻窗口 (Unfreeze)
- **URL**: `POST /api/v1/release-windows/{id}/unfreeze`
- **Request**: N/A
- **Response**: `ReleaseWindowView` (frozen=false)

### 6. 关闭 (Close)
- **URL**: `POST /api/v1/release-windows/{id}/close`
- **Pre-condition**: Status MUST be PUBLISHED.
- **Request**: N/A
- **Response**: `ReleaseWindowView` (status=CLOSED)

### 7. 查询详情 (Get)
- **URL**: `GET /api/v1/release-windows/{id}`
- **Response**: `ReleaseWindowView`

### 8. 列表查询 (List)
- **URL**: `GET /api/v1/release-windows`
- **Response**: `List<ReleaseWindowView>`

### 9. 导出发布窗口报告 (Export Report)
- **URL**: `GET /api/v1/release-windows/{id}/report.json`
- **Response**: `ReleaseWindowReportView`，包含窗口基本信息、Run、RunItem、RunStep 和结果分布。

- **URL**: `GET /api/v1/release-windows/{id}/report.csv`
- **Response**: CSV，列为 `windowId,windowKey,runId,runType,runStatus,repo,iterationKey,finalResult,stepType,stepResult,stepStart,stepEnd,message`。

## ReleaseWindowView 结构
```json
{
  "id": "uuid",
  "name": "2024-W01",
  "status": "PUBLISHED",
  "createdAt": "...",
  "updatedAt": "...",
  "startAt": "...",
  "endAt": "...",
  "frozen": true,
  "publishedAt": "..."
}
```
