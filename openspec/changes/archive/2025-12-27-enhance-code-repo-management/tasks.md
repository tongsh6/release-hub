## 1. Implementation
- [ ] 1.1 后端接口补全：仓库更新/删除、分页筛选（name/cloneUrl/projectId/keyword）、GitLab 设置缺失时同步返回业务错误
- [ ] 1.2 GitLab 同步/统计：校验设置存在，异常兜底日志+业务码，扩展 gate/branch/MR 汇总测试
- [ ] 1.3 前端仓库列表/详情：搜索过滤、同步按钮、健康状态/统计展示、空态/加载/错误提示、国际化
- [ ] 1.4 前端表单：创建/编辑校验（必填/长度/GitLab ID 数值）、提交失败提示、成功刷新列表
- [ ] 1.5 更新文档与自动化：契约说明、集成/单元测试覆盖，确保 lint/test/typecheck 与 openspec validate 通过
- [ ] 1.6 自动执行校验：在变更完成后运行 `pnpm lint && pnpm typecheck`（release-hub-web），并记录结果
