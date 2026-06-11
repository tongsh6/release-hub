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

### Requirement: 历史不合规分支只读治理入口
系统 SHALL 提供仓库维度的历史不合规分支只读治理入口，基于当前启用的 BranchRule 和仓库作用域上下文识别活跃不合规分支。

#### Scenario: 查看仓库历史不合规分支
- **GIVEN** 仓库存在历史活跃分支 `legacy_branch`
- **AND** 当前仓库作用域下启用的 BranchRule 不匹配 `legacy_branch`
- **WHEN** 用户请求该仓库的不合规分支治理清单
- **THEN** 返回 `legacy_branch`
- **AND** 响应包含仓库 ID、仓库名称、分支名、作用域项目 ID、作用域子项目 ID 和 `MANUAL_REVIEW_ONLY` 动作边界

#### Scenario: 归档和基础分支不进入治理清单
- **GIVEN** 仓库存在 `archive/unpublished/legacy_branch`、默认分支和基础分支
- **WHEN** 用户请求该仓库的不合规分支治理清单
- **THEN** 系统不会把这些分支作为活跃历史不合规分支返回

#### Scenario: 治理入口不执行自动修复
- **WHEN** 用户查看历史不合规分支治理清单
- **THEN** 系统只展示可见性和安全处理建议
- **AND** 系统不会自动重命名、删除或归档历史分支
