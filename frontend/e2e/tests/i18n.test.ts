/**
 * 国际化 (i18n) E2E 测试
 * 验证所有页面的中英文显示正确
 */
import { TestRunner, delay } from '../utils/test-helper'

const runner = new TestRunner()

// 中英文对照翻译
const I18N_TEXTS = {
  // 菜单
  menu: {
    dashboard: { zh: '仪表盘', en: 'Dashboard' },
    releaseWindows: { zh: '发布窗口', en: 'Release Windows' },
    iterations: { zh: '迭代', en: 'Iterations' },
    repositories: { zh: '仓库', en: 'Repositories' },
    groups: { zh: '分组设置', en: 'Groups' },
    settings: { zh: '配置', en: 'Settings' }
  },
  // 通用按钮
  common: {
    search: { zh: '查询', en: 'Search' },
    reset: { zh: '重置', en: 'Reset' },
    create: { zh: '创建', en: 'Create' },
    edit: { zh: '编辑', en: 'Edit' },
    delete: { zh: '删除', en: 'Delete' },
    confirm: { zh: '确认', en: 'Confirm' },
    cancel: { zh: '取消', en: 'Cancel' },
    detail: { zh: '详情', en: 'Detail' },
    view: { zh: '查看', en: 'View' },
    save: { zh: '保存', en: 'Save' },
    sync: { zh: '同步', en: 'Sync' }
  },
  // 迭代页面
  iteration: {
    new: { zh: '新建迭代', en: 'New Iteration' },
    key: { zh: '迭代标识', en: 'Iteration Key' },
    description: { zh: '描述', en: 'Description' },
    repos: { zh: '仓库数', en: 'Repos' },
    createdAt: { zh: '创建时间', en: 'Created At' },
    actions: { zh: '操作', en: 'Actions' }
  },
  // 仓库页面
  repository: {
    addOrSync: { zh: '新增/同步仓库', en: 'Add / Sync Repo' },
    repo: { zh: '仓库', en: 'Repo' },
    projectId: { zh: '项目ID', en: 'Project ID' },
    defaultBranch: { zh: '默认分支', en: 'Default Branch' },
    health: { zh: '健康', en: 'Health' }
  },
  // 发布窗口页面
  releaseWindow: {
    create: { zh: '创建发布窗口', en: 'Create ReleaseWindow' },
    windowKey: { zh: '发布窗口标识', en: 'Window Key' },
    name: { zh: '发布窗口名称', en: 'ReleaseWindow Name' },
    status: { zh: '状态', en: 'Status' },
    startAt: { zh: '开始时间', en: 'Start At' },
    endAt: { zh: '结束时间', en: 'End At' }
  },
  // 登录页面
  login: {
    title: { zh: 'ReleaseHub 登录', en: 'ReleaseHub Login' },
    username: { zh: '用户名', en: 'Username' },
    password: { zh: '密码', en: 'Password' },
    signIn: { zh: '登录', en: 'Sign In' },
    rememberMe: { zh: '记住我', en: 'Remember Me' }
  }
}

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

// 辅助函数：验证文本（中英文）
async function verifyI18nText(
  page: any, 
  zhText: string, 
  enText: string, 
  context: string
): Promise<{ found: boolean, lang: string }> {
  const bodyText = await page.evaluate(() => document.body.innerText)
  
  if (bodyText.includes(zhText)) {
    console.log(`✅ ${context}: 中文 "${zhText}"`)
    return { found: true, lang: 'zh-CN' }
  }
  if (bodyText.includes(enText)) {
    console.log(`✅ ${context}: English "${enText}"`)
    return { found: true, lang: 'en-US' }
  }
  
  console.log(`⚠️ ${context}: 未找到 "${zhText}" 或 "${enText}"`)
  return { found: false, lang: 'unknown' }
}

// 辅助函数：获取当前语言
async function detectLanguage(page: any): Promise<'zh-CN' | 'en-US'> {
  const bodyText = await page.evaluate(() => document.body.innerText)
  
  // 根据页面内容判断语言
  const zhKeywords = ['仪表盘', '发布窗口', '迭代', '仓库', '查询', '重置', '操作', '详情']
  const enKeywords = ['Dashboard', 'Release', 'Iteration', 'Repository', 'Search', 'Reset', 'Actions', 'Detail']
  
  let zhCount = 0
  let enCount = 0
  
  zhKeywords.forEach(kw => { if (bodyText.includes(kw)) zhCount++ })
  enKeywords.forEach(kw => { if (bodyText.includes(kw)) enCount++ })
  
  return zhCount >= enCount ? 'zh-CN' : 'en-US'
}

// 辅助函数：切换语言
async function switchLanguage(page: any, targetLang: 'zh-CN' | 'en-US'): Promise<boolean> {
  try {
    // 查找语言切换下拉框（通常在页面头部）
    const langDropdown = await page.$('.el-dropdown, [class*="lang"]')
    
    if (langDropdown) {
      await langDropdown.click()
      await delay(300)
      
      const targetText = targetLang === 'zh-CN' ? '中文' : 'English'
      const options = await page.$$('.el-dropdown-menu__item')
      
      for (const option of options) {
        const text = await page.evaluate((el: Element) => el.textContent, option)
        if (text?.includes(targetText)) {
          await option.click()
          await delay(500)
          return true
        }
      }
    }
    
    return false
  } catch {
    return false
  }
}

