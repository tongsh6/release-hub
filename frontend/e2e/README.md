# E2E 自动化测试

基于 Playwright 的外部页面自动化。

## 验收口径

本项目中“验收测试”特指外部 Playwright 进程驱动浏览器访问真实运行中的 ReleaseHub 页面，并按真实用户路径完成操作或复核。业务数据必须通过页面旅程产生或变更；API、数据库和 GitLab 查询只能作为旅程后的证据复核或环境 fixture。

以下检查不计为场景化验收测试通过：

- 使用 `page.route()` / `route.fulfill()` stub 业务 API 的用例。
- 直接调用 API 或数据库造数后只观察页面。
- `playwright test --list`。
- `tsc -p e2e/tsconfig.json --noEmit`。

这些检查可以作为 UI 回归、异常状态构造、可发现性或类型检查，但报告中必须明确标注，不得写成“验收通过”。

## 目录结构

```
e2e/
├── playwright.config.ts     # Playwright 配置
├── tsconfig.json            # TypeScript 配置
├── README.md                # 本文档
└── tests/
    ├── helpers.ts           # 共享工具函数
    ├── login.spec.ts        # 登录页面测试
    ├── navigation.spec.ts   # 导航和布局测试
    ├── release-window.spec.ts  # 发布窗口测试
    ├── repository.spec.ts   # 仓库管理测试
    ├── iteration.spec.ts    # 迭代管理测试
    ├── release-automation.spec.ts  # 发布自动化测试
    ├── smoke.spec.ts        # 冒烟测试
    ├── business-flow.spec.ts # 业务全流程测试
    └── i18n.spec.ts         # 国际化测试
```

## 运行测试

### 前置条件

启动真实后端和前端服务，或通过 `E2E_BASE_URL` 指向已运行的前端服务。需要完整验收结论时，后端、数据库和 GitLab fixture 也必须处于可用状态。

### 运行所有测试

```bash
pnpm test:e2e
```

### 运行单个测试

```bash
pnpm test:e2e:login
pnpm test:e2e:navigation
pnpm test:e2e:release-window
pnpm test:e2e:smoke
pnpm test:e2e:i18n
```

### 调试模式

```bash
# 有界面模式
pnpm test:e2e:headed

# Playwright UI 模式
pnpm test:e2e:ui
```

## 配置选项

通过环境变量配置测试参数：

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `E2E_BASE_URL` | `http://localhost:5173` | 前端服务地址 |

Playwright 配置见 `playwright.config.ts`：chromium、60s timeout、失败时截图+trace。

## 编写新测试

使用 Playwright test API。新增场景验收用例应优先从页面完成建数和操作，不应通过 route stub 替代业务后端：

```typescript
import { test, expect } from '@playwright/test'

test('测试描述', async ({ page }) => {
  await page.goto('/your-page')
  await expect(page.locator('.your-element')).toBeVisible()
})
```

共享辅助函数放在 `tests/helpers.ts`。

## 故障排除

### 测试超时

检查前端/后端服务是否正常运行，或增加 `playwright.config.ts` 中的 timeout。

### 元素找不到

1. 使用 `pnpm test:e2e:headed` 或 `pnpm test:e2e:ui` 调试
2. 检查选择器是否正确
3. Trace 文件可在 Playwright Trace Viewer 中回放
