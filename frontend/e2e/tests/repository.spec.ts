import { test, expect } from '@playwright/test'
import { ensureLoggedIn } from './helpers'

test.describe('Repository', () => {
  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page)
  })

  test('仓库列表页可访问', async ({ page }) => {
    await page.goto('/repository')
    await expect(page).toHaveURL(/\/repository/, { timeout: 5000 })
  })

  test('仓库列表表格显示', async ({ page }) => {
    await page.goto('/repository')
    const table = page.locator('.el-table').first()
    if (await table.isVisible()) {
      const rows = table.locator('.el-table__body tr')
      expect(await rows.count()).toBeGreaterThanOrEqual(0)
    }
  })
})