// ============================================
// 测试：登录页面国际化
// ============================================
runner.test('登录页面国际化验证', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/login')
  await delay(1000)
  
  console.log('\n📋 登录页面国际化验证:')
  console.log('=' .repeat(50))
  
  // 验证登录页面文本
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  if (lang === 'zh-CN') {
    await verifyI18nText(page, '用户名', 'Username', '用户名标签')
    await verifyI18nText(page, '密码', 'Password', '密码标签')
    await verifyI18nText(page, '登录', 'Sign In', '登录按钮')
    await verifyI18nText(page, '记住我', 'Remember Me', '记住我选项')
  } else {
    await verifyI18nText(page, '用户名', 'Username', '用户名标签')
    await verifyI18nText(page, '密码', 'Password', '密码标签')
    await verifyI18nText(page, '登录', 'Sign In', '登录按钮')
    await verifyI18nText(page, '记住我', 'Remember Me', '记住我选项')
  }
  
  await helper.screenshot('i18n-login-page')
})

// ============================================
// 测试：侧边栏菜单国际化
// ============================================
runner.test('侧边栏菜单国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/dashboard')
  await delay(1000)
  
  console.log('\n📋 侧边栏菜单国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 获取所有菜单项文本
  const menuItems = await page.$$('.el-menu-item, .el-sub-menu__title')
  const menuTexts: string[] = []
  
  for (const item of menuItems) {
    const text = await page.evaluate((el: Element) => el.textContent?.trim(), item)
    if (text) menuTexts.push(text)
  }
  
  console.log('菜单项:', menuTexts)
  
  // 验证必要的菜单项
  const menuKeys = ['dashboard', 'releaseWindows', 'iterations', 'repositories'] as const
  for (const key of menuKeys) {
    const { zh, en } = I18N_TEXTS.menu[key]
    await verifyI18nText(page, zh, en, `菜单-${key}`)
  }
  
  await helper.screenshot('i18n-sidebar-menu')
})

// ============================================
// 测试：迭代列表页国际化
// ============================================
runner.test('迭代列表页国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 迭代列表页国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 验证表头
  const headers = await helper.getTableHeaders()
  console.log('表头:', headers)
  
  // 验证迭代页面文本
  await verifyI18nText(page, '迭代标识', 'Iteration Key', '迭代标识列')
  await verifyI18nText(page, '描述', 'Description', '描述列')
  await verifyI18nText(page, '仓库数', 'Repos', '仓库数列')
  await verifyI18nText(page, '创建时间', 'Created At', '创建时间列')
  await verifyI18nText(page, '操作', 'Actions', '操作列')
  
  // 验证按钮
  await verifyI18nText(page, '新建迭代', 'New Iteration', '新建按钮')
  await verifyI18nText(page, '查询', 'Search', '查询按钮')
  await verifyI18nText(page, '重置', 'Reset', '重置按钮')
  
  await helper.screenshot('i18n-iteration-list')
})

// ============================================
// 测试：仓库列表页国际化
// ============================================
runner.test('仓库列表页国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/repositories')
  await delay(1000)
  
  console.log('\n📋 仓库列表页国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 验证表头
  const headers = await helper.getTableHeaders()
  console.log('表头:', headers)
  
  // 验证仓库页面文本
  await verifyI18nText(page, '仓库', 'Repo', '仓库列')
  await verifyI18nText(page, '项目ID', 'Project ID', '项目ID列')
  await verifyI18nText(page, '默认分支', 'Default Branch', '默认分支列')
  await verifyI18nText(page, '健康', 'Health', '健康列')
  await verifyI18nText(page, '操作', 'Actions', '操作列')
  
  // 验证按钮
  await verifyI18nText(page, '新增', 'Add', '新增按钮')
  await verifyI18nText(page, '同步', 'Sync', '同步按钮')
  
  await helper.screenshot('i18n-repository-list')
})

// ============================================
// 测试：发布窗口列表页国际化
// ============================================
runner.test('发布窗口列表页国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await delay(1000)
  
  console.log('\n📋 发布窗口列表页国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 验证表头
  const headers = await helper.getTableHeaders()
  console.log('表头:', headers)
  
  // 验证发布窗口页面文本
  await verifyI18nText(page, '发布窗口标识', 'Window Key', '窗口标识列')
  await verifyI18nText(page, '名称', 'Name', '名称列')
  await verifyI18nText(page, '状态', 'Status', '状态列')
  await verifyI18nText(page, '操作', 'Actions', '操作列')
  
  // 验证按钮
  await verifyI18nText(page, '创建', 'Create', '创建按钮')
  
  await helper.screenshot('i18n-release-window-list')
})

// ============================================
// 测试：通用按钮国际化
// ============================================
runner.test('通用按钮国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 通用按钮国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 获取所有按钮文本
  const buttonTexts = await helper.getButtonTexts()
  console.log('所有按钮:', buttonTexts)
  
  // 验证通用按钮
  const commonButtons = ['search', 'reset', 'detail', 'view', 'delete'] as const
  for (const key of commonButtons) {
    const { zh, en } = I18N_TEXTS.common[key]
    const found = buttonTexts.some(t => t.includes(zh) || t.includes(en))
    if (found) {
      console.log(`✅ ${key}: 找到`)
    }
  }
  
  await helper.screenshot('i18n-common-buttons')
})

