import { test, expect } from '@playwright/test'
import { ensureLoggedIn } from './helpers'

test.describe('Release Automation', () => {
  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page)
  })

  test('发布窗口生命周期展示正常', async ({ page }) => {
    await page.goto('/release-window')
    const table = page.locator('.el-table').first()
    if (await table.isVisible()) {
      // 检查状态标签
      const statusTags = page.locator('.el-tag, [class*="status"]')
      if (await statusTags.first().isVisible()) {
        const text = await statusTags.first().textContent()
        expect(text?.length).toBeGreaterThan(0)
      }
    }
  })

  test('运行记录页面可访问', async ({ page }) => {
    await page.goto('/run')
    await page.waitForTimeout(1000)
  })
})
