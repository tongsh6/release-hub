# 2026-05-24 SA-002 数据质量受控处置执行审计设计

## 背景

SA-002 已完成 dry-run、人工复核入口、复核队列页面、数据源口径、命名空间、保留策略和人工复核后的处置策略。下一步不能直接把复核队列升级成“执行清理”按钮；必须先定义执行审计模型，保证历史数据风险处理可追踪、可复核、可失败恢复、可防重。

## 范围

- 新增进行中需求：`docs/requirements/in-progress/SA-002-数据质量受控处置执行审计.md`。
- 新增 OpenSpec change：`docs/openspec/changes/update-data-quality-disposition-audit/`。
- 设计处置 case 模型、状态机、幂等键、快照策略、处置等级边界、API 形态和前端体验。
- 明确下一最小实现切片：创建/list/detail/start/verify/fail/cancel 处置 case，只更新审计记录，不直接修改业务资源。

## 设计结论

- `APPLICATION_MANUAL` 风险未来可创建处置 case 并跳转到既有业务页面；真实动作仍由原页面和原应用服务完成。
- `OBSERVE_ONLY` 只记录观察 case，不允许进入执行状态。
- `MIGRATION_REQUIRED` 只记录阻断 case，必须另建迁移服务 proposal。
- 处置 case 使用幂等键防止同一 dry-run 动作重复创建。
- 执行前后快照只保存白名单脱敏字段，禁止保存 token 明文。

## 验证

```bash
openspec list
openspec list --specs
bash scripts/dev/check-roadmap.sh
pnpm run typecheck
pnpm i18n:lint
git diff --check
```

结果：

- `openspec` CLI 当前本机不可用：`command not found`，未安装新工具。
- roadmap 检查通过：HEAD 唯一且转向 SA-002 数据质量受控处置执行审计最小实现。
- 前端 typecheck 通过。
- i18n lint 通过。
- `git diff --check` 通过。

## 结论

SA-002 数据质量受控处置执行审计设计已完成。当前仍不执行清理、不删除数据库记录、不关闭发布窗口、不迁移业务数据、不触碰 GitLab 远端资源。下一队首转向最小实现切片：处置 case 审计模型和页面入口。
