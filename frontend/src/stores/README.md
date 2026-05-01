# Pinia State Management Boundaries

在本项目中，Pinia **仅允许** 存放以下三类全局状态。其他状态（特别是页面级数据）严禁放入 Store。

## ✅ 允许放入 Store 的状态

### 1. `auth` (用户态)
- **定义**：与当前登录用户强绑定的数据。
- **内容**：
  - `token`: 身份凭证（需持久化）。
  - `profile`: 用户基本信息（ID, name, avatar）。
  - `permissions`: 权限列表（用于路由守卫和指令）。
- **文件**：`src/stores/auth.ts`

### 2. `ui` (全局 UI)
- **定义**：控制应用整体外观和交互布局的状态。
- **内容**：
  - `theme`: 主题模式（light/dark）。
  - `sidebarCollapsed`: 侧边栏折叠状态。
  - `locale`: 国际化语言设置（如有）。
- **文件**：`src/stores/ui.ts`

### 3. `context` (共享上下文)
- **定义**：跨越多个路由/页面共享的业务上下文。
- **内容**：
  - `currentProjectId`: 当前选中的项目 ID（当用户在不同页面间切换但仍处于同一个项目上下文中时）。
  - `currentReleaseWindow`: 当前选中的发布窗口。
- **文件**：`src/stores/context.ts`

---

## ❌ 禁止放入 Store 的状态

### 1. 页面级表单数据
- **错误示例**：在 Store 中定义 `createProjectForm`。
- **正确做法**：在组件内使用 `ref` 或 `reactive`。

### 2. 列表/表格查询数据
- **错误示例**：在 Store 中定义 `projectList`、`searchParams`。
- **正确做法**：在组件内使用 `ref` 并配合 API 调用。如果需要缓存（KeepAlive），请使用 Vue 的 `<KeepAlive>` 组件，而不是存入 Store。

### 3. 组件内部 UI 状态
- **错误示例**：`isDialogVisible`、`isLoading`。
- **正确做法**：组件内部 `ref`。

## 判断标准

1.  **是否跨路由复用？**
    *   否 -> 组件内状态。
    *   是 -> 继续判断。
2.  **是否全局共享且生命周期长于页面？**
    *   否（如从列表页跳详情页传递数据） -> 使用路由参数或 props。
    *   是 -> 放入 `context` 或 `ui` Store。
3.  **是否属于用户身份信息？**
    *   是 -> 放入 `auth` Store。
