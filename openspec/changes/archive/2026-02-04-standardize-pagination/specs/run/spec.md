## ADDED Requirements
### Requirement: 运行记录分页与筛选
系统 SHALL 提供运行记录列表的服务端分页查询，使用 1-based `page` 与 `size`，并支持按运行类型、操作人、窗口/仓库/迭代、状态过滤。

#### Scenario: 运行记录过滤分页
- **WHEN** 用户按 `page=1&size=20&runType=VERSION_UPDATE&operator=alice&windowKey=W1&repoId=R1&iterationKey=I1&status=FAILED` 请求运行记录列表
- **THEN** 返回满足过滤条件的分页结果
- **AND** `page.total` 为总条数且 `page` 为 1-based
