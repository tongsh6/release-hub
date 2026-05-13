/**
 * Shared E2E test helpers — Playwright version
 */
import { expect } from '@playwright/test'
import type { Page } from '@playwright/test'

const TEST_USER = { username: 'admin', password: 'admin' }

export const FORCE = { force: true } as const

/** Ensure logged in via UI, from any starting page. */
export async function ensureLoggedIn(page: Page) {
  await page.goto('/')
  await page.waitForTimeout(500)
  if (page.url().includes('/login')) {
    await page.locator('.el-form-item:nth-child(1) .el-input__inner').fill(TEST_USER.username)
    await page.locator('.el-form-item:nth-child(2) .el-input__inner').fill(TEST_USER.password)
    await page.locator('.el-button--primary').click()
    await expect(page).not.toHaveURL(/\/login/, { timeout: 5000 })
  }
}

/**
 * Click the primary button in the currently visible Element Plus dialog,
 * wait for it to be destroyed, then wait for overlay animation to finish.
 */
export async function confirmDialog(page: Page) {
  await page.locator('.el-dialog .el-button--primary').last().click(FORCE)
  await page.locator('.el-dialog').last().waitFor({ state: 'detached', timeout: 10000 }).catch(() => {})
  await page.waitForTimeout(300)
}

/**
 * Click the primary button in an ElMessageBox (confirm/alert popup),
 * then wait for it to dismiss.
 */
export async function confirmMessageBox(page: Page) {
  const confirmButton = page.locator('.el-message-box .el-button--primary').last()
  await expect(confirmButton).toBeVisible({ timeout: 10000 })
  await confirmButton.click(FORCE)
  await page.waitForTimeout(500)
}

/**
 * Translate an i18n key to the current locale's text by calling Vue's $t function.
 * The app must be loaded (any page) before calling this.
 */
export async function t(page: Page, key: string): Promise<string> {
  return page.evaluate((k) => {
    const app = (document.querySelector('#app') as any)?.__vue_app__
    if (!app) throw new Error('Vue app not found — page must be loaded first')
    return app.config.globalProperties.$t(k)
  }, key)
}

/**
 * Pre-fetch all needed i18n labels for a spec. Returns an object with resolved strings.
 * Call once in beforeAll after ensureLoggedIn.
 */
export async function loadLabels(page: Page, keys: string[]): Promise<Record<string, string>> {
  return page.evaluate((k) => {
    const app = (document.querySelector('#app') as any)?.__vue_app__
    if (!app) return {}
    const $t = app.config.globalProperties.$t
    const result: Record<string, string> = {}
    for (const key of k) result[key] = $t(key)
    return result
  }, keys)
}

/** Generate a timestamp-prefixed unique name for test data isolation. */
export function tcName(prefix: string) {
  return `TC-${prefix}-${Date.now()}`
}
