# Iteration API 文档

## 概述

迭代（Iteration）代表一个冲刺/迭代周期，聚合一组相关的代码仓库，可关联到发布窗口。

创建、编辑、添加和移除仓库均为配置操作，不创建、归档或删除远端分支。AUTO/NAMED 保存分支名、模式和版本映射；EXISTING 只读核实已有分支。真正建分支与集成由窗口的显式 Orchestrate 承担，使用现有持久 Run/Item/Task 记录。已挂窗迭代的仓库范围锁定、同分组校验继续有效。

当前触发语义见 [迭代规范变更](../../../openspec/changes/add-durable-run-recovery/specs/iteration/spec.md)；历史自动创建/归档行为已移除。

## 接口列表

### 1. 创建迭代
- **URL**: `POST /api/v1/iterations`
- **Request**: `{ "name": "Sprint 1", "description": "...", "expectedReleaseAt": "2026-03-01", "groupCode": "001001", "repoIds": ["repo-id"], "repoConfigs": [{ "repoId": "repo-id", "branchCreationMode": "AUTO" }] }`
- **Response**: `IterationView`

### 2. 获取迭代详情
- **URL**: `GET /api/v1/iterations/{key}`
- **Response**: `IterationView`

### 3. 分页列表
- **URL**: `GET /api/v1/iterations/paged?page=1&size=20&keyword=xxx`
- **Response**: `ApiPageResponse<IterationView[]>`

### 4. 更新迭代
- **URL**: `PUT /api/v1/iterations/{key}`
- **Request**: `{ "name": "...", "description": "...", "expectedReleaseAt": "2026-03-01", "groupCode": "001001", "repoIds": ["repo-id"], "repoConfigs": [{ "repoId": "repo-id", "branchCreationMode": "NAMED", "customBranchName": "feature/change" }] }`
- **Response**: `IterationView`

### 5. 删除迭代
- **URL**: `DELETE /api/v1/iterations/{key}`
- **Constraint**: 未关联发布窗口且未挂载仓库的迭代才可删除

### 6. 挂载仓库
- **URL**: `POST /api/v1/iterations/{key}/repos/add`
- **Request**: `{ "repoIds": ["repo-id"], "branchCreationMode": "AUTO", "customBranchName": null }`
- **Behavior**: AUTO 保存 `feature/{iterationKey}`；NAMED 使用通过校验的 `customBranchName`；EXISTING 只读验证所选已有分支。保存关联与版本信息，不进行 Git 写入。

### 7. 移除仓库
- **URL**: `POST /api/v1/iterations/{key}/repos/remove`
- **Request**: `{ "repoIds": ["repo-id"] }`
- **Behavior**: 仅移除迭代关联及其版本配置，保留远端分支和历史执行证据；不自动归档或删除。

### 8. 版本信息
- **URL**: `GET /api/v1/iterations/{key}/repos/{repoId}/version-info`
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
