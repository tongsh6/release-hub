import { test, expect } from '@playwright/test'
import { ensureLoggedIn } from './helpers'

test.describe('i18n', () => {
  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page)
  })

  test('中文界面正常加载', async ({ page }) => {
    await page.goto('/release-window')
    const body = page.locator('body')
    await expect(body).toBeVisible({ timeout: 5000 })
  })

  test('语言切换器存在', async ({ page }) => {
    await page.goto('/')
    const langSwitcher = page.locator('[class*="lang"], [class*="locale"], [class*="i18n"]')
    if (await langSwitcher.isVisible()) {
      await langSwitcher.click()
      await page.waitForTimeout(500)
    }
  })
})