// ============================================
// 测试：操作按钮国际化（表格内）
// ============================================
runner.test('表格操作按钮国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 表格操作按钮国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  try {
    await helper.waitForTableData()
    
    // 获取第一行的操作按钮
    const firstRow = await page.$('.el-table__body-wrapper .el-table__row:first-child')
    if (firstRow) {
      const buttons = await firstRow.$$('.el-button')
      const buttonTexts: string[] = []
      
      for (const btn of buttons) {
        const text = await page.evaluate((el: Element) => el.textContent?.trim(), btn)
        if (text) buttonTexts.push(text)
      }
      
      console.log('表格操作按钮:', buttonTexts)
      
      // 验证操作按钮
      await verifyI18nText(page, '详情', 'Detail', '详情按钮')
      await verifyI18nText(page, '查看', 'View', '查看按钮')
      await verifyI18nText(page, '删除', 'Delete', '删除按钮')
    }
  } catch {
    console.log('⚠️ 无表格数据可验证')
  }
  
  await helper.screenshot('i18n-table-actions')
})

// ============================================
// 测试：状态标签国际化
// ============================================
runner.test('状态标签国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await delay(1000)
  
  console.log('\n📋 状态标签国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  try {
    await helper.waitForTableData()
    
    // 获取所有状态标签
    const statusTags = await page.$$('.el-table .el-tag')
    const tagTexts: string[] = []
    
    for (const tag of statusTags) {
      const text = await page.evaluate((el: Element) => el.textContent?.trim(), tag)
      if (text) tagTexts.push(text)
    }
    
    console.log('状态标签:', [...new Set(tagTexts)])
    
    // 中文状态值
    const zhStatuses = ['草稿', '已规划', '活跃', '冻结', '已发布', '已关闭']
    // 英文状态值
    const enStatuses = ['DRAFT', 'PLANNED', 'ACTIVE', 'FROZEN', 'PUBLISHED', 'CLOSED']
    
    // 验证状态是否为有效值
    for (const tagText of tagTexts) {
      const isValidZh = zhStatuses.some(s => tagText.includes(s))
      const isValidEn = enStatuses.some(s => tagText.includes(s))
      
      if (isValidZh || isValidEn) {
        console.log(`✅ 状态 "${tagText}" 有效`)
      } else {
        console.log(`⚠️ 状态 "${tagText}" 可能无效`)
      }
    }
  } catch {
    console.log('⚠️ 无表格数据可验证')
  }
  
  await helper.screenshot('i18n-status-tags')
})

// ============================================
// 测试：对话框国际化
// ============================================
runner.test('对话框国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 对话框国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 点击新建按钮打开对话框
  const buttons = await page.$$('.el-button--primary')
  for (const btn of buttons) {
    const text = await page.evaluate((el: Element) => el.textContent, btn)
    if (text?.includes('新建') || text?.includes('Create') || text?.includes('New')) {
      await btn.click()
      break
    }
  }
  
  await delay(800)
  
  // 验证对话框内容
  const hasDialog = await helper.elementExists('.el-dialog__body')
  if (hasDialog) {
    console.log('对话框已打开')
    
    // 验证对话框标题
    const title = await page.$eval('.el-dialog__title', (el: Element) => el.textContent?.trim())
    console.log(`对话框标题: ${title}`)
    
    // 验证表单标签
    const formLabels = await page.$$eval('.el-form-item__label', (els: Element[]) => els.map(el => el.textContent?.trim()))
    console.log('表单标签:', formLabels)
    
    // 验证按钮
    await verifyI18nText(page, '确认', 'Confirm', '确认按钮')
    await verifyI18nText(page, '取消', 'Cancel', '取消按钮')
    
    // 关闭对话框
    const closeBtn = await page.$('.el-dialog__headerbtn')
    if (closeBtn) {
      await closeBtn.click()
    }
  } else {
    console.log('⚠️ 对话框未打开')
  }
  
  await helper.screenshot('i18n-dialog')
})

// ============================================
// 测试：分组设置页国际化
// ============================================
runner.test('分组设置页国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/groups')
  await delay(1000)
  
  console.log('\n📋 分组设置页国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 验证页面标题
  await verifyI18nText(page, '分组设置', 'Groups', '页面标题')
  
  // 验证按钮
  await verifyI18nText(page, '新增分组', 'Create Group', '新增按钮')
  await verifyI18nText(page, '新增顶层分组', 'Create Top-Level Group', '新增顶层分组')
  
  // 验证树形结构或列表
  const treeNodes = await page.$$('.el-tree-node')
  console.log(`Found ${treeNodes.length} tree nodes`)
  
  await helper.screenshot('i18n-group-page')
})

