# Group API 文档

## 概述

分组（Group）按层级组织项目/发布/仓库。支持父子层级结构，code 可自动生成（001, 001001, 002001...）。发布窗口、迭代、代码仓库创建时必须选择末端分组节点。

## 接口列表

### 1. 创建分组
- **URL**: `POST /api/v1/groups`
- **Request**: `{ "name": "后端服务", "code": "", "parentCode": "001" }`
- **code**: 留空自动生成（如 001001）
- **Response**: `GroupView`

### 2. 获取分组
- **URL**: `GET /api/v1/groups/{id}`
- **URL**: `GET /api/v1/groups/by-code/{code}`
- **Response**: `GroupView`

### 3. 更新分组
- **URL**: `PUT /api/v1/groups/{id}`
- **Request**: `{ "name": "...", "parentCode": "..." }`
- **Response**: `GroupView`

### 4. 删除分组
- **URL**: `DELETE /api/v1/groups/{id}`
- **URL**: `DELETE /api/v1/groups/by-code/{code}`
- **Constraint**: 有子分组或被引用的分组不允许删除

### 5. 列表查询
- **URL**: `GET /api/v1/groups`
- **Response**: `List<GroupView>`

### 6. 分页列表
- **URL**: `GET /api/v1/groups/paged?page=1&size=20`
- **Response**: `ApiPageResponse<GroupView[]>`

### 7. 子分组查询
- **URL**: `GET /api/v1/groups/children/{parentCode}`
- **Response**: `List<GroupView>`

### 8. 顶级分组
- **URL**: `GET /api/v1/groups/top-level`
- **Response**: `List<GroupView>`

### 9. 分组树
- **URL**: `GET /api/v1/groups/tree`
- **Response**: 完整分组树结构 `List<GroupNodeView>`

## GroupView 结构

```json
{
  "id": "uuid",
  "name": "后端服务",
  "code": "001001",
  "parentCode": "001",
  "createdAt": "...",
  "updatedAt": "..."
}
```

## 业务规则

- **叶子节点约束**：发布窗口/迭代/代码仓库只能选择叶子分组（无子节点的分组）
- **编码自动生成**：顶层分组自动生成 001/002/003，子分组继承父编码 + 顺序号
- **删除保护**：被引用的分组不允许删除
