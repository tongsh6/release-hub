# SA-003 受控空叶子分组移动

## 完整目标蓝图

### 最终行为

- 系统管理员可以调整组织树中尚未承载资源的末级分组位置。
- 已承载仓库、迭代、发布窗口的分组不得被移动，避免发布范围和历史证据失去可追溯性。
- 仍有子分组的分组不得被移动，避免一次隐式移动整棵组织子树。
- 已承载资源的目标父分组不得新增子分组，避免资源挂载在非叶子分组上的不变量被破坏。

### 产品边界

- 允许：未挂资源、无子分组的空叶子分组移动到未挂资源的父分组下，或移动为顶层分组。
- 拒绝：移动有子分组的分组。
- 拒绝：移动已被仓库、迭代或发布窗口引用的分组。
- 拒绝：移动到已被仓库、迭代或发布窗口引用的父分组下。
- 非目标：批量组织重构向导、跨 Git provider 仓库迁移、修改仓库 Git 地址或 token、重写迭代/发布窗口归属、绕过删除保护或发布计划锁定。

## Slice：后端权威约束与前端拒绝提示

- 蓝图归属：完整目标中的“受控空叶子分组移动”。
- 目标：把现有 `PUT /groups/{id}` 的 `parentCode` 变更从隐式自由移动收口为显式受控移动。
- 涉及层：Common 错误码、Application 业务规则、Frontend 错误提示、OpenSpec、矩阵和台账。
- 后续：SA-003 继续补页面级父级选择体验和外部 Playwright 旅程复核。

## 变更

- `GroupAppService.update` 在 `parentCode` 变化时执行移动治理校验。
- 新增 `GROUP_015/GROUP_016/GROUP_017`，分别表达“有子分组不可移动”“已挂资源不可移动”“目标父分组已挂资源不可作为新上级”。
- 保留纯重命名路径：未改变父级时，即便分组已挂资源，也仍可更新名称。
- 前端分组编辑弹窗对移动治理错误展示业务提示，不落入通用请求失败。
- `group` OpenSpec 新增受控分组移动需求和拒绝场景。

## 验证

```bash
mvn -pl releasehub-application -am -Dtest=GroupAppServiceValidationTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/utils/__tests__/groupMoveProtection.spec.ts src/views/group/__tests__/GroupDialog.spec.ts
pnpm run typecheck
pnpm i18n:lint
git diff --check
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 后端 `GroupAppServiceValidationTest`：12 PASS / 0 FAIL / 0 SKIP。
- 前端 Vitest：3 PASS / 0 FAIL / 0 SKIP。
- 前端 typecheck、i18n lint、路线图检查和静态扫描均通过；静态扫描报告：`.ai/reports/static-scan/20260523-012026/summary.md`。

## 结论

- SA-003 的资源移动治理已完成第一切片：后端不再允许自由改父级破坏叶子分组归属不变量。
- 当前切片没有引入批量资源迁移，也没有改变仓库、迭代、发布窗口、Run 的既有归属语义。
- 下一步继续补分组编辑的页面级父级选择体验和外部 Playwright 用户旅程复核。
