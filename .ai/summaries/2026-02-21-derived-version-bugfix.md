# 会话摘要：derivedVersion Bug 修复

**日期**：2026-02-21
**仓库**：release-hub（内层后端仓库）
**PR**：https://github.com/tongsh6/release-hub/pull/5

---

## 背景

`POST /api/v1/release-windows/{id}/validate` 接口的 `derivedVersion` 始终返回 `"0.1.0"`，无论传入什么 `currentVersion`。

---

## 根因分析

`VersionValidationAppService.validateVersion()` 中存在 fallback 逻辑：

```java
// 修复前（有 bug）
String derivedVersion;
if (currentVersion != null && !currentVersion.isBlank()) {
    derivedVersion = policy.deriveNextVersion(currentVersion);
} else {
    derivedVersion = policy.deriveNextVersion("0.0.0");  // ← bug
}
```

当调用方不传 `currentVersion` 时，`"0.0.0"` + MINOR 策略 = `"0.1.0"`，导致固定返回。

---

## 修复方案

**方案选择**：使 `currentVersion` 成为必填字段（`@NotBlank`），移除 fallback。

理由：`currentVersion` 在业务上不可缺失，fallback 到 "0.0.0" 无业务语义，且会掩盖调用方问题。

### 修改文件

| 文件 | 变更 |
|------|------|
| `releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/VersionValidationRequest.java` | 为 `currentVersion` 添加 `@NotBlank` |
| `releasehub-application/src/main/java/io/releasehub/application/version/VersionValidationAppService.java` | 移除 "0.0.0" fallback |
| `releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/VersionValidationApiTest.java` | 补充 Order 7-10：missing currentVersion 400 场景 + PATCH/MINOR/MAJOR happy path |

---

## 遇到的坑

### Maven 多模块编译缓存问题

- `mvn -pl releasehub-bootstrap test` 不会重编译 releasehub-interfaces
- 修改 `VersionValidationRequest.java` 后测试仍用旧编译，Order 7 返回 200 而非 400
- **修复**：`mvn clean test -pl releasehub-application,releasehub-interfaces,releasehub-bootstrap -am`

---

## 测试结果

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- VersionValidationApiTest
[INFO] Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

所有 E2E 测试同步通过（ReleaseWorkflowE2ETest、VersionPolicyE2ETest、ErrorHandlingE2ETest）。

---

## 经验教训

1. **AppService 不应有无语义 fallback**：fallback 会掩盖调用方问题，应用 Bean Validation 在接口层拦截
2. **修改 interfaces 模块后必须 `mvn clean`**：子模块编译缓存可能导致测试行为不符预期
3. **E2E 测试通过 cast(:keyword as string) 不等于安全**：本次未改该语句，但 MEMORY.md 中的警告仍适用于 null 参数场景
