# 2026-05-21 SA-012 REPO_AHEAD 接受仓库版本

## 范围

- `resolveVersionConflict(USE_REPO)` 从 feature 分支读取仓库版本，更新系统 `devVersion`，并把 `versionSource` 标记为 `REPO`。
- 仓库版本无法解析或缺少 feature 分支时不再静默回退到旧系统版本，避免假解决。
- 窗口冲突面板对 `REPO_AHEAD` 显示“接受仓库版本”，提交 `resolution=USE_REPO`。
- `MISMATCH` / `SYSTEM_AHEAD` 继续保持 `USE_SYSTEM` 解决路径。
- `docs/execution-roadmap.md` 出队 SA-012，下一 HEAD 移到 SA-016。

## 验证

```bash
mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ConflictPanel.spec.ts src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
pnpm run typecheck
pnpm i18n:lint
git diff --check
```

## 结果

- 后端 IterationAppService 专项：`22 PASS / 0 FAIL / 0 SKIP`。
- 前端冲突面板和窗口详情专项：`7 PASS / 0 FAIL / 0 SKIP`。
- typecheck、i18n lint、diff 检查通过。

## 结论

- SA-012 已补 `REPO_AHEAD` 的应用内 `USE_REPO` 解决路径；后续保持回归。
