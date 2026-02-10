# ReleaseHub 前端开发规范

> 适用范围：`release-hub-web/**/*.vue`, `release-hub-web/**/*.ts`, `release-hub-web/**/*.tsx`

**重要：所有 AI 响应必须使用中文。**

## 技术栈
- Vue 3.5+ 组合式 API (`<script setup>`)
- TypeScript 5.9+
- Vite (rolldown-vite)
- Element Plus UI
- Pinia（状态管理 - 谨慎使用）
- Vue Router
- Vue I18n
- Axios

## 项目结构
```
src/
├── api/           → API 客户端和类型
│   ├── http.ts    → Axios 实例配置
│   ├── modules/   → 按功能分类的 API 模块
│   └── schema.d.ts → 自动生成的 OpenAPI 类型
├── components/    → 可复用 UI 组件
├── composables/   → 组合式函数 (useXxx)
├── directives/    → 自定义 Vue 指令 (v-perm)
├── i18n/          → 国际化消息
├── layouts/       → 页面布局
├── router/        → 路由定义
│   └── modules/   → 按功能分类的路由
├── stores/        → Pinia stores（仅全局状态）
├── styles/        → 全局样式
├── types/         → TypeScript 类型定义
├── utils/         → 工具函数
└── views/         → 功能页面
```

## 状态管理规则

### 页面级状态：使用 `reactive`/`ref`，不要用 Pinia
```typescript
<script setup lang="ts">
import { reactive, ref } from 'vue'

// ✅ 页面本地状态
const queryForm = reactive<BranchRuleQuery>({
  page: 1,
  size: 10,
  name: ''
})

const loading = ref(false)
const dataList = ref<BranchRuleDTO[]>([])

const fetchData = async () => {
  loading.value = true
  const res = await pageBranchRules(queryForm)
  dataList.value = res.records
  loading.value = false
}
</script>
```

### 全局状态：Pinia（谨慎使用）
仅对真正的全局状态使用 Pinia（认证、用户偏好、应用设置）。

## API 模块模式

```typescript
// src/api/modules/branchRule.ts
import { get, post, put, del } from '@/api/http'
import type { PageQuery, PageResult } from '@/api/types'

const API_BASE = '/v1/branch-rules'

export function pageBranchRules(query: PageQuery): Promise<PageResult<BranchRuleDTO>> {
  return get<PageResult<BranchRuleDTO>>(API_BASE, { params: toQuery(query) })
}

export function getBranchRule(id: string): Promise<BranchRuleDTO> {
  return get<BranchRuleDTO>(`${API_BASE}/${id}`)
}

export function createBranchRule(data: BranchRuleCreateReq): Promise<BranchRuleDTO> {
  return post<BranchRuleDTO>(API_BASE, data)
}

export function updateBranchRule(id: string, data: BranchRuleUpdateReq): Promise<BranchRuleDTO> {
  return put<BranchRuleDTO>(`${API_BASE}/${id}`, data)
}

export function deleteBranchRule(id: string): Promise<void> {
  return del(`${API_BASE}/${id}`)
}
```

### API 路径约定
- 基础 URL：`VITE_API_BASE_URL`（必须包含 `/api` 前缀）
- API 路径以 `/v1/...` 开头（代理添加 `/api` 前缀）
- 自动生成类型：`pnpm gen:api`（需要后端运行中）

## 组件约定

### 单文件组件模板
```vue
<template>
  <div class="feature-page">
    <SearchForm :model="queryForm" @search="handleSearch" @reset="handleReset">
      <!-- 表单项 -->
    </SearchForm>
    
    <DataTable :data="dataList" :loading="loading" :total="total" @page-change="handlePageChange">
      <!-- 表格列 -->
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// 状态
const loading = ref(false)
const dataList = ref<ItemDTO[]>([])
const total = ref(0)

const queryForm = reactive({
  page: 1,
  size: 10,
  keyword: ''
})

// 方法
const fetchData = async () => {
  loading.value = true
  try {
    const res = await pageItems(queryForm)
    dataList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryForm.page = 1
  fetchData()
}

const handleReset = () => {
  Object.assign(queryForm, { page: 1, size: 10, keyword: '' })
  fetchData()
}

onMounted(fetchData)
</script>
```

## 国际化 (i18n)

### 消息位置
- 消息文件：`src/i18n/locales/{zh-CN,en}.ts`
- 语言持久化键：`RH_LOCALE`
- 验证命令：`pnpm i18n:lint`

### 使用方法
```typescript
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 模板中：{{ t('releaseWindow.title') }}
// 脚本中：t('releaseWindow.title')
```

## 权限指令

```vue
<!-- v-perm 指令用于权限控制 -->
<el-button v-perm="'release-window:create'">创建</el-button>

<!-- 或在脚本中使用 hasPerm() -->
<script setup>
import { hasPerm } from '@/utils/permission'
const canCreate = hasPerm('release-window:create')
</script>
```

## CRUD 脚手架 (Plop)

```bash
pnpm gen:crud  # 交互式 CRUD 生成器

# 创建内容：
# - 列表页面
# - 详情/对话框组件
# - API 模块
# - 类型定义
# - 路由配置

# 重要：手动在 src/router/routes.ts 中注册路由
```

## 开发工作流

```bash
pnpm install          # 安装依赖
pnpm dev              # 启动开发服务器 (http://localhost:5173)
pnpm gen:api          # 生成 API 类型（需要后端运行）
pnpm lint             # 运行 ESLint
pnpm typecheck        # 运行 TypeScript 检查
pnpm test             # 运行 Vitest 测试
pnpm i18n:lint        # 检查 i18n 完整性
```

## 代码风格
- ESLint flat 配置（Vue/TS/Prettier）
- 允许单词组件名
- 必要时允许使用 `any`（但优先使用正确类型）
- 未使用的参数可用 `_` 前缀
- 优先使用 `<script setup>` 而非 Options API

## 测试策略（TDD 优先）

**必须遵循 TDD 流程**：先写测试 → 最小实现 → 重构

详细 TDD 规则参见 `testing.mdc`

### 开发新功能/组件流程
```
1. 先写组件测试（期望的行为）
2. 实现组件让测试通过
3. 重构优化代码
```

### 组件测试模式
```typescript
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import VersionUpdateDialog from './VersionUpdateDialog.vue'

describe('VersionUpdateDialog', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('应该在提交时验证必填字段', async () => {
    const wrapper = mount(VersionUpdateDialog, {
      props: { visible: true }
    })
    
    await wrapper.find('form').trigger('submit')
    
    expect(wrapper.text()).toContain('请选择仓库')
  })
})
```

### 测试文件位置
- 与源文件同目录：`Component.vue` → `Component.spec.ts`
- 或在 `__tests__/` 目录中

### 运行测试
```bash
pnpm test              # 运行所有测试
pnpm test:watch        # 监听模式
pnpm test:coverage     # 带覆盖率
```

## 常见问题
- **API 类型过时**：后端 schema 变更后运行 `pnpm gen:api`
- **路由未加载**：验证模块已在 `src/router/routes.ts` 中导入
- **Token 存储**：使用 localStorage 中的 `RH_TOKEN`
- **代理配置**：检查 `vite.config.ts` 中 `/api/*` → `http://localhost:8080`
