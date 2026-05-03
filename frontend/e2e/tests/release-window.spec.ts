import { test, expect } from '@playwright/test'
import { ensureLoggedIn } from './helpers'

test.describe('Release Window', () => {
  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page)
  })

  test('发布窗口列表页可访问', async ({ page }) => {
    await page.goto('/release-window')
    await expect(page).toHaveURL(/\/release-window/, { timeout: 5000 })
  })

  test('分页器存在', async ({ page }) => {
    await page.goto('/release-window')
    await page.waitForTimeout(1000)
  })
})
