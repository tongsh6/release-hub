# Vue 3 + TypeScript + Vite

This template should help get you started developing with Vue 3 and TypeScript in Vite. The template uses Vue 3 `<script setup>` SFCs, check out the [script setup docs](https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup) to learn more.

Learn more about the recommended Project Setup and IDE Support in the [Vue Docs TypeScript Guide](https://vuejs.org/guide/typescript/overview.html#project-setup).

## API Generation

本项目集成 `openapi-typescript` 用于根据后端 OpenAPI/Swagger 文档生成 TypeScript 类型定义，实现前后端类型安全。

### 前置条件

确保后端服务已启动，并且 OpenAPI 文档地址可访问（默认为 `http://localhost:8080/v3/api-docs`）。

### 生成类型

运行以下命令生成 API 类型定义文件 `src/api/schema.d.ts`：

```bash
pnpm gen:api
```

### 使用方式

使用封装好的 `apiClient` 进行请求，即可获得完整的类型提示和校验：

```typescript
import { apiClient } from '@/api/client'

// 编译器会自动提示 URL、参数和返回值类型
const data = await apiClient.get('/projects', {
  page: 1,
  size: 10
})
```

建议在 CI/CD 流程或提交代码前执行 `pnpm gen:api` 以确保类型定义与后端保持同步。
