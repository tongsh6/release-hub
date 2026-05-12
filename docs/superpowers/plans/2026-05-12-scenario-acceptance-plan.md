# Scenario Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the calibrated scenario acceptance matrix into traceable automation across the real GitLab acceptance script and the frontend Playwright observation path.

**Architecture:** Keep `scripts/acceptance/run-acceptance.sh` as the real GitLab acceptance entrypoint. Add SA identifiers and stronger assertions without introducing a new shell framework. Keep frontend additions focused on read-only observation so they remain stable against persisted local data.

**Tech Stack:** Bash, curl, Python JSON snippets, Spring Boot REST APIs, GitLab CE local container, Vue 3 frontend, Playwright.

---

## File Map

- Modify `scripts/acceptance/run-acceptance.sh`: add SA labels, build three-level group data, harden clean golden path assertions, bind version update to the clean window.
- Modify or add `frontend/e2e/tests/slice-2-full-flow.spec.ts`: extend existing smoke coverage with a tester observation path for release windows and run details.
- Modify `docs/reports/scenario-acceptance-matrix.md`: update coverage statuses after each automation slice lands.
- Modify `docs/reports/acceptance-v0.1.11-real-gitlab.md` only after a fresh real acceptance run produces new evidence.

## Task DAG

```text
Task 1 -> Task 2 -> Task 3 -> Task 4
                         └── Task 5
Task 6 follows any completed automation slice.
```

### Task 1: Add SA Trace Labels To Acceptance Script

**Files:**
- Modify: `scripts/acceptance/run-acceptance.sh`
- Modify: `docs/reports/scenario-acceptance-matrix.md`

- [ ] **Step 1: Add SA IDs to section headings**

Update existing `h2` headings so output can be mapped back to the matrix:

```bash
h2 "SA-001/SA-004: GitLab Settings 配置与持久化"
h2 "SA-002: 存量数据质量审计"
h2 "SA-003/SA-005/SA-008/SA-009: 三层分组与发布准备数据"
h2 "SA-010: Attach 迭代 & GitLab release 分支创建"
h2 "SA-011: 冲突检测"
h2 "SA-012/SA-013: 干净窗口黄金路径"
h2 "SA-014: 版本更新 & GitLab commit 验证"
h2 "SA-015: Run 执行详情"
```

- [ ] **Step 2: Run shell syntax check**

Run:

```bash
bash -n scripts/acceptance/run-acceptance.sh
```

Expected: no output and exit code 0.

- [ ] **Step 3: Update matrix coverage note**

In `docs/reports/scenario-acceptance-matrix.md`, keep statuses unchanged but add a short note under section four:

```markdown
脚本输出应包含 SA 编号，验收报告引用脚本日志时必须保留这些编号。
```

- [ ] **Step 4: Commit**

```bash
git add scripts/acceptance/run-acceptance.sh docs/reports/scenario-acceptance-matrix.md
git commit -m "test: add scenario IDs to acceptance script"
```

### Task 2: Build Three-Level Group Fixture In Acceptance Script

**Files:**
- Modify: `scripts/acceptance/run-acceptance.sh`
- Modify: `docs/reports/scenario-acceptance-matrix.md`

- [ ] **Step 1: Replace single acceptance group with customer/line/brand fixture**

Replace the current single `验收分组` setup with a helper that creates or reuses:

```text
验收-客户A
└── 验收-业务线X
    └── 验收-品牌Y
```

The helper must expose:

```bash
CUSTOMER_CODE=""
BUSINESS_LINE_CODE=""
BRAND_CODE=""
GROUP_CODE="$BRAND_CODE"
```

All later repo/window/iteration creation should continue to use `GROUP_CODE`, now pointing to the brand leaf.

- [ ] **Step 2: Verify group tree through API**

After fixture creation, call:

```bash
curl -s "$BACKEND/api/v1/groups/tree" -H "$AUTH"
```

Assert that all three group names or codes exist in the returned tree.

