# ReleaseHub 项目开发规范与标准

## 1. 核心技术栈
- **框架**: Vue 3 (Composition API + `<script setup>`)
- **语言**: TypeScript (严格模式)
- **构建工具**: Vite
- **UI 组件库**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router
- **HTTP 客户端**: Axios (已封装在 `@/api/client`)
- **国际化**: vue-i18n

## 2. 目录结构规范
- **API 定义**: `src/api/modules/` (按业务功能拆分)
- **页面视图**: `src/views/` (按业务功能分组)
- **公共组件**: `src/components/` (通用组件)
- **路由配置**: `src/router/modules/` (模块化配置)
- **逻辑复用**: `src/composables/` (Hooks/Composables)

## 3. 强制开发工作流 (工程化能力)
在编写代码前，**必须严格遵循**以下工程化标准：

### 3.1 脚手架与代码生成
- **新建 CRUD 模块**: **必须**使用 `pnpm gen:crud` 生成 View、API、Router 和 Types 代码。
  - 使用方法: 运行 `pnpm gen:crud` -> 跟随提示操作。
- **API 类型同步**: **必须**使用 `pnpm gen:api` 将后端 OpenAPI 文档同步到 `src/api/schema.d.ts`。
  - **禁止**手动定义后端 Schema 中已存在的类型。

### 3.2 视图层实现规范
- **列表页 (List Pages)**: 
  - **必须**使用 `@/composables/crud/useListPage` 组合式函数。
  - **必须**使用 `<DataTable>` 和 `<SearchForm>` 组件。
- **详情/表单页 (Detail/Form Pages)**:
  - **必须**使用 `useDetailForm` 组合式函数。
- **弹窗/模态框 (Dialogs/Modals)**:
  - **必须**使用 `useDialogForm` 组合式函数。
  - **必须**使用 `<EntityDialog>` 组件。

### 3.3 API 层规范
- **客户端调用**: **始终**使用 `@/api/client` 中的 `apiClient`。
- **接口定义**: 必须放置在 `src/api/modules/<module>.ts` 中。

## 4. 编码惯例
- **Vue 写法**: 统一使用 `<script setup lang="ts">`。
- **国际化 (I18n)**: 所有 UI 文本**必须**使用 `useI18n` 进行国际化处理。
- **图标 (Icons)**: 使用 `@element-plus/icons-vue`。
- **命名规范**: 
  - 组件文件: PascalCase (大驼峰，例如 `ProjectList.vue`)
  - Composables: camelCase (小驼峰) 并以 `use` 开头 (例如 `useProjectTree`)
