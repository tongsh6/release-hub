## MODIFIED Requirements

### Requirement: 代码仓库管理
系统 SHALL 支持代码仓库的创建、更新、删除、分页查询及关键字筛选，并返回包含 `cloneUrl`、默认分支、`monoRepo` 标记与统计字段（分支/MR 数量、非合规分支数、最后同步时间）。

#### Scenario: 创建/更新校验
- **WHEN** 用户提交包含 `name`、`cloneUrl`、`defaultBranch`（可选）、`monoRepo`、`initialVersion`（可选）的创建或更新请求
- **THEN** 系统校验必填与长度（`name<=128`、`cloneUrl<=512`、`defaultBranch<=128`）
- **AND** 当 `defaultBranch` 为空时使用默认值 `main`
- **AND** 当 `initialVersion` 不为空时保存为仓库初始版本号

#### Scenario: 分页与筛选
- **WHEN** 用户按 `page=1&size=20` 与 `keyword`（匹配 `name/cloneUrl`）请求仓库列表
- **THEN** 返回对应分页数据
- **AND** 返回包含 `page/size/total` 的分页元信息（1-based `page`）

#### Scenario: 删除保护
- **WHEN** 用户删除不存在的仓库
- **THEN** 返回业务错误码；删除成功返回 `success=true`

### Requirement: 仓库统计与同步
系统 SHALL 提供仓库的 gate 概览与分支/MR 汇总，并支持触发 GitLab 同步；当未配置 GitLab 设置时，同步返回业务错误码 `GITLAB_SETTINGS_MISSING` 且不修改仓库数据。

#### Scenario: 获取统计
- **WHEN** 用户请求某仓库的 `gate-summary` 或 `branch-summary`
- **THEN** 返回 `protectedBranch/approvalRequired/pipelineGate/permissionDenied` 以及分支总数、活跃数、非合规数、MR 统计等字段

#### Scenario: 同步校验
- **WHEN** 用户触发仓库同步且系统未配置 GitLab `baseUrl/token`
- **THEN** 返回业务错误码 `GITLAB_SETTINGS_MISSING`
- **AND** 不修改仓库统计字段与 `lastSyncAt`

### Requirement: 仓库管理页面体验
前端仓库页面 SHALL 支持关键字搜索、列表分页、健康状态标签、手动同步、详情/抽屉展示统计；表单需有必填/长度校验；操作成功/失败均需国际化提示及空态/加载状态。

#### Scenario: 表单校验
- **WHEN** 用户创建或编辑仓库
- **THEN** 表单校验 `name`、`cloneUrl`、`defaultBranch`、`monoRepo`、`initialVersion`
- **AND** 校验失败弹出提示；成功后关闭弹窗并刷新列表

