# Plop Generator

ReleaseHub Web 使用 Plop 快速生成标准化的 CRUD 业务模块。

## 快速开始

### 1. 生成 CRUD 模块

```bash
# 交互式生成
pnpm gen:crud

# 示例交互：
# ? Module name: releaseWindow
# ? Entity name: ReleaseWindow
# ? Generate Detail Page? Yes
# ? Generate Dialog Component? Yes
```

### 2. 预览生成结果 (Dry Run)

如果不确定生成的文件路径或内容是否正确，可以先运行 Dry Run 模式：

```bash
pnpm gen:crud:dry
```

### 3. 强制覆盖

默认情况下，如果目标文件已存在，Plop 会报错。如需强制覆盖：

```bash
pnpm gen:crud:force
```

## 生成内容清单

以 `moduleName=releaseWindow` 为例，生成器将创建以下文件：

| 类型 | 路径 | 说明 |
|------|------|------|
| View (List) | `src/views/ReleaseWindow/ReleaseWindowList.vue` | 列表页，包含 SearchForm 和 DataTable |
| View (Detail) | `src/views/ReleaseWindow/ReleaseWindowDetail.vue` | 详情/独立编辑页 (可选) |
| View (Dialog) | `src/views/ReleaseWindow/ReleaseWindowDialog.vue` | 弹窗编辑组件 (可选) |
| Type | `src/types/releaseWindow.ts` | 包含 Query, DTO, Form 类型定义 |
| API | `src/api/releaseWindowApi.ts` | 包含 list/get/create/update/freeze 接口及 Mock 数据 |
| Router | `src/router/modules/releaseWindow.ts` | 路由配置模块，需手动在 `src/router/index.ts` 引入 |

## 开发约束

1. **状态本地化**：生成的页面代码严格遵循“页面级状态不入 Pinia”原则，查询条件和表单数据均使用 `reactive/ref` 管理。
2. **命名规范**：
   - 文件名：PascalCase (组件/页面) 或 camelCase (API/Types/Router)
   - 变量名：camelCase
3. **路由注册**：生成 Router 模块后，**必须手动**在 `src/router/index.ts` 中引入并添加到 `moduleRoutes` 数组中（为了安全起见，生成器不会自动修改入口文件）。
