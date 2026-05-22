/**
 * SA-007: VersionPolicy management user journey.
 *
 * Covers real-page scoped policy creation, validation, editing, and cleanup
 * for GLOBAL, PROJECT, and SUB_PROJECT scopes. Labels are resolved from Vue
 * I18n at runtime so the test follows the rendered product UI.
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { ensureLoggedIn, loadLabels, tcName, FORCE } from './helpers.js'

type ScopeLevel = 'GLOBAL' | 'PROJECT' | 'SUB_PROJECT'

test.describe.serial('SA-007: Version policy management', () => {
  let L: Record<string, string> = {}
  const globalPolicyName = tcName('VP-GLOBAL')
  const projectPolicyName = tcName('VP-PROJECT')
  const subPolicyName = tcName('VP-SUB')
  const projectId = `project-${Date.now()}`
  const subProjectId = `repo-${Date.now()}`

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await ensureLoggedIn(page)
    L = await loadLabels(page, [
      'common.search', 'common.save', 'common.delete', 'common.edit',
      'versionPolicy.name', 'versionPolicy.create',
      'versionPolicy.scheme', 'versionPolicy.bumpRule',
      'versionPolicy.scopeGlobal', 'versionPolicy.scopeProject', 'versionPolicy.scopeSubProject',
      'versionPolicy.scopeProjectId', 'versionPolicy.scopeSubProjectId',
      'versionPolicy.scopeProjectRequired'
    ])
    await page.close()
  })

  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/version-policies')
    await page.waitForTimeout(800)
  })

  async function searchPolicy(page: Page, name: string) {
    await page.getByRole('textbox', { name: L['versionPolicy.name'] }).first().fill(name)
    await page.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    await page.locator('.el-loading-mask').last().waitFor({ state: 'detached', timeout: 3000 }).catch(() => {})
  }

  function policyRow(page: Page, name: string) {
    return page.locator('.el-table__body tr').filter({ hasText: name }).last()
  }

  function exactText(text: string) {
    return new RegExp(`^${text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`)
  }

  function scopeRadio(dialog: ReturnType<Page['locator']>, label: string) {
    return dialog.locator('.el-radio').filter({ hasText: exactText(label) })
  }

  async function fillScope(dialog: ReturnType<Page['locator']>, scope: ScopeLevel) {
    const scopeLabels: Record<ScopeLevel, string> = {
      GLOBAL: L['versionPolicy.scopeGlobal'],
      PROJECT: L['versionPolicy.scopeProject'],
      SUB_PROJECT: L['versionPolicy.scopeSubProject']
    }
    await scopeRadio(dialog, scopeLabels[scope]).click(FORCE)
    if (scope !== 'GLOBAL') {
      await dialog.getByRole('textbox', { name: L['versionPolicy.scopeProjectId'], exact: true }).fill(projectId)
    }
    if (scope === 'SUB_PROJECT') {
      await dialog.getByRole('textbox', { name: L['versionPolicy.scopeSubProjectId'], exact: true }).fill(subProjectId)
    }
  }

  async function createPolicy(page: Page, name: string, scope: ScopeLevel) {
    await page.getByRole('button', { name: L['versionPolicy.create'] }).click(FORCE)
    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible({ timeout: 5000 })

    await dialog.getByRole('textbox', { name: L['versionPolicy.name'] }).fill(name)
    await fillScope(dialog, scope)

    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog).toBeHidden({ timeout: 10000 })
    await searchPolicy(page, name)
    await expect(policyRow(page, name)).toBeVisible({ timeout: 5000 })
  }

  async function editPolicy(page: Page, currentName: string, nextName: string, scope: ScopeLevel) {
    await searchPolicy(page, currentName)
    const row = policyRow(page, currentName)
    await expect(row).toBeVisible({ timeout: 5000 })
    await row.locator('button').filter({ hasText: L['common.edit'] }).click(FORCE)

    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByRole('textbox', { name: L['versionPolicy.name'] }).fill(nextName)
    await fillScope(dialog, scope)
    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog).toBeHidden({ timeout: 10000 })

    await searchPolicy(page, nextName)
    await expect(policyRow(page, nextName)).toBeVisible({ timeout: 5000 })
  }

  async function deletePolicy(page: Page, name: string) {
    await searchPolicy(page, name)
    const row = policyRow(page, name)
    await expect(row).toBeVisible({ timeout: 5000 })
    await row.locator('button').filter({ hasText: L['common.delete'] }).click(FORCE)
    const confirmButton = page.locator('.el-popconfirm .el-button--primary').last()
    await expect(confirmButton).toBeVisible({ timeout: 5000 })
    await confirmButton.click(FORCE)
    await page.waitForTimeout(800)

    await searchPolicy(page, name)
    await expect(policyRow(page, name)).toHaveCount(0)
  }

  test('1. create, edit, and delete global policy', async ({ page }) => {
    const editedName = `${globalPolicyName}-edited`
    await createPolicy(page, globalPolicyName, 'GLOBAL')
    await expect(policyRow(page, globalPolicyName)).toContainText(L['versionPolicy.scopeGlobal'])

    await editPolicy(page, globalPolicyName, editedName, 'GLOBAL')
    await expect(policyRow(page, editedName)).toContainText(L['versionPolicy.scopeGlobal'])

    await deletePolicy(page, editedName)
  })

  test('2. create project-scoped policy with scope validation, then edit and delete it', async ({ page }) => {
    const editedName = `${projectPolicyName}-edited`
    await page.getByRole('button', { name: L['versionPolicy.create'] }).click(FORCE)
    const dialog = page.locator('.el-dialog:visible').last()
    await expect(dialog).toBeVisible({ timeout: 5000 })

    await dialog.getByRole('textbox', { name: L['versionPolicy.name'] }).fill(projectPolicyName)
    await scopeRadio(dialog, L['versionPolicy.scopeProject']).click(FORCE)

    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog.locator('.el-form-item__error')).toContainText(L['versionPolicy.scopeProjectRequired'])

    await dialog.getByRole('textbox', { name: L['versionPolicy.scopeProjectId'], exact: true }).fill(projectId)
    await dialog.locator('button').filter({ hasText: L['common.save'] }).click(FORCE)
    await expect(dialog).toBeHidden({ timeout: 10000 })
    await searchPolicy(page, projectPolicyName)
    await expect(policyRow(page, projectPolicyName)).toBeVisible({ timeout: 5000 })
    await expect(policyRow(page, projectPolicyName)).toContainText(projectId)

    await editPolicy(page, projectPolicyName, editedName, 'PROJECT')
    await expect(policyRow(page, editedName)).toContainText(projectId)

    await deletePolicy(page, editedName)
  })

  test('3. create, edit, and delete sub-project policy', async ({ page }) => {
    const editedName = `${subPolicyName}-edited`
    await createPolicy(page, subPolicyName, 'SUB_PROJECT')
    await expect(policyRow(page, subPolicyName)).toContainText(projectId)
    await expect(policyRow(page, subPolicyName)).toContainText(subProjectId)

    await editPolicy(page, subPolicyName, editedName, 'SUB_PROJECT')
    await expect(policyRow(page, editedName)).toContainText(projectId)
    await expect(policyRow(page, editedName)).toContainText(subProjectId)

    await deletePolicy(page, editedName)
  })
})
