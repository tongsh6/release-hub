# Design: Pagination contract standardization

## Goals
- 列表查询统一为服务端分页，避免全量拉取与前端切片。
- 明确分页契约（1-based page + total 字段），前后端一致。
- 运行记录筛选与实际模型字段一致。
- 分组树保持非分页（结构化树展示）。

## Non-Goals
- 不引入通用排序/多字段排序能力（仅保持当前默认排序）。
- 不改变现有非分页 list API 的用途（保留兼容）。

## Pagination Contract
### Request
- `page`: 1-based（第一页为 1）
- `size`: 每页条数

### Response (ApiPageResponse.page)
- `page`: 1-based 当前页
- `size`: 每页条数
- `total`: 总条数
- 其余字段（如 `totalPages/hasNext/hasPrevious`）保留但以 `total` 为主

### Ordering
- 保持现有列表默认顺序（与当前 list() 返回顺序一致），确保分页稳定。
- 如需显式排序，后续单独扩展。

## Endpoint Coverage
### Already has /paged (改为真实分页 + 1-based)
- `/api/v1/repositories/paged`
- `/api/v1/iterations/paged`
- `/api/v1/release-windows/paged`
- `/api/v1/runs/paged`
- `/api/v1/groups/paged`（树视图不使用）
- `/api/v1/release-windows/{id}/iterations/paged`

### Add /paged endpoints
- `/api/v1/branch-rules/paged`
- `/api/v1/version-policies/paged`

## Run Filters (align to model)
- `runType` / `operator`: 直接基于 Run 字段过滤
- `windowKey` / `repoId` / `iterationKey`: 基于 RunItem 关联字段过滤
- `status`: 复用现有状态推导逻辑（RUNNING/COMPLETED/SUCCESS/FAILED）并支持按该状态过滤

## Frontend Integration
- `useListPage` 仍以 1-based 管理，API 参数直接传递。
- API 模块统一读取 `page.total` 并去除前端分页切片逻辑。
- 发布窗口与迭代“选择/关联”对话框也改为服务端分页拉取。

## Compatibility
- 保留现有非分页列表 API 以支持下拉或轻量场景。
- `/paged` 契约变化属于破坏性变更，需要同步前后端。
