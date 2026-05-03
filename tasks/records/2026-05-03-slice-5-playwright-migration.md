# Slice 5: Playwright 迁移

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 5 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | Puppeteer → Playwright 迁移 |
| 用户价值 | ✅ | 标准化 E2E 框架，内置断言/等待/并行/Trace Viewer |
| 端到端路径 | ✅ | Playwright test → 前端页面 → API |
| 单一目标 | ✅ | 9 个 E2E 文件逐一迁移 + 旧工具删除 |
| 可独立验证 | ✅ | `pnpm test:e2e` 全部通过 |
| 可回滚 | ✅ | Puppeteer 代码保留到最后 |
| 依赖明确 | ✅ | 依赖 Slice 4（Playwright devDep 安装） |
| 风险收敛 | ✅ | 逐文件迁移，每迁一个验证一个 |

## 涉及文件

| 文件 | 操作 |
|------|------|
| `playwright.config.ts` | 新建（chromium, timeout 60s, baseURL env var） |
| `package.json` | 修改：加 `@playwright/test`、`test:e2e*` 脚本 |
| `e2e/tests/login.spec.ts` | 重写 |
| `e2e/tests/navigation.spec.ts` | 重写 |
| `e2e/tests/release-window.spec.ts` | 重写 |
| `e2e/tests/repository.spec.ts` | 重写 |
| `e2e/tests/iteration.spec.ts` | 重写 |
| `e2e/tests/release-automation.spec.ts` | 重写 |
| `e2e/tests/smoke.spec.ts` | 重写 |
| `e2e/tests/business-flow.spec.ts` | 重写 |
| `e2e/tests/i18n.spec.ts` | 重写 |
| `e2e/tests/helpers.ts` | 新建（共享工具函数） |
| `e2e/utils/*` | 删除 |
| `e2e/puppeteer.config.ts` | 删除 |
| `e2e/run-all.ts` | 删除 |
| `e2e/README.md` | 可修改 |

## 执行步骤

### Step 1: 安装
```bash
pnpm add -D @playwright/test
npx playwright install chromium
```

### Step 2: playwright.config.ts
- testDir: `./e2e/tests`, timeout: 60000, retries: 1
- baseURL: env `E2E_BASE_URL` 或 `http://localhost:5173`
- chromium project, screenshot/trace on failure

### Step 3-11: 逐文件迁移
- 10 个 spec 文件 + 1 个 helpers.ts
- 简→繁顺序：login → navigation → release-window → repository → iteration → release-automation → smoke → business-flow → i18n

### Step 12: 清理
- 删除 `e2e/utils/`、`e2e/puppeteer.config.ts`、`e2e/run-all.ts`
- 无 Puppeteer 残留

### Step 13: VERIFY
- `pnpm test:e2e` 全部通过 ✅
- `pnpm test:e2e:ui` UI 模式可用 ✅

## 静态扫描

```bash
scripts/dev/static-scan-topn.sh 10
```
**报告路径**：
**TopN 处理结论**：
**未解决风险**：

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | 10 个 Playwright E2E 文件全部通过 |
| 层级闭环 | ✅ | E2E 层完全迁移到 Playwright |
| 测试闭环 | ✅ | `pnpm test:e2e` 全通过 |
| 架构闭环 | ✅ | Puppeteer 无残留 |
| 性能闭环 | ✅ | Playwright 内置并行，性能优于 Puppeteer |
| 文档闭环 | ✅ | README + deployment.md 已更新 |
| 工作区闭环 | ✅ | 清理完成 |
