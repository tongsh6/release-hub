## ADDED Requirements
### Requirement: 发布窗口列表分页与筛选
系统 SHALL 提供发布窗口列表的服务端分页查询，使用 1-based `page` 与 `size`，并支持名称筛选。

#### Scenario: 发布窗口分页查询
- **WHEN** 用户按 `page=1&size=20&name=alpha` 请求发布窗口列表
- **THEN** 返回对应分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based

### Requirement: 发布窗口关联迭代分页
系统 SHALL 提供窗口关联迭代的服务端分页查询，使用 1-based `page` 与 `size`。

#### Scenario: 关联迭代分页查询
- **WHEN** 用户按 `page=1&size=20` 请求某窗口的迭代列表
- **THEN** 返回对应分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based
