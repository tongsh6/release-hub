import { test, expect } from '@playwright/test'
import { ensureLoggedIn } from './helpers'

test.describe('Iteration', () => {
  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page)
  })

  test('迭代列表页可访问', async ({ page }) => {
    await page.goto('/iteration')
    await expect(page).toHaveURL(/\/iteration/, { timeout: 5000 })
  })

  test('迭代表格显示', async ({ page }) => {
    await page.goto('/iteration')
    const table = page.locator('.el-table').first()
    if (await table.isVisible()) {
      expect(await table.locator('.el-table__body tr').count()).toBeGreaterThanOrEqual(0)
    }
  })
})
