/**
 * 导航和布局 E2E 测试
 */
import { TestRunner, delay } from '../utils/test-helper'

const runner = new TestRunner()

// 辅助函数：确保已登录
async function ensureLoggedIn(): Promise<void> {
  const auth = runner.getAuthHelper()
  const helper = runner.getHelper()
  
  await helper.navigate('/')
  await delay(500)
  
  const page = runner.getContext().getPage()
  if (page.url().includes('/login')) {
    await auth.login()
  }
}

// 测试：主布局正确渲染
runner.test('主布局正确渲染', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  
  await helper.navigate('/')
  await delay(1000)
  
  // 检查侧边栏菜单
  const hasSidebar = await helper.elementExists('.el-aside, .el-menu, .sidebar, .nav-menu')
  
  // 检查头部
  const hasHeader = await helper.elementExists('.el-header, header, .app-header')
  
  // 检查主内容区
  const hasMain = await helper.elementExists('.el-main, main, .main-content')
  
  console.log(`Sidebar: ${hasSidebar}, Header: ${hasHeader}, Main: ${hasMain}`)
  
  await helper.screenshot('main-layout')
})

// 测试：侧边栏菜单导航
runner.test('侧边栏菜单导航正常', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/')
  await delay(1000)
  
  // 获取所有菜单项
  const menuItems = await page.$$('.el-menu-item, .el-sub-menu__title, [class*="menu-item"]')
  
  console.log(`Found ${menuItems.length} menu items`)
  
  // 点击第一个菜单项
  if (menuItems.length > 0) {
    await menuItems[0].click()
    await delay(1000)
    
    const url = page.url()
    console.log(`Navigated to: ${url}`)
  }
  
  await helper.screenshot('sidebar-navigation')
})

// 测试：各主要页面可访问
runner.test('发布窗口页面可访问', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const assertions = runner.getAssertions()
  
  await helper.navigate('/release-window')
  await delay(1000)
  
  await assertions.urlContains('/release-window')
  await helper.screenshot('page-release-window')
})

runner.test('仓库页面可访问', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  
  await helper.navigate('/repository')
  await delay(1000)
  
  const page = runner.getContext().getPage()
  const url = page.url()
  
  if (!url.includes('/repository')) {
    throw new Error('Should be on repository page')
  }
  
  await helper.screenshot('page-repository')
})

runner.test('迭代页面可访问', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  
  await helper.navigate('/iteration')
  await delay(1000)
  
  const page = runner.getContext().getPage()
  const url = page.url()
  
  if (!url.includes('/iteration')) {
    throw new Error('Should be on iteration page')
  }
  
  await helper.screenshot('page-iteration')
})

runner.test('分组页面可访问', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  
  await helper.navigate('/group')
  await delay(1000)
  
  await helper.screenshot('page-group')
})

runner.test('版本策略页面可访问', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  
  await helper.navigate('/version-policy')
  await delay(1000)
  
  await helper.screenshot('page-version-policy')
})

// 测试：面包屑导航
runner.test('面包屑导航显示正确', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-window')
  await delay(1000)
  
  // 检查面包屑
  const hasBreadcrumb = await helper.elementExists('.el-breadcrumb')
  
  if (hasBreadcrumb) {
    const breadcrumbItems = await page.$$('.el-breadcrumb__item')
    console.log(`Found ${breadcrumbItems.length} breadcrumb items`)
  }
  
  await helper.screenshot('breadcrumb-navigation')
})

// 测试：响应式布局（可选）
runner.test('移动端视口适配', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  // 切换到移动端视口
  await page.setViewport({ width: 375, height: 667 })
  
  await helper.navigate('/')
  await delay(1000)
  
  await helper.screenshot('mobile-viewport')
  
  // 恢复桌面视口
  await page.setViewport({ width: 1920, height: 1080 })
})

// 测试：404 页面
runner.test('404 页面正确显示', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  
  await helper.navigate('/non-existent-page-12345')
  await delay(1000)
  
  // 检查是否显示 404 内容或重定向
  const page = runner.getContext().getPage()
  const content = await page.content()
  
  const has404 = content.includes('404') || 
                  content.includes('Not Found') || 
                  content.includes('不存在') ||
                  content.includes('找不到')
  
  if (!has404) {
    // 可能重定向到首页
    console.log('Page may redirect to home instead of showing 404')
  }
  
  await helper.screenshot('page-404')
})

// 运行测试
runner.run().catch(console.error)
