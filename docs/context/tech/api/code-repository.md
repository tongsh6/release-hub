# CodeRepository API 文档

## 概述

代码仓库（CodeRepository）代表一个 Git 仓库实体，集成 GitLab/GitHub API，支持分支/MR 统计和 Git Provider 管理。

## 接口列表

### 1. 添加仓库
- **URL**: `POST /api/v1/repositories`
- **Request**:
  ```json
  {
    "name": "my-service",
    "cloneUrl": "https://gitlab.com/group/my-service.git",
    "defaultBranch": "main",
    "repoType": "SERVICE",
    "monoRepo": false,
    "groupCode": "001001",
    "gitProvider": "GITLAB",
    "gitToken": "glpat-xxx"
  }
  ```
- **Response**: `CodeRepositoryView`

### 2. 分页列表
- **URL**: `GET /api/v1/repositories/paged?page=1&size=20&name=xxx`
- **Response**: `ApiPageResponse<CodeRepositoryView[]>`

### 3. 获取详情
- **URL**: `GET /api/v1/repositories/{id}`
- **Response**: `CodeRepositoryView`

### 4. 更新仓库
- **URL**: `PUT /api/v1/repositories/{id}`
- **Request**: 同创建字段
- **Response**: `CodeRepositoryView`

### 5. 删除仓库
- **URL**: `DELETE /api/v1/repositories/{id}`
- **Constraint**: 关联到迭代或发布窗口的仓库不允许删除

### 6. 同步仓库统计
- **URL**: `POST /api/v1/repositories/{id}/sync`
- **Behavior**: 从 Git Provider 拉取最新分支/MR 统计数据

### 7. 分支摘要
- **URL**: `GET /api/v1/repositories/{id}/branch-summary`
- **Response**: 分支数量、活跃分支、不合规分支等统计

## CodeRepositoryView 结构

```json
{
  "id": "uuid",
  "name": "my-service",
  "cloneUrl": "https://gitlab.com/group/my-service.git",
  "defaultBranch": "main",
  "repoType": "SERVICE",
  "monoRepo": false,
  "gitProvider": "GITLAB",
  "groupCode": "001001",
  "branchCount": 12,
  "activeBranchCount": 3,
  "mrCount": 5,
  "lastSyncAt": "...",
  "createdAt": "...",
  "updatedAt": "..."
}
```
