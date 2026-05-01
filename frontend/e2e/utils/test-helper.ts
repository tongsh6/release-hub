import puppeteer, { Browser, Page } from 'puppeteer'
import { config } from '../puppeteer.config'
import * as fs from 'fs'
import * as path from 'path'

/**
 * 测试上下文 - 管理浏览器和页面实例
 */
export class TestContext {
  browser: Browser | null = null
  page: Page | null = null
  
  /**
   * 初始化浏览器
   */
  async setup(): Promise<Page> {
    this.browser = await puppeteer.launch({
      headless: config.browser.headless,
      slowMo: config.browser.slowMo,
      devtools: config.browser.devtools,
      executablePath: config.browser.executablePath,
      args: config.browser.args
    })
    
    this.page = await this.browser.newPage()
    await this.page.setViewport(config.viewport)
    
    // 设置默认超时
    this.page.setDefaultTimeout(config.timeout.action)
    this.page.setDefaultNavigationTimeout(config.timeout.navigation)
    
    return this.page
  }
  
  /**
   * 清理资源
   */
  async teardown(): Promise<void> {
    if (this.page) {
      await this.page.close()
      this.page = null
    }
    if (this.browser) {
      await this.browser.close()
      this.browser = null
    }
  }
  
  /**
   * 获取当前页面
   */
  getPage(): Page {
    if (!this.page) {
      throw new Error('Page not initialized. Call setup() first.')
    }
    return this.page
  }
}

/**
 * 页面操作帮助类
 */
export class PageHelper {
  constructor(private page: Page) {}
  
  /**
   * 导航到指定路径
   */
  async navigate(path: string): Promise<void> {
    const url = `${config.baseUrl}${path}`
    await this.page.goto(url, { waitUntil: 'networkidle0' })
  }
  
  /**
   * 等待元素出现并点击
   */
  async clickElement(selector: string): Promise<void> {
    await this.page.waitForSelector(selector, { visible: true })
    await this.page.click(selector)
  }
  
  /**
   * 在输入框中输入文本
   */
  async typeText(selector: string, text: string, options?: { clear?: boolean }): Promise<void> {
    await this.page.waitForSelector(selector, { visible: true })
    
    if (options?.clear) {
      await this.page.click(selector, { clickCount: 3 })
      await this.page.keyboard.press('Backspace')
    }
    
    await this.page.type(selector, text)
  }
  
  /**
   * 清空并输入文本
   */
  async clearAndType(selector: string, text: string): Promise<void> {
    await this.typeText(selector, text, { clear: true })
  }
  
  /**
   * 等待文本出现
   */
  async waitForText(text: string, timeout?: number): Promise<void> {
    await this.page.waitForFunction(
      (searchText: string) => {
        return document.body.innerText.includes(searchText)
      },
      { timeout: timeout || config.timeout.assertion },
      text
    )
  }
  
  /**
   * 检查元素是否存在
   */
  async elementExists(selector: string): Promise<boolean> {
    try {
      await this.page.waitForSelector(selector, { timeout: 3000 })
      return true
    } catch {
      return false
    }
  }
  
  /**
   * 获取元素文本内容
   */
  async getTextContent(selector: string): Promise<string> {
    await this.page.waitForSelector(selector)
    const element = await this.page.$(selector)
    if (!element) return ''
    return await this.page.evaluate(el => el.textContent || '', element)
  }
  
  /**
   * 获取输入框的值
   */
  async getInputValue(selector: string): Promise<string> {
    await this.page.waitForSelector(selector)
    return await this.page.$eval(selector, (el: Element) => (el as HTMLInputElement).value)
  }
  
  /**
   * 等待加载完成 (Element Plus loading)
   */
  async waitForLoading(): Promise<void> {
    // 等待 loading 出现然后消失
    try {
      await this.page.waitForSelector('.el-loading-mask', { timeout: 1000 })
      await this.page.waitForSelector('.el-loading-mask', { hidden: true, timeout: 30000 })
    } catch {
      // loading 可能太快没捕获到，忽略
    }
  }
  
