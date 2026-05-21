/**
 * Slice 1 E2E: Group Hierarchy + Release Window Lifecycle
 *
 * Covers 11 scenarios across Admin / Release Manager / Tester roles.
 * All UI labels resolved at runtime from Vue I18n — no hardcoded text.
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { ensureLoggedIn, loadLabels, confirmDialog, confirmMessageBox, tcName, FORCE } from './helpers.js'

test.describe.serial('Slice-1: Group + Window', () => {
  let L: Record<string, string> = {}
  const p = tcName('GP')
  const c = tcName('GC')
  const leaf = tcName('GL')
  const selectableLeaf = '001'
  let windowName: string

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await ensureLoggedIn(page)
    L = await loadLabels(page, [
      'group.createTop', 'group.createChild', 'group.name', 'group.code', 'group.parentCode', 'group.hasChildren',
      'common.search', 'common.delete', 'common.keyword', 'common.confirm', 'common.cancel',
      'iteration.new', 'iteration.columns.name',
      'repository.addOrSync',
      'releaseWindow.create', 'releaseWindow.name',
      'releaseWindow.freeze', 'releaseWindow.unfreeze',
      'releaseWindow.publish', 'releaseWindow.view',
      'releaseWindow.attachIterations', 'releaseWindow.close', 'releaseWindow.statusText.CLOSED',
      'releaseWindow.noIterations', 'common.remove',
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

  async function selectGroupFromTree(page: Page, groupCode: string) {
    const node = page.locator('.el-tree-node__content').filter({ hasText: groupCode }).last()
    await expect(node).toBeVisible({ timeout: 5000 })
    await node.click({ position: { x: 24, y: 12 } })
    await expect(page.locator('.detail-card')).toContainText(groupCode, { timeout: 5000 })
  }

  async function selectGroupInDialog(page: Page, dialog: ReturnType<Page['locator']>, groupCode: string) {
    await dialog.locator('.el-tree-select, .el-select').first().click(FORCE)
    await page.waitForTimeout(800)
    const option = page.getByRole('option').filter({ hasText: groupCode }).first()
    await expect(option).toBeVisible({ timeout: 5000 })
    await option.click(FORCE)
    await page.waitForTimeout(500)
  }

  async function assertNonLeafGroupDisabled(page: Page, dialog: ReturnType<Page['locator']>, groupCode: string) {
    const groupSelect = dialog.locator('.el-tree-select, .el-select').first()
    await groupSelect.click(FORCE)
    await page.waitForTimeout(800)
    const popper = page.locator('.el-popper:visible').last()
    const nonLeafNode = popper.getByRole('treeitem')
      .filter({ hasText: groupCode })
      .filter({ hasText: L['group.hasChildren'] })
      .first()
    await expect(nonLeafNode).toBeVisible({ timeout: 5000 })
    await expect(nonLeafNode).toHaveAttribute('aria-disabled', 'true')
    await nonLeafNode.click(FORCE)
    await expect(groupSelect).not.toContainText(groupCode)
    await page.keyboard.press('Escape')
    await expect(dialog).toBeVisible()
  }

  async function closeVisibleDialog(page: Page) {
    const dialog = page.locator('.el-dialog:visible').last()
    await dialog.locator('.el-dialog__headerbtn').click(FORCE)
    await expect(dialog).toBeHidden({ timeout: 5000 })
  }

  async function searchIteration(page: Page, iterationName: string) {
    await page.getByRole('textbox', { name: L['common.keyword'] }).first().fill(iterationName)
    await page.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    await page.locator('.el-loading-mask').last().waitFor({ state: 'detached', timeout: 3000 }).catch(() => {})
  }

  async function createIteration(page: Page, iterationName: string) {
    await page.goto('/iterations')
    await page.waitForTimeout(1000)

    await page.getByRole('button', { name: L['iteration.new'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 5000 })
    const iterationDialog = page.locator('.el-dialog:visible').last()
    await iterationDialog.getByRole('textbox', { name: L['iteration.columns.name'] }).fill(iterationName)
    await selectGroupInDialog(page, iterationDialog, selectableLeaf)
    await confirmDialog(page)

    await searchIteration(page, iterationName)
    const iterationRow = page.locator('.el-table__body tr').filter({ hasText: iterationName }).last()
    await expect(iterationRow).toBeVisible({ timeout: 5000 })
    return (await iterationRow.locator('td').first().innerText()).trim()
  }

  async function attachIterationToWindow(page: Page, iterationKey: string) {
    await page.goto('/release-windows')
    await page.waitForTimeout(1000)
    await searchWindow(page)

    await windowRow(page).locator('button').filter({ hasText: L['releaseWindow.attachIterations'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 5000 })
    const attachDialog = page.locator('.el-dialog').last()
    await attachDialog.getByPlaceholder(L['common.keyword']).fill(iterationKey)
    await attachDialog.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    const attachRow = attachDialog.locator('.el-table__body tr').filter({ hasText: iterationKey }).last()
    await expect(attachRow).toBeVisible({ timeout: 5000 })
    await attachRow.locator('input.el-checkbox__original').evaluate((el: HTMLElement) => el.click())
    await expect(attachRow.locator('.el-checkbox__input')).toHaveClass(/is-checked/)
    await attachDialog.locator('.el-button--primary').filter({ hasText: L['common.confirm'] }).click(FORCE)
    await expect(attachDialog).toBeHidden({ timeout: 10000 })
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
    await selectGroupFromTree(page, p)
    await page.locator('.detail-card .el-button--success').filter({ hasText: L['group.createChild'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 3000 })
    const d2 = page.locator('.el-dialog').last()
    await expect(d2.getByRole('textbox', { name: L['group.parentCode'] })).toHaveValue(p)
    await d2.getByRole('textbox', { name: L['group.name'] }).fill('E2E-Team')
    await d2.getByRole('textbox', { name: L['group.code'], exact: true }).fill(c)
    await confirmDialog(page)

    // Leaf
    await selectGroupFromTree(page, c)
    await page.locator('.detail-card .el-button--success').filter({ hasText: L['group.createChild'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 3000 })
    const d3 = page.locator('.el-dialog').last()
    await expect(d3.getByRole('textbox', { name: L['group.parentCode'] })).toHaveValue(c)
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

    const parentNode = page.getByRole('treeitem').filter({ hasText: p }).last()
    const childNode = parentNode.getByRole('treeitem').filter({ hasText: c }).last()
    const leafNode = childNode.getByRole('treeitem').filter({ hasText: leaf }).last()
    await expect(parentNode).toContainText(c)
    await expect(childNode).toContainText(leaf)
    await leafNode.locator('.el-tree-node__content').click(FORCE)
    await page.waitForTimeout(300)
    // Detail panel should show leaf group info after selection
    await expect(page.locator('.detail-card')).toBeVisible()
    await expect(page.locator('.detail-card')).toContainText(leaf)
  })

  // ══════════════ Group + Window Constraints ══════════════

  test('3 — reject deleting parent group with children', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/groups')
    await page.waitForTimeout(1000)

    await selectGroupFromTree(page, p)

    await page.locator('.detail-card .el-button--danger').filter({ hasText: L['common.delete'] }).click(FORCE)
    await confirmMessageBox(page)

    await page.waitForTimeout(500)
    await expect(page.locator('.el-tree-node__content').filter({ hasText: p })).toBeVisible()
    await expect(page.getByRole('treeitem').filter({ hasText: p }).last()).toContainText(c)
  })

  test('4 — resource creation only allows leaf groups', async ({ page }) => {
    await ensureLoggedIn(page)

    await page.goto('/repositories')
    await page.waitForTimeout(1000)
    await page.getByRole('button', { name: L['repository.addOrSync'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 5000 })
    await assertNonLeafGroupDisabled(page, page.locator('.el-dialog:visible').last(), p)
    await closeVisibleDialog(page)

    await page.goto('/iterations')
    await page.waitForTimeout(1000)
    await page.getByRole('button', { name: L['iteration.new'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 5000 })
    await assertNonLeafGroupDisabled(page, page.locator('.el-dialog:visible').last(), p)
    await closeVisibleDialog(page)

    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(1000)

    await page.getByRole('button', { name: L['releaseWindow.create'] }).click(FORCE)
    await expect(page.locator('.el-dialog').last()).toBeVisible({ timeout: 5000 })
    const d = page.locator('.el-dialog:visible').last()
    await d.getByRole('textbox', { name: L['releaseWindow.name'] }).fill(tcName('RW-NONLEAF'))
    await assertNonLeafGroupDisabled(page, d, p)
    await closeVisibleDialog(page)
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

    await selectGroupInDialog(page, d, selectableLeaf)
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

  test('10 — detach iteration from window detail via UI', async ({ page }) => {
    const iterationName = tcName('IT-DETACH')

    await ensureLoggedIn(page)
    const iterationKey = await createIteration(page, iterationName)
    await attachIterationToWindow(page, iterationKey)

    await searchWindow(page)
    await windowRow(page).locator('button').filter({ hasText: L['releaseWindow.view'] }).click(FORCE)
    await expect(page).toHaveURL(/\/release-windows\//, { timeout: 5000 })
    await expect(page.locator('.iterations-list')).toContainText(iterationKey, { timeout: 10000 })

    const detachResponse = page.waitForResponse(response =>
      response.request().method() === 'POST' &&
      response.url().includes('/api/v1/release-windows/') &&
      response.url().endsWith('/detach')
    )
    await page
      .locator('.el-collapse-item')
      .filter({ hasText: iterationKey })
      .locator('button.detach-iteration-button')
      .filter({ hasText: L['common.remove'] })
      .click(FORCE)
    await confirmMessageBox(page)
    expect((await detachResponse).ok()).toBeTruthy()
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    await page.locator('.el-loading-mask').last().waitFor({ state: 'detached', timeout: 3000 }).catch(() => {})

    await expect(page.locator('.empty-tip')).toContainText(L['releaseWindow.noIterations'], { timeout: 10000 })
    await expect(page.locator('.iterations-list')).toHaveCount(0)
    await expect(page.locator('.release-plan-panel')).toHaveCount(0)
    await expect(page.getByRole('button', { name: L['releaseWindow.attachIterations'] })).toBeVisible()
  })

  test('11 — close published window after attaching iteration', async ({ page }) => {
    const iterationName = tcName('IT-CLOSE')

    await ensureLoggedIn(page)
    await attachIterationToWindow(page, await createIteration(page, iterationName))

    await searchWindow(page)
    await windowRow(page).locator('button').filter({ hasText: L['releaseWindow.publish'] }).click(FORCE)
    await confirmMessageBox(page)
    await searchWindow(page)

    await expect(windowRow(page).locator('button').filter({ hasText: L['releaseWindow.close'] })).toBeVisible({ timeout: 5000 })
    await windowRow(page).locator('button').filter({ hasText: L['releaseWindow.close'] }).click(FORCE)
    await confirmMessageBox(page)

    await searchWindow(page)
    await expect(windowRow(page)).toContainText(L['releaseWindow.statusText.CLOSED'], { timeout: 5000 })
    await expect(windowRow(page).locator('button').filter({ hasText: L['releaseWindow.attachIterations'] })).toHaveCount(0)

    await windowRow(page).locator('button').filter({ hasText: L['releaseWindow.view'] }).click(FORCE)
    await expect(page).toHaveURL(/\/release-windows\//, { timeout: 5000 })
    await expect(page.getByRole('button', { name: L['releaseWindow.attachIterations'] })).toHaveCount(0)
  })
})
