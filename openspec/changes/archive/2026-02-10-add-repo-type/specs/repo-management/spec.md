## MODIFIED Requirements

### Requirement: 代码仓库管理
系统 SHALL 支持代码仓库的创建、更新、删除、分页查询及关键字筛选，并返回包含项目、GitLab 项目 ID、cloneUrl、默认分支、monoRepo 标记、**repoType（仓库类型：LIBRARY/SERVICE）**、统计字段（分支/MR 数量、非合规分支数、最后同步时间）。

#### Scenario: 创建/更新校验
- **WHEN** 用户提交包含 projectId、gitlabProjectId、name、cloneUrl、defaultBranch、monoRepo、**repoType** 的创建或更新请求
- **THEN** 系统校验必填与长度（name<=128、cloneUrl<=512、defaultBranch<=128，gitlabProjectId 必须为数值且 projectId 非空），repoType 默认为 SERVICE，校验失败返回业务错误码

#### Scenario: 分页与筛选
- **WHEN** 用户按 page/size 与 keyword（匹配 name/cloneUrl/projectId/gitlabProjectId）请求仓库列表
- **THEN** 返回对应分页数据与 total 元信息，每个仓库包含 repoType 字段

#### Scenario: 删除保护
- **WHEN** 用户删除不存在的仓库
- **THEN** 返回业务错误码；删除成功返回 success=true

## MODIFIED Requirements

### Requirement: 仓库管理页面体验
前端仓库页面 SHALL 支持关键字搜索、列表分页、健康状态标签、**仓库类型标签（LIBRARY/SERVICE）**、手动同步、详情/抽屉展示统计，表单需有必填/长度/GitLab ID 数值校验、**仓库类型选择**，操作成功/失败均需国际化提示及空态/加载状态。

#### Scenario: 列表交互
- **WHEN** 用户输入关键字或分页操作
- **THEN** 列表按名称/cloneUrl/项目过滤，显示分页与空态；同步按钮可触发后端同步并提示结果，健康状态用 tag 显示非合规/正常，**仓库类型用 tag 显示 LIBRARY（纯功能包）或 SERVICE（服务包）**

#### Scenario: 表单校验
- **WHEN** 用户创建或编辑仓库
- **THEN** 表单校验 projectId、gitlabProjectId、name、cloneUrl、defaultBranch、monoRepo、**repoType**，失败弹出提示；成功后关闭弹窗并刷新列表
