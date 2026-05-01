# BranchRule API 文档

## 概述

分支规则约束分支命名规范。支持 TEMPLATE（模板匹配 / glob / {placeholder}）和 REGEX（正则匹配）两种模式。规则可配置作用域（全局/项目/子项目）、启用/禁用状态。

## 接口列表

### 1. 创建规则
- **URL**: `POST /api/v1/branch-rules`
- **Request**:
  ```json
  {
    "name": "feature 分支规则",
    "pattern": "feature/{key}",
    "type": "TEMPLATE",
    "description": "可选描述",
    "scopeLevel": "GLOBAL",
    "scopeProjectId": null,
    "scopeSubProjectId": null
  }
  ```
- **type**: `TEMPLATE` | `REGEX`
- **scopeLevel**: `GLOBAL` | `PROJECT` | `SUB_PROJECT`
- **Response**: `BranchRuleView`

### 2. 分页列表
- **URL**: `GET /api/v1/branch-rules/paged?page=1&size=20&name=xxx`
- **Response**: `ApiPageResponse<BranchRuleView[]>`

### 3. 获取详情
- **URL**: `GET /api/v1/branch-rules/{id}`
- **Response**: `BranchRuleView`

### 4. 更新规则
- **URL**: `PUT /api/v1/branch-rules/{id}`
- **Request**: 同创建
- **Response**: `BranchRuleView`

### 5. 删除规则
- **URL**: `DELETE /api/v1/branch-rules/{id}`

### 6. 启用规则
- **URL**: `POST /api/v1/branch-rules/{id}/enable`

### 7. 禁用规则
- **URL**: `POST /api/v1/branch-rules/{id}/disable`

### 8. 合规检查
- **URL**: `GET /api/v1/branch-rules/check?branchName=feature/ITER-1`
- **Response**: `{ "branchName": "feature/ITER-1", "compliant": true }`

### 9. 测试匹配
- **URL**: `POST /api/v1/branch-rules/test`
- **Request**: `{ "pattern": "feature/{key}", "type": "TEMPLATE", "branchName": "feature/ITER-1" }`
- **Response**: `{ "ok": true, "rendered": null, "errors": [] }`

## BranchRuleView 结构

```json
{
  "id": "uuid",
  "name": "feature 分支规则",
  "pattern": "feature/{key}",
  "type": "TEMPLATE",
  "description": "可选描述",
  "scope": { "level": "GLOBAL", "projectId": null, "subProjectId": null },
  "status": "ENABLED",
  "createdAt": "...",
  "updatedAt": "..."
}
```

## 模式说明

| 类型 | 语法 | 示例 |
|------|------|------|
| TEMPLATE | glob (*, **, ?) + 占位符 ({name}) | `feature/{key}`, `release/*` |
| REGEX | 直接正则表达式 | `^release/[A-Z]+-\d+$` |
