# Iteration API 文档

## 概述

迭代（Iteration）代表一个冲刺/迭代周期，聚合一组相关的代码仓库，可关联到发布窗口。

## 接口列表

### 1. 创建迭代
- **URL**: `POST /api/v1/iterations`
- **Request**: `{ "name": "Sprint 1", "description": "...", "expectedReleaseAt": "2026-03-01", "groupCode": "001001" }`
- **Response**: `IterationView`

### 2. 获取迭代详情
- **URL**: `GET /api/v1/iterations/{key}`
- **Response**: `IterationView`

### 3. 分页列表
- **URL**: `GET /api/v1/iterations/paged?page=1&size=20&name=xxx`
- **Response**: `ApiPageResponse<IterationView[]>`

### 4. 更新迭代
- **URL**: `PUT /api/v1/iterations/{key}`
- **Request**: `{ "name": "...", "description": "...", "expectedReleaseAt": "...", "groupCode": "001001" }`
- **Response**: `IterationView`

### 5. 删除迭代
- **URL**: `DELETE /api/v1/iterations/{key}`
- **Constraint**: 未关联发布窗口且未挂载仓库的迭代才可删除

### 6. 挂载仓库
- **URL**: `POST /api/v1/iterations/{key}/repos/add`
- **Request**: `{ "repoId": "...", "repoType": "ITERATION"|"HOTFIX" }`
- **Behavior**: 挂载时自动创建 feature/hotfix 分支（ITERATION→feature/<key>, HOTFIX→hotfix/<key>）

### 7. 移除仓库
- **URL**: `POST /api/v1/iterations/{key}/repos/remove`
- **Request**: `{ "repoId": "..." }`
- **Behavior**: 自动归档对应分支（archive/unpublished/原分支名）

### 8. 版本信息
- **URL**: `GET /api/v1/iterations/{key}/version-info?repoId=xxx`
- **Response**: `IterationRepoVersionInfo`

## IterationView 结构

```json
{
  "key": "ITER-20260101-XXXX",
  "name": "Sprint 1",
  "description": "...",
  "expectedReleaseAt": "2026-03-01",
  "groupCode": "001001",
  "status": "ACTIVE",
  "repos": ["repo-id-1", "repo-id-2"],
  "createdAt": "...",
  "updatedAt": "..."
}
```
