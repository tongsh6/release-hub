# SA-017 首次部署登录修复

## 范围与结果

用户在项目价值分析和下一步建议后要求开工，先完成首次部署可登录这一项。完整蓝图见 `tasks/plans/2026-10-05-initial-admin-bootstrap.md`，三个切片（复现、修复、交付）均已完成，当前分支 `feature/initial-admin-bootstrap`。

- 新增 `InitialAdminAppService` 和启动入口，生产 `prd,real` 配置使用部署者提供的用户名和密码，仅在空用户表中创建账户。
- 使用 PostgreSQL 事务表锁保护空表判断和写入；已有任意用户时跳过，不新增、不重置密码。
- 校验用户名及 BCrypt 密码字节边界；缺失凭据或同时开启开发 seed 时拒绝启动；日志不记录初始密码。
- Compose 增加初始凭据传递，保持开发 seed 关闭。
- 空库演练额外发现 PostgreSQL 18.1 不接受旧数据目录挂载，修正为 `/var/lib/postgresql`，验证重建容器后记录保留。
- 首次登录、凭据移除、升级数据卷边界已写入部署文档；SA-017 出队，SA-004 生产 Token 加密配置进入 HEAD，原 SA-002 迁移评审限制保留。

## RED / GREEN 与回归

- RED：隔离 PostgreSQL 18.1，原始生产配置关闭 seed，自定义账户登录期望 200，实际 401。
- GREEN：新增应用单测 13 PASS；首次部署生产配置集成 5 PASS；架构规则 4 PASS。
- 最终回归：Surefire 333 项，其中 328 PASS / 5 SKIP / 0 FAIL / 0 ERROR；Failsafe 定向 `InitialAdminBootstrapIT,AuthE2ETest` 共 10 PASS。
- 集成覆盖真实 `prd,real` 启动、登录和认证访问、第二次应用启动不提供密码、改名改密不覆盖、并发只创建一个账户、空库缺失凭据失败、seed 冲突保护。
- Compose 原挂载路径出现重启循环；修正后 healthy，写入测试记录并强制重建后查询 `retained_rows=1`。本次独立测试容器、卷和网络已删除。
- 复用本机缓存 Maven 依赖和 Colima 镜像，无工具或依赖升级；pnpm 未经全局入口执行。

本次未运行会创建真实业务/GitLab 资源的全量验收脚本，未重跑前端浏览器旅程或完整镜像构建；API 集成证据不冒充浏览器旅程。

## 静态扫描与边界

按本机策略，报告及原始日志归档在 `/Users/loong/backup-manifest/reports/releasehub-initial-admin-20261005/`。

等价静态扫描使用离线 Maven SpotBugs、前端现有二进制 ESLint/typecheck、git diff 和 roadmap 检查，避免统一脚本的 Maven install 和全局 pnpm 12 自动重建行为。

- SpotBugs：通过，无后端问题。
- ESLint：0 error，8 条既有测试组件警告，逐项记录保留理由；本次未改动对应前端代码。
- TypeScript、git diff --check、roadmap：通过。
- Flyway 对 PostgreSQL 18.1 提示超出其已测试支持范围；本轮 33 项迁移和登录已通过，但不能据此声明全面兼容，未擅自升级依赖。

本次未修改存量数据库或 GitLab；不包含 RBAC、生产 Token 加密和发布中断恢复。后续范围由路线图继续追踪。

## 2026-10-08 类型检查结论更正

此前 `pnpm typecheck` 的成功退出来自仅含 references、files 为空的根配置，实际检查文件数为 0，不能代表应用源码类型检查通过。显式 `vue-tsc --noEmit -p tsconfig.app.json` 对当前代码报告 44 个存量错误，HEAD 基线 45 个，S1 新增 0 个。类型门禁已重新列为 SA-018 队首；原测试和构建结果不受此更正影响。详见 `tasks/records/2026-10-08-sa-013-s1-run-plan-preview.md`。
