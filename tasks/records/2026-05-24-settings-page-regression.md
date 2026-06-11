# 2026-05-24 Settings 页面形态回归修复

## 背景

清理本地开发库后复核 `/settings`，发现当前分支展示的是早期 Tab 版配置页，`基线 Ref` 与 `显示偏好` 仍是空 Tab，页面形态与历史卡片化设置页不一致。

排查结论：

- 当前运行的 Vite 服务来自本仓库 `frontend`，不是旧构建或旧目录。
- 历史卡片化页面存在于 `b02df02 feat: settings persistence + card layout + Baseline Refs`，但该提交不在当前分支祖先链上。
- 当前分支后续 GitLab 连接安全和诊断能力是在早期 Tab 版 Settings 上追加的。

## 范围

- 恢复 Settings 分组卡片布局。
- 保留 GitLab 连接测试成功提示和分类诊断展示。
- 规则入口跳转修正为当前真实路由 `/branch-rules` 与 `/version-policies`。
- `基线 Ref` 不展示假保存表单；当前后端 `SettingsRef` 无字段且 `saveRef` 不接请求体，因此仅展示明确占位。
- 更新 Settings 专项单测和 i18n 文案。

## 验证

```bash
pnpm exec vitest run src/views/settings/__tests__/Settings.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
git diff --check
```

真实浏览器复核：

- 打开 `http://localhost:5173/settings`。
- 登录后页面展示 `外部集成`、`规则与策略`、`通用偏好` 三个分组。
- GitLab 卡展示已有 `http://localhost:9080` 与遮罩 token。
- 分支规则和版本策略入口展示在规则卡片区。
- 基线 Ref 展示“基线 Ref 当前没有可配置项”，不提供假保存入口。

## 结论

Settings 页面已恢复到产品化卡片形态，并保留当前分支新增的 GitLab 连接诊断能力。
静态扫描通过：`.ai/reports/static-scan/20260524-205015/summary.md`。
