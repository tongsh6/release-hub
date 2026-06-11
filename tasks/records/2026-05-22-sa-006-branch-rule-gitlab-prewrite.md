# SA-006 分支规则 GitLab 前置拒绝证据

日期：2026-05-22

## 背景

- SA-006 剩余缺口要求证明“规则配置 -> 分支创建被规则约束”的真实 GitLab 路径。
- 既有实现已经把 scoped BranchRule 传入核心创建链路，但 `IterationAppService.addRepos` 对 NAMED/EXISTING 的分支准备异常会记录日志后继续保存迭代仓库集合，证据无法支撑“写入前拒绝”。
- 本地历史数据还存在空或旧值 `git_provider`，导致仓库列表和 cloneUrl 唯一性检查被单条旧数据拖垮，阻断真实验收数据创建。

## 变更

- `IterationAppService` 拆出分支准备计划：先解析并校验 AUTO/NAMED/EXISTING 分支，再执行 GitLab 创建和版本信息写入。
- NAMED 不合规、EXISTING 不存在时直接抛出 `ValidationException`，不保存迭代仓库集合，不调用 GitLab 创建分支。
- `AttachAppService.createReleaseBranchForIteration` 先对全部目标仓库做 BranchRule 校验，不合规时不调用 GitLab 创建 release 分支，也不更新 releaseBranch 记录。
- `CodeRepositoryPersistenceAdapter` 对历史空/未知 `git_provider` 兼容读取为 GITLAB，避免旧数据让仓库列表整体失败。
- 新增 `scripts/acceptance/sa006-branch-rule-gitlab-evidence.sh`，用独立 GitLab 项目、独立叶子分组、真实仓库引用和 PROJECT / GLOBAL / SUB_PROJECT scoped BranchRule 采集可复现证据。
- 修正 `scripts/acceptance/run-acceptance.sh` 中非叶子仓库探针使用已废弃的 `MOCK` provider，以及 NAMED/EXISTING 旧断言。

## 验证

```bash
mvn -q -pl releasehub-application -am -Dtest=IterationAppServiceTest,AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-infrastructure -am -Dtest=CodeRepositoryPersistenceAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
bash -n scripts/acceptance/run-acceptance.sh
bash -n scripts/acceptance/sa006-branch-rule-gitlab-evidence.sh
bash scripts/acceptance/sa006-branch-rule-gitlab-evidence.sh
bash scripts/dev/static-scan-topn.sh 10
```

聚焦验收结果：

- `PASS=37 FAIL=0`
- PROJECT 合规 NAMED 分支 `feature/sa006-allowed-20260522-154651` 写入成功，系统 version-info 记录一致，GitLab 直查为 `FOUND`；不合规 NAMED 分支 `feature/sa006-denied-20260522-154651` 被写入前拒绝，迭代仓库集合未写入，GitLab 直查为 `NOT_FOUND`；不合规 release 分支 `release/RW-20260522-E4FA` 被创建前拒绝，GitLab 直查为 `NOT_FOUND`。
- GLOBAL 合规 NAMED 分支 `feature/sa006-global-allowed-20260522-154651` 写入成功，GitLab 直查为 `FOUND`；不合规 NAMED 分支 `feature/sa006-global-denied-20260522-154651` 和不合规 release 分支 `release/RW-20260522-A5A8` 均在 GitLab 直查为 `NOT_FOUND`。
- SUB_PROJECT 合规 NAMED 分支 `feature/sa006-sub-allowed-20260522-154651` 写入成功，GitLab 直查为 `FOUND`；不合规 NAMED 分支 `feature/sa006-sub-denied-20260522-154651` 和不合规 release 分支 `release/RW-20260522-3A74` 均在 GitLab 直查为 `NOT_FOUND`。
- Top10 静态扫描通过，报告：`.ai/reports/static-scan/20260522-234957/summary.md`。

## 剩余缺口

- 本轮补齐 PROJECT / GLOBAL / SUB_PROJECT scoped 真实 GitLab 前置拒绝证据，SA-006 P0 可出队。
- 聚焦脚本保留为专项证据入口，暂不并入默认全量 `run-acceptance.sh`，避免进一步拉长主验收耗时。