- [ ] **Step 3: Add non-leaf creation probes when API behavior is stable**

If repository/window/iteration APIs currently reject non-leaf group codes, add explicit checks for customer and business-line codes. If they do not reject non-leaf codes yet, record the gap as a WARN instead of failing the full script.

- [ ] **Step 4: Run shell syntax check**

Run:

```bash
bash -n scripts/acceptance/run-acceptance.sh
```

Expected: no output and exit code 0.

- [ ] **Step 5: Commit**

```bash
git add scripts/acceptance/run-acceptance.sh docs/reports/scenario-acceptance-matrix.md
git commit -m "test: use three-level group fixture in acceptance"
```

### Task 3: Harden Clean Golden Path Assertions

**Files:**
- Modify: `scripts/acceptance/run-acceptance.sh`
- Modify: `docs/reports/scenario-acceptance-matrix.md`

- [ ] **Step 1: Make SA-013 clean path status a hard assertion**

In clean window orchestration, keep the existing `wait_for_run` call and fail when final status is not `SUCCESS`:

```bash
[ "$CLEAN_FINAL_STATUS" = "SUCCESS" ] && ok "SA-013 干净窗口编排 SUCCESS" || no "SA-013 干净窗口编排状态: $CLEAN_FINAL_STATUS"
```

- [ ] **Step 2: Require RunItem count greater than zero**

Parse clean run detail and fail if `items` is 0:

```bash
CLEAN_ITEM_COUNT=$(echo "$CLEAN_RUN_DETAIL" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',{}).get('items',[])))")
[ "$CLEAN_ITEM_COUNT" -gt 0 ] && ok "SA-013 RunItem > 0: $CLEAN_ITEM_COUNT" || no "SA-013 RunItem 为 0"
```

- [ ] **Step 3: Require at least one completed RunStep**

Count completed steps:

```bash
CLEAN_COMPLETED_STEPS=$(echo "$CLEAN_RUN_DETAIL" | python3 -c "
import sys,json
items=json.load(sys.stdin).get('data',{}).get('items',[])
print(sum(1 for item in items for step in item.get('steps',[]) if step.get('result') in ('SUCCESS','COMPLETED')))
")
[ "$CLEAN_COMPLETED_STEPS" -gt 0 ] && ok "SA-013 RunStep completed > 0: $CLEAN_COMPLETED_STEPS" || no "SA-013 无完成的 RunStep"
```

- [ ] **Step 4: Run shell syntax check**

Run:

```bash
bash -n scripts/acceptance/run-acceptance.sh
```

Expected: no output and exit code 0.

- [ ] **Step 5: Commit**

```bash
git add scripts/acceptance/run-acceptance.sh docs/reports/scenario-acceptance-matrix.md
git commit -m "test: harden clean release golden path"
```

### Task 4: Bind Version Update To Clean Window

**Files:**
- Modify: `scripts/acceptance/run-acceptance.sh`
- Modify: `docs/reports/scenario-acceptance-matrix.md`

- [ ] **Step 1: Preserve clean window identifiers for SA-014**

Ensure `CLEAN_WINDOW_ID`, `CLEAN_ITER_KEY`, and `CLEAN_RUN_ID` stay available after scene 5.2.

- [ ] **Step 2: Prefer clean window in version update**

Before executing version update, choose:

```bash
VU_WINDOW_ID="${CLEAN_WINDOW_ID:-$WINDOW_ID}"
VU_REPO_ID="$R1"
```

Use `VU_WINDOW_ID` for validate/update endpoints and GitLab release branch lookup.

- [ ] **Step 3: Fail SA-014 when clean window exists but version update fails**

If `CLEAN_WINDOW_ID` is set, a failed version update is a `no`, not a `skip`:

```bash
if [ -n "$CLEAN_WINDOW_ID" ]; then
    no "SA-014 版本更新失败: $ERR"
else
    skip "SA-014 版本更新未执行: $ERR"
fi
```

