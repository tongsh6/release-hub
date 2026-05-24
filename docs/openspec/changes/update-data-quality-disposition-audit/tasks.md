# Tasks: 数据质量受控处置执行审计

## 1. 设计门禁

- [x] 新建需求文档并登记到 `docs/requirements/INDEX.md`。
- [x] 新建 OpenSpec proposal，反向引用需求文档。
- [x] 新建设计文档，明确 case 模型、状态机、幂等、防重、快照和不同处置等级边界。
- [x] 新建 data-quality delta spec。
- [x] 同步场景矩阵、项目台账、执行路线图和任务记录。

## 2. 最小实现切片（下一步）

- [ ] 后端新增处置 case 应用模型和端口。
- [ ] 基础设施新增持久化表与 JPA adapter。
- [ ] API 新增 create/list/detail/start/verify/fail/cancel。
- [ ] 前端复核队列增加创建/查看 case 入口。
- [ ] 前端新增 case 列表与详情复核视图。
- [ ] 补应用层、API 和前端组件测试。
- [ ] 运行 typecheck、i18n lint、roadmap 检查、diff 检查和静态扫描。

## 3. 明确暂缓

- [ ] BranchCreationMode 独立迁移服务另建 proposal。
- [ ] 自动批量清理继续禁止。
- [ ] RBAC、通知和审批流不进入当前阶段。
