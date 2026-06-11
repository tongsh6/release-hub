# 2026-05-23 SA-003 code 自动生成当前测试证据

## 背景

SA-003 的组织资源移动治理、父级树选择体验、资源创建叶子分组约束和删除保护已经具备自动化证据。矩阵仍保留的可执行缺口是 code 自动生成当前测试证据复核，需要确认实现规则、后端业务约束和前端真实页面表现一致。

## 范围

- 明确分组 code 自动生成的当前规则：顶层分组按三位数字递增；子分组以父分组 code 为前缀并追加三位序号。
- 后端应用层覆盖 code 缺省、空白、同级已有数字 code 和非数字自定义 code 的组合。
- 前端 Slice-1 外部 Playwright 覆盖管理员在页面创建顶层/子分组时留空 code，并从组织树复核生成结果。

## 非目标

- 不改变现有 code 生成算法。
- 不做批量组织重构或资源迁移向导。
- 不改变资源只能挂载叶子分组、分组删除保护和受控空叶子分组移动约束。

## 改动

- `GroupAppServiceValidationTest` 新增 code 自动生成回归：
  - 顶层分组 code 缺省或空白时生成 `001`、`002`。
  - 子分组 code 缺省或空白时生成 `父code + 三位序号`。
  - 已有同级三位数字 code 驱动下一号，非数字自定义 code 不参与递增序列。
- `slice-1-group-window.spec.ts` 新增真实页面旅程：
  - 创建顶层分组时保持 code 输入为空，提交后从组织树读取生成 code。
  - 在该顶层分组下创建子分组时保持 code 输入为空，提交后复核子分组 code 以父 code 为前缀。
- `docs/openspec/specs/group/spec.md` 将 code 自动生成从泛化唯一值描述修正为当前实现的层级递增规则。
- `docs/reports/scenario-acceptance-matrix.md`、`docs/project-ledger.md` 和 `docs/execution-roadmap.md` 同步 SA-003 出队与下一 HEAD。

## 验证

```bash
mvn -pl releasehub-application -am -Dtest=GroupAppServiceValidationTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec playwright test e2e/tests/slice-1-group-window.spec.ts -g "auto-generated codes"
pnpm run test:e2e:slice-1
pnpm run typecheck
pnpm i18n:lint
git diff --check
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `GroupAppServiceValidationTest`：15 PASS / 0 FAIL / 0 SKIP。
- code 自动生成定向外部 Playwright：1 PASS / 0 FAIL / 0 SKIP。
- Slice-1 全量外部 Playwright：13 PASS / 0 FAIL / 0 SKIP。
- 前端 typecheck、i18n lint、路线图检查和最终静态扫描均通过。
- 静态扫描报告：`.ai/reports/static-scan/20260523-133505/summary.md`。

## 结论

SA-003 已具备三层分组、资源叶子归属、删除保护、受控空叶子分组移动、父级组织树选择体验和 code 自动生成当前测试证据。当前可转入后续保持回归；路线图下一 HEAD 转向 SA-016 发布报告制品包归档。
