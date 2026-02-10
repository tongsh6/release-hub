# branch-rule Specification

## Purpose
TBD - created by archiving change standardize-pagination. Update Purpose after archive.
## Requirements
### Requirement: 分支规则列表分页与筛选
系统 SHALL 提供分支规则列表的服务端分页查询，使用 1-based `page` 与 `size`，并支持按名称筛选。

#### Scenario: 分支规则分页查询
- **WHEN** 用户按 `page=1&size=20&name=feature` 请求分支规则列表
- **THEN** 返回对应分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based

