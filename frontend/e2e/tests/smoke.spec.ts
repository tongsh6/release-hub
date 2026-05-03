import { test, expect } from '@playwright/test'
import { ensureLoggedIn } from './helpers'

test.describe('Smoke Test', () => {
  test('完整业务流程：登录 → 浏览 → 登出', async ({ page }) => {
    await page.goto('/')
    await page.waitForTimeout(500)

    // 如果在登录页，先登录
    if (page.url().includes('/login')) {
      await page.locator('.el-form-item:nth-child(1) .el-input__inner').fill('admin')
      await page.locator('.el-form-item:nth-child(2) .el-input__inner').fill('admin')
      await page.locator('.el-button--primary').click()
      await expect(page).not.toHaveURL(/\/login/, { timeout: 5000 })
    }

    // 访问核心页面
    await page.goto('/release-window')
    await expect(page).toHaveURL(/\/release-window/, { timeout: 5000 })

    await page.goto('/repository')
    await expect(page).toHaveURL(/\/repository/, { timeout: 5000 })

    await page.goto('/iteration')
    await expect(page).toHaveURL(/\/iteration/, { timeout: 5000 })

    // 登出
    const logoutBtn = page.locator('[class*="logout"], [class*="user-menu"]')
    if (await logoutBtn.isVisible()) {
      await logoutBtn.click()
      await page.waitForTimeout(500)
    }
  })
})
