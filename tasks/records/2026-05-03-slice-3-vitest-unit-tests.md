# Slice 3: 前端 Vitest 补齐

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 3 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 前端单测补齐 |
| 用户价值 | ✅ | `pnpm test` 从 1 个占位变成有实际覆盖 |
| 端到端路径 | ✅ | composable → store → api 三层 |
| 单一目标 | ✅ | 只补齐 Vitest，不做 E2E |
| 可独立验证 | ✅ | `pnpm test` 返回非零测试数 |
| 可回滚 | ✅ | git revert |
| 依赖明确 | ✅ | 无前置依赖 |
| 风险收敛 | ✅ | 只加测试文件，不改业务代码 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `src/composables/__tests__/*.spec.ts` | 新建 | Test |
| `src/stores/__tests__/*.spec.ts` | 新建 | Test |
| `src/api/__tests__/*.spec.ts` | 新建 | Test |
| `vite.config.ts` | 不修改（vitest 配置已有） | Config |
| `package.json` | 不修改（`test` 脚本已有） | Config |

## 执行步骤

### Step 1: 盘点待测模块
- `grep -rl "export function\|export const use" src/composables/` 列出所有 composable
- `ls src/stores/` 列出所有 Pinia store
- `ls src/api/` 列出所有 API 模块
- 优先级：纯逻辑 composable > store actions/getters > API 请求构造

### Step 2: RED → GREEN（逐模块）
- 每个模块先写测试（RED），确认实现通过（GREEN）
- 纯逻辑优先（不依赖 DOM），再测 store（mock API 依赖）
- API 层测试关注请求参数构造和响应解析

### Step 3: VERIFY
- `pnpm test` 全通过
- `pnpm test --reporter=verbose` 测试数 > 10

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
