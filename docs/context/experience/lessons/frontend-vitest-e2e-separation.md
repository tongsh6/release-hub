# 前端测试分层：Vitest 单测与 e2e 脚本解耦

## 问题

`pnpm test` 默认执行 `vitest run`，会把仓库中所有 `*.test.ts` 都当作 Vitest 用例收集。若 e2e 脚本也使用 `*.test.ts` 命名（但并非 Vitest suite），会导致 CI/本地执行出现 `No test suite found` 并失败。

## 结论

将单测与 e2e 明确分层：Vitest 只收集 `src/` 下的单测用例；e2e 固定使用独立命令入口（如 `pnpm test:e2e`），避免互相污染。

## 推荐做法

- Vitest 配置：`include` 限定到 `src/**`，并 `exclude: ['e2e/**']`
- 脚本约定：
  - `pnpm test`：只跑单测（Vitest）
  - `pnpm test:e2e`：只跑 e2e（tsx/puppeteer 等）

## 验证清单

- `pnpm test` 只收集 `src/` 单测，且退出码正确
- `pnpm test:e2e` 可以在需要时独立执行（可依赖后端/数据准备）

## 常见坑

- 仅靠文件命名区分测试类型，未在 runner 配置里隔离
- e2e 与单测混跑导致 flaky，用例失败难定位
