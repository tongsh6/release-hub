# SA-013 发布编排 Run 页面内复核

## 完整目标蓝图

### 最终行为

- 技术负责人在发布窗口详情页触发发布编排后，页面立即出现对应 Run 的可见复核结果，而不是只依赖成功提示、后端接口或日志。
- 失败 Run 在窗口详情页有明确入口和摘要：能看到失败状态、失败项数量、发布窗口/仓库/迭代上下文、失败步骤和失败原因。
- 最近执行记录稳定展示最新 Run，避免管理员在窗口详情页复核到旧 Run。

### 范围

- 后端：Run 分页读模型按最近开始时间倒序返回。
- 前端：发布编排面板展示最新 Run 复核摘要，并保留跳转 Run 详情的入口。
- 测试：覆盖 Run 分页排序、编排返回 Run 后详情加载、失败 Run 摘要和失败上下文展示。
- 文档：同步 Run OpenSpec、场景矩阵、项目台账和执行路线图。

### 非目标

- 不改变发布编排业务语义、冲突预检和发布准入规则。
- 不重写 Run 存储模型、RunItem/RunStep 结构或重试模型。
- 不新增失败 Run 自动重试、通知、RBAC、CI 深集成或自动回滚。
- 不把 route-level stub 伪装成真实场景化验收；真实 GitLab 成功编排仍由 `run-acceptance.sh` 基线承担。

## Slice：窗口详情最新 Run 复核摘要

- 蓝图归属：完整目标中的“编排后可见结果 + 失败 Run 可追溯入口”。
- 目标：管理员能在发布窗口详情页直接看到最新发布编排 Run 的结果和失败摘要。
- 涉及层：Infrastructure、API 读模型、Frontend、Test、Docs。
- 依赖：既有 `RunPort.findPaged`、`runApi.getRunById`、`OrchestrationPanel` 和 Run 详情页。
- 后续：SA-013 后续保持回归；下一队首转向 SA-003 组织资源移动治理。

## 变更

- `RunJpaPersistenceAdapter.findPaged` 使用 `startedAt DESC` 排序，保证最近 Run 排在前面。
- `OrchestrationPanel` 在编排返回 Run ID 后加载 Run 详情，并展示最新 Run ID、状态、执行项数量和失败项数量。
- 最近 Run 列表加载后会同步加载第一条 Run 详情，确保已有失败 Run 也能在窗口详情页被复核。
- 失败 Run 摘要展示发布窗口/仓库/迭代上下文、失败步骤和失败原因，并提供进入 Run 详情的入口。
- `run` OpenSpec 新增“发布编排 Run 页面内复核”需求和失败 Run 场景。

## 验证

```bash
mvn -q -pl releasehub-bootstrap -am -Dtest=RunPagedApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/OrchestrationPanel.spec.ts
pnpm run typecheck
pnpm i18n:lint
git diff --check
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `RunPagedApiTest` 覆盖同一窗口下最新 Run 优先返回。
- `OrchestrationPanel.spec.ts` 覆盖编排请求作用域、编排后加载返回 Run、最新 Run 摘要、失败 Run 上下文和失败步骤展示。
- 前端 typecheck、i18n lint、路线图检查和静态扫描均通过；静态扫描报告：`.ai/reports/static-scan/20260523-010723/summary.md`，TopN 未发现代码问题。

## 结论

- SA-013 的 UI 侧执行后结果复核和失败 Run 观察缺口已收口。
- 本切片没有改变发布编排语义；真实 GitLab 成功编排继续由既有验收基线承担。
- 下一步按路线图推进 SA-003 组织资源移动治理。
