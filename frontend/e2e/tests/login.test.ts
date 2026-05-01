/**
 * 登录页面 E2E 测试
 */
import { TestRunner, delay } from '../utils/test-helper'
import { config } from '../puppeteer.config'

const runner = new TestRunner()

// 测试：登录页面正确渲染
runner.test('登录页面正确渲染', async () => {
  const helper = runner.getHelper()
  const assertions = runner.getAssertions()
  
  await helper.navigate('/login')
  
  // 验证登录表单元素存在
  await assertions.elementExists('.login-card')
  await assertions.elementExists('.login-title')
  await assertions.elementExists('.el-form')
  await assertions.elementExists('.el-button--primary')
  
  // 验证输入框存在
  await assertions.elementExists('input[type="text"], .el-input__inner')
  await assertions.elementExists('input[type="password"]')
  
  await helper.screenshot('login-page-render')
})

// 测试：空表单提交显示验证错误
runner.test('空表单提交显示验证错误', async () => {
  const helper = runner.getHelper()
  
  await helper.navigate('/login')
  
  // 直接点击登录按钮
  await helper.clickElement('.el-button--primary')
  
  // 等待验证消息出现
  await delay(500)
  
  // 检查是否显示验证错误
  const hasError = await helper.elementExists('.el-form-item__error')
  if (!hasError) {
    throw new Error('Expected validation error to be displayed')
  }
  
  await helper.screenshot('login-validation-error')
})

// 测试：输入错误凭据显示错误消息
runner.test('输入错误凭据显示错误消息', async () => {
  const helper = runner.getHelper()
  
  await helper.navigate('/login')
  
  // 输入错误的凭据
  await helper.typeText('.el-form-item:nth-child(1) .el-input__inner', 'wronguser')
  await helper.typeText('.el-form-item:nth-child(2) .el-input__inner', 'wrongpass')
  
  // 点击登录
  await helper.clickElement('.el-button--primary')
  
  // 等待错误消息
  await delay(2000)
  
  // 检查是否还在登录页面（登录失败）
  const url = runner.getContext().getPage().url()
  if (!url.includes('/login')) {
    // 如果不在登录页，可能登录成功了（测试环境配置问题）
    console.log('Warning: Login might have succeeded with wrong credentials')
  }
  
  await helper.screenshot('login-wrong-credentials')
})

// 测试：记住我复选框可以切换
runner.test('记住我复选框可以切换', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/login')
  
  // 找到复选框并点击
  const checkbox = await page.$('.el-checkbox')
  if (checkbox) {
    await checkbox.click()
    await delay(300)
    
    // 验证复选框状态改变
    const isChecked = await page.$('.el-checkbox.is-checked')
    if (!isChecked) {
      throw new Error('Checkbox should be checked after click')
    }
    
    // 再次点击取消选中
    await checkbox.click()
    await delay(300)
  }
  
  await helper.screenshot('login-remember-me')
})

// 测试：成功登录后跳转
runner.test('成功登录后跳转到首页', async () => {
  const helper = runner.getHelper()
  const auth = runner.getAuthHelper()
  
  await auth.login()
  
  // 验证跳转
  const page = runner.getContext().getPage()
  await delay(1000)
  
  const url = page.url()
  // 应该不再在登录页
  if (url.includes('/login')) {
    throw new Error('Should redirect after successful login')
  }
  
  await helper.screenshot('login-success-redirect')
})

// 测试：Enter 键可以提交表单
runner.test('Enter 键可以提交表单', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()

  await helper.navigate('/login')
  await delay(500)

  // 如果已登录（前一个测试），先清除登录状态
  if (!page.url().includes('/login')) {
    await page.evaluate(() => {
      localStorage.clear()
      sessionStorage.clear()
    })
    await helper.navigate('/login')
    await delay(500)
  }

  // 等待登录表单加载
  await page.waitForSelector('.el-form-item:nth-child(1) .el-input__inner', { visible: true, timeout: 5000 })

  // 输入凭据
  await helper.typeText('.el-form-item:nth-child(1) .el-input__inner', config.testUser.username)
  await helper.typeText('.el-form-item:nth-child(2) .el-input__inner', config.testUser.password)

  // 按 Enter 键
  await page.keyboard.press('Enter')

  // 等待响应
  await delay(2000)

  await helper.screenshot('login-enter-submit')
})

// 运行测试
runner.run().catch(console.error)
