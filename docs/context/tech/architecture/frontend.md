# ReleaseHub 前端架构文档

## 技术栈

| 组件 | 版本/选型 |
|------|-----------|
| 框架 | Vue 3.5+ (Composition API, `<script setup>`) |
| 语言 | TypeScript 5.9+ |
| 构建 | Vite (rolldown-vite 7.2) |
| UI 组件库 | Element Plus 2.12 |
| 状态管理 | Pinia 3.0（仅全局状态） |
| 路由 | Vue Router 4.6 |
| 国际化 | vue-i18n 9 |
| HTTP | Axios |
| 测试 | Vitest + Playwright (E2E) |
| 包管理 | pnpm |

## 目录结构

```
frontend/src/
├── api/                    # API 层
│   ├── http.ts             # Axios 实例 + 拦截器
│   ├── modules/            # 按业务域拆分（releaseWindow/iteration/...）
│   ├── branchRuleApi.ts    # 独立 API 模块（非 modules/ 模式）
│   └── schema.d.ts         # openapi-typescript 自动生成
├── assets/                 # 静态资源
├── components/
│   ├── common/             # 通用组件（GroupTreeSelect 等）
│   ├── crud/               # CRUD 组件（SearchForm, DataTable, EntityDialog）
│   └── run/                # 运行记录专用组件
├── composables/
│   ├── crud/               # CRUD 组合式函数（useListPage, useDetailForm, useDialogForm）
│   └── useGroupTree.ts     # 分组树管理
├── directives/             # 自定义指令（v-perm）
├── i18n/                   # 国际化
│   └── messages/           # zh-CN.ts, en-US.ts
├── layouts/                # 页面布局
├── router/
│   └── modules/            # 按功能拆分路由
├── stores/                 # Pinia stores（仅全局状态）
├── styles/                 # 全局样式
├── types/                  # TypeScript 类型定义（dto.ts, crud.ts）
├── utils/                  # 工具函数（error.ts, perm.ts）
└── views/                  # 15 个功能视图
    ├── audit/
    ├── branch-rule/
    ├── calendar/
    ├── dashboard/
    ├── group/
    ├── home/
    ├── iteration/
    ├── login/
    ├── release-window/
    ├── repository/
    ├── run/
    ├── settings/
    ├── system/
    ├── version-ops/
    └── version-policy/
```

## 数据流

```
API 模块 (src/api/modules/)
    ↓ 返回类型化的 Promise
Vue 组件 / Composable
    ↓ reactive/ref 管理本地状态
Template (template)
```

- **页面状态**：使用 `reactive`/`ref`，**避免 Pinia**（除非跨页面共享状态）
- **API 调用**：统一通过 `src/api/http.ts` 的 Axios 实例，路径使用 `/v1/...` 前缀
- **类型生成**：后端 OpenAPI 变更后运行 `pnpm gen:api` 更新 `schema.d.ts`

## CRUD 模式

项目提供可复用的 CRUD 组合式函数：

```typescript
// 列表页
const { query, loading, list, total, search, reload } = useListPage({
  fetcher: someApi.list,
  defaultQuery: { name: '' }
})

// 详情页/表单
const { form, loading, save, loadDetail } = useDetailForm({
  fetcher: someApi.get,
  saver: someApi.create  // or someApi.update
})
```

配套组件：
- `SearchForm.vue` — 搜索表单容器
- `DataTable.vue` — 分页表格（内置 pagination + loading）
- `EntityDialog.vue` — 创建/编辑对话框

## 路由设计

```typescript
// src/router/modules/calendar.ts
{
  path: 'calendar',
  name: 'Calendar',
  component: () => import('@/views/calendar/CalendarView.vue'),
  meta: {
    titleKey: 'menu.calendar',
    permission: 'release-window:read',
    order: 15
  }
}
```

- 路由按模块拆分到 `modules/`
- 懒加载：`() => import(...)`
- 权限控制：`meta.permission` + `v-perm` 指令

## 权限控制

```vue
<el-button v-perm="'version-ops:write'">执行</el-button>
```

```typescript
import { hasPerm } from '@/utils/perm'
if (hasPerm('branch-rule:write')) { ... }
```

## 国际化

- 翻译文件：`src/i18n/messages/zh-CN.ts`, `en-US.ts`
- 语言持久化到 `RH_LOCALE` (localStorage)
- 添加新翻译键：在对应 `messages/*.ts` 中添加，使用 `t('namespace.key')` 引用
- 检查完整性：`pnpm i18n:lint`

## i18n 命名空间约定

| 命名空间 | 用途 |
|---------|------|
| `common.` | 通用操作（save/cancel/delete/actions） |
| `menu.` | 侧边栏菜单 |
| `releaseWindow.` | 发布窗口 |
| `iteration.` | 迭代 |
| `branchRule.` | 分支规则 |
| `versionPolicy.` | 版本策略 |
| `versionOps.` | 版本运维 |
| `calendar.` | 发布日历 |
| `repository.` | 代码仓库 |
| `group.` | 分组 |
| `login.` | 登录 |
| `conflict.` | 冲突检测 |

## 开发命令

```bash
pnpm dev              # 启动开发服务器
pnpm build            # 构建
pnpm gen:api          # 从后端 OpenAPI 生成类型
pnpm gen:crud         # 生成 CRUD 代码（Plop 模板）
pnpm typecheck        # TypeScript 类型检查
pnpm lint             # ESLint 检查
pnpm test             # 运行 Vitest
pnpm i18n:lint        # 检查 i18n 翻译完整性
```
