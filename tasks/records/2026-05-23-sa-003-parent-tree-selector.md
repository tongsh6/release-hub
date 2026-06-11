# SA-003 分组父级树选择体验

## 完整目标蓝图

### 最终行为

- 系统管理员在编辑分组时，通过组织树选择新的父分组，而不是手输父级编码。
- 当前分组自身不能被选择为父分组。
- 清空父级选择表示移动为顶层分组。
- 页面在移动入口明确提示治理边界：只有未挂资源且无子分组的空叶子分组可以移动。
- 真实页面旅程能证明空叶子分组可以通过父级树选择器移动，并能在组织树中复核新的父子关系。

### 非目标

- 不做批量组织重构向导。
- 不做资源归属批量迁移。
- 不绕过后端受控移动约束。
- 不改变仓库、迭代、发布窗口、Run 的既有归属语义。

## Slice：父级树选择器和外部旅程复核

- 蓝图归属：SA-003 组织资源移动治理中的“页面级父级选择体验”。
- 目标：把分组编辑弹窗的父级编码自由输入升级为组织树选择器，并用外部 Playwright 验证真实页面移动旅程。
- 涉及层：Frontend、E2E、OpenSpec、矩阵、台账和路线图。
- 后续：SA-003 继续补 code 自动生成当前测试证据复核。

## 变更

- `GroupTreeSelect` 增加 `disabledCodes`，支持在非叶子可选场景禁选指定分组。
- `GroupDialog` 的父级字段改用 `GroupTreeSelect`，设置 `leafOnly=false`，并在编辑时禁选当前分组自身。
- 分组编辑弹窗新增移动边界提示，避免用户把父级选择误解成任意资源迁移。
- Slice-1 Playwright 新增“空叶子分组通过父级树选择器移动”旅程。
- Slice-1 解除挂载按钮点击改为直接触发目标按钮，降低折叠面板标题内按钮的点击不稳定性。

## 验证

```bash
pnpm exec vitest run src/components/common/__tests__/GroupTreeSelect.spec.ts src/views/group/__tests__/GroupDialog.spec.ts
pnpm run typecheck
pnpm i18n:lint
pnpm exec playwright test e2e/tests/slice-1-group-window.spec.ts -g "move empty leaf group"
pnpm run test:e2e:slice-1
git diff --check
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 前端 Vitest：3 PASS / 0 FAIL / 0 SKIP。
- 定向外部 Playwright：1 PASS / 0 FAIL / 0 SKIP。
- Slice-1 全量：12 PASS / 0 FAIL / 0 SKIP。
- 前端 typecheck、i18n lint、路线图检查和静态扫描均通过；静态扫描报告：`.ai/reports/static-scan/20260523-130022/summary.md`。

备注：

- 曾有一次全量 Slice-1 在既有解除挂载确认框等待处失败；新增 SA-003 移动旅程当时已通过。随后将解除挂载按钮触发方式改为直接点击目标按钮，并复跑 Slice-1 全量通过。
- in-app Browser 检查尝试失败，原因是当前环境没有可用 `iab` 实例；本切片以真实 Chromium Playwright 作为页面旅程证据。

## 结论

- SA-003 的页面级父级选择体验已收口。
- 当前仍保留 code 自动生成当前测试证据复核；完成后可判断 SA-003 是否出队。
