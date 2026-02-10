## ADDED Requirements
### Requirement: 分组维护与约束
系统 SHALL 支持分组的创建、查询、更新、删除，且保证代码(code)唯一；父级不可等于自身，子分组删除需防止级联损坏（有子节点或绑定资源时应阻止或提示）。

#### Scenario: 创建分组校验
- **WHEN** 用户提交包含 name 与 code 的创建请求（可选 parentCode）
- **THEN** 系统校验 code 唯一且 name/code 符合长度限制，并拒绝 parentCode 等于自身

#### Scenario: 删除分组保护
- **WHEN** 用户删除存在子节点或受保护关联的分组
- **THEN** 系统返回业务错误并提示无法直接删除

### Requirement: 分组树查询与详情
系统 SHALL 提供分组树查询（含子节点）与分组详情接口，支持按 code/name 过滤并返回父子关系。

#### Scenario: 查询分组树
- **WHEN** 用户请求分组树
- **THEN** 返回按层级组织的分组节点列表，节点包含 name、code、parentCode、children（可为空）

#### Scenario: 查看分组详情
- **WHEN** 用户请求某个分组详情
- **THEN** 返回该分组的 name、code、parentCode 及必要的元数据（如创建时间、子节点数量或关联计数，如实现）

### Requirement: 分组页面体验
前端分组页面 SHALL 提供树形导航、关键字过滤、高亮、权限与空态提示，创建/编辑/删除操作需有确认与国际化文案。

#### Scenario: 关键字过滤与高亮
- **WHEN** 用户在分组页面输入关键字
- **THEN** 列表仅保留匹配 name/code 的节点并高亮当前选中节点，空结果时显示空态提示

#### Scenario: 操作权限与提示
- **WHEN** 用户无相应写/删权限执行创建/编辑/删除
- **THEN** 前端应提示权限不足并阻止操作，成功/失败均显示国际化反馈
