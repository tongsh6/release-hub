## 1. 接口设计与领域模型
- [ ] 1.1 设计 VersionUpdater Port 接口（application 层）
- [ ] 1.2 定义版本更新结果模型（VersionUpdateResult）
- [ ] 1.3 定义版本更新请求模型（VersionUpdateRequest）

## 2. Maven VersionUpdater 实现
- [ ] 2.1 实现 MavenVersionUpdater（infrastructure 层）
- [ ] 2.2 pom.xml 解析（使用 DOM/SAX）
- [ ] 2.3 单模块版本更新逻辑
- [ ] 2.4 多模块版本一致性处理
- [ ] 2.5 Diff 生成（更新前后对比）
- [ ] 2.6 Maven VersionUpdater 单元测试

## 3. Gradle VersionUpdater 实现
- [ ] 3.1 实现 GradleVersionUpdater（infrastructure 层）
- [ ] 3.2 gradle.properties 版本更新
- [ ] 3.3 build.gradle 版本更新（基础实现，复杂场景提示）
- [ ] 3.4 Diff 生成
- [ ] 3.5 Gradle VersionUpdater 单元测试

## 4. 版本更新服务集成
- [ ] 4.1 创建 VersionUpdateAppService（application 层）
- [ ] 4.2 集成到 Run 执行流程
- [ ] 4.3 执行记录存储（RunItem、RunStep）
- [ ] 4.4 错误处理和回滚机制
- [ ] 4.5 版本更新服务集成测试

## 5. 版本校验功能
- [ ] 5.1 版本推导服务（根据 VersionPolicy）
- [ ] 5.2 分支推导服务（根据 BranchRule）
- [ ] 5.3 版本号格式校验
- [ ] 5.4 冲突检测（版本号冲突、分支冲突）
- [ ] 5.5 校验 API（`POST /api/v1/release-windows/{id}/validate`）

## 6. API 实现
- [ ] 6.1 版本更新 API 控制器（`POST /api/v1/release-windows/{id}/execute/version-update`）
- [ ] 6.2 版本校验 API 控制器（`POST /api/v1/release-windows/{id}/validate`）
- [ ] 6.3 API 请求/响应 DTO
- [ ] 6.4 API 文档更新（Swagger）

## 7. 前端集成
- [ ] 7.1 版本更新执行按钮和对话框
- [ ] 7.2 执行结果展示（Run 详情页）
- [ ] 7.3 Diff 查看组件
- [ ] 7.4 错误提示和加载状态
- [ ] 7.5 版本校验 UI

## 8. 测试与文档
- [ ] 8.1 VersionUpdater 单元测试覆盖
- [ ] 8.2 版本更新集成测试
- [ ] 8.3 API 集成测试
- [ ] 8.4 前端 E2E 测试（可选）
- [ ] 8.5 更新项目文档

## 9. 代码质量
- [ ] 9.1 代码审查和重构
- [ ] 9.2 确保 ArchUnit 测试通过
- [ ] 9.3 确保前端 lint 和 typecheck 通过
- [ ] 9.4 OpenSpec 验证通过
