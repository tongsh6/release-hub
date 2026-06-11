# SA-004 GitLab 连接异常诊断展示

## 目标

- 管理员在系统设置页测试 GitLab 连接时，能区分 token 无效、权限不足和 GitLab 不可达。
- 后端错误码和页面提示不泄露 token、baseUrl 凭据或 GitLab 原始响应体。
- 保持连接测试调用真实 GitLab `/api/v4/user`，不退回固定成功。

## 变更

- 扩展 GitLab 错误码：
  - `GITLAB_004`：GitLab token 无效或过期。
  - `GITLAB_005`：GitLab token 缺少必要权限。
  - `GITLAB_006`：GitLab 服务不可达。
- `GitLabAdapter.testConnection()` 保持调用 `/api/v4/user`，并将 401 / 403 / 远程不可达映射为上述错误码；新增错误均不携带敏感参数。
- 设置页连接测试失败时显示页面内诊断提示，同时继续走统一错误处理。
- 同步更新场景矩阵、项目台账和执行路线图：SA-004 出队，下一队首为 SA-006 历史不合规分支治理入口。

## 验证

```bash
mvn -q -pl releasehub-infrastructure -am -Dtest=GitLabAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-bootstrap -am -Dtest=SettingsApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/settings/__tests__/Settings.spec.ts
pnpm i18n:lint
```

结果：

- `GitLabAdapterTest` 覆盖连接成功、401 token 无效、403 权限不足、不可达、配置缺失和归档分支统计排除。
- `SettingsApiTest` 覆盖 `GITLAB_004/005/006` 的 HTTP 状态、错误码和安全消息。
- 设置页 Vitest：`3 PASS / 0 FAIL / 0 SKIP`，页面内诊断提示可见。
- 前端 i18n lint 通过。

## 结论

- SA-004 GitLab 连接异常诊断展示已覆盖，后续保持回归。
- 下一步按路线图推进 SA-006 历史不合规分支治理入口。
