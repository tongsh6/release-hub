# Change: Standardize server-side pagination

## Why
当前列表分页存在前后端契约不一致（page/total 字段、0/1 based）、部分列表仍为前端分页与全量拉取，且运行记录筛选与模型字段不匹配，导致分页不可用或结果不稳定。

## What Changes
- 统一分页契约：请求使用 1-based `page` + `size`，响应统一 `page.total`。
- 列表统一服务端分页：仓库、迭代、发布窗口、运行记录、分支规则、版本策略等列表改为服务端分页查询。
- 运行记录筛选基于 Run/RunItem 模型字段实现（windowKey/repo/iterationKey/status/runType/operator）。
- 保留现有非分页 list API 供下拉/全量场景使用（不作为主列表使用）。
- **BREAKING**: `/paged` 端点与分页元数据字段、`page` 基准调整。

## Impact
- Affected specs: `repo-management`, `iteration`, `release-window`, `run`, `branch-rule`, `version-policy`.
- Affected code: 后端分页控制器/应用服务/持久化查询；`ApiPageResponse`/`PageMeta`；前端 `useListPage` 与 API 模块（repository/run/iteration/release-window/branch-rule/version-policy）。
- Requirement doc: [分页标准化](../../../requirements/completed/分页标准化.md)
