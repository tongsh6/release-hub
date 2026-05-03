/**
 * Shared E2E test helpers — Playwright version
 */
import { expect } from '@playwright/test'
import type { Page } from '@playwright/test'

const TEST_USER = { username: 'admin', password: 'admin' }

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
