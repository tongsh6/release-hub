# Slice 4: Playwright 迁移

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 4 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 前端 E2E 框架迁移 |
| 用户价值 | ✅ | 标准化 E2E 框架，更好的调试体验，并行执行 |
| 端到端路径 | ✅ | Playwright 测试 → 前端页面 → API |
| 单一目标 | ✅ | Puppeteer 自建框架 → Playwright |
| 可独立验证 | ✅ | `pnpm test:e2e` 用 Playwright 跑通 |
| 可回滚 | ✅ | Puppeteer 代码保留到全部迁移完成 |
| 依赖明确 | ✅ | 依赖 Slice 3（Playwright 作为 devDep 安装） |
| 风险收敛 | ✅ | 逐文件迁移，每迁一个验证一个 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `frontend/playwright.config.ts` | 新建 | Config |
| `frontend/e2e/tests/login.test.ts` | 重写（Puppeteer → Playwright） | E2E |
| `frontend/e2e/tests/navigation.test.ts` | 重写 | E2E |
| `frontend/e2e/tests/release-window.test.ts` | 重写 | E2E |
| `frontend/e2e/tests/repository.test.ts` | 重写 | E2E |
| `frontend/e2e/tests/iteration.test.ts` | 重写 | E2E |
| `frontend/e2e/tests/release-automation.test.ts` | 重写 | E2E |
| `frontend/e2e/tests/smoke-business-flow.test.ts` | 重写 | E2E |
| `frontend/e2e/tests/business-flow-e2e.test.ts` | 重写 | E2E |
| `frontend/e2e/tests/i18n.test.ts` | 重写 | E2E |
| `frontend/e2e/utils/*` | 删除（TestRunner/PageHelper/Assertions/ApiVerifier） | E2E infra |
| `frontend/e2e/puppeteer.config.ts` | 删除 | Config |
| `frontend/e2e/run-all.ts` | 删除（Playwright 内置 test runner） | E2E infra |
| `frontend/e2e/README.md` | 修改：更新为 Playwright 文档 | Doc |
| `frontend/package.json` | 修改：`test:e2e` 脚本改为 `playwright test` | Config |

## 迁移顺序

按从简单到复杂，逐个迁移验证：

1. `login.test.ts` — 只有一个页面，最简
2. `navigation.test.ts` — 导航布局
3. `release-window.test.ts` — CRUD + Element Plus 交互
4. `repository.test.ts` — 同上
5. `iteration.test.ts` — 复杂表单
6. `release-automation.test.ts` — 多步骤流程
7. `smoke-business-flow.test.ts` — 全业务流
8. `business-flow-e2e.test.ts` — UI + API 验证
9. `i18n.test.ts` — 1445 行，最大

## 执行步骤

### Step 1: 安装 Playwright
```bash
pnpm add -D @playwright/test
npx playwright install chromium
```

### Step 2: 配置 playwright.config.ts
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

### Step 3-11: 逐文件迁移（每个文件 → 跑通 → 下一个）
- RED：写 Playwright 版本测试
- GREEN：跑通过
- 对比：确认与 Puppeteer 版本覆盖相同场景

### Step 12: 清理
- 全部迁移完成后删除 `e2e/utils/`、`e2e/puppeteer.config.ts`、`e2e/run-all.ts`
- 更新 `package.json` 脚本

### Step 13: VERIFY
- `pnpm test:e2e` 全部通过
- 对比迁移前后的覆盖场景数一致

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

## 静态扫描

**扫描命令**：
**报告路径**：
**TopN 处理结论**：
**未解决风险**：

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| | | |

## 经验沉淀

- [ ] 不需要
- [ ] 已创建经验文档
- [ ] 已更新经验索引
