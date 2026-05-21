/**
 * SA-006: BranchRule management user journey.
 *
 * Covers the UI path for creating a scoped rule, validating required scope
 * fields, testing a branch name, and toggling rule status.
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { ensureLoggedIn, loadLabels, tcName, FORCE } from './helpers.js'

test.describe.serial('SA-006: Branch rule management', () => {
  let L: Record<string, string> = {}
  const ruleName = tcName('BR')
  const projectId = `project-${Date.now()}`

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await ensureLoggedIn(page)
    L = await loadLabels(page, [
      'common.search', 'common.save', 'common.delete', 'common.confirm',
      'branchRule.name', 'branchRule.pattern', 'branchRule.create',
      'branchRule.scopeProject', 'branchRule.scopeProjectId',
      'branchRule.scopeProjectRequired', 'branchRule.test',
      'branchRule.testBranchName', 'branchRule.testRun',
      'branchRule.testMatch', 'branchRule.enableSuccess',
      'branchRule.disableSuccess'
    ])
    await page.close()
  })

  async function searchRule(page: Page) {
    await page.getByRole('textbox', { name: L['branchRule.name'] }).first().fill(ruleName)
    await page.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    await page.locator('.el-loading-mask').last().waitFor({ state: 'detached', timeout: 3000 }).catch(() => {})
  }

  function ruleRow(page: Page) {
    return page.locator('.el-table__body tr').filter({ hasText: ruleName }).last()
  }

  test('1. create project-scoped rule with scope validation', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/branch-rules')
    await page.waitForTimeout(800)

    await page.getByRole('button', { name: L['branchRule.create'] }).click(FORCE)
    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByRole('textbox', { name: L['branchRule.name'] }).fill(ruleName)
    await dialog.getByRole('textbox', { name: L['branchRule.pattern'] }).fill('feature/{key}')
    await dialog.locator('.el-radio').filter({ hasText: L['branchRule.scopeProject'] }).click(FORCE)

    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog.locator('.el-form-item__error')).toContainText(L['branchRule.scopeProjectRequired'])

    await dialog.getByRole('textbox', { name: L['branchRule.scopeProjectId'] }).fill(projectId)
    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog).toBeHidden({ timeout: 10000 })

    await searchRule(page)
    await expect(ruleRow(page)).toBeVisible({ timeout: 5000 })
    await expect(ruleRow(page)).toContainText(projectId)
  })

  test('2. test matching and toggle rule status', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/branch-rules')
    await page.waitForTimeout(800)
    await searchRule(page)
    const row = ruleRow(page)
    await expect(row).toBeVisible({ timeout: 5000 })

    await row.locator('button').filter({ hasText: L['branchRule.test'] }).click(FORCE)
    const testDialog = page.locator('.el-dialog:visible').last()
    await expect(testDialog).toBeVisible({ timeout: 5000 })
    await testDialog.getByRole('textbox', { name: L['branchRule.testBranchName'] }).fill('feature/SA-006')
    await testDialog.locator('button').filter({ hasText: L['branchRule.testRun'] }).click(FORCE)
    await expect(testDialog.locator('.el-alert')).toContainText(L['branchRule.testMatch'])
    await testDialog.locator('.el-dialog__headerbtn').click(FORCE)
    await expect(testDialog).toBeHidden({ timeout: 5000 })

    await row.locator('.el-switch').click(FORCE)
    await expect(page.locator('.el-message').last()).toContainText(L['branchRule.disableSuccess'], { timeout: 5000 })
    await searchRule(page)
    await ruleRow(page).locator('.el-switch').click(FORCE)
    await expect(page.locator('.el-message').last()).toContainText(L['branchRule.enableSuccess'], { timeout: 5000 })
  })

  test('3. cleanup created rule', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/branch-rules')
    await page.waitForTimeout(800)
    await searchRule(page)
    const row = ruleRow(page)
    await expect(row).toBeVisible({ timeout: 5000 })

    await row.locator('button').filter({ hasText: L['common.delete'] }).click(FORCE)
    const confirmButton = page.locator('.el-popconfirm .el-button--primary').last()
    await expect(confirmButton).toBeVisible({ timeout: 5000 })
    await confirmButton.click(FORCE)
    await page.waitForTimeout(800)
    await searchRule(page)
    await expect(ruleRow(page)).toHaveCount(0)
  })
})
