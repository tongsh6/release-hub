# Change: 完善代码仓库管理功能和页面

## Why
当前代码仓库管理存在功能与体验不足：后端缺少更新/删除接口与更完整的过滤，GitLab 同步缺少设置校验；前端列表仅支持简单搜索、无法触发同步/查看统计，表单校验与提示不足，健康状态展示不清晰。

## What Changes
- 后端：补全仓库更新/删除、分页/关键字筛选（名称/cloneUrl/projectId），GitLab 设置缺失时同步需返回业务错误并提示；返回分支/MR统计与 gate 概览接口稳定化。
- 前端：仓库列表支持关键字搜索、手动同步、健康状态标签；详情/抽屉展示分支/MR/Gate 统计；创建/编辑表单增加必填校验与错误提示；操作反馈与空态/加载态完善。
- 文档/测试：补充接口契约、错误码与集成测试覆盖上述行为。

## Impact
- Affected specs: repo-management
- Affected code: releasehub-interfaces/repo API，releasehub-application/infrastructure GitLab 同步逻辑，release-hub-web 仓库页面与 i18n。
