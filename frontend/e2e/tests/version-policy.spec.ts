/**
 * SA-007: VersionPolicy management user journey.
 *
 * Covers the UI path for scoped policy creation, scope validation, editing,
 * and cleanup. Labels are resolved from Vue I18n at runtime.
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { ensureLoggedIn, loadLabels, tcName, FORCE } from './helpers.js'

test.describe.serial('SA-007: Version policy management', () => {
  let L: Record<string, string> = {}
  const policyName = tcName('VP')
  const updatedPolicyName = `${policyName}-updated`
  const projectId = `project-${Date.now()}`
  const subProjectId = `repo-${Date.now()}`

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await ensureLoggedIn(page)
    L = await loadLabels(page, [
      'common.search', 'common.save', 'common.delete', 'common.edit',
      'versionPolicy.name', 'versionPolicy.create',
      'versionPolicy.scheme', 'versionPolicy.bumpRule',
      'versionPolicy.scopeProject', 'versionPolicy.scopeSubProject',
      'versionPolicy.scopeProjectId', 'versionPolicy.scopeSubProjectId',
      'versionPolicy.scopeProjectRequired'
    ])
    await page.close()
  })

  async function searchPolicy(page: Page, name = policyName) {
    await page.getByRole('textbox', { name: L['versionPolicy.name'] }).first().fill(name)
    await page.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    await page.locator('.el-loading-mask').last().waitFor({ state: 'detached', timeout: 3000 }).catch(() => {})
  }

  function policyRow(page: Page, name = policyName) {
    return page.locator('.el-table__body tr').filter({ hasText: name }).last()
  }

  async function selectVisibleOption(page: Page, dialog: ReturnType<Page['locator']>, label: string) {
    await dialog.locator('.el-select').last().click(FORCE)
    const option = page.getByRole('option').filter({ hasText: label }).last()
    await expect(option).toBeVisible({ timeout: 5000 })
    await option.click(FORCE)
  }

  test('1. create project-scoped policy with scope validation', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/version-policies')
    await page.waitForTimeout(800)

    await page.getByRole('button', { name: L['versionPolicy.create'] }).click(FORCE)
    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible({ timeout: 5000 })

    await dialog.getByRole('textbox', { name: L['versionPolicy.name'] }).fill(policyName)
    await selectVisibleOption(page, dialog, 'SEMVER')
    await selectVisibleOption(page, dialog, 'PATCH')
    await dialog.locator('.el-radio').filter({ hasText: L['versionPolicy.scopeProject'] }).click(FORCE)

    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog.locator('.el-form-item__error')).toContainText(L['versionPolicy.scopeProjectRequired'])

    await dialog.getByRole('textbox', { name: L['versionPolicy.scopeProjectId'] }).fill(projectId)
    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog).toBeHidden({ timeout: 10000 })

    await searchPolicy(page)
    await expect(policyRow(page)).toBeVisible({ timeout: 5000 })
    await expect(policyRow(page)).toContainText(projectId)
  })

  test('2. edit policy to sub-project scope', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/version-policies')
    await page.waitForTimeout(800)
    await searchPolicy(page)

    const row = policyRow(page)
    await expect(row).toBeVisible({ timeout: 5000 })
    await row.locator('button').filter({ hasText: L['common.edit'] }).click(FORCE)

    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByRole('textbox', { name: L['versionPolicy.name'] }).fill(updatedPolicyName)
    await selectVisibleOption(page, dialog, 'MINOR')
    await dialog.locator('.el-radio').filter({ hasText: L['versionPolicy.scopeSubProject'] }).click(FORCE)
    await dialog.getByRole('textbox', { name: L['versionPolicy.scopeSubProjectId'] }).fill(subProjectId)
    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog).toBeHidden({ timeout: 10000 })

    await searchPolicy(page, updatedPolicyName)
    await expect(policyRow(page, updatedPolicyName)).toBeVisible({ timeout: 5000 })
    await expect(policyRow(page, updatedPolicyName)).toContainText(projectId)
    await expect(policyRow(page, updatedPolicyName)).toContainText(subProjectId)
    await expect(policyRow(page, updatedPolicyName)).toContainText('MINOR')
  })

  test('3. cleanup edited policy', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/version-policies')
    await page.waitForTimeout(800)
    await searchPolicy(page, updatedPolicyName)

    const row = policyRow(page, updatedPolicyName)
    await expect(row).toBeVisible({ timeout: 5000 })
    await row.locator('button').filter({ hasText: L['common.delete'] }).click(FORCE)
    const confirmButton = page.locator('.el-popconfirm .el-button--primary').last()
    await expect(confirmButton).toBeVisible({ timeout: 5000 })
    await confirmButton.click(FORCE)
    await page.waitForTimeout(800)

    await searchPolicy(page, updatedPolicyName)
    await expect(policyRow(page, updatedPolicyName)).toHaveCount(0)
  })
})