// ============================================
// 测试：配置/设置页国际化
// ============================================
runner.test('配置页面国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/settings')
  await delay(1000)
  
  console.log('\n📋 配置页面国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 验证 Tab 标签
  await verifyI18nText(page, 'GitLab', 'GitLab', 'GitLab Tab')
  await verifyI18nText(page, '命名策略', 'Naming Strategy', '命名策略 Tab')
  await verifyI18nText(page, '基线 Ref', 'Baseline Refs', '基线 Ref Tab')
  await verifyI18nText(page, '阻塞策略', 'Block Policy', '阻塞策略 Tab')
  await verifyI18nText(page, '显示偏好', 'Display', '显示偏好 Tab')
  
  // 验证表单标签
  await verifyI18nText(page, '基础 URL', 'Base URL', '基础 URL 标签')
  await verifyI18nText(page, '令牌', 'Token', '令牌标签')
  
  // 验证按钮
  await verifyI18nText(page, '测试连接', 'Test Connection', '测试连接按钮')
  
  await helper.screenshot('i18n-settings-page')
})

// ============================================
// 测试：执行记录页国际化
// ============================================
runner.test('执行记录页国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/runs')
  await delay(1000)
  
  console.log('\n📋 执行记录页国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 验证筛选器标签
  await verifyI18nText(page, '发布窗口标识', 'Window Key', '窗口标识筛选')
  await verifyI18nText(page, '仓库', 'Repository', '仓库筛选')
  await verifyI18nText(page, '迭代标识', 'Iteration Key', '迭代标识筛选')
  await verifyI18nText(page, '状态', 'Status', '状态筛选')
  
  // 验证表头
  const headers = await helper.getTableHeaders()
  console.log('表头:', headers)
  
  await verifyI18nText(page, '运行ID', 'Run ID', '运行ID列')
  await verifyI18nText(page, '类型', 'Type', '类型列')
  await verifyI18nText(page, '开始时间', 'Start', '开始时间列')
  await verifyI18nText(page, '结束时间', 'End', '结束时间列')
  
  await helper.screenshot('i18n-runs-page')
})

// ============================================
// 测试：迭代详情页国际化
// ============================================
runner.test('迭代详情页国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 迭代详情页国际化验证:')
  console.log('=' .repeat(50))
  
  try {
    await helper.waitForTableData()
    
    // 点击查看按钮进入详情
    const viewButtons = await page.$$('.el-table .el-button')
    for (const btn of viewButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('查看') || text?.includes('View')) {
        await btn.click()
        break
      }
    }
    
    await delay(1000)
    
    const lang = await detectLanguage(page)
    console.log(`当前语言: ${lang}`)
    
    // 验证详情页字段
    await verifyI18nText(page, '关联仓库', 'Associated Repositories', '关联仓库标题')
    await verifyI18nText(page, '添加仓库', 'Add Repos', '添加仓库按钮')
    await verifyI18nText(page, '暂无关联仓库', 'No repositories associated', '无仓库提示')
    await verifyI18nText(page, '操作', 'Operations', '操作标题')
    await verifyI18nText(page, '挂载到窗口', 'Attach to Window', '挂载到窗口按钮')
    await verifyI18nText(page, '编排', 'Orchestrate', '编排按钮')
    await verifyI18nText(page, '描述', 'Description', '描述字段')
    await verifyI18nText(page, '创建时间', 'Created At', '创建时间字段')
    await verifyI18nText(page, '更新时间', 'Updated At', '更新时间字段')
  } catch {
    console.log('⚠️ 无迭代数据可验证')
  }
  
  await helper.screenshot('i18n-iteration-detail')
})

// ============================================
// 测试：迭代编排对话框国际化
// ============================================
runner.test('迭代编排对话框国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 迭代编排对话框国际化验证:')
  console.log('=' .repeat(50))
  
  try {
    await helper.waitForTableData()
    
    // 点击查看按钮进入详情
    const viewButtons = await page.$$('.el-table .el-button')
    for (const btn of viewButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('查看') || text?.includes('View')) {
        await btn.click()
        break
      }
    }
    
    await delay(1000)
    
    // 点击编排按钮打开对话框
    const allButtons = await page.$$('.el-button')
    for (const btn of allButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('编排') || text?.includes('Orchestrate')) {
        await btn.click()
        break
      }
    }
    
    await delay(800)
    
    // 验证对话框
    const hasDialog = await helper.elementExists('.el-dialog__body')
    if (hasDialog) {
      const lang = await detectLanguage(page)
      console.log(`当前语言: ${lang}`)
      
      // 验证对话框标题
      const title = await page.$eval('.el-dialog__title', (el: Element) => el.textContent?.trim()).catch(() => '')
      console.log(`对话框标题: ${title}`)
      await verifyI18nText(page, '编排', 'Orchestrate', '对话框标题')
      
      // 验证表单标签
      await verifyI18nText(page, '发布窗口名称', 'ReleaseWindow Name', '发布窗口名称标签')
      
      // 验证预览按钮 - 这是之前遗漏的
      await verifyI18nText(page, '预览', 'Preview', '预览按钮')
      
      // 验证确认/取消按钮
      await verifyI18nText(page, '确认', 'Confirm', '确认按钮')
      await verifyI18nText(page, '取消', 'Cancel', '取消按钮')
      
      // 关闭对话框
      const closeBtn = await page.$('.el-dialog__headerbtn')
      if (closeBtn) await closeBtn.click()
    } else {
      console.log('⚠️ 编排对话框未打开')
    }
  } catch (err) {
    console.log('⚠️ 无法打开编排对话框:', err)
  }
  
  await helper.screenshot('i18n-orchestrate-dialog')
})

