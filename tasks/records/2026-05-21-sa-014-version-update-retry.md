# 2026-05-21 SA-014 版本更新失败重试

## 范围

- 版本更新 RunItem 保存可重试参数 metadata：`buildTool`、`branchName`、`repoPath`、`targetVersion`、`pomPath`、`gradlePropertiesPath`。
- `RunAppService.retry` 对 `VERSION_UPDATE` Run 走专用重试路径，只重放 `VERSION_UPDATE_FAILED` 项。
- retry 新 RunItem 写入 `retry.sourceRunId` 和 `retry.sourceItemId`，保留与原失败项的追溯关系。
- Run 详情前端覆盖版本更新成功/失败混合列表，只向 retry API 提交失败项 key。
- `docs/execution-roadmap.md` 出队 SA-014，下一 HEAD 移到 SA-012。

## 验证

```bash
mvn -pl releasehub-application -am -Dtest=RunAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl releasehub-infrastructure -am -DskipTests compile
mvn -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/run/__tests__/RunDetail.spec.ts
```

## 结果

- 后端 RunAppService 专项：`10 PASS / 0 FAIL / 0 SKIP`。
- infrastructure 编译：通过。
- MockMvc/JPA 集成：`1 PASS / 0 FAIL / 0 SKIP`，Flyway 30 条迁移通过。
- 前端 RunDetail 专项：`3 PASS / 0 FAIL / 0 SKIP`。

## 结论

- SA-014 版本更新失败重试已闭环；后续保持回归。
