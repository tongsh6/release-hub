# 2026-05-23 SA-014 版本解析异常样本治理

## 背景

SA-014 已具备 Maven 单模块、多模块、Gradle 真实写回、批量版本更新、部分失败和 retry 证据。风险池仍保留空仓库、无版本文件和异常版本号样本；如果这些仓库只表现为笼统“解析失败”或后续版本更新“不明失败”，技术负责人无法在版本更新前定位问题。

## 范围

- 将仓库初始版本解析失败拆成可追溯状态。
- 初始版本 API 返回默认分支、检查路径、错误类型和说明文案。
- 仓库详情页和仓库抽屉展示异常诊断，并保留重新解析入口。
- 同步 OpenSpec、场景矩阵、项目台账和执行路线图。

## 非目标

- 不重写版本解析框架或 Git Provider 适配层。
- 不改变 Maven 单模块、多模块、Gradle、批量版本更新、部分失败 retry 的既有语义。
- 不自动修复 GitLab 远端仓库内容。
- 不引入新的全局工具、系统级依赖或外部服务。

## 改动

- `VersionExtractorUseCase` 新增 `inspectVersion` 诊断契约。
- `VersionExtractor` 识别并返回：
  - `VERSION_FILE_MISSING`：默认分支缺少 `pom.xml` 和 `gradle.properties`。
  - `VERSION_DECL_MISSING`：版本文件存在但缺少项目版本号声明。
  - `VERSION_INVALID`：版本值格式异常。
  - `VERSION_READ_ERROR`：读取版本文件失败。
- `CodeRepositoryAppService` 在创建和重新解析版本时保存具体异常状态，不再统一落到模糊 `VERSION_UNRESOLVED`。
- `InitialVersionView` 返回 `branch`、`checkedPaths`、`errorType`、`message`。
- 仓库详情页和仓库抽屉展示异常诊断上下文。

## 验证

```bash
mvn -pl releasehub-application -Dtest=VersionExtractorTest,CodeRepositoryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl releasehub-bootstrap -am -Dtest=RepositorySyncApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/repository/__tests__/RepositoryDetail.spec.ts src/views/repository/__tests__/RepositoryDrawer.spec.ts
```

结果：

- `VersionExtractorTest` / `CodeRepositoryAppServiceTest`：22 PASS。
- `RepositorySyncApiTest`：3 PASS。
- `RepositoryDetail.spec.ts` / `RepositoryDrawer.spec.ts`：6 PASS。

## 结论

SA-014 版本解析异常样本已具备用户可见状态和可追溯诊断，不再把空仓库、无版本文件或异常版本号表现为不明失败。当前路线图 HEAD 转向 SA-008 多窗口并行发布可观测性。
