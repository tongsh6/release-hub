# Slice: SA-014 Maven 多模块 / Gradle 真实写回

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-014 技术负责人执行版本更新
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

- Maven 单模块真实写回和批量版本更新前端入口已闭环。
- 场景矩阵仍将 Maven 多模块和 Gradle 真实 GitLab 写回列为 SA-014 Phase 2 缺口。
- Maven/Gradle 更新器已有单测，本切片补真实 GitLab release 分支证据，不新增业务 API。

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `scripts/acceptance/run-acceptance.sh` | 修改 | Acceptance |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |
| `tasks/records/2026-05-20-sa-014-multi-gradle-real-writeback.md` | 新增 | Tasks |

## 执行步骤

### Step 1: RED / 缺口确认

- 首轮新增探针暴露两个问题：
  - fixture 在 `addRepos` 后写入 feature 分支，导致 `versionInfo` 的系统开发版本与仓库实际版本不一致，被 `CONFLICT_001` 阻断。
  - Gradle `gradle.properties` 在部分种子状态下已存在，单纯 GitLab `create` 返回 400。

### Step 2: GREEN

- 新增 `gitlab_upsert_file` 和 `gitlab_file_contains` helper。
- SA-014 新增 8.3 Maven 多模块真实写回探针：
  - 使用独立窗口/迭代。
  - 临时设置并复原 R2 初始版本。
  - 在 feature 分支准备父 POM 与 `module-a/module-b` POM。
  - 版本更新后校验 release 分支 commit 和 root/module 文件内容。
- SA-014 新增 8.4 Gradle 真实写回探针：
  - 使用独立窗口/迭代。
  - 临时设置并复原 R3 初始版本。
  - upsert `gradle.properties` fixture。
  - 版本更新后校验 release 分支 commit 和 `gradle.properties` 内容。

### Step 3: VERIFY

| 命令 | 结果 |
|------|------|
| `bash -n scripts/acceptance/run-acceptance.sh` | PASS |
| `git diff --check` | PASS |
| `mvn -pl releasehub-infrastructure -Dtest=MavenVersionUpdaterTest,GradleVersionUpdaterTest test` | PASS：12 passed |
| `bash scripts/acceptance/run-acceptance.sh` | PASS：154 passed, 0 failed, 0 skipped |
| `bash scripts/dev/static-scan-topn.sh 10` | PASS：TopN 未发现问题 |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | OK | Maven 多模块和 Gradle 均可在真实 GitLab release 分支写回版本 |
| 层级闭环 | OK | 复用既有版本更新 API、Run、GitLab file adapter 和验收脚本 |
| 测试闭环 | OK | 更新器单测 + 真实 GitLab 验收覆盖 |
| 架构闭环 | OK | 未改变 Domain/Application/API 语义，仅补验收证据 |
| 性能闭环 | OK | 探针为独立窗口/单仓验证，不引入批量路径额外开销 |
| 文档闭环 | OK | 已同步场景矩阵、项目台账和本执行记录 |
| 工作区闭环 | OK | `git diff --check` 和静态扫描已通过；扫描报告：`.ai/reports/static-scan/20260520-234151/summary.md` |

## 经验沉淀

- [x] 不新增经验文档。本轮是验收脚本补证据；关键经验已记录在本切片 RED 小节。
