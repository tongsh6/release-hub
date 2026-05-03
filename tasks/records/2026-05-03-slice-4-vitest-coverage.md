# Slice 4: 前端 Vitest + Coverage

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 4 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 前端单测补齐 + 覆盖率 |
| 用户价值 | ✅ | `pnpm test` 从 1 个占位变有实际覆盖，覆盖率量化 |
| 端到端路径 | ✅ | composable → store → api |
| 单一目标 | ✅ | Vitest 单测 + coverage 配置 |
| 可独立验证 | ✅ | `pnpm test --coverage` 测试数 > 10 且覆盖率 > 阈值 |
| 可回滚 | ✅ | git revert |
| 依赖明确 | ✅ | 无前置依赖 |
| 风险收敛 | ✅ | 只加测试，不改业务代码 |

## 涉及文件

| 文件 | 操作 |
|------|------|
| `vite.config.ts` | 修改：加 coverage 配置 |
| `package.json` | 修改：加 `@vitest/coverage-v8` |
| `src/api/__tests__/*.spec.ts` | 新建（3-5 个） |
| `src/stores/__tests__/*.spec.ts` | 新建（2-3 个） |
| `src/composables/__tests__/*.spec.ts` | 新建（2-3 个） |

## 执行步骤

### Step 1: 安装依赖
```bash
pnpm add -D @vitest/coverage-v8
```

### Step 2: 配置 coverage（vite.config.ts）
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

### Step 0: 确认待测模块
执行前先 `ls src/composables/`、`ls src/stores/`、`ls src/api/`，以实际文件为准确定测试文件名。`usePagination`、`useFormValidation` 为示例命名，如有差异以实际为准。

### Step 3: API 层测试（最简，纯函数）
- `src/api/__tests__/release-window.spec.ts`
- `src/api/__tests__/repository.spec.ts`
- `src/api/__tests__/iteration.spec.ts`
- 关注：请求构造、响应解析

### Step 4: Stores 层测试（mock API）
- `src/stores/__tests__/release-window.spec.ts`
- `src/stores/__tests__/repository.spec.ts`

### Step 5: Composables 层测试（纯逻辑优先）
- `src/composables/__tests__/usePagination.spec.ts`
- `src/composables/__tests__/useFormValidation.spec.ts`

### Step 6: VERIFY
- `pnpm test` 测试数 > 10，全通过
- `pnpm test --coverage` 覆盖率 > 阈值

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
