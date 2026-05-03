# Slice 5: Playwright 迁移

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 5 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | Puppeteer → Playwright 迁移 |
| 用户价值 | ✅ | 标准化 E2E 框架，内置断言/等待/并行/Trace Viewer |
| 端到端路径 | ✅ | Playwright test → 前端页面 → API |
| 单一目标 | ✅ | 9 个 E2E 文件逐迁移 + 旧工具删除 |
| 可独立验证 | ✅ | `pnpm test:e2e` 全部通过 |
| 可回滚 | ✅ | Puppeteer 代码保留到最后 |
| 依赖明确 | ✅ | 依赖 Slice 4（Playwright devDep 安装） |
| 风险收敛 | ✅ | 逐文件迁移，每迁一个验证一个 |

## 涉及文件

| 文件 | 操作 |
|------|------|
| `playwright.config.ts` | 新建 |
| `package.json` | 修改：加 `@playwright/test`、改 `test:e2e*` 脚本 |
| `e2e/tests/login.test.ts` | 重写 |
| `e2e/tests/navigation.test.ts` | 重写 |
| `e2e/tests/release-window.test.ts` | 重写 |
| `e2e/tests/repository.test.ts` | 重写 |
| `e2e/tests/iteration.test.ts` | 重写 |
| `e2e/tests/release-automation.test.ts` | 重写 |
| `e2e/tests/smoke-business-flow.test.ts` | 重写 |
| `e2e/tests/business-flow-e2e.test.ts` | 重写 |
| `e2e/tests/i18n.test.ts` | 重写 |
| `e2e/utils/*` | 删除 |
| `e2e/puppeteer.config.ts` | 删除 |
| `e2e/run-all.ts` | 删除 |
| `e2e/README.md` | 修改 |

## 执行步骤

### Step 1: 安装
```bash
pnpm add -D @playwright/test
npx playwright install chromium
```

### Step 2: playwright.config.ts
```typescript
export default defineConfig({
  testDir: './e2e/tests',
  timeout: 60000,
  retries: 1,
  use: {
    baseURL: 'http://localhost:5173',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
```

### Step 3-11: 逐文件迁移（简→繁）
1. login → 2. navigation → 3. release-window → 4. repository → 5. iteration → 6. release-automation → 7. smoke → 8. business-flow → 9. i18n

每个文件：写 Playwright 测试 → 跑通 → 对照 Puppeteer → 下一个

### Step 12: 清理
删除 `e2e/utils/`、`e2e/puppeteer.config.ts`、`e2e/run-all.ts`

### Step 13: VERIFY
- `pnpm test:e2e` 全部通过

## 静态扫描

```bash
scripts/dev/static-scan-topn.sh 10
```

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ⬜ | |
| 层级闭环 | ⬜ | |
| 测试闭环 | ⬜ | |
| 架构闭环 | ⬜ | |
| 性能闭环 | ⬜ | |
| 文档闭环 | ⬜ | |
| 工作区闭环 | ⬜ | |
