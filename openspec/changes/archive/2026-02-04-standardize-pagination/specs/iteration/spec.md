## ADDED Requirements
### Requirement: 迭代列表分页与筛选
系统 SHALL 提供迭代列表的服务端分页查询，使用 1-based `page` 与 `size`，并支持关键字匹配迭代 key/name/description。

#### Scenario: 迭代分页查询
- **WHEN** 用户按 `page=1&size=20&keyword=foo` 请求迭代列表
- **THEN** 返回对应分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based
