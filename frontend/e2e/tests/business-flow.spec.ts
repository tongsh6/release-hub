import { test, expect } from '@playwright/test'

test.describe('Business Flow', () => {
  test('完整业务流：登录 → 核心页面导航', async ({ page }) => {
    // 登录
    await page.goto('/login')
    await page.locator('.el-form-item:nth-child(1) .el-input__inner').fill('admin')
    await page.locator('.el-form-item:nth-child(2) .el-input__inner').fill('admin')
    await page.locator('.el-button--primary').click()
    await expect(page).not.toHaveURL(/\/login/, { timeout: 5000 })

    // 导航验证
    const pages = ['/release-window', '/repository', '/iteration', '/version-policy']
    for (const path of pages) {
      await page.goto(path)
      await expect(page).toHaveURL(new RegExp(path), { timeout: 5000 })
    }
  })
})