  /**
   * 等待表格加载完成
   */
  async waitForTableData(): Promise<void> {
    await this.waitForLoading()
    // 等待表格行出现
    await this.page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 10000 })
  }
  
  /**
   * 截图
   */
  async screenshot(name: string): Promise<void> {
    if (!config.screenshot.enabled) return
    
    const dir = config.screenshot.path
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true })
    }
    
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
    const filename = `${name}-${timestamp}.png`
    
    await this.page.screenshot({
      path: path.join(dir, filename),
      fullPage: config.screenshot.fullPage
    })
    
    console.log(`📸 Screenshot saved: ${filename}`)
  }
  
  /**
   * 等待网络请求完成
   */
  async waitForApiResponse(urlPattern: string | RegExp): Promise<void> {
    await this.page.waitForResponse(
      response => {
        const url = response.url()
        if (typeof urlPattern === 'string') {
          return url.includes(urlPattern)
        }
        return urlPattern.test(url)
      },
      { timeout: config.timeout.action }
    )
  }
  
  /**
   * 选择下拉框选项 (Element Plus Select)
   */
  async selectOption(selector: string, optionText: string): Promise<void> {
    // 点击下拉框
    await this.clickElement(selector)
    // 等待下拉选项出现
    await this.page.waitForSelector('.el-select-dropdown__item', { visible: true })
    // 点击匹配的选项
    const options = await this.page.$$('.el-select-dropdown__item')
    for (const option of options) {
      const text = await this.page.evaluate(el => el.textContent, option)
      if (text?.includes(optionText)) {
        await option.click()
        break
      }
    }
  }
  
  /**
   * 等待 Element Plus Message 提示
   */
  async waitForMessage(type: 'success' | 'error' | 'warning' | 'info'): Promise<string> {
    const selector = `.el-message--${type}`
    await this.page.waitForSelector(selector, { visible: true })
    const message = await this.getTextContent(`${selector} .el-message__content`)
    return message
  }
  
  /**
   * 确认对话框
   */
  async confirmDialog(): Promise<void> {
    await this.page.waitForSelector('.el-message-box', { visible: true })
    await this.clickElement('.el-message-box__btns .el-button--primary')
    await this.page.waitForSelector('.el-message-box', { hidden: true })
  }
  
  /**
   * 取消对话框
   */
  async cancelDialog(): Promise<void> {
    await this.page.waitForSelector('.el-message-box', { visible: true })
    await this.clickElement('.el-message-box__btns .el-button:not(.el-button--primary)')
    await this.page.waitForSelector('.el-message-box', { hidden: true })
  }

  /**
   * 获取当前语言
   */
  async getCurrentLanguage(): Promise<'zh-CN' | 'en-US'> {
    // 检查页面上的语言标识元素
    const langText = await this.page.evaluate(() => {
      // 尝试从语言切换器获取
      const langSwitcher = document.querySelector('.lang-switcher, .language-switch, [data-lang]')
      if (langSwitcher) {
        const text = langSwitcher.textContent || ''
        if (text.includes('中文') || text.includes('Chinese')) return 'zh-CN'
        if (text.includes('English') || text.includes('英文')) return 'en-US'
      }
      // 从 HTML lang 属性获取
      const htmlLang = document.documentElement.lang
      if (htmlLang.includes('en')) return 'en-US'
      // 默认检查常见中文文本
      const bodyText = document.body.innerText
      if (bodyText.includes('仪表盘') || bodyText.includes('登录') || bodyText.includes('查询')) return 'zh-CN'
      return 'en-US'
    })
    return langText as 'zh-CN' | 'en-US'
  }

  /**
   * 切换语言
   */
  async switchLanguage(targetLang: 'zh-CN' | 'en-US'): Promise<void> {
    // 查找语言切换器
    const langSwitcher = await this.page.$('.el-dropdown, [class*="lang"], [class*="language"]')
    
    if (langSwitcher) {
      await langSwitcher.click()
      await new Promise(resolve => setTimeout(resolve, 300))
      
      // 查找对应语言的选项
      const targetText = targetLang === 'zh-CN' ? '中文' : 'English'
      const options = await this.page.$$('.el-dropdown-menu__item, .el-select-dropdown__item')
      
      for (const option of options) {
        const text = await this.page.evaluate(el => el.textContent, option)
        if (text?.includes(targetText)) {
          await option.click()
          break
        }
      }
      
      await new Promise(resolve => setTimeout(resolve, 500))
    } else {
      console.log('Language switcher not found')
    }
  }

  /**
   * 验证文本存在（多语言）
   */
  async verifyTextExists(zhText: string, enText: string): Promise<{ found: boolean, lang: string, text: string }> {
    const bodyText = await this.page.evaluate(() => document.body.innerText)
    
    if (bodyText.includes(zhText)) {
      return { found: true, lang: 'zh-CN', text: zhText }
    }
    if (bodyText.includes(enText)) {
      return { found: true, lang: 'en-US', text: enText }
    }
    
    return { found: false, lang: 'unknown', text: '' }
  }

  /**
   * 获取所有表头文本（用于国际化验证）
   */
  async getTableHeaders(): Promise<string[]> {
    const headers = await this.page.$$('.el-table__header th')
    const headerTexts: string[] = []
    
    for (const header of headers) {
      const text = await this.page.evaluate(el => el.textContent?.trim(), header)
      if (text) {
        headerTexts.push(text)
      }
    }
    
    return headerTexts
  }

  /**
   * 获取所有按钮文本（用于国际化验证）
   */
  async getButtonTexts(): Promise<string[]> {
    const buttons = await this.page.$$('.el-button')
    const buttonTexts: string[] = []
    
    for (const button of buttons) {
      const text = await this.page.evaluate(el => el.textContent?.trim(), button)
      if (text) {
        buttonTexts.push(text)
      }
    }
    
    return buttonTexts
  }
}

