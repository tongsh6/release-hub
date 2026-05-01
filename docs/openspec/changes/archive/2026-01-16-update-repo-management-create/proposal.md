# Change: 完善代码仓库新增与同步（对齐 repo-management 规范）

## Why
当前系统已提供仓库列表与新增弹窗，但“新增代码仓库”链路仍不闭环：初始版本号无法在新增/编辑时直接录入、同步口径不清晰（统计同步 vs 版本同步）、分页/筛选契约需要严格对齐、以及数据库列长度与接口校验不一致，导致新增仓库后无法稳定支撑后续版本更新与发布流程。

## What Changes
- **数据库**
  - 扩容 `code_repository.clone_url` 至 `VARCHAR(512)`，避免与接口校验上限不一致导致写入失败
- **后端（API/校验/查询）**
  - 创建/更新请求支持可选 `initialVersion/defaultBranch`，并按规范做长度校验（含 defaultBranch 默认值策略）
  - 分页与关键字筛选覆盖 `name/cloneUrl`，并对齐 1-based `page/size` 契约
- **后端（同步能力）**
  - 新增仓库同步接口（更新统计字段与 `lastSyncAt`），未配置 GitLab 设置返回 `GITLAB_SETTINGS_MISSING`
  - 保留现有“同步初始版本号”能力，避免与统计同步混淆
- **前端（仓库管理页面）**
  - 新增/编辑表单支持 `initialVersion/defaultBranch` 的录入与校验
  - 列表支持删除、手动同步、并对常见失败场景（如 GitLab 未配置）提供国际化提示
- **测试与验收**
  - 增加/更新后端 API 集成测试与前端类型/接口测试，确保规范场景可回归

## Impact
- **Affected specs**: repo-management
- **Affected code**:
  - `releasehub-interfaces`: 仓库 API 请求/响应与控制器
  - `releasehub-application`: 仓库用例、同步用例、校验与错误码
  - `releasehub-infrastructure`: 仓库持久化实体/查询与 Flyway 迁移
  - `release-hub-web`: 仓库新增/编辑弹窗、列表页交互、API 封装与 i18n
- Requirement doc: [完善新增代码仓库功能](../../../../requirements/completed/完善新增代码仓库功能.md)

