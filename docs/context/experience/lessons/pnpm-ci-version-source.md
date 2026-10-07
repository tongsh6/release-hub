# 项目包管理器版本必须有统一来源

## 本次事实

- 缺少 `packageManager` 时，本机 Corepack 入口落到全局 pnpm 12，普通 run 命令曾触发依赖重建。已有锁文件本身不足以固定执行工具版本。
- `frontend/package.json` 声明 `pnpm@10.33.0` 后，前端目录中的 `corepack pnpm --version` 和普通 `pnpm --version` 都离线解析为 10.33.0。
- CI 的 pnpm action 通过 `package_json_file` 读取声明；Docker 直接使用 `corepack pnpm`，避免另写一份版本号或全局安装 latest。
- 本地和 CI 使用根目录 `.nvmrc` 的 Node 24 主版本，和两个既有前端 Docker 基础镜像一致。Node 补丁和镜像摘要不在本轮固定范围。
- 后端 SpotBugs 使用已验证的完整插件坐标，在同一 Maven reactor 调用中接在 verify 后，避免独立调用依赖本地已安装的内部模块。

## 验证边界

版本与 YAML 结构检查、lint、单测、Pact 和构建均可本地完成；这些结果不能冒充 GitHub Actions 已运行或分支保护已配置。

2026-10-08 更正：原 `vue-tsc --noEmit` 对根配置列出 0 个源码文件，退出码 0 不能证明类型正确。显式检查 `tsconfig.app.json` 后，HEAD 基线有 45 个错误，S1 工作区有 44 个，新增 0 个。真实检查入口和存量类型错误仍需修复，SA-018 已重新进入路线图 HEAD。验证类型门禁时必须确认实际覆盖的文件，不能只观察命令退出码。

证据：`tasks/records/2026-10-05-sa-018-build-ci-consistency.md`。

## 真实类型门禁修复结果

2026-10-08 后续修复将 `typecheck` 改为显式检查 app/node 配置，node 覆盖所有根目录 `*.config.ts`；158 个源码/测试文件与 3 个工具配置均进入检查，44 项存量错误清零。Vue 与配置的临时错误注入都会触发失败，清理后恢复通过；没有降低严格设置、排除类型检查文件或更新依赖。

处理类型问题时优先核对真实契约：版本运维详情实际返回 items；Pact 中的空值不能使用 string matcher；当前 Element Plus 类型声明使用错误交叉类型时，测试桩按已安装实现的真实 action 返回值声明，避免伪造返回对象或强制转 any。数组边界、表单能力和空选择状态需要明确处理，不能仅增加非空断言。

i18n 硬编码检查应聚焦产品文案，测试描述与 fixture 可使用中文；通过临时业务源码中文注入证明检查仍能阻断产品硬编码。测试源码继续参与 TypeScript 与 Vitest。

证据：`tasks/records/2026-10-08-sa-018-real-typecheck.md`。

关键词：`pnpm`、`Corepack`、`packageManager`、`Node`、`CI`、`develop`、`构建`、`vue-tsc`、`references`、`typecheck`。
