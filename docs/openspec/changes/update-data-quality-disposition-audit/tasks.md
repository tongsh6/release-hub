# Tasks: 数据质量受控处置执行审计

## 1. 设计门禁

- [x] 新建需求文档并登记到 `docs/requirements/INDEX.md`。
- [x] 新建 OpenSpec proposal，反向引用需求文档。
- [x] 新建设计文档，明确 case 模型、状态机、幂等、防重、快照和不同处置等级边界。
- [x] 新建 data-quality delta spec。
- [x] 同步场景矩阵、项目台账、执行路线图和任务记录。

## 2. 最小实现切片

- [x] 后端新增处置 case 应用模型和端口。
- [x] 基础设施新增持久化表与 JPA adapter。
- [x] API 新增 create/list/detail/start/verify/fail/cancel。
- [x] 前端复核队列增加创建/查看 case 入口。
- [x] 前端在复核队列内展示 case 列表与详情复核抽屉。
- [x] 补应用层、API 和前端组件测试。
- [x] 运行 typecheck、i18n lint、roadmap 检查、diff 检查和静态扫描。

## 3. 页面场景验收

- [x] 新增外部 Playwright 真实页面旅程，覆盖导入 dry-run、提交复核、创建 case、打开详情、开始人工处置和记录复核通过。
- [x] 页面验收不使用业务 API route stub；API 调用只作为页面旅程后的证据复核。
- [x] 页面验收明确断言没有直接清理或自动清理按钮。

## 4. 明确暂缓

- [ ] BranchCreationMode 独立迁移服务另建 proposal。
- [ ] 自动批量清理继续禁止。
- [ ] RBAC、通知和审批流不进入当前阶段。
