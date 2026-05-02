# branch-rule Specification

## Purpose
分支规则（BranchRule）定义分支命名的校验规范，支持 TEMPLATE（模板匹配）和 REGEX（正则表达式）双模式，可配置作用域（scope）、启用/禁用开关，并提供 test API 用于预验证分支名称合规性。分支规则应用于代码仓库的分支创建门禁和健康检查。
## Requirements
### Requirement: 分支规则列表分页与筛选
系统 SHALL 提供分支规则列表的服务端分页查询，使用 1-based `page` 与 `size`，并支持按名称筛选。

#### Scenario: 分支规则分页查询
- **WHEN** 用户按 `page=1&size=20&name=feature` 请求分支规则列表
- **THEN** 返回对应分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based

