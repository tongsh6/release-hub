## MODIFIED Requirements
### Requirement: 代码仓库管理
系统 SHALL 支持代码仓库的分页查询与关键字筛选，分页参数使用 1-based `page` 与 `size`，并返回包含 `page/size/total` 的分页元信息。

#### Scenario: 分页与筛选
- **WHEN** 用户按 `page=1&size=20` 与 `keyword`（匹配 name/cloneUrl）请求仓库列表
- **THEN** 返回对应分页数据
- **AND** `page.total` 为总条数且 `page` 为 1-based