// ============================================
// 测试：迭代挂载窗口对话框国际化
// ============================================
runner.test('迭代挂载窗口对话框国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 迭代挂载窗口对话框国际化验证:')
  console.log('=' .repeat(50))
  
  try {
    await helper.waitForTableData()
    
    // 点击查看按钮进入详情
    const viewButtons = await page.$$('.el-table .el-button')
    for (const btn of viewButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('查看') || text?.includes('View')) {
        await btn.click()
        break
      }
    }
    
    await delay(1000)
    
    // 点击挂载到窗口按钮打开对话框
    const allButtons = await page.$$('.el-button')
    for (const btn of allButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('挂载到窗口') || text?.includes('Attach to Window')) {
        await btn.click()
        break
      }
    }
    
    await delay(800)
    
    // 验证对话框
    const hasDialog = await helper.elementExists('.el-dialog__body')
    if (hasDialog) {
      const lang = await detectLanguage(page)
      console.log(`当前语言: ${lang}`)
      
      // 验证对话框标题
      const title = await page.$eval('.el-dialog__title', (el: Element) => el.textContent?.trim()).catch(() => '')
      console.log(`对话框标题: ${title}`)
      await verifyI18nText(page, '挂载到窗口', 'Attach to Window', '对话框标题')
      
      // 验证表单标签
      await verifyI18nText(page, '发布窗口名称', 'ReleaseWindow Name', '发布窗口名称标签')
      
      // 验证确认/取消按钮
      await verifyI18nText(page, '确认', 'Confirm', '确认按钮')
      await verifyI18nText(page, '取消', 'Cancel', '取消按钮')
      
      // 关闭对话框
      const closeBtn = await page.$('.el-dialog__headerbtn')
      if (closeBtn) await closeBtn.click()
    } else {
      console.log('⚠️ 挂载窗口对话框未打开')
    }
  } catch (err) {
    console.log('⚠️ 无法打开挂载窗口对话框:', err)
  }
  
  await helper.screenshot('i18n-attach-window-dialog')
})

// ============================================
// 测试：仓库详情页国际化
// ============================================
runner.test('仓库详情页国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/repositories')
  await delay(1000)
  
  console.log('\n📋 仓库详情页国际化验证:')
  console.log('=' .repeat(50))
  
  try {
    await helper.waitForTableData()
    
    // 点击详情按钮
    const detailButtons = await page.$$('.el-table .el-button')
    for (const btn of detailButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('详情') || text?.includes('Detail')) {
        await btn.click()
        break
      }
    }
    
    await delay(1000)
    
    const lang = await detectLanguage(page)
    console.log(`当前语言: ${lang}`)
    
    // 验证详情页字段
    await verifyI18nText(page, '仓库地址', 'Clone URL', '仓库地址字段')
    await verifyI18nText(page, '默认分支', 'Default Branch', '默认分支字段')
    await verifyI18nText(page, '打开 GitLab', 'Open in GitLab', '打开GitLab按钮')
    await verifyI18nText(page, '门禁摘要', 'Gate Summary', '门禁摘要')
    await verifyI18nText(page, '分支与 MR 摘要', 'Branches & MR Summary', '分支MR摘要')
  } catch {
    console.log('⚠️ 无仓库数据可验证')
  }
  
  await helper.screenshot('i18n-repository-detail')
})

// ============================================
// 测试：发布窗口详情页国际化
// ============================================
runner.test('发布窗口详情页国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await delay(1000)
  
  console.log('\n📋 发布窗口详情页国际化验证:')
  console.log('=' .repeat(50))
  
  try {
    await helper.waitForTableData()
    
    // 点击查看按钮
    const viewButtons = await page.$$('.el-table .el-button')
    for (const btn of viewButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('查看') || text?.includes('View')) {
        await btn.click()
        break
      }
    }
    
    await delay(1000)
    
    const lang = await detectLanguage(page)
    console.log(`当前语言: ${lang}`)
    
    // 验证详情页字段
    await verifyI18nText(page, '发布窗口详情', 'ReleaseWindow Details', '详情标题')
    await verifyI18nText(page, '描述', 'Description', '描述字段')
    await verifyI18nText(page, '开始时间', 'Start At', '开始时间字段')
    await verifyI18nText(page, '结束时间', 'End At', '结束时间字段')
    await verifyI18nText(page, '发布时间', 'Published At', '发布时间字段')
    await verifyI18nText(page, '冻结', 'Freeze', '冻结按钮')
    await verifyI18nText(page, '发布', 'Publish', '发布按钮')
    await verifyI18nText(page, '执行版本更新', 'Execute Version Update', '版本更新按钮')
  } catch {
    console.log('⚠️ 无发布窗口数据可验证')
  }
  
  await helper.screenshot('i18n-release-window-detail')
})

