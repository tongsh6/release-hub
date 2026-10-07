# SA-018 固定构建环境与 CI 门禁

## 目标与授权

用户要求执行当前 HEAD。继续保留 SA-017 和 SA-004 的未提交改动，本轮只处理项目构建声明、镜像构建入口、CI 触发和交付说明。

最终行为：前端 `packageManager` 固定为已验证的 pnpm 10.33.0；本地 Corepack、前端 CI、两个前端 Docker 构建入口均读取该声明。Node 统一使用 24 主版本，由 `.nvmrc` 为本地和 CI 提供入口，与现有 Node 24 镜像一致。三个工作流均覆盖 main/develop 推送，并保留 PR 检查。

## 完整范围与边界

- 前端元数据：声明 pnpm 精确版本、Node 24 运行线；不改依赖版本和锁文件。
- Docker：移除未固定版本的全局 pnpm 安装及重复版本声明，改用 Corepack 读取 packageManager。
- CI：pnpm action 从 frontend/package.json 读取版本，Node action 读取 .nvmrc；修复 develop 覆盖；后端 SpotBugs 使用已有扫描基线版本并与 verify 同次执行，避免跨 Maven 调用丢失 reactor 依赖。
- Domain / Application / API / 数据库 / 权限：无变更，不修改前两项交付逻辑。
- 验证：一次性结构校验 RED/GREEN；从项目目录通过现有 Corepack 执行版本检查、类型检查、lint、单测、Pact 和构建。
- 运维：不修改全局工具、Corepack shim、shell 或 nvm 默认值；不安装或升级依赖。
- 非目标：不声称镜像字节级可复现，Node 24 的补丁版本及现有镜像标签仍可更新；不将配置检查冒充 GitHub 远程 CI 或完整 GitLab 验收。

## 切片与 DAG

1. 复现工具声明和触发缺口 → 2. 项目 / Docker / CI 统一 → 3. 本地命令验证、静态扫描、文档同步。

切片分别可验证：规则缺口被识别、所有入口使用一致版本来源、现有依赖可继续测试和构建。配置可整体回退，无业务数据迁移。

## 验收

- [x] 项目 pnpm 为 10.33.0，Corepack 离线解析到该缓存版本。
- [x] Node 本地声明、CI 和两个前端 Docker 使用 24 主版本。
- [x] 三个工作流覆盖 develop 推送与 PR，前端 CI 不再使用 latest。
- [x] Docker 不再全局安装未固定的 pnpm。
- [x] 锁文件 SHA-256 保持 `7c65eb57eafcf3dde705fa1b47174918ed9b9cea7a379dcdc923b8a5b41f52ba`。
- [x] 类型检查、lint、单测、Pact、构建与静态检查完成并记录实际结果。
- [x] 文档、队列、台账、场景矩阵和证据归档同步。

## 配置依据

- pnpm action 支持 `package_json_file` 读取 `packageManager`：https://raw.githubusercontent.com/pnpm/action-setup/v4/action.yml
- Node action 支持 `node-version-file`：https://raw.githubusercontent.com/actions/setup-node/v4/action.yml

## 进度

- [x] 事实与范围确认。
- [x] RED/GREEN 配置校验。
- [x] 本地验证和静态扫描。
- [x] 文档与切片复盘。

## 切片复盘

三个切片全部完成：版本及触发缺口已通过一次性 RED/GREEN 校验定位；项目、Docker 和 CI 版本来源已统一；前端 95 项单测、6 项 Pact、类型检查、lint 和生产构建通过，锁文件哈希不变。原有 8 条测试 stub 警告及大包提示记录保留，本轮不重构前端。未执行 GitHub 远程工作流、完整镜像构建或修改分支保护。详细证据见 `tasks/records/2026-10-05-sa-018-build-ci-consistency.md`。
