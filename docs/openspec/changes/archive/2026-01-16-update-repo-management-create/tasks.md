# 代码仓库新增与同步任务清单

## 1. 数据库迁移
- [x] 1.1 扩容 code_repository.clone_url 到 VARCHAR(512)

## 2. 后端：DTO/校验/错误码
- [x] 2.1 CreateRepoRequest/UpdateRepoRequest 支持 initialVersion/defaultBranch（可选）并对齐长度校验
- [x] 2.2 新增创建/更新的默认分支策略（defaultBranch 为空默认 main）
- [x] 2.3 新增/编辑时可写入初始版本号（写入时标记为 MANUAL）

## 3. 后端：持久化与查询
- [x] 3.1 CodeRepositoryJpaEntity 增加 cloneUrl 长度约束（与 DB 迁移一致）
- [x] 3.2 分页查询 keyword 支持 name/cloneUrl
- [x] 3.3 统一仓库列表分页契约（1-based page/size 与 page.total）

## 4. 后端：同步能力
- [x] 4.1 新增 POST /api/v1/repositories/{id}/sync 接口（同步统计与 lastSyncAt）
- [x] 4.2 未配置 GitLab 设置时返回 GITLAB_SETTINGS_MISSING
- [x] 4.3 明确区分 sync（统计）与 sync-version（版本提取）

## 5. 前端：表单与列表体验
- [x] 5.1 RepositoryEdit 增加 initialVersion/defaultBranch 字段与校验
- [x] 5.2 repositoryApi 增加 sync 与 delete 方法
- [x] 5.3 RepositoryList 增加删除按钮与手动同步入口
- [x] 5.4 i18n 增加新增字段文案与错误码提示（含 GitLab 未配置）
- [x] 5.5 操作成功/失败提示、空态与加载状态完善

## 6. 测试与验收
- [x] 6.1 后端集成测试：创建/更新校验（defaultBranch 默认值、initialVersion 写入）、分页筛选字段覆盖
- [x] 6.2 后端集成测试：sync 未配置 GitLab 返回 GITLAB_SETTINGS_MISSING
- [x] 6.3 前端类型检查与基础联调验证（创建/同步/删除）
- [x] 6.4 OpenSpec 严格校验通过

