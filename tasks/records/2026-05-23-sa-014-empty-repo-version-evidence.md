# 2026-05-23 SA-014 空仓库版本解析真实 GitLab 证据

## 背景

SA-014 版本解析异常已经具备应用层、API 和页面组件诊断证据，但路线图当前 HEAD 仍要求补真实 GitLab 空仓库样本。这个样本要证明系统不会把空仓库误判为正常版本、不会填充假版本，也不会因为解析失败阻塞仓库列表。

## 范围

- 新增 focused 验收脚本，创建真实 GitLab 空仓库。
- 通过系统仓库纳管入口注册空仓库，不绕过应用层。
- 复核 `initial-version` 与 `sync-version` 都返回 `VERSION_FILE_MISSING` 诊断。
- 保留默认分支、检查路径、错误类型、用户诊断文案和仓库列表可见性证据。
- 同步 OpenSpec、场景矩阵、脚本索引和项目台账。

## 非目标

- 不修改 Maven、Gradle 或版本更新写回语义。
- 不自动修复 GitLab 空仓库内容。
- 不通过数据库脚本制造或修复验收数据。
- 不引入新的全局工具、系统级依赖或外部服务。

## 改动

- 新增 `scripts/acceptance/sa014-empty-repo-version-evidence.sh`。
- 脚本刷新 GitLab Settings 为当前 PAT 后，再创建独立叶子分组和真实 GitLab 空项目。
- 脚本通过系统仓库纳管入口创建仓库引用，随后复核创建后解析与重新解析结果。
- 脚本输出 `.ai/reports/sa014-empty-repo-version/<timestamp>/summary.md` 和 `evidence.json`，用于后续归档。
- `docs/openspec/specs/repo-management/spec.md` 补充真实 GitLab 空仓库可诊断场景。

## 验证

```bash
bash -n scripts/acceptance/sa014-empty-repo-version-evidence.sh
mvn -pl releasehub-application -Dtest=VersionExtractorTest,CodeRepositoryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/repository/__tests__/RepositoryDetail.spec.ts src/views/repository/__tests__/RepositoryDrawer.spec.ts
scripts/acceptance/sa014-empty-repo-version-evidence.sh
```

结果：

- 脚本语法检查通过。
- `VersionExtractorTest` / `CodeRepositoryAppServiceTest`：22 PASS / 0 FAIL / 0 SKIP。
- `RepositoryDetail.spec.ts` / `RepositoryDrawer.spec.ts`：6 PASS / 0 FAIL / 0 SKIP。
- `scripts/acceptance/sa014-empty-repo-version-evidence.sh`：23 PASS / 0 FAIL。
- 真实 GitLab 项目：`e2e-user/sa014-empty-20260523-113039`，分支数 0。
- ReleaseHub 仓库 ID：`daa74c37-73c8-4ee2-a680-bc3b021042b8`。
- 证据报告：`.ai/reports/sa014-empty-repo-version/20260523-113039/summary.md`。

## 结论

SA-014 空仓库版本解析真实 GitLab 证据已闭环。空仓库不会被误判为正常版本，不会填充假版本，重新解析仍返回明确诊断，且仓库列表仍可见。当前路线图 HEAD 转向 SA-001 场景矩阵清账与下一阶段候选排序。
