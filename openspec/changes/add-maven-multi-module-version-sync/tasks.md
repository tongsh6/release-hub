## 1. Implementation

- [x] 1.1 扩展 Maven 更新器，支持解析并更新多模块父/子 pom 版本
- [x] 1.2 保持单模块更新行为与返回结构兼容
- [x] 1.3 增加 MavenVersionUpdaterAdapter 多模块场景单元测试
- [x] 1.4 增加 VersionUpdateApiTest 多模块集成测试

## 2. Verification

- [ ] 2.1 受影响模块编译通过（被仓库现有 `ReleaseRunService` 编译错误阻断）
- [x] 2.2 `mvn -q -pl releasehub-infrastructure -Dtest=MavenVersionUpdaterTest test` 通过
- [ ] 2.3 `mvn -q -pl releasehub-bootstrap -Dtest=VersionUpdateApiTest test` 通过（被仓库现有 `ReleaseRunService` 编译错误阻断）
