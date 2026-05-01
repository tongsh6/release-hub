# Tasks: 代码仓库类型区分（repoType）

## 1. 后端实现

- [x] 1.1 创建 `RepoType` 枚举（LIBRARY, SERVICE）
- [x] 1.2 修改 `CodeRepository` 领域实体，添加 `repoType` 字段
- [x] 1.3 修改 `CodeRepositoryJpaEntity`，添加 `repo_type` 列映射
- [x] 1.4 修改 `CodeRepositoryPersistenceAdapter`，处理 repoType 转换
- [x] 1.5 修改创建/更新 DTO 和 Controller，支持 repoType 参数
- [x] 1.6 修改查询响应 DTO，返回 repoType 字段

## 2. 数据库迁移

- [x] 2.1 创建 Flyway 迁移脚本 V25，添加 `repo_type` 列（VARCHAR(32)，默认 'SERVICE'）

## 3. 前端实现

- [x] 3.1 手动更新 `repositoryApi.ts` TypeScript 类型定义
- [x] 3.2 修改仓库表单组件（RepositoryEdit.vue），添加仓库类型 Radio 选择
- [x] 3.3 修改仓库列表组件（RepositoryList.vue），显示仓库类型 Tag
- [x] 3.4 修改仓库详情抽屉（RepositoryDrawer.vue），显示仓库类型 Tag
- [x] 3.5 添加国际化文案（zh-CN / en-US：仓库类型、服务、类库）

## 4. 测试

- [x] 4.1 编写/更新 CodeRepository 领域层单元测试（3 个新测试）
- [x] 4.2 更新全部应用层测试（5 个测试文件适配新签名）
- [x] 4.3 后端 41 tests 全部通过，前端 typecheck 通过