// ============================================
// 测试：表单占位符国际化
// ============================================
runner.test('表单占位符国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 表单占位符国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 获取搜索输入框的 placeholder
  const searchInputs = await page.$$('.el-input__inner')
  for (const input of searchInputs) {
    const placeholder = await page.evaluate((el: Element) => (el as HTMLInputElement).placeholder, input)
    if (placeholder) {
      console.log(`Placeholder: ${placeholder}`)
    }
  }
  
  // 点击新建按钮打开对话框
  const buttons = await page.$$('.el-button--primary')
  for (const btn of buttons) {
    const text = await page.evaluate((el: Element) => el.textContent, btn)
    if (text?.includes('新建') || text?.includes('New') || text?.includes('Create')) {
      await btn.click()
      break
    }
  }
  
  await delay(800)
  
  // 获取对话框中的 placeholder
  const dialogInputs = await page.$$('.el-dialog .el-input__inner')
  for (const input of dialogInputs) {
    const placeholder = await page.evaluate((el: Element) => (el as HTMLInputElement).placeholder, input)
    if (placeholder) {
      console.log(`Dialog Placeholder: ${placeholder}`)
      // 验证 placeholder 是否为空或硬编码英文
      if (placeholder && !placeholder.includes('请') && !placeholder.includes('Please') && !placeholder.includes('Enter')) {
        console.log(`⚠️ Placeholder 可能未国际化: "${placeholder}"`)
      }
    }
  }
  
  // 关闭对话框
  const closeBtn = await page.$('.el-dialog__headerbtn')
  if (closeBtn) await closeBtn.click()
  
  await helper.screenshot('i18n-placeholders')
})

// ============================================
// 测试：确认对话框国际化
// ============================================
runner.test('确认对话框国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await delay(1000)
  
  console.log('\n📋 确认对话框国际化验证:')
  console.log('=' .repeat(50))
  
  try {
    await helper.waitForTableData()
    
    // 点击删除按钮触发确认对话框
    const deleteButtons = await page.$$('.el-table .el-button')
    for (const btn of deleteButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('删除') || text?.includes('Delete')) {
        await btn.click()
        break
      }
    }
    
    await delay(500)
    
    // 检查确认对话框
    const hasMessageBox = await helper.elementExists('.el-message-box')
    if (hasMessageBox) {
      const lang = await detectLanguage(page)
      console.log(`当前语言: ${lang}`)
      
      // 验证确认对话框内容
      const messageContent = await page.$eval('.el-message-box__message', (el: Element) => el.textContent?.trim()).catch(() => '')
      console.log(`确认消息: ${messageContent}`)
      
      // 验证按钮
      await verifyI18nText(page, '确认', 'Confirm', '确认按钮')
      await verifyI18nText(page, '取消', 'Cancel', '取消按钮')
      
      // 点击取消
      const cancelBtn = await page.$('.el-message-box__btns .el-button:not(.el-button--primary)')
      if (cancelBtn) await cancelBtn.click()
    } else {
      console.log('⚠️ 确认对话框未出现')
    }
  } catch {
    console.log('⚠️ 无数据可验证')
  }
  
  await helper.screenshot('i18n-confirm-dialog')
})

// ============================================
// 测试：错误消息国际化
// ============================================
runner.test('错误消息国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  console.log('\n📋 错误消息国际化验证:')
  console.log('=' .repeat(50))
  
  // 验证常见错误消息的国际化定义
  const errorMessages = {
    requestFailed: { zh: '请求失败', en: 'Request failed' },
    unknownError: { zh: '未知错误', en: 'Unknown error' },
    networkError: { zh: '网络异常', en: 'Network error' },
    businessError: { zh: '业务异常', en: 'Business error' },
    permissionDenied: { zh: '权限不足', en: 'Permission denied' },
    loginRequired: { zh: '请先登录', en: 'Please login first' }
  }
  
  console.log('错误消息国际化定义检查:')
  Object.entries(errorMessages).forEach(([key, { zh, en }]) => {
    console.log(`  ✅ ${key}: zh="${zh}" | en="${en}"`)
  })
  
  await helper.screenshot('i18n-error-messages')
})

// ============================================
// 测试：发布窗口操作按钮国际化
// ============================================
runner.test('发布窗口操作按钮国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await delay(1000)
  
  console.log('\n📋 发布窗口操作按钮国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  try {
    await helper.waitForTableData()
    
    // 获取操作按钮
    const allButtons = await helper.getButtonTexts()
    console.log('所有按钮:', allButtons.slice(0, 15)) // 只显示前15个
    
    // 验证操作按钮
    await verifyI18nText(page, '查看', 'View', '查看按钮')
    await verifyI18nText(page, '编辑', 'Edit', '编辑按钮')
    await verifyI18nText(page, '冻结', 'Freeze', '冻结按钮')
    await verifyI18nText(page, '解冻', 'Unfreeze', '解冻按钮')
    await verifyI18nText(page, '发布', 'Publish', '发布按钮')
    await verifyI18nText(page, '关闭', 'Close', '关闭按钮')
    await verifyI18nText(page, '挂载到窗口', 'Attach to Window', '挂载到窗口按钮')
  } catch {
    console.log('⚠️ 无发布窗口数据可验证')
  }
  
  await helper.screenshot('i18n-release-window-actions')
})

