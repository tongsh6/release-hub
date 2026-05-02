## Purpose
代码仓库（CodeRepository）是 ReleaseHub 管理的核心资源，承载分支、MR、版本等元信息，支撑发布窗口与迭代的版本管理流程。仓库支持 GitLab/GitHub 双 Provider 接入，提供分支/MR 统计、健康检查（合规/不合规分支）、Git 配置管理、手动同步和 gate 概览。仓库 name 和 cloneUrl 在系统内全局唯一。

## Requirements

### Requirement: 代码仓库管理
系统 SHALL 支持代码仓库的创建、更新、删除、分页查询及关键字筛选，并返回包含项目、GitLab 项目 ID、cloneUrl、默认分支、monoRepo 标记、统计字段（分支/MR 数量、非合规分支数、最后同步时间）。

#### Scenario: 创建/更新校验
- **WHEN** 用户提交包含 projectId、gitlabProjectId、name、cloneUrl、defaultBranch、monoRepo 的创建或更新请求
- **THEN** 系统校验必填与长度（name<=128、cloneUrl<=512、defaultBranch<=128，gitlabProjectId 必须为数值且 projectId 非空），否则返回业务错误码

#### Scenario: 分页与筛选
- **WHEN** 用户按 page/size 与 keyword（匹配 name/cloneUrl/projectId/gitlabProjectId）请求仓库列表
- **THEN** 返回对应分页数据与 total 元信息

#### Scenario: 删除保护
- **WHEN** 用户删除不存在的仓库
- **THEN** 返回业务错误码；删除成功返回 success=true

### Requirement: 仓库统计与同步
系统 SHALL 提供仓库的 gate 概览与分支/MR 汇总，并支持触发 GitLab 同步；当未配置 GitLab 设置时，同步返回业务错误（如 `GITLAB_SETTINGS_MISSING`）。

#### Scenario: 获取统计
- **WHEN** 用户请求某仓库的 gate-summary 或 branch-summary
- **THEN** 返回 protectedBranch/approvalRequired/pipelineGate/permissionDenied 以及分支总数、活跃数、非合规数、MR 统计等字段

#### Scenario: 同步校验
- **WHEN** 用户触发仓库同步且系统未配置 GitLab baseUrl/token
- **THEN** 返回业务错误码 `GITLAB_SETTINGS_MISSING`，不修改仓库数据

### Requirement: 仓库管理页面体验
前端仓库页面 SHALL 支持关键字搜索、列表分页、健康状态标签、手动同步、详情/抽屉展示统计，表单需有必填/长度/GitLab ID 数值校验，操作成功/失败均需国际化提示及空态/加载状态。

#### Scenario: 列表交互
- **WHEN** 用户输入关键字或分页操作
- **THEN** 列表按名称/cloneUrl/项目过滤，显示分页与空态；同步按钮可触发后端同步并提示结果，健康状态用 tag 显示非合规/正常

#### Scenario: 表单校验
- **WHEN** 用户创建或编辑仓库
- **THEN** 表单校验 projectId、gitlabProjectId、name、cloneUrl、defaultBranch、monoRepo，失败弹出提示；成功后关闭弹窗并刷新列表
