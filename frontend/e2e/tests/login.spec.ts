/**
 * 登录页面 E2E 测试 — Playwright 版本
 */
import { test, expect } from '@playwright/test'

const TEST_USER = { username: 'admin', password: 'admin' }

test.describe('Login Page', () => {

  test('登录页面正确渲染', async ({ page }) => {
    await page.goto('/login')

    await expect(page.locator('.login-card')).toBeVisible()
    await expect(page.locator('.login-title')).toBeVisible()
    await expect(page.locator('.el-form')).toBeVisible()
    await expect(page.locator('.el-button--primary')).toBeVisible()
    await expect(page.locator('input[type="text"], .el-input__inner').first()).toBeVisible()
    await expect(page.locator('input[type="password"]')).toBeVisible()
  })

  test('空表单提交显示验证错误', async ({ page }) => {
    await page.goto('/login')
    await page.locator('.el-button--primary').click()

    await expect(page.locator('.el-form-item__error').first()).toBeVisible()
  })

  test('输入错误凭据显示错误消息', async ({ page }) => {
    await page.goto('/login')
    await page.locator('.el-form-item:nth-child(1) .el-input__inner').fill('wronguser')
    await page.locator('.el-form-item:nth-child(2) .el-input__inner').fill('wrongpass')
    await page.locator('.el-button--primary').click()

    // 应该留在登录页或显示错误
    await expect(page).not.toHaveURL(/\/$/, { timeout: 3000 })
  })

  test('记住我复选框可以切换', async ({ page }) => {
    await page.goto('/login')
    const checkbox = page.locator('.el-checkbox')

    await checkbox.click()
    await expect(page.locator('.el-checkbox.is-checked')).toBeVisible()

    await checkbox.click()
    await expect(page.locator('.el-checkbox.is-checked')).not.toBeVisible()
  })

  test('成功登录后跳转到首页', async ({ page }) => {
    await page.goto('/login')
    await page.locator('.el-form-item:nth-child(1) .el-input__inner').fill(TEST_USER.username)
    await page.locator('.el-form-item:nth-child(2) .el-input__inner').fill(TEST_USER.password)
    await page.locator('.el-button--primary').click()

    // 等待跳转
    await expect(page).not.toHaveURL(/\/login/, { timeout: 5000 })
  })

  test('Enter 键可以提交表单', async ({ page }) => {
    await page.goto('/login')
    await page.locator('.el-form-item:nth-child(1) .el-input__inner').fill(TEST_USER.username)
    await page.locator('.el-form-item:nth-child(2) .el-input__inner').fill(TEST_USER.password)
    await page.keyboard.press('Enter')

    // 等待响应
    await page.waitForTimeout(2000)
  })
})
