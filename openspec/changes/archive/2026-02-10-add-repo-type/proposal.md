# Change: 为代码仓库添加类型区分（repoType）

## Why

ReleaseHub 管理的代码仓库分为两类：纯功能包（LIBRARY，被其他仓库依赖的库）和服务包（SERVICE，可独立部署的服务）。当前系统无法区分仓库类型，无法为后续的依赖发现和版本同步功能提供基础。

## What Changes

- CodeRepository 实体新增 `repoType` 字段，枚举值：`LIBRARY`（纯功能包）、`SERVICE`（服务包）
- 创建/更新 API 支持设置 `repoType`，默认值为 `SERVICE`
- 查询 API 返回 `repoType` 字段
- 前端仓库表单支持选择仓库类型
- 前端仓库列表显示仓库类型标签
- 数据库迁移脚本添加 `repo_type` 字段

## Impact

- Affected specs: `repo-management`
- Affected code:
  - Domain: `CodeRepository.java`, `RepoType.java`（新增枚举）
  - Application: `CodeRepositoryAppService.java`
  - Infrastructure: `CodeRepositoryJpaEntity.java`, `CodeRepositoryMapper.java`
  - Interfaces: `CodeRepositoryController.java`, `CodeRepositoryDTO.java`
  - Frontend: `CodeRepositoryForm.vue`, `CodeRepositoryList.vue`
  - Database: 新增迁移脚本

## References

- 需求文档：[requirements/in-progress/代码仓库类型区分.md](../../requirements/in-progress/代码仓库类型区分.md)
