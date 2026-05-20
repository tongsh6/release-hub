# SA-004 GitLab Settings Token 加密反查

## Slice 1: 系统级 GitLab token 加密审计闭环

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-004 / SA-002 token 安全证据
- **日期**：2026-05-21
- **执行者**：AI
- **状态**：已完成

## 选题理由

台账和场景矩阵明确留下“系统级 Settings token 是否加密存储需要反查”。该项优先级高于前端连接测试体验，因为它属于安全与数据治理风险：如果系统级 GitLab PAT 明文落库，后续真实 GitLab 操作、验收脚本复用和数据库备份都会暴露长期凭据。

本轮没有选择 SA-014 版本更新失败重试，原因是该能力会触达 Run 重试模型、版本更新执行器和前端入口，属于更大的垂直切片；而 Settings token 加密是当前矩阵中的明确安全缺口，已有 `GitTokenCrypto` 基础设施，可用较小范围完成深层修复和证据补齐。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | 通过 | SA-004 GitLab 连接安全，SA-002 token 安全审计 |
| 用户价值 | 通过 | 管理员保存 GitLab Settings 后，系统级 token 不再依赖人工反查判断是否安全 |
| 端到端路径 | 通过 | Infrastructure converter + 验收脚本审计 + 文档矩阵 |
| 单一目标 | 通过 | 只处理 Settings token 加密与审计证据 |
| 可独立验证 | 通过 | Converter 单测、Settings adapter 回归、脚本语法、静态扫描 |
| 可回滚 | 通过 | 仅影响 token converter 与验收审计输出，历史无前缀密文保持可读 |
| 依赖明确 | 通过 | 复用既有 `GitTokenCrypto` 与 JPA `@Convert` |
| 风险收敛 | 通过 | 新密文带 `enc:v1:` 前缀，兼容历史密文和加密关闭场景 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/crypto/GitTokenAttributeConverter.java` | 修改 | Infrastructure |
| `backend/releasehub-infrastructure/src/test/java/io/releasehub/infrastructure/crypto/GitTokenAttributeConverterTest.java` | 新建 | Test |
| `scripts/acceptance/run-acceptance.sh` | 修改 | Acceptance |
| `docs/project-ledger.md` | 修改 | Docs |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |

## 执行步骤

### Step 1: RED

- 新增 `GitTokenAttributeConverterTest`
- 失败证据：`mvn -pl releasehub-infrastructure -Dtest=GitTokenAttributeConverterTest test`
- 初始失败：长明文 `glpat-...` 被旧长度启发式误判为密文并原样返回。

### Step 2: GREEN

- `GitTokenAttributeConverter` 新密文改为 `enc:v1:` 前缀格式。
- 读取时兼容历史无前缀 AES-GCM 密文。
- 写入时只有“可成功解密”的值才视为密文，避免长明文和伪前缀明文绕过加密。

### Step 3: VERIFY

- `mvn -pl releasehub-infrastructure -Dtest=GitTokenCryptoTest,GitTokenAttributeConverterTest,SettingsAdapterTest test`：13 tests，0 failures。
- `bash -n scripts/acceptance/run-acceptance.sh`：通过。
- `git diff --check`：通过。
- `bash scripts/dev/static-scan-topn.sh 10`：通过。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | 通过 | Settings token 使用同一 converter 加密，验收脚本纳入明文审计 |
| 层级闭环 | 通过 | 无新增 API 或 UI 悬空能力 |
| 测试闭环 | 通过 | RED/GREEN 证据和边界用例已覆盖 |
| 架构闭环 | 通过 | 加密保持在 Infrastructure/JPA converter 横切点，未污染 Application |
| 性能闭环 | 通过 | 单字段转换，无批量远程调用或 N+1 风险 |
| 文档闭环 | 通过 | 台账、场景矩阵和任务记录已同步 |
| 工作区闭环 | 通过 | 已检查 `git status --short`，仅保留本任务相关改动 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260521-002048/summary.md`
- **TopN 处理结论**：未发现代码问题；SpotBugs 0 bugs，frontend lint/typecheck 通过。
- **未解决风险**：未运行完整真实 GitLab 场景验收；本轮脚本变更已通过 `bash -n`，运行环境当前后端/前端未就绪。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 前端 GitLab 连接测试与错误提示 | 已由 `2026-05-21-sa-004-gitlab-connection-test.md` 补齐 | `docs/reports/scenario-acceptance-matrix.md` SA-004 |
| 完整真实 GitLab 验收复跑 | 当前本地后端/前端服务未就绪；本轮变更已由 converter 单测和脚本语法覆盖核心风险 | `scripts/acceptance/run-acceptance.sh` |

## 经验沉淀

- 不新增经验文档；本轮模式归入既有 `static-scan-topn-workflow` 和 token 加密治理经验。
