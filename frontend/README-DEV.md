# ReleaseHub 前端开发指南

## Project Setup

### 环境要求
- Node.js 24 主版本，项目根目录 `.nvmrc` 与 CI 使用同一声明。
- pnpm 10.33.0，由本目录 `package.json` 的 `packageManager` 固定；CI 和 Docker 也读取该声明。
- 本机通过既有 nvm 管理 Node；需要安装或升级时先遵守本机环境策略，不使用全局安装 pnpm 的方式。

### 准备与安装

在项目根目录使用 `nvm use` 选择已安装的 Node 24，然后进入前端目录。现有 Corepack 可直接读取项目版本，无需修改全局 shim 或默认包管理器：

```bash
cd frontend
node --version
corepack pnpm --version # 应为 10.33.0
corepack pnpm install --frozen-lockfile
```

依赖已安装时不需要重复安装。已缓存所需 pnpm 时，可用 `COREPACK_ENABLE_NETWORK=0 corepack pnpm --version` 验证离线解析。不要在仓库根目录使用全局 pnpm 版本执行前端命令；前端命令应在 `frontend` 目录运行。

### Development
```bash
corepack pnpm dev
```
Access the application at `http://localhost:5173`.

### 提交前检查

```bash
corepack pnpm typecheck
corepack pnpm lint
corepack pnpm test
corepack pnpm test:pact
corepack pnpm build:prd
```

单测、Pact 契约测试和需要完整服务环境的 E2E 分别运行。三个 CI 工作流均覆盖 `main`、`develop` 推送和 PR；完整链路工作流保留仅文档 PR 的既有忽略规则。

`typecheck` 显式检查 `tsconfig.app.json` 和 `tsconfig.node.json`：前者覆盖全部 `src` 下的 Vue/TypeScript 源码及测试，后者覆盖根目录的 Vite、Pact、Playwright 配置。不要改回仅对根 `tsconfig.json` 执行 `vue-tsc --noEmit`，该配置只有 references，会空跑。可用 `pnpm exec vue-tsc --noEmit -p tsconfig.app.json --listFilesOnly` 核对实际覆盖文件。E2E 用例仍由独立配置与执行入口管理。

`i18n:lint` 检查产品源码中的中文硬编码；测试目录和 `*.test.*` / `*.spec.*` 的描述与 fixture 不属于产品文案。测试仍参与类型检查及测试执行。

Node 固定到 24 主版本，pnpm 固定到精确版本；现有镜像标签和 Node 补丁仍可能更新，本配置不承诺镜像字节级一致。

## Directory Structure

```
src/
├── api/                # API requests
│   ├── modules/        # API modules by feature
│   └── http.ts         # Axios instance
├── assets/             # Static assets
├── components/         # Shared components
├── layouts/            # Layout components (MainLayout)
├── router/             # Router configuration
│   ├── modules/        # Route modules
│   ├── index.ts        # Router entry
│   └── routes.ts       # Route definitions
├── views/              # Page components
│   ├── auth/           # Authentication pages
│   ├── dashboard/      # Dashboard pages
│   ├── release-window/ # Release Window feature pages
│   ├── project/        # Project feature pages
│   ├── branch-rule/    # Branch Rule feature pages
│   ├── version-policy/ # Version Policy feature pages
│   ├── version-ops/    # Version Ops feature pages
│   └── system/         # System pages (404, etc.)
├── App.vue             # Root component
└── main.ts             # Application entry
```

## Routing

Routes are modularized in `src/router/modules/`.
Main routes:
- `/dashboard`: Dashboard
- `/release-windows`: Release Window List
- `/projects`: Project Tree
- `/branch-rules`: Branch Rule List
- `/version-policies`: Version Policy List
- `/version-ops`: Version Ops Dashboard

## API

API calls are defined in `src/api/modules/` and use the axios instance from `src/api/http.ts`.
Base URL is configured in `.env.development` via `VITE_API_BASE_URL`.
