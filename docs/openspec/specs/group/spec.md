# group Specification

## Purpose
分组（Group）提供层级化的组织树结构，用于对发布窗口、代码仓库、迭代等核心资源进行分组管理。支持父子层级、code 自动生成、叶子节点约束（仅叶子节点可挂载资源），后端保证层级数据一致性和唯一性。

## Requirements

### Requirement: 层级树管理
系统 SHALL 支持分组的创建、更新、删除、分页查询及按关键字搜索。

#### Scenario: 创建分组
- **WHEN** 用户提交包含 name、parentId、code 的创建请求
- **THEN** 系统校验 name 必填且长度不超过 128
- **AND** 若未提供 code，系统自动生成唯一 code
- **AND** 若 parentId 非空，校验父分组存在

#### Scenario: 删除叶子分组
- **WHEN** 用户删除无子节点的分组
- **THEN** 系统删除该分组并返回成功

#### Scenario: 删除非叶子分组
- **WHEN** 用户删除有子节点或已挂载资源的分组
- **THEN** 系统返回业务错误"非叶子节点或已关联资源的分组不可删除"

#### Scenario: 分页与筛选
- **WHEN** 用户按 page/size 与 keyword（匹配 name/code）请求分组列表
- **THEN** 返回对应分页数据与 total 元信息

### Requirement: Code 自动生成
系统 SHALL 在分组创建时若未提供 code 则自动生成全局唯一的 code 值。

#### Scenario: 自动生成 code
- **WHEN** 用户创建分组时未提供 code
- **THEN** 系统生成唯一 code（如 UUID 短码或时间戳派生）
- **AND** code 在系统中全局唯一

### Requirement: 叶子节点约束
系统 SHALL 确保只有叶子分组可以挂载资源（仓库、窗口、迭代等）。

#### Scenario: 非叶子节点拒绝挂载
- **WHEN** 用户尝试将资源挂载到有子节点的分组
- **THEN** 系统返回业务错误"仅叶子节点可挂载资源"
