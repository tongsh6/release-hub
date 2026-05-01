# ReleaseHub Frontend Development Guide

## Project Setup

### Prerequisites
- Node.js >= 20.19 or >= 22.12
- pnpm

### Installation
```bash
pnpm install
```

### Development
```bash
pnpm dev
```
Access the application at `http://localhost:5173`.

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
