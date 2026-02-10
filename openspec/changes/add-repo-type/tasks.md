# Tasks: 代码仓库类型区分（repoType）

## 1. 后端实现

- [ ] 1.1 创建 `RepoType` 枚举（LIBRARY, SERVICE）
- [ ] 1.2 修改 `CodeRepository` 领域实体，添加 `repoType` 字段
- [ ] 1.3 修改 `CodeRepositoryJpaEntity`，添加 `repo_type` 列映射
- [ ] 1.4 修改 `CodeRepositoryMapper`，处理 repoType 转换
- [ ] 1.5 修改创建/更新 DTO 和 Controller，支持 repoType 参数
- [ ] 1.6 修改查询响应 DTO，返回 repoType 字段

## 2. 数据库迁移

- [ ] 2.1 创建 Flyway 迁移脚本，添加 `repo_type` 列（VARCHAR(32)，默认 'SERVICE'）

## 3. 前端实现

- [ ] 3.1 运行 `pnpm gen:api` 更新 TypeScript 类型定义
- [ ] 3.2 修改仓库表单组件，添加仓库类型选择（Radio 或 Select）
- [ ] 3.3 修改仓库列表组件，显示仓库类型标签（Tag）
- [ ] 3.4 添加国际化文案（仓库类型、LIBRARY、SERVICE）

## 4. 测试

- [ ] 4.1 编写/更新 CodeRepository 领域层单元测试
- [ ] 4.2 编写/更新 CodeRepositoryAppService 应用层测试
- [ ] 4.3 验证前端表单和列表功能
