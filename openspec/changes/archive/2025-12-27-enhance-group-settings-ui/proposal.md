# Change: 完善分组设置功能和页面

## Why
当前分组设置存在交互/校验空缺：缺少分组唯一性/父子约束提示、删除保护、树刷新与搜索一致性、分页/空态反馈及权限提示不够清晰。

## What Changes
- 完善分组校验与交互：创建/编辑时校验唯一性、父子不可同名，删除需保护有子节点或关联（留接口/提示）。
- 优化前端分组页面：树列表搜索/高亮一致，详情/弹窗信息更完整，删除/创建提示国际化补全。
- 增强权限/状态提示：写删权限缺省提示、加载/空态反馈清晰，树刷新与选择状态保持。

## Impact
- Affected specs: group-management
- Affected code: releasehub-interfaces 分组 API，release-hub-web 分组视图/组件与 i18n
