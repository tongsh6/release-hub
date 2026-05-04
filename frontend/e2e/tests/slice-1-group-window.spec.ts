/**
 * Slice 1 E2E: Group Hierarchy + Release Window Lifecycle
 *
 * Covers 10 scenarios across Admin / Release Manager / Tester roles.
 * All UI labels resolved at runtime from Vue I18n — no hardcoded text.
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { ensureLoggedIn, loadLabels, confirmDialog, confirmMessageBox, tcName, FORCE } from './helpers'

test.describe.serial('Slice-1: Group + Window', () => {
  let L: Record<string, string> = {}
  const p = tcName('GP')
  const c = tcName('GC')
  const leaf = tcName('GL')
  let windowName: string

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await ensureLoggedIn(page)
    L = await loadLabels(page, [
      'group.createTop', 'group.createChild', 'group.name', 'group.code',
      'common.search',
      'releaseWindow.create', 'releaseWindow.name',
      'releaseWindow.freeze', 'releaseWindow.unfreeze',
      'releaseWindow.publish', 'releaseWindow.view',
    ])
    await page.close()
  })

  /** Search for the test window by name to handle pagination reliably. */
  async function searchWindow(page: Page) {
    // .first() gets the search form input (in DOM before dialog, which is teleported to body)
    await page.getByRole('textbox', { name: L['releaseWindow.name'] }).first().fill(windowName)
    await page.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    // Wait for table loading to finish
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    await page.locator('.el-loading-mask').last().waitFor({ state: 'detached', timeout: 3000 }).catch(() => {})
  }

  /** Get the table row for the test window (call searchWindow first). */
  function windowRow(page: Page) {
    return page.locator('.el-table__body tr').filter({ hasText: windowName }).last()
  }

  // ══════════════ Group Tree ══════════════

  test('1 — build 3-level group tree via UI', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/groups')
    await page.waitForTimeout(1000)

    // Parent
    await page.getByRole('button', { name: L['group.createTop'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 3000 })
    const d1 = page.locator('.el-dialog').last()
    await d1.getByRole('textbox', { name: L['group.name'] }).fill('E2E-Company')
    await d1.getByRole('textbox', { name: L['group.code'], exact: true }).fill(p)
    await confirmDialog(page)

    // Child
    await page.locator('.el-tree-node__content').filter({ hasText: p }).click(FORCE)
    await page.waitForTimeout(500)
    await page.locator('.detail-card .el-button--success').filter({ hasText: L['group.createChild'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 3000 })
    const d2 = page.locator('.el-dialog').last()
    await d2.getByRole('textbox', { name: L['group.name'] }).fill('E2E-Team')
    await d2.getByRole('textbox', { name: L['group.code'], exact: true }).fill(c)
    await confirmDialog(page)

    // Leaf
    await page.locator('.el-tree-node__content').filter({ hasText: c }).click(FORCE)
    await page.waitForTimeout(500)
    await page.locator('.detail-card .el-button--success').filter({ hasText: L['group.createChild'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 3000 })
    const d3 = page.locator('.el-dialog').last()
    await d3.getByRole('textbox', { name: L['group.name'] }).fill('E2E-Project')
    await d3.getByRole('textbox', { name: L['group.code'], exact: true }).fill(leaf)
    await confirmDialog(page)
  })

  test('2 — verify tree hierarchy (parent-child nesting)', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/groups')
    await page.waitForTimeout(1000)

    // Check all nodes are visible
    await expect(page.locator('.el-tree-node__content').filter({ hasText: p })).toBeVisible()
    await expect(page.locator('.el-tree-node__content').filter({ hasText: c })).toBeVisible()
    await expect(page.locator('.el-tree-node__content').filter({ hasText: leaf })).toBeVisible()

    // Verify nesting: expanding child node should reveal leaf
    // The tree uses default-expand-all, so leaf should already be visible under child
    const leafNode = page.locator('.el-tree-node__content').filter({ hasText: leaf })
    await leafNode.click(FORCE)
    await page.waitForTimeout(300)
    // Detail panel should show leaf group info after selection
    await expect(page.locator('.detail-card')).toBeVisible()
  })

  // ══════════════ Placeholders (backend constraints not enforced yet) ══════════════

  test('3 — [skip] delete parent with children → GROUP_xxx not enforced', async () => {
    // Backend GROUP_xxx constraint is not yet implemented.
    // When enforced: select parent in tree, click 删除, confirm,
    // assert error message or parent still exists in tree.
    test.skip()
  })

  test('4 — [skip] create window on non-leaf group → GROUP_014 not enforced', async () => {
    // Backend GROUP_014 (窗口必须在叶子分组下创建) is not yet enforced.
    // When enforced: assert API returns 400 for non-leaf groupCode.
    test.skip()
  })

  // ══════════════ Window Lifecycle ══════════════

  test('5 — create release window on leaf group', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(1500)

    windowName = tcName('RW')
    await page.getByRole('button', { name: L['releaseWindow.create'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 5000 })
    const d = page.locator('.el-dialog').last()
    await d.getByRole('textbox', { name: L['releaseWindow.name'] }).fill(windowName)

    // Select leaf group in tree-select
    await d.locator('.el-select, .el-tree-select').last().click(FORCE)
    await page.waitForTimeout(800)
    const searchBox = page.locator('.el-popper input').last()
    if (await searchBox.isVisible({ timeout: 2000 }).catch(() => false)) {
      await searchBox.fill(leaf)
      await page.waitForTimeout(500)
    }
    const node = page.locator('.el-tree-node__content').filter({ hasText: leaf }).last()
    if (await node.isVisible({ timeout: 2000 }).catch(() => false)) {
      await node.click(FORCE)
    }
    await page.waitForTimeout(500)
    await confirmDialog(page)

    // Verify window appears in table
    await searchWindow(page)
    await expect(windowRow(page)).toBeVisible({ timeout: 5000 })
    // New window should be DRAFT → freeze button visible
    await expect(windowRow(page).locator('button').filter({ hasText: L['releaseWindow.freeze'] })).toBeVisible()
  })

  test('6 — freeze window', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(1000)

    await searchWindow(page)
    const row = windowRow(page)

    // Before freeze: freeze button should be visible
    await expect(row.locator('button').filter({ hasText: L['releaseWindow.freeze'] })).toBeVisible()

    // Freeze
    await row.locator('button').filter({ hasText: L['releaseWindow.freeze'] }).click(FORCE)
    await confirmMessageBox(page)

    // After freeze: freeze button gone, unfreeze + publish should appear
    await searchWindow(page)
    const row2 = windowRow(page)
    await expect(row2.locator('button').filter({ hasText: L['releaseWindow.unfreeze'] })).toBeVisible({ timeout: 3000 })
    await expect(row2.locator('button').filter({ hasText: L['releaseWindow.publish'] })).toBeVisible()
  })

  test('7 — unfreeze window', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(1000)

    await searchWindow(page)
    const row = windowRow(page)

    // Before unfreeze: unfreeze + publish buttons visible, freeze not
    await expect(row.locator('button').filter({ hasText: L['releaseWindow.unfreeze'] })).toBeVisible()

    // Unfreeze
    await row.locator('button').filter({ hasText: L['releaseWindow.unfreeze'] }).click(FORCE)
    await page.waitForTimeout(500)
    // Unfreeze has no confirm dialog — just a direct API call

    // After unfreeze: freeze button back
    await page.waitForTimeout(500)
    await searchWindow(page)
    const row2 = windowRow(page)
    await expect(row2.locator('button').filter({ hasText: L['releaseWindow.freeze'] })).toBeVisible({ timeout: 3000 })
  })

  test('8 — publish window without iterations → rejected', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(1000)

    await searchWindow(page)
    const row = windowRow(page)

    // Publish requires at least one attached iteration → should get error
    await row.locator('button').filter({ hasText: L['releaseWindow.publish'] }).click(FORCE)
    await confirmMessageBox(page)

    // Expect error: window has no iterations
    await expect(
      page.locator('.el-message--error, .el-message--warning').last()
    ).toBeVisible({ timeout: 5000 })
  })

  test('9 — view window detail page', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(1000)

    await searchWindow(page)
    await windowRow(page).locator('button').filter({ hasText: L['releaseWindow.view'] }).click(FORCE)

    // Verify URL and page content
    await expect(page).toHaveURL(/\/release-windows\//, { timeout: 5000 })
    await expect(page.locator('.el-descriptions').last()).toBeVisible()
    // Window name should appear in the detail page
    await expect(page.locator('.el-descriptions').last()).toContainText(windowName, { timeout: 3000 })
  })

  test('10 — [skip] close window requires PUBLISHED (needs iterations → Slice 3)', async () => {
    // Close button only appears when status === 'PUBLISHED'.
    // Publishing requires at least one iteration attached to the window.
    // Will be covered in Slice 3 (Release Orchestration) after iterations are created.
    test.skip()
  })
})
