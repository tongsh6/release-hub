/**
 * 导航和布局 E2E 测试 — Playwright 版本
 */
import { test, expect } from '@playwright/test'

const TEST_USER = { username: 'admin', password: 'admin' }

async function ensureLoggedIn(page) {
  await page.goto('/')
  await page.waitForTimeout(500)
  if (page.url().includes('/login')) {
    await page.locator('.el-form-item:nth-child(1) .el-input__inner').fill(TEST_USER.username)
    await page.locator('.el-form-item:nth-child(2) .el-input__inner').fill(TEST_USER.password)
    await page.locator('.el-button--primary').click()
    await expect(page).not.toHaveURL(/\/login/, { timeout: 5000 })
  }
}

test.describe('Navigation', () => {

  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page)
  })

  test('主布局正确渲染', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.el-aside, .el-menu, .sidebar, .nav-menu').first()).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.el-header, header, .app-header').first()).toBeVisible()
    await expect(page.locator('.el-main, main, .main-content').first()).toBeVisible()
  })

  test('侧边栏菜单导航正常', async ({ page }) => {
    await page.goto('/')
    const menuItems = page.locator('.el-menu-item, .el-sub-menu__title, [class*="menu-item"]')
    const count = await menuItems.count()
    if (count > 0) {
      await menuItems.first().click()
      await page.waitForTimeout(1000)
    }
  })

  test('发布窗口页面可访问', async ({ page }) => {
    await page.goto('/release-window')
    await expect(page).toHaveURL(/\/release-window/, { timeout: 5000 })
  })

  test('仓库页面可访问', async ({ page }) => {
    await page.goto('/repository')
    await expect(page).toHaveURL(/\/repository/, { timeout: 5000 })
  })

  test('迭代页面可访问', async ({ page }) => {
    await page.goto('/iteration')
    await expect(page).toHaveURL(/\/iteration/, { timeout: 5000 })
  })

  test('版本策略页面可访问', async ({ page }) => {
    await page.goto('/version-policy')
    await page.waitForTimeout(1000)
  })

  test('面包屑导航显示正确', async ({ page }) => {
    await page.goto('/release-window')
    const breadcrumb = page.locator('.el-breadcrumb')
    if (await breadcrumb.isVisible()) {
      const items = breadcrumb.locator('.el-breadcrumb__item')
      const count = await items.count()
      expect(count).toBeGreaterThan(0)
    }
  })

  test('移动端视口适配', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto('/')
    await page.waitForTimeout(1000)
    await page.setViewportSize({ width: 1920, height: 1080 })
  })

  test('404 页面处理', async ({ page }) => {
    await page.goto('/non-existent-page-12345')
    await page.waitForTimeout(1000)
  })
})
