# 2026-05-25 全局 light 样式基线修复

## 背景

阻塞看板在系统深色模式或夜间自动切换时出现黑色内容背景。根因不是阻塞看板页面本身，而是 `frontend/src/styles/index.css` 仍保留 Vite 模板默认的 `color-scheme: light dark`、深色根背景和全局 `button/a/h1/.card` 样式。

## 范围

- 移除 Vite 模板残留的深色自动切换逻辑。
- 移除全局 `button`、`a`、`h1`、`.card` 模板样式，避免污染 Element Plus 组件。
- 为 `:root`、`html/body/#app` 建立 ReleaseHub 固定 light 基线。
- 为 `MainLayout` 的 `.rh-layout`、`.rh-main-container`、`.rh-main` 显式设置浅灰背景，避免内容区透出根背景。

## 验证

```bash
pnpm run typecheck
pnpm i18n:lint
git diff --check
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

浏览器复核：

- 启动 Vite 前端。
- Playwright 模拟 `colorScheme: dark` 打开 `/audit/blocks`。
- 复核 computed background：`html`、`body`、`#app`、`.rh-layout`、`.rh-main` 均为 `rgb(245, 247, 250)`。

## 结论

全局背景不再跟随系统深色模式变黑，阻塞看板内容区保持浅色后台系统基线。
静态扫描通过：`.ai/reports/static-scan/20260525-004755/summary.md`。
