# SA-004 生产 Git Token 加密配置与恢复验证

## 交付结果

完整蓝图：`tasks/plans/2026-10-05-production-token-crypto.md`。复现、修复、恢复交付三个切片全部完成，保留 SA-017 已有改动。

- `prd` 默认启用 AES-GCM，独立密钥由 `RELEASEHUB_CRYPTO_SECRET_KEY` 提供；Compose 对缺失密钥直接报错。
- 转换器读取 `enc:v1:` 密文时，错钥、损坏和加密关闭均明确失败，不能再原样返回密文冒充 Token。
- `TokenCryptoStartupVerifier` 在首次账户初始化前，以只读事务和分批读取校验仓库及 Settings 密文；不输出敏感值，不迁移数据。
- 无标识旧凭据继续兼容读取，只提示数量；无法保证识别所有无标识旧密文的错钥情况。
- 恢复脚本增加 `ON_ERROR_STOP=1`，SQL 导入错误不继续启动应用；部署说明补齐密钥独立备份、沿用原密钥和隔离恢复演练。

## RED / GREEN

- 转换器新增 4 项测试：原代码在错误密钥、关闭加密、损坏密文的 3 个拒绝场景失败。
- 原生产配置的真实 PostgreSQL 测试显示 Settings Token 是明文，`enc:v1:` 断言失败。
- 修复后，生产加密 4 个集成场景全部通过：双字段加密、重启、SQL 备份恢复、三类非法密钥、错误有效密钥、损坏密文、历史凭据不自动迁移。
- SQL 备份包含两类密文，不包含原 Token 或密钥；恢复后 Settings 和仓库分支 HTTP 请求均使用原始 Token，累计 6 次受控请求成功。

## 最终验证

- Surefire：337 项，332 PASS / 5 SKIP / 0 FAIL / 0 ERROR；跳过项为既有分组测试。
- Failsafe：`ProductionTokenCryptoIT,InitialAdminBootstrapIT,AuthE2ETest` 共 14 PASS。
- SpotBugs、TypeScript、Compose 配置门禁、脚本语法、diff 和 roadmap 检查通过。
- ESLint：0 error / 8 条既有前端测试 stub 警告，逐项保留理由见扫描报告。
- 运行日志不包含两个测试 Token 和加密密钥。
- 沿用缓存 Maven 依赖与 Colima 镜像，无工具或依赖升级。

报告按本机策略归档到 `/Users/loong/backup-manifest/reports/releasehub-production-crypto-20261005/summary.md`，含原始日志与 TopN 处理。

## 验证边界与后续

数据库和凭据均为本次隔离 fixture；没有读取、迁移用户现有数据库或轮换真实密钥。HTTP fixture 证明凭据正确使用，不冒充真实 GitLab 发布验收；未执行完整镜像构建、浏览器旅程或生产恢复脚本对业务库的破坏性操作。

Flyway 对 PostgreSQL 18.1 的既有支持范围提示仍保留，未升级依赖。历史无标识凭据的审计、迁移和密钥轮换须独立处理。

蓝图、部署文档、路线图、台账、矩阵和经验均已同步；SA-004 出队，SA-018 固定构建环境与 CI 门禁进入 HEAD。原 SA-002 迁移评审边界继续保留。

## 2026-10-08 类型检查结论更正

此前 `pnpm typecheck` 的成功退出来自仅含 references、files 为空的根配置，实际检查文件数为 0，不能代表应用源码类型检查通过。显式 `vue-tsc --noEmit -p tsconfig.app.json` 对当前代码报告 44 个存量错误，HEAD 基线 45 个，S1 新增 0 个。类型门禁已重新列为 SA-018 队首；原测试和构建结果不受此更正影响。详见 `tasks/records/2026-10-08-sa-013-s1-run-plan-preview.md`。
