## 1. 实现

- [x] 1.1 扩展 `VersionValidationAppService`，基于 ReleaseWindow + BranchRule 推导 `derivedBranch`
- [x] 1.2 调整 `VersionUpdateController` 调用签名，传入 `windowId`
- [x] 1.3 为分支推导补充应用层单元测试（含规则匹配/不匹配）
- [x] 1.4 更新 API 集成测试断言，验证 `derivedBranch` 返回值

## 2. 验证

- [ ] 2.1 `lsp_diagnostics` 零错误（当前环境缺少 jdtls，无法执行）
- [ ] 2.2 `mvn -q test -Dtest=VersionValidationApiTest,VersionValidationAppServiceTest` 通过（受现有 application 模块测试编译失败影响）
- [ ] 2.3 `mvn -q test` 全量通过
