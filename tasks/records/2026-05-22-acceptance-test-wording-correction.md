# 验收测试口径描述修正

日期：2026-05-22

## 背景

- 项目文档和近期台账中存在把 route-level API stub、Playwright `--list`、E2E TypeScript 检查、后端/真实 GitLab 脚本证据写成“验收测试”或“验收通过”的描述。
- 正确口径应为：验收测试由外部 Playwright 驱动真实页面完成场景化用户旅程；API、数据库、GitLab 和脚本只能作为证据复核或环境 fixture。

## 修正范围

- `docs/reports/scenario-acceptance-matrix.md`
  - 增加“验收测试”的完整定义。
  - 将 route-stub Playwright 标注为 UI 回归，不计入验收通过。
  - 将 Playwright `--list` 和 E2E TypeScript 检查标注为可发现性/可编译检查。
  - 将真实 GitLab 脚本结论改为“证据复核通过”，避免替代页面旅程。
- `docs/superpowers/specs/2026-05-12-scenario-acceptance-design.md`
  - 将最终验收体系修正为外部 Playwright 场景验收 + 后端/数据/GitLab 证据复核。
- `frontend/e2e/README.md`
  - 增加 E2E/验收口径说明，明确 route stub、直接造数、`--list` 和 TypeScript 检查不算验收通过。
- 近期 SA-006/SA-007 台账
  - 将“UI E2E / 实跑 / 验收通过”等描述修正为“候选旅程”“UI 回归”或“真实页面验收待补”。

## 结论

- 后续只有外部 Playwright 驱动真实页面、连接真实后端并通过页面旅程完成业务操作或复核，才可写为场景化验收测试通过。
- route stub、API 造数、数据库造数、`--list`、TypeScript 检查、Vitest 和后端脚本证据都必须明确标注为回归检查、可运行性检查或证据复核。
