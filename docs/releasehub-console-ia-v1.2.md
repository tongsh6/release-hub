# ReleaseHub 控制台信息架构（IA）落地规格 v1.2

## 路由与菜单映射
- /dashboard
- /windows, /windows/:windowKey (overview | plan | orchestrate | runs | settings)
- /iterations, /iterations/:iterationKey
- /repos, /repos/:repo
- /runs, /runs/:runId
- /settings

## RBAC 与按钮可见性
- Viewer：只读；写操作按钮隐藏或禁用
- Operator：允许 Attach/Detach、Orchestrate、Retry；禁止 Settings 保存
- Admin：全部操作可见；风险动作二次确认

## 核心数据模型与三元组
- ReleaseWindow：windowKey、title、releaseBaseRef、defaultBlockingPolicy
- Iteration：iterationKey、desc、repos[]
- Repository：repo、gitlabProjectId、defaultBranch
- WindowIteration：windowKey、iterationKey、attachAt
- PlanItem：windowKey、repo、iterationKey、plannedOrder、lastExecutedOrder、precheck
- Run：runId、runType、scope、policySnapshot、start/end、operator、status、repoCount、itemCount
- RunItem：
  - 主键：runId + windowKey + repo + iterationKey
  - 四步：ENSURE_FEATURE / ENSURE_RELEASE / ENSURE_MR / TRY_MERGE
  - 每步：result、start/finish、message
  - 证据链：gitlabProjectId、source/target、mrIid/url、blockReason
  - 终态：MERGED | ALREADY_MERGED | MERGE_BLOCKED | FAILED | SKIPPED_DUE_TO_BLOCK | SKIPPED
  - 排序：plannedOrder、executedOrder

## 后端 API 清单（示意）
- Windows：GET/POST /windows，POST /windows/:windowKey/archive
- Window Overview：GET /windows/:windowKey/overview
- Plan（只读）：GET /windows/:windowKey/plan
- Attach/Detach：POST /windows/:windowKey/attach，POST /windows/:windowKey/detach
- Orchestrate：POST /windows/:windowKey/orchestrate
- Runs：GET /runs，GET /runs/:runId，GET /runs/:runId/export，POST /runs/:runId/retry
- Blocks Board：GET /blocks/board
- Repos：GET /repos，POST /repos/sync，GET /repos/:repo
- Iterations：GET /iterations，POST /iterations，GET /iterations/:iterationKey，POST /iterations/:iterationKey/attach
- Settings：GET/POST /settings/gitlab，/settings/naming，/settings/ref，/settings/blocking

## GitLab 读写边界
- 只读：Dashboard、Plan、Runs、Repos 刷新、阻塞看板、导出、Settings 测试连接
- 写 GitLab 唯一入口：开始 Orchestrate（创建/复用 MR、尝试合并）
- Dry-Plan：只读预检，不写 GitLab

## 审计与导出规格
- 全量落库 Run/RunItem：四步占位、证据链、plannedOrder/executedOrder、终态与消息
- 导出：
  - CSV：扁平行（每行一个 RunItem）
  - JSON：层级结构（Run → Items → Steps）

## 校验与防呆、验证策略
- 按钮前置校验：
  - Orchestrate：GitLab token 权限、挂载非空、策略明确
  - Retry：状态 ∈ {FAILED, MERGE_BLOCKED}，三元组存在
  - Settings 保存：ref/template 合法、连接测试可选强制通过
- 并发与幂等：
  - 仓库级并行，仓库内按 plannedOrder 串行
  - MR 复用与 TRY_MERGE 幂等，避免重复副作用
- 差异可视化：planned vs executed 高亮差异与排序提示