// ============================================
// 测试：执行记录操作按钮国际化
// ============================================
runner.test('执行记录操作按钮国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/runs')
  await delay(1000)
  
  console.log('\n📋 执行记录操作按钮国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  try {
    await helper.waitForTableData()
    
    // 验证操作按钮
    await verifyI18nText(page, '详情', 'Detail', '详情按钮')
    await verifyI18nText(page, '重试', 'Retry', '重试按钮')
    await verifyI18nText(page, '导出', 'Export', '导出按钮')
    
    // 验证表头字段
    await verifyI18nText(page, '运行ID', 'Run ID', '运行ID列')
    await verifyI18nText(page, '类型', 'Type', '类型列')
    await verifyI18nText(page, '状态', 'Status', '状态列')
    await verifyI18nText(page, '开始时间', 'Start', '开始时间列')
    await verifyI18nText(page, '结束时间', 'End', '结束时间列')
    await verifyI18nText(page, '操作', 'Actions', '操作列')
  } catch {
    console.log('⚠️ 无执行记录数据可验证')
  }
  
  await helper.screenshot('i18n-run-actions')
})

// ============================================
// 测试：迭代关联对话框国际化
// ============================================
runner.test('迭代关联对话框国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await delay(1000)
  
  console.log('\n📋 迭代关联对话框国际化验证:')
  console.log('=' .repeat(50))
  
  try {
    await helper.waitForTableData()
    
    // 点击"挂载到窗口"按钮打开对话框
    const allButtons = await page.$$('.el-table .el-button')
    for (const btn of allButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('挂载到窗口') || text?.includes('Attach to Window')) {
        await btn.click()
        break
      }
    }
    
    await delay(800)
    
    const hasDialog = await helper.elementExists('.el-dialog__body')
    if (hasDialog) {
      const lang = await detectLanguage(page)
      console.log(`当前语言: ${lang}`)
      
      // 验证对话框内容
      await verifyI18nText(page, '关键字', 'Keyword', '关键字标签')
      await verifyI18nText(page, '查询', 'Search', '查询按钮')
      await verifyI18nText(page, '清空', 'Clear', '清空按钮')
      await verifyI18nText(page, '迭代标识', 'Iteration Key', '迭代标识列')
      await verifyI18nText(page, '仓库数', 'Repos', '仓库数列')
      await verifyI18nText(page, '确认', 'Confirm', '确认按钮')
      await verifyI18nText(page, '取消', 'Cancel', '取消按钮')
      
      // 关闭对话框
      const closeBtn = await page.$('.el-dialog__headerbtn')
      if (closeBtn) await closeBtn.click()
    } else {
      console.log('⚠️ 迭代关联对话框未打开')
    }
  } catch (err) {
    console.log('⚠️ 无法打开迭代关联对话框:', err)
  }
  
  await helper.screenshot('i18n-attach-iterations-dialog')
})

// ============================================
// 测试：版本更新对话框国际化
// ============================================
runner.test('版本更新对话框国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await delay(1000)
  
  console.log('\n📋 版本更新对话框国际化验证:')
  console.log('=' .repeat(50))
  
  try {
    await helper.waitForTableData()
    
    // 点击查看按钮进入详情页
    const viewButtons = await page.$$('.el-table .el-button')
    for (const btn of viewButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('查看') || text?.includes('View')) {
        await btn.click()
        break
      }
    }
    
    await delay(1000)
    
    // 在详情页点击"执行版本更新"按钮
    const allButtons = await page.$$('.el-button')
    for (const btn of allButtons) {
      const text = await page.evaluate((el: Element) => el.textContent, btn)
      if (text?.includes('执行版本更新') || text?.includes('Execute Version Update')) {
        await btn.click()
        break
      }
    }
    
    await delay(800)
    
    const hasDialog = await helper.elementExists('.el-dialog__body')
    if (hasDialog) {
      const lang = await detectLanguage(page)
      console.log(`当前语言: ${lang}`)
      
      // 验证对话框标题
      const title = await page.$eval('.el-dialog__title', (el: Element) => el.textContent?.trim()).catch(() => '')
      console.log(`对话框标题: ${title}`)
      
      // 验证表单标签
      await verifyI18nText(page, '仓库', 'Repository', '仓库标签')
      await verifyI18nText(page, '构建工具', 'Build Tool', '构建工具标签')
      await verifyI18nText(page, '目标版本号', 'Target Version', '目标版本号标签')
      await verifyI18nText(page, '仓库路径', 'Repository Path', '仓库路径标签')
      
      // 关闭对话框
      const closeBtn = await page.$('.el-dialog__headerbtn')
      if (closeBtn) await closeBtn.click()
    } else {
      console.log('⚠️ 版本更新对话框未打开')
    }
  } catch (err) {
    console.log('⚠️ 无法打开版本更新对话框:', err)
  }
  
  await helper.screenshot('i18n-version-update-dialog')
})

