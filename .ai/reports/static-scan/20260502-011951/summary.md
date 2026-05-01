# 静态代码扫描与 TopN 处理报告

- 时间：2026-05-02 01:19:51 +0800
- 仓库：`/Users/loong/workspace/code/github/release-hub`
- TopN：10
- 处理人：AI
- Phase：Phase 5 (部署文档) + Phase 6 (发布自动化)

## 变更范围

本次会话变更文件（共 18 个）：
- `backend/releasehub-application/.../AttachResult.java` (新建)
- `backend/releasehub-application/.../WindowLifecycleListener.java` (新建)
- `backend/releasehub-application/.../WindowPublishedEvent.java` (新建)
- `backend/releasehub-application/.../AttachAppService.java` (修改)
- `backend/releasehub-application/.../ReleaseWindowAppService.java` (修改)
- `backend/releasehub-interfaces/.../AttachController.java` (修改)
- `backend/releasehub-application/.../ReleaseWindowAppServiceTest.java` (修改)
- `backend/releasehub-bootstrap/.../WindowIterationApiTest.java` (修改)
- `backend/releasehub-bootstrap/.../WindowRunApiTest.java` (修改)
- `docs/deployment.md` (新建)
- `docs/docker-compose.yml` (修改)
- `docs/scripts/dev/static-scan-topn.sh` (修改)
- `tasks/records/2026-05-02-phase-5-deployment-docs.md` (新建)
- `tasks/records/2026-05-02-phase-6-release-automation.md` (新建)

## 扫描命令

- git-diff-check: `git diff --check`
  - 状态：PASS
- backend-spotbugs: `mvn -q -B -DskipTests com.github.spotbugs:spotbugs-maven-plugin:4.9.8.3:check`
  - 状态：FAIL，退出码 1（5 bugs in releasehub-common，全部是预存问题）
- frontend-lint: `pnpm -s lint`
  - 状态：PASS
- frontend-typecheck: `pnpm -s typecheck`
  - 状态：PASS

## TopN 问题清单与处理结论

| # | 扫描来源 | 问题摘要 | 优先级依据 | 处理方式 | 处理结果 | 复扫证据 |
|---|----------|----------|------------|----------|----------|----------|
| 1 | `backend-spotbugs.txt:1` | EI_EXPOSE_REP: PageResult.items() 暴露内部集合 | 中 | **跳过**：预存问题，非本次变更引入；PageResult 在项目中广泛使用，修复需全局评估影响 | 不修复 | N/A |
| 2 | `backend-spotbugs.txt:2` | EI_EXPOSE_REP2: PageResult 构造器存储外部集合 | 中 | **跳过**：同上，预存问题 | 不修复 | N/A |
| 3 | `backend-spotbugs.txt:3` | EI_EXPOSE_REP: ApiPageResponse.getPage() 暴露内部对象 | 中 | **跳过**：同上，预存问题 | 不修复 | N/A |
| 4 | `backend-spotbugs.txt:4` | EI_EXPOSE_REP2: ApiPageResponse 构造器存储外部对象 | 中 | **跳过**：同上，预存问题 | 不修复 | N/A |
| 5 | `backend-spotbugs.txt:5` | EI_EXPOSE_REP2: ApiPageResponse.setPage() 存储外部对象 | 中 | **跳过**：同上，预存问题 | 不修复 | N/A |
| 6 | `backend-spotbugs.txt:6` | SpotBugs 构建失败汇总信息 | 低 | **跳过**：构建错误汇总行，非代码问题 | N/A | N/A |
| 7 | `backend-spotbugs.txt:7` | (空白行) | 无 | **跳过**：空行 | N/A | N/A |
| 8 | `backend-spotbugs.txt:8` | Maven 错误堆栈提示 | 无 | **跳过**：构建工具提示信息 | N/A | N/A |
| 9 | `backend-spotbugs.txt:9` | Maven 调试提示 | 无 | **跳过**：构建工具提示信息 | N/A | N/A |
| 10 | `backend-spotbugs.txt:10` | (空白行) | 无 | **跳过**：空行 | N/A | N/A |

## TopN 处理总结

- **本次变更引入的新问题**：0
- **预存问题（非本次引入）**：5 (EI_EXPOSE_REP × 3 + EI_EXPOSE_REP2 × 2)
- **非问题（构建工具/空白）**：5
- **修复**：0
- **跳过**：10

> 预存问题涉及 `PageResult`（releasehub-common）和 `ApiPageResponse`（releasehub-common），两者均为项目基础类，在大量模块中使用。修复需将集合/可变对象改为防御性拷贝或不可变包装，属于全局影响的技术债务清理，不适合在当前 Phase 中混入处理。建议在专门的技术债务 Sprint 中统一处理。

## 质量基线

| 指标 | 数值 | 状态 |
|------|------|:----:|
| 后端单元测试 | 64/64 | ✅ |
| 后端集成/E2E测试 | 64 全通过 | ✅ |
| 前端 typecheck | 通过 | ✅ |
| 前端 lint | 通过 | ✅ |
| git diff --check | 通过 | ✅ |
| SpotBugs (新引入) | 0 bugs | ✅ |

原始扫描日志保留在 `raw/` 目录。