/**
 * 登录帮助类
 */
export class AuthHelper {
  constructor(private helper: PageHelper) {}
  
  /**
   * 执行登录
   */
  async login(username?: string, password?: string): Promise<void> {
    const user = username || config.testUser.username
    const pass = password || config.testUser.password
    
    await this.helper.navigate('/login')
    
    // 等待登录表单加载
    await this.helper.page['waitForSelector']('.login-card', { visible: true })
    
    // 输入用户名
    await this.helper.typeText('.el-form-item:nth-child(1) .el-input__inner', user)
    
    // 输入密码
    await this.helper.typeText('.el-form-item:nth-child(2) .el-input__inner', pass)
    
    // 点击登录按钮
    await this.helper.clickElement('.el-button--primary')
    
    // 等待跳转完成
    await this.helper.page['waitForNavigation']({ waitUntil: 'networkidle0' })
  }
  
  /**
   * 检查是否已登录
   */
  async isLoggedIn(): Promise<boolean> {
    try {
      // 检查是否有用户菜单或登出按钮
      return await this.helper.elementExists('.user-dropdown, .logout-btn, .el-avatar')
    } catch {
      return false
    }
  }
  
  /**
   * 登出
   */
  async logout(): Promise<void> {
    // 点击用户菜单
    await this.helper.clickElement('.user-dropdown, .el-avatar')
    // 点击登出
    await this.helper.clickElement('[data-action="logout"], .logout-item')
  }
}

/**
 * 断言帮助类
 */
export class Assertions {
  constructor(private page: Page) {}
  
  /**
   * 断言 URL 包含指定路径
   */
  async urlContains(path: string): Promise<void> {
    const url = this.page.url()
    if (!url.includes(path)) {
      throw new Error(`Expected URL to contain "${path}", but got "${url}"`)
    }
  }
  
  /**
   * 断言页面标题
   */
  async titleEquals(expected: string): Promise<void> {
    const title = await this.page.title()
    if (title !== expected) {
      throw new Error(`Expected title "${expected}", but got "${title}"`)
    }
  }
  
