# SA-018 固定构建环境与 CI 门禁

## 交付结果

完整蓝图：`tasks/plans/2026-10-05-build-ci-consistency.md`。复现、统一配置、本地验证三个切片完成，保留前两项未提交改动。

- `frontend/package.json` 固定 `packageManager=pnpm@10.33.0`，声明 Node 24；`.nvmrc` 与 CI 对齐到 24 主版本，保留现有 Node 24 镜像。
- 两个前端 Docker 构建入口使用 `corepack pnpm` 和 `--frozen-lockfile`，移除全局安装及重复 pnpm 版本号。
- 前端 CI 从项目声明读取 pnpm；三个工作流覆盖 main/develop 推送和 PR，完整链路工作流保留仅文档 PR 的原有忽略规则。
- 后端 CI 使用现有 SpotBugs 4.9.8.3 完整坐标，与 verify 处于同一 Maven 调用，避免前后两次 reactor 调用依赖本地安装状态。
- 未改变依赖版本、锁文件、全局工具或默认运行时。

## 验证

- 一次性 YAML/JSON/Dockerfile 结构检查：原配置 9 项失败，修复后 10 项通过；另行核对后端 Maven 调用中的固定插件坐标。
- 前端目录使用 `COREPACK_ENABLE_NETWORK=0`：Corepack 和普通 pnpm 均解析为 10.33.0；本机 Node 为既有 v24.19.0。
- `pnpm typecheck`、`pnpm lint` 通过；8 条既有测试 stub 警告保留。
- `pnpm test`：30 个文件、95 项全部通过，沿用原覆盖率范围和阈值。
- `pnpm test:pact`：5 个文件、6 项全部通过；仅本机临时契约服务，不访问业务服务。
- `pnpm build:prd`：通过。主 JS 产物约 1,279 kB，保留既有大包提示，不在工具版本任务中引入代码分包重构。
- 锁文件 SHA-256 不变：`7c65eb57eafcf3dde705fa1b47174918ed9b9cea7a379dcdc923b8a5b41f52ba`。
- `git diff --check`、roadmap 和新增文件检查通过。

报告：`/Users/loong/backup-manifest/reports/releasehub-build-ci-20261005/summary.md`，包含配置 RED/GREEN、工具版本、测试、构建及 TopN 记录。

## 静态扫描与边界

本轮静态检查覆盖配置语法及关键字段、前端 ESLint/typecheck。未改后端源码，未重复后端测试或 SpotBugs；其同一代码基线结果见上一项生产加密报告。TopN 中 8 条既有测试警告和 1 条构建体积提示均保留明确理由。

未安装 actionlint/hadolint 或其他工具；未运行远程 GitHub Actions、完整镜像构建、完整 GitLab E2E，也未配置远端分支保护。触发声明完整不等于远程工作流已经通过。

Node 固定到主版本，既有镜像摘要和 Node 补丁仍可更新，不宣称镜像字节级可复现。

前端指南、README、部署说明、经验、台账及矩阵同步；SA-018 出队，下一 HEAD 为 SA-013 发布编排中断恢复设计。

## 2026-10-08 类型检查结论更正

此前 `pnpm typecheck` 的成功退出来自仅含 references、files 为空的根配置，实际检查文件数为 0，不能代表应用源码类型检查通过。显式 `vue-tsc --noEmit -p tsconfig.app.json` 对当前代码报告 44 个存量错误，HEAD 基线 45 个，S1 新增 0 个。类型门禁已重新列为 SA-018 队首；原测试和构建结果不受此更正影响。详见 `tasks/records/2026-10-08-sa-013-s1-run-plan-preview.md`。