- [ ] **Step 4: Keep GitLab commit verification**

Continue using `verify_gitlab_commit`, but look up `windowKey` from `VU_WINDOW_ID`.

- [ ] **Step 5: Run shell syntax check**

Run:

```bash
bash -n scripts/acceptance/run-acceptance.sh
```

Expected: no output and exit code 0.

- [ ] **Step 6: Commit**

```bash
git add scripts/acceptance/run-acceptance.sh docs/reports/scenario-acceptance-matrix.md
git commit -m "test: bind version update acceptance to clean window"
```

### Task 5: Add Frontend Tester Observation Path

**Files:**
- Modify: `frontend/e2e/tests/slice-2-full-flow.spec.ts`
- Modify: `docs/reports/scenario-acceptance-matrix.md`

- [ ] **Step 1: Add Run detail navigation test**

Add a Playwright test that opens `/runs`, ensures at least one row exists, opens the first detail/action button when available, and asserts a detail surface is visible.

Use stable broad selectors already present in the file:

```ts
test('9. Tester can inspect run evidence', async ({ page }) => {
  await ensureLoggedIn(page)
  await page.goto('/runs')
  await page.waitForTimeout(1000)
  await expect(page.locator('.el-table, .el-table__body-wrapper').first()).toBeVisible({ timeout: 5000 })

  const rows = page.locator('.el-table__body-wrapper tbody tr')
  expect(await rows.count()).toBeGreaterThan(0)

  const firstRow = rows.first()
  const action = firstRow.locator('button').last()
  if (await action.isVisible({ timeout: 2000 }).catch(() => false)) {
    await action.click(FORCE)
    await page.waitForTimeout(800)
  }

  await expect(page.locator('.el-drawer, .el-dialog, .el-descriptions, .el-card, .el-main').first()).toBeVisible({ timeout: 5000 })
})
```

- [ ] **Step 2: Add release window detail observation test**

Add a test that opens `/release-windows`, ensures a row exists, clicks view/detail when available, and asserts a detail surface appears.

- [ ] **Step 3: Run frontend E2E target when local frontend is available**

Run:

```bash
cd frontend && pnpm test:e2e -- slice-2-full-flow.spec.ts
```

Expected: the modified spec passes against a running frontend/backend. If services are not running, record that verification was not run.

- [ ] **Step 4: Commit**

```bash
git add frontend/e2e/tests/slice-2-full-flow.spec.ts docs/reports/scenario-acceptance-matrix.md
git commit -m "test: add frontend acceptance observation path"
```

### Task 6: Refresh Acceptance Evidence

**Files:**
- Modify: `docs/reports/scenario-acceptance-matrix.md`
- Modify: `docs/reports/acceptance-v0.1.11-real-gitlab.md` or create a new report for the next release tag

- [ ] **Step 1: Run acceptance script in the prepared local environment**

Run:

```bash
bash scripts/acceptance/run-acceptance.sh
```

Expected: no FAIL for P0 scenarios. Any WARN/SKIP must map to a matrix gap.

- [ ] **Step 2: Capture summary**

Record PASS/FAIL/SKIP counts and the SA IDs that failed or skipped.

- [ ] **Step 3: Update matrix statuses**

Change only statuses backed by evidence:

```markdown
| SA-013 | ... | 已覆盖 |
| SA-014 | ... | 已覆盖 |
```

- [ ] **Step 4: Commit**

```bash
git add docs/reports/scenario-acceptance-matrix.md docs/reports/acceptance-v0.1.11-real-gitlab.md
git commit -m "docs: refresh scenario acceptance evidence"
```

## Self-Review Checklist

- [ ] Every automation task maps to at least one SA row.
- [ ] No task changes business semantics without explicit matrix backing.
- [ ] Shell checks run before committing script edits.
- [ ] Frontend E2E changes remain observation-focused and do not depend on brittle fixed historical names.
- [ ] Matrix statuses only move to `已覆盖` after a real verification run.