  /**
   * 断言元素存在
   */
  async elementExists(selector: string): Promise<void> {
    const element = await this.page.$(selector)
    if (!element) {
      throw new Error(`Element "${selector}" not found`)
    }
  }
  
  /**
   * 断言元素不存在
   */
  async elementNotExists(selector: string): Promise<void> {
    const element = await this.page.$(selector)
    if (element) {
      throw new Error(`Element "${selector}" should not exist`)
    }
  }
  
  /**
   * 断言元素文本内容
   */
  async textContentEquals(selector: string, expected: string): Promise<void> {
    const element = await this.page.$(selector)
    if (!element) {
      throw new Error(`Element "${selector}" not found`)
    }
    const text = await this.page.evaluate(el => el.textContent, element)
    if (text !== expected) {
      throw new Error(`Expected text "${expected}", but got "${text}"`)
    }
  }
  
  /**
   * 断言元素包含文本
   */
  async textContentContains(selector: string, expected: string): Promise<void> {
    const element = await this.page.$(selector)
    if (!element) {
      throw new Error(`Element "${selector}" not found`)
    }
    const text = await this.page.evaluate(el => el.textContent, element)
    if (!text?.includes(expected)) {
      throw new Error(`Expected text to contain "${expected}", but got "${text}"`)
    }
  }
  
  /**
   * 断言输入框值
   */
  async inputValueEquals(selector: string, expected: string): Promise<void> {
    const value = await this.page.$eval(selector, (el: Element) => (el as HTMLInputElement).value)
    if (value !== expected) {
      throw new Error(`Expected input value "${expected}", but got "${value}"`)
    }
  }
  
  /**
   * 断言表格行数
   */
  async tableRowCount(minCount: number): Promise<void> {
    const rows = await this.page.$$('.el-table__body-wrapper .el-table__row')
    if (rows.length < minCount) {
      throw new Error(`Expected at least ${minCount} table rows, but got ${rows.length}`)
    }
  }
}

/**
 * 测试运行器
 */
export class TestRunner {
  private tests: Array<{ name: string; fn: () => Promise<void> }> = []
  private passed = 0
  private failed = 0
  private context: TestContext
  
  constructor() {
    this.context = new TestContext()
  }
  
  /**
   * 注册测试用例
   */
  test(name: string, fn: () => Promise<void>): void {
    this.tests.push({ name, fn })
  }
  
  /**
   * 运行所有测试
   */
  async run(): Promise<void> {
    console.log('\n🚀 Starting E2E tests...\n')
    
    await this.context.setup()
    
    for (const test of this.tests) {
      try {
        console.log(`⏳ Running: ${test.name}`)
        await test.fn()
        this.passed++
        console.log(`✅ Passed: ${test.name}\n`)
      } catch (error) {
        this.failed++
        console.log(`❌ Failed: ${test.name}`)
        console.log(`   Error: ${error instanceof Error ? error.message : error}\n`)
        
        // 失败时截图
        const helper = new PageHelper(this.context.getPage())
        await helper.screenshot(`failed-${test.name.replace(/\s+/g, '-')}`)
      }
    }
    
    await this.context.teardown()
    
    console.log('\n📊 Test Results:')
    console.log(`   ✅ Passed: ${this.passed}`)
    console.log(`   ❌ Failed: ${this.failed}`)
    console.log(`   📝 Total: ${this.tests.length}\n`)
    
    if (this.failed > 0) {
      process.exit(1)
    }
  }
  
  /**
   * 获取测试上下文
   */
  getContext(): TestContext {
    return this.context
  }
  
  /**
   * 获取页面帮助类
   */
  getHelper(): PageHelper {
    return new PageHelper(this.context.getPage())
  }
  
  /**
   * 获取断言帮助类
   */
  getAssertions(): Assertions {
    return new Assertions(this.context.getPage())
  }
  
  /**
   * 获取认证帮助类
   */
  getAuthHelper(): AuthHelper {
    return new AuthHelper(this.getHelper())
  }
}

/**
 * 延迟函数
 */
export function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}
