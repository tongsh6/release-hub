# Slice 4: 前端测试基础设施

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 4 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | Vitest 单测 + coverage + Playwright 迁移 |
| 用户价值 | ✅ | `pnpm test` 从 1 个占位变有实际覆盖；E2E 框架标准化 |
| 端到端路径 | ✅ | composable → store → api → E2E |
| 单一目标 | ✅ | 前端测试全栈（单测+覆盖率+E2E） |
| 可独立验证 | ✅ | `pnpm test` / `pnpm test:e2e` 各自独立 |
| 可回滚 | ✅ | git revert |
| 依赖明确 | ✅ | 无前置依赖 |
| 风险收敛 | ✅ | 只加测试，不改业务代码 |

## 涉及文件

### Part A — Vitest + Coverage
| 文件 | 操作 |
|------|------|
| `vite.config.ts` | 修改：加 coverage 配置 |
| `package.json` | 修改：加 `@vitest/coverage-v8` |
| `src/api/__tests__/*.spec.ts` | 新建（3-5 个） |
| `src/stores/__tests__/*.spec.ts` | 新建（2-3 个） |
| `src/composables/__tests__/*.spec.ts` | 新建（2-3 个） |

### Part B — Playwright 迁移
| 文件 | 操作 |
|------|------|
| `playwright.config.ts` | 新建 |
| `package.json` | 修改：加 `@playwright/test`、改 scripts |
| `e2e/tests/*.test.ts`（9 个） | 重写 |
| `e2e/utils/*` | 删除 |
| `e2e/puppeteer.config.ts` | 删除 |
| `e2e/run-all.ts` | 删除 |
| `e2e/README.md` | 修改 |

## 执行步骤

### Part A: Vitest + Coverage

#### A1. 安装依赖
```bash
pnpm add -D @vitest/coverage-v8
```

#### A2. 配置 vite.config.ts
```typescript
test: {
  environment: 'jsdom',
  globals: true,
  coverage: {
    provider: 'v8',
    reporter: ['text', 'html'],
    thresholds: {
      lines: 50, branches: 40, functions: 50, statements: 50,
    },
    include: ['src/composables/**', 'src/stores/**', 'src/api/**'],
  },
}
```

#### A3. API 层测试（最简，纯函数）
- `src/api/__tests__/release-window.spec.ts`
- `src/api/__tests__/repository.spec.ts`
- `src/api/__tests__/iteration.spec.ts`
- 关注：请求构造、响应解析

#### A4. Stores 层测试（mock API）
- `src/stores/__tests__/release-window.spec.ts`
- `src/stores/__tests__/repository.spec.ts`

#### A5. Composables 层测试（纯逻辑优先）
- `src/composables/__tests__/usePagination.spec.ts`
- `src/composables/__tests__/useFormValidation.spec.ts`

### Part B: Playwright 迁移

#### B1. 安装
```bash
pnpm add -D @playwright/test
npx playwright install chromium
```

#### B2. playwright.config.ts
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

#### B3-B11. 逐文件迁移（9 个，简→繁）
1. login → 2. navigation → 3. release-window → 4. repository → 5. iteration → 6. release-automation → 7. smoke → 8. business-flow → 9. i18n

每个文件：写 Playwright 测试 → 跑通 → 对照 Puppeteer 覆盖 → 下一个

#### B12. 清理
删除 `e2e/utils/`、`e2e/puppeteer.config.ts`、`e2e/run-all.ts`，更新 scripts

### Part C: VERIFY
- `pnpm test` 测试数 > 10，全通过
- `pnpm test --coverage` 覆盖率 > 阈值
- `pnpm test:e2e` 全部通过

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
