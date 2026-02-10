# 版本更新功能任务清单

> 最后更新：2026-01-12
> 状态：✅ 核心功能已完成

## 1. 接口设计与领域模型
- [x] 1.1 设计 VersionUpdater Port 接口（application 层）
- [x] 1.2 定义版本更新结果模型（VersionUpdateResult）
- [x] 1.3 定义版本更新请求模型（VersionUpdateRequest）

## 2. Maven VersionUpdater 实现
- [x] 2.1 实现 MavenVersionUpdater（infrastructure 层）
- [x] 2.2 pom.xml 解析（使用 DOM）
- [x] 2.3 单模块版本更新逻辑
- [ ] 2.4 多模块版本一致性处理（延后到 Phase 2）
- [x] 2.5 Diff 生成（更新前后对比）
- [x] 2.6 Maven VersionUpdater 单元测试

## 3. Gradle VersionUpdater 实现
- [x] 3.1 实现 GradleVersionUpdater（infrastructure 层）
- [x] 3.2 gradle.properties 版本更新
- [x] 3.3 build.gradle 版本更新（不支持时明确提示）
- [x] 3.4 Diff 生成
- [x] 3.5 Gradle VersionUpdater 单元测试

## 4. 版本更新服务集成
- [x] 4.1 创建 VersionUpdateAppService（application 层）
- [x] 4.2 集成到 Run 执行流程（RunAppService.executeVersionUpdate）
- [x] 4.3 执行记录存储（RunItem、RunStep）
- [x] 4.4 错误处理（BizException + 404 状态码）
- [x] 4.5 版本更新服务集成测试

## 5. 版本校验功能
- [x] 5.1 版本推导服务（根据 VersionPolicy）
- [ ] 5.2 分支推导服务（根据 BranchRule）- 依赖 BranchRule 后端实现
- [x] 5.3 版本号格式校验
- [ ] 5.4 冲突检测（版本号冲突、分支冲突）- 延后到 Phase 2
- [x] 5.5 校验 API（`POST /api/v1/release-windows/{id}/validate`）

## 6. API 实现
- [x] 6.1 版本更新 API 控制器（`POST /api/v1/release-windows/{id}/execute/version-update`）
- [x] 6.2 批量版本更新 API（`POST /api/v1/release-windows/{id}/execute/batch-version-update`）
- [x] 6.3 版本校验 API 控制器（`POST /api/v1/release-windows/{id}/validate`）
- [x] 6.4 API 请求/响应 DTO
- [x] 6.5 API 文档更新（Swagger）

## 7. 前端集成
- [x] 7.1 版本更新执行按钮和对话框（VersionUpdateDialog.vue）
- [x] 7.2 执行结果展示（Run 详情页）
- [x] 7.3 Diff 查看组件（DiffViewer 语法高亮）
- [x] 7.4 错误提示和加载状态
- [x] 7.5 版本校验 UI

## 8. 测试与文档
- [x] 8.1 VersionUpdater 单元测试覆盖
- [x] 8.2 版本更新集成测试（7 个测试用例）
- [x] 8.3 版本校验 API 集成测试（6 个测试用例）
- [ ] 8.4 前端 E2E 测试（可选，延后）
- [x] 8.5 更新项目文档

## 9. 代码质量
- [x] 9.1 代码审查和重构
- [x] 9.2 确保 ArchUnit 测试通过（11 个测试）
- [x] 9.3 确保前端 lint 和 typecheck 通过
- [x] 9.4 OpenSpec 验证通过

---

## 完成总结

### 已完成的核心功能
1. ✅ **版本更新器**：Maven 和 Gradle 版本更新器实现
2. ✅ **版本更新 API**：单个和批量版本更新 API
3. ✅ **版本校验 API**：版本推导和格式校验
4. ✅ **前端集成**：版本更新对话框、Diff 查看、运行记录展示
5. ✅ **测试覆盖**：单元测试和集成测试（13 个测试用例）

### 延后的功能
1. ⏳ 多模块 Maven 版本一致性处理
2. ⏳ 分支推导服务（依赖 BranchRule 后端）
3. ⏳ 冲突检测增强
4. ⏳ 前端 E2E 测试

### 相关代码文件
- `releasehub-application/src/main/java/io/releasehub/application/version/`
  - VersionUpdater.java
  - VersionUpdateRequest.java
  - VersionUpdateResult.java
  - VersionUpdateAppService.java
  - VersionValidationAppService.java
- `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/version/`
  - MavenVersionUpdater.java
  - GradleVersionUpdater.java
- `releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/`
  - VersionUpdateController.java
- `releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/`
  - VersionUpdateApiTest.java
  - VersionValidationApiTest.java
