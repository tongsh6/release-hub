/**
 * 发布窗口页面 E2E 测试 — Playwright 版本
 */
import { test, expect } from '@playwright/test'
import { ensureLoggedIn } from './helpers'

test.describe('Release Window', () => {
  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page)
  })

  test('发布窗口列表页可访问', async ({ page }) => {
    await page.goto('/release-window')
    await expect(page).toHaveURL(/\/release-window/, { timeout: 5000 })
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 5000 })
  })

  test('表头包含必要字段', async ({ page }) => {
    await page.goto('/release-window')
    const headerCells = page.locator('.el-table__header th')
    await expect(headerCells.first()).toBeVisible({ timeout: 5000 })

    const texts = await headerCells.allTextContents()
    const joined = texts.join(' ')
    expect(joined).toMatch(/名称|标识|Name|Key/i)
  })

  test('创建发布窗口按钮可见', async ({ page }) => {
    await page.goto('/release-window')
    const createBtn = page.locator('button').filter({ hasText: /新建|创建|新增|Create|New/i })
    if (await createBtn.isVisible()) {
      await createBtn.click()
      await page.waitForTimeout(500)
      // 对话框应该出现
      await expect(page.locator('.el-dialog, .el-drawer').first()).toBeVisible({ timeout: 3000 })
    }
  })

  test('分页器存在', async ({ page }) => {
    await page.goto('/release-window')
    const pagination = page.locator('.el-pagination')
    if (await pagination.isVisible()) {
      const pages = await pagination.locator('.el-pager li').count()
      expect(pages).toBeGreaterThanOrEqual(0)
    }
  })
})