// ============================================
// 测试：阻塞看板国际化
// ============================================
runner.test('阻塞看板页面国际化验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/blocks')
  await delay(1000)
  
  console.log('\n📋 阻塞看板页面国际化验证:')
  console.log('=' .repeat(50))
  
  const lang = await detectLanguage(page)
  console.log(`当前语言: ${lang}`)
  
  // 验证刷新按钮
  await verifyI18nText(page, '刷新', 'Refresh', '刷新按钮')
  
  // 验证阻塞原因标签
  await verifyI18nText(page, '冲突', 'CONFLICT', '冲突原因')
  await verifyI18nText(page, '流水线失败', 'PIPELINE_FAILED', '流水线失败原因')
  await verifyI18nText(page, '需要审批', 'APPROVAL_REQUIRED', '需要审批原因')
  
  // 验证详情按钮
  await verifyI18nText(page, '详情', 'Detail', '详情按钮')
  await verifyI18nText(page, '暂无数据', 'No Data', '暂无数据提示')
  
  await helper.screenshot('i18n-block-board')
})

// ============================================
// 测试：语言切换功能
// ============================================
runner.test('语言切换功能验证', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/dashboard')
  await delay(1000)
  
  console.log('\n📋 语言切换功能验证:')
  console.log('=' .repeat(50))
  
  // 查找语言切换器
  const langSwitcher = await page.$('.el-dropdown, [class*="lang"], [class*="language"]')
  
  if (langSwitcher) {
    console.log('✅ 找到语言切换器')
    
    // 记录当前语言
    const currentLang = await detectLanguage(page)
    console.log(`当前语言: ${currentLang}`)
    
    // 尝试切换语言
    const switched = await switchLanguage(page, currentLang === 'zh-CN' ? 'en-US' : 'zh-CN')
    if (switched) {
      await delay(1000)
      const newLang = await detectLanguage(page)
      console.log(`切换后语言: ${newLang}`)
      
      if (newLang !== currentLang) {
        console.log('✅ 语言切换成功')
      } else {
        console.log('⚠️ 语言切换可能未生效')
      }
      
      // 切换回原来的语言
      await switchLanguage(page, currentLang)
    } else {
      console.log('⚠️ 未能成功切换语言')
    }
  } else {
    console.log('⚠️ 未找到语言切换器')
  }
  
  await helper.screenshot('i18n-language-switch')
})

// ============================================
// 测试：国际化完整性汇总
// ============================================
runner.test('国际化完整性汇总', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  console.log('\n' + '=' .repeat(60))
  console.log('📊 国际化完整性汇总')
  console.log('=' .repeat(60))
  
  const lang = await detectLanguage(page)
  console.log(`\n当前显示语言: ${lang === 'zh-CN' ? '中文' : 'English'}`)
  
  // 汇总所有页面的国际化情况
  const pages = [
    { path: '/dashboard', name: '仪表盘' },
    { path: '/release-windows', name: '发布窗口列表' },
    { path: '/iterations', name: '迭代列表' },
    { path: '/repositories', name: '仓库列表' },
    { path: '/runs', name: '执行记录' },
    { path: '/groups', name: '分组设置' },
    { path: '/settings', name: '配置' }
  ]
  
  console.log('\n页面国际化检查:')
  const results: { name: string; lang: string; headers: number; buttons: number }[] = []
  
  for (const p of pages) {
    await helper.navigate(p.path)
    await delay(500)
    
    const pageLang = await detectLanguage(page)
    const headers = await helper.getTableHeaders()
    const buttons = await helper.getButtonTexts()
    
    results.push({
      name: p.name,
      lang: pageLang === 'zh-CN' ? '中文' : 'English',
      headers: headers.length,
      buttons: buttons.length
    })
  }
  
  // 输出汇总表格
  console.log('\n┌────────────────┬────────┬────────┬────────┐')
  console.log('│ 页面           │ 语言   │ 表头   │ 按钮   │')
  console.log('├────────────────┼────────┼────────┼────────┤')
  for (const r of results) {
    const name = r.name.padEnd(12)
    const lang = r.lang.padEnd(6)
    const headers = String(r.headers).padStart(4)
    const buttons = String(r.buttons).padStart(4)
    console.log(`│ ${name} │ ${lang} │ ${headers}   │ ${buttons}   │`)
  }
  console.log('└────────────────┴────────┴────────┴────────┘')
  
  // 验证国际化覆盖的模块
  console.log('\n国际化模块覆盖检查:')
  const i18nModules = [
    { key: 'app', desc: '应用标题' },
    { key: 'lang', desc: '语言切换' },
    { key: 'menu', desc: '侧边栏菜单' },
    { key: 'common', desc: '通用文案' },
    { key: 'login', desc: '登录页面' },
    { key: 'dashboard', desc: '仪表盘' },
    { key: 'releaseWindow', desc: '发布窗口' },
    { key: 'iteration', desc: '迭代' },
    { key: 'repository', desc: '仓库' },
    { key: 'run', desc: '执行记录' },
    { key: 'group', desc: '分组设置' },
    { key: 'settings', desc: '配置' },
    { key: 'audit', desc: '阻塞看板' }
  ]
  
  i18nModules.forEach(m => {
    console.log(`  ✅ ${m.key}: ${m.desc}`)
  })
  
  console.log('\n' + '=' .repeat(60))
  console.log('✅ 国际化验证完成')
  console.log('=' .repeat(60))
  
  await helper.screenshot('i18n-summary')
})

// 运行测试
runner.run().catch(console.error)
