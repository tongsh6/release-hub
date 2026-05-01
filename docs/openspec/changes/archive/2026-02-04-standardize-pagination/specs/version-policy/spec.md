## ADDED Requirements
### Requirement: 版本策略列表分页与筛选
系统 SHALL 提供版本策略列表的服务端分页查询，使用 1-based `page` 与 `size`，并支持名称或策略关键字筛选。

#### Scenario: 版本策略分页查询
- **WHEN** 用户按 `page=1&size=20&keyword=semver` 请求版本策略列表
- **THEN** 返回对应分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based
