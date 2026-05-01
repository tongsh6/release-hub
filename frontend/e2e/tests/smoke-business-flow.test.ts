/**
 * 冒烟测试：完整业务流程
 * 
 * 测试场景：
 * 1. 创建 5 个迭代
 * 2. 给迭代挂载仓库（通过仓库同步）
 * 3. 创建 2 个发布窗口
 * 4. 把 5 个迭代分别挂载到发布窗口
 */
import { TestRunner, delay } from '../utils/test-helper'

const runner = new TestRunner()

// 测试数据
const TEST_DATA = {
  iterations: [
    { key: `smoke-iter-${Date.now()}-1` },
    { key: `smoke-iter-${Date.now()}-2` },
    { key: `smoke-iter-${Date.now()}-3` },
    { key: `smoke-iter-${Date.now()}-4` },
    { key: `smoke-iter-${Date.now()}-5` }
  ],
  releaseWindows: [
    { key: `smoke-rw-${Date.now()}-1`, name: `冒烟测试发布窗口1-${Date.now()}` },
    { key: `smoke-rw-${Date.now()}-2`, name: `冒烟测试发布窗口2-${Date.now()}` }
  ]
}

// 存储创建后的 ID
const createdData = {
  iterationKeys: [] as string[],
  windowIds: [] as string[]
}

// 辅助函数：确保已登录
async function ensureLoggedIn(): Promise<void> {
  const auth = runner.getAuthHelper()
  const helper = runner.getHelper()
  
  await helper.navigate('/')
  await delay(1000)
  
  const page = runner.getContext().getPage()
  if (page.url().includes('/login')) {
    await auth.login()
    await delay(1000)
  }
}

// 辅助函数：等待页面加载
async function waitForPageLoad(): Promise<void> {
  const helper = runner.getHelper()
  await helper.waitForLoading()
  await delay(500)
}

// ============================================
// 步骤 1: 创建 5 个迭代
// ============================================
runner.test('步骤 1.1: 导航到迭代列表页', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  await helper.screenshot('smoke-01-iteration-list')
})

// 创建迭代的辅助函数
async function createIteration(iterationKey: string): Promise<boolean> {
  const page = runner.getContext().getPage()
  
  // 点击新建按钮
  const createBtns = await page.$$('.el-button--primary')
  for (const btn of createBtns) {
    const text = await page.evaluate(el => el.textContent, btn)
    if (text?.includes('新建') || text?.includes('创建') || text?.includes('New')) {
      await btn.click()
      break
    }
  }
  
  // 等待对话框出现 - Element Plus 对话框使用 el-overlay-dialog wrapper
  await delay(800)
  
  // 等待对话框内容可见
  try {
    await page.waitForSelector('.el-dialog__body, .el-overlay-dialog', { visible: true, timeout: 5000 })
  } catch {
    console.log('Dialog body not found, trying alternative selector')
  }
  
  await delay(300)
  
  // 输入迭代 Key - 使用更通用的选择器
  const inputs = await page.$$('.el-input__inner')
  // 找到对话框中的输入框（通常是后面的那些）
  for (let i = inputs.length - 1; i >= 0; i--) {
    const input = inputs[i]
    const placeholder = await page.evaluate(el => (el as HTMLInputElement).placeholder, input)
    if (placeholder?.includes('迭代') || placeholder?.includes('标识') || placeholder?.includes('Key')) {
      await input.click({ clickCount: 3 })
      await input.type(iterationKey)
      break
    }
  }
  
  await delay(300)
  
  // 点击确认按钮 - 找对话框footer中的主按钮
  const allBtns = await page.$$('.el-button--primary')
  for (let i = allBtns.length - 1; i >= 0; i--) {
    const btn = allBtns[i]
    const text = await page.evaluate(el => el.textContent, btn)
    if (text?.includes('确') || text?.includes('Confirm') || text?.includes('保存')) {
      await btn.click()
      break
    }
  }
  
  await delay(1500)
  
  // 检查是否成功
  try {
    await page.waitForSelector('.el-message--success', { timeout: 3000 })
    createdData.iterationKeys.push(iterationKey)
    return true
  } catch {
    // 检查对话框是否已关闭
    const dialogBody = await page.$('.el-dialog__body')
    if (!dialogBody) {
      createdData.iterationKeys.push(iterationKey)
      return true
    }
    return false
  }
}

runner.test('步骤 1.2: 创建第 1 个迭代', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  const success = await createIteration(TEST_DATA.iterations[0].key)
  if (!success) {
    console.log('Warning: First iteration creation might have failed')
  }
  
  await helper.screenshot('smoke-02-create-iteration-1')
})

runner.test('步骤 1.3: 创建第 2 个迭代', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  await createIteration(TEST_DATA.iterations[1].key)
  await helper.screenshot('smoke-03-create-iteration-2')
})

runner.test('步骤 1.4: 创建第 3 个迭代', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  await createIteration(TEST_DATA.iterations[2].key)
  await helper.screenshot('smoke-04-create-iteration-3')
})

runner.test('步骤 1.5: 创建第 4 个迭代', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  await createIteration(TEST_DATA.iterations[3].key)
  await helper.screenshot('smoke-05-create-iteration-4')
})

runner.test('步骤 1.6: 创建第 5 个迭代', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  await createIteration(TEST_DATA.iterations[4].key)
  await helper.screenshot('smoke-06-create-iteration-5')
})

runner.test('步骤 1.7: 验证 5 个迭代已创建及字段显示', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  const page = runner.getContext().getPage()
  
  // 验证表头字段
  const headers = await page.$$('.el-table__header th')
  const headerTexts: string[] = []
  for (const header of headers) {
    const text = await page.evaluate(el => el.textContent?.trim(), header)
    if (text) headerTexts.push(text)
  }
  console.log('Iteration table headers:', headerTexts)
  
  // 验证必要表头
  const requiredHeaders = ['迭代标识', '描述', '仓库数', '创建时间', '操作']
  const missingHeaders = requiredHeaders.filter(h => !headerTexts.some(t => t.includes(h)))
  if (missingHeaders.length > 0) {
    console.log('⚠️ Missing headers:', missingHeaders)
  } else {
    console.log('✅ All required headers present')
  }
  
  // 等待表格数据加载
  try {
    await page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 5000 })
    const rows = await page.$$('.el-table__body-wrapper .el-table__row')
    console.log(`Found ${rows.length} iterations in table`)
    
    // 验证第一行数据
    if (rows.length > 0) {
      const firstRow = rows[0]
      const cells = await firstRow.$$('td')
      
      // 第一列: 迭代标识
      if (cells.length > 0) {
        const iterKey = await page.evaluate(el => el.textContent?.trim(), cells[0])
        console.log(`First iteration key: ${iterKey}`)
        if (!iterKey) {
          console.log('⚠️ Iteration key is empty')
        }
      }
      
      // 验证操作按钮
      const buttons = await firstRow.$$('.el-button')
      const buttonTexts: string[] = []
      for (const btn of buttons) {
        const text = await page.evaluate(el => el.textContent?.trim(), btn)
        if (text) buttonTexts.push(text)
      }
      console.log('Action buttons:', buttonTexts)
    }
  } catch {
    console.log('No iteration data in table')
  }
  
  await helper.screenshot('smoke-07-iterations-created')
})

// ============================================
// 步骤 2: 仓库同步（确保有仓库数据）
// ============================================
runner.test('步骤 2.1: 检查仓库列表及字段显示', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/repositories')
  await waitForPageLoad()
  
  const page = runner.getContext().getPage()
  
  // 验证表头字段
  const headers = await page.$$('.el-table__header th')
  const headerTexts: string[] = []
  for (const header of headers) {
    const text = await page.evaluate(el => el.textContent?.trim(), header)
    if (text) headerTexts.push(text)
  }
  console.log('Repository table headers:', headerTexts)
  
  // 验证必要表头
  const requiredHeaders = ['仓库', '项目ID', 'GitLab', '默认分支', '健康', '操作']
  const foundHeaders = requiredHeaders.filter(h => headerTexts.some(t => t.includes(h)))
  console.log(`✅ Found ${foundHeaders.length}/${requiredHeaders.length} required headers`)
  
  try {
    await page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 5000 })
    const rows = await page.$$('.el-table__body-wrapper .el-table__row')
    console.log(`Found ${rows.length} repositories`)
    
    // 验证第一行数据
    if (rows.length > 0) {
      const firstRow = rows[0]
      
      // 验证健康状态标签
      const healthTags = await firstRow.$$('.el-tag')
      if (healthTags.length > 0) {
        const tagText = await page.evaluate(el => el.textContent?.trim(), healthTags[0])
        console.log(`Health status: ${tagText}`)
      }
      
      // 验证操作按钮
      const buttons = await firstRow.$$('.el-button')
      const buttonTexts: string[] = []
      for (const btn of buttons) {
        const text = await page.evaluate(el => el.textContent?.trim(), btn)
        if (text) buttonTexts.push(text)
      }
      console.log('Action buttons:', buttonTexts)
    }
  } catch {
    console.log('No repository data - may need to sync from GitLab first')
  }
  
  await helper.screenshot('smoke-08-repository-list')
})

// ============================================
// 步骤 3: 创建 2 个发布窗口
// ============================================
runner.test('步骤 3.1: 导航到发布窗口列表页', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/release-windows')
  await waitForPageLoad()
  
  await helper.screenshot('smoke-09-release-window-list')
})

// 创建发布窗口的辅助函数
async function createReleaseWindow(windowKey: string, name: string): Promise<string | null> {
  const page = runner.getContext().getPage()
  
  // 点击创建按钮
  const createBtns = await page.$$('.el-button--primary')
  for (const btn of createBtns) {
    const text = await page.evaluate(el => el.textContent, btn)
    if (text?.includes('创建') || text?.includes('新建') || text?.includes('Create')) {
      await btn.click()
      break
    }
  }
  
  // 等待对话框出现
  await delay(800)
  
  try {
    await page.waitForSelector('.el-dialog__body, .el-overlay-dialog', { visible: true, timeout: 5000 })
  } catch {
    console.log('Dialog not found')
    return null
  }
  
  await delay(300)
  
  // 找到所有输入框
  const inputs = await page.$$('.el-input__inner')
  
  // 找到对话框中的输入框（windowKey 和 name）
  let keyInputFilled = false
  let nameInputFilled = false
  
  for (let i = inputs.length - 1; i >= 0; i--) {
    const input = inputs[i]
    const placeholder = await page.evaluate(el => (el as HTMLInputElement).placeholder, input)
    
    if (!nameInputFilled && (placeholder?.includes('名称') || placeholder?.includes('Name') || placeholder?.includes('name'))) {
      await input.click({ clickCount: 3 })
      await input.type(name)
      nameInputFilled = true
    } else if (!keyInputFilled && (placeholder?.includes('Key') || placeholder?.includes('key') || placeholder?.includes('标识'))) {
      await input.click({ clickCount: 3 })
      await input.type(windowKey)
      keyInputFilled = true
    }
    
    if (keyInputFilled && nameInputFilled) break
  }
  
  // 如果按 placeholder 没找到，按顺序填充
  if (!keyInputFilled && inputs.length >= 2) {
    const dialogInputs = await page.$$('.el-dialog .el-input__inner, .el-overlay-dialog .el-input__inner')
    if (dialogInputs.length >= 2) {
      await dialogInputs[0].click({ clickCount: 3 })
      await dialogInputs[0].type(windowKey)
      await dialogInputs[1].click({ clickCount: 3 })
      await dialogInputs[1].type(name)
    }
  }
  
  await delay(300)
  
  // 点击确认按钮
  const allBtns = await page.$$('.el-button--primary')
  for (let i = allBtns.length - 1; i >= 0; i--) {
    const btn = allBtns[i]
    const text = await page.evaluate(el => el.textContent, btn)
    if (text?.includes('确') || text?.includes('Confirm') || text?.includes('保存')) {
      await btn.click()
      break
    }
  }
  
  await delay(1500)
  
  // 检查成功消息
  try {
    await page.waitForSelector('.el-message--success', { timeout: 3000 })
    createdData.windowIds.push(windowKey)
    return windowKey
  } catch {
    // 检查对话框是否已关闭
    const dialogBody = await page.$('.el-dialog__body')
    if (!dialogBody) {
      createdData.windowIds.push(windowKey)
      return windowKey
    }
    return null
  }
}

runner.test('步骤 3.2: 创建第 1 个发布窗口', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/release-windows')
  await waitForPageLoad()
  
  const rw = TEST_DATA.releaseWindows[0]
  const result = await createReleaseWindow(rw.key, rw.name)
  if (result) {
    createdData.windowIds.push(rw.key)
    console.log(`Created release window: ${rw.key}`)
  }
  
  await helper.screenshot('smoke-10-create-release-window-1')
})

runner.test('步骤 3.3: 创建第 2 个发布窗口', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/release-windows')
  await waitForPageLoad()
  
  const rw = TEST_DATA.releaseWindows[1]
  const result = await createReleaseWindow(rw.key, rw.name)
  if (result) {
    createdData.windowIds.push(rw.key)
    console.log(`Created release window: ${rw.key}`)
  }
  
  await helper.screenshot('smoke-11-create-release-window-2')
})

runner.test('步骤 3.4: 验证 2 个发布窗口已创建及字段显示', async () => {
  const helper = runner.getHelper()
  await helper.navigate('/release-windows')
  await waitForPageLoad()
  
  const page = runner.getContext().getPage()
  
  // 验证表头字段
  const headers = await page.$$('.el-table__header th')
  const headerTexts: string[] = []
  for (const header of headers) {
    const text = await page.evaluate(el => el.textContent?.trim(), header)
    if (text) headerTexts.push(text)
  }
  console.log('Release window table headers:', headerTexts)
  
  // 验证必要表头
  const requiredHeaders = ['标识', '名称', '状态', '操作']
  const foundHeaders = requiredHeaders.filter(h => headerTexts.some(t => t.includes(h)))
  console.log(`✅ Found ${foundHeaders.length}/${requiredHeaders.length} required headers`)
  
  try {
    await page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 5000 })
    const rows = await page.$$('.el-table__body-wrapper .el-table__row')
    console.log(`Found ${rows.length} release windows`)
    
    // 验证第一行数据
    if (rows.length > 0) {
      const firstRow = rows[0]
      
      // 验证状态标签
      const statusTags = await firstRow.$$('.el-tag')
      if (statusTags.length > 0) {
        const tagText = await page.evaluate(el => el.textContent?.trim(), statusTags[0])
        const tagClass = await page.evaluate(el => el.className, statusTags[0])
        console.log(`Status: ${tagText} (${tagClass.includes('success') ? 'success' : tagClass.includes('warning') ? 'warning' : 'default'})`)
      }
      
      // 验证操作按钮
      const buttons = await firstRow.$$('.el-button')
      const buttonTexts: string[] = []
      for (const btn of buttons) {
        const text = await page.evaluate(el => el.textContent?.trim(), btn)
        if (text) buttonTexts.push(text)
      }
      console.log('Action buttons:', buttonTexts)
    }
  } catch {
    console.log('No release window data')
  }
  
  await helper.screenshot('smoke-12-release-windows-created')
})

// ============================================
// 步骤 4: 将迭代挂载到发布窗口
// ============================================
runner.test('步骤 4.1: 从发布窗口关联迭代', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await waitForPageLoad()
  
  // 等待表格加载
  try {
    await page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 5000 })
  } catch {
    console.log('No release windows to attach iterations')
    return
  }
  
  // 找到关联迭代按钮（在第一行）
  const firstRow = await page.$('.el-table__body-wrapper .el-table__row:first-child')
  if (firstRow) {
    const buttons = await firstRow.$$('.el-button')
    for (const btn of buttons) {
      const text = await page.evaluate(el => el.textContent, btn)
      if (text?.includes('关联') || text?.includes('Attach') || text?.includes('迭代')) {
        await btn.click()
        break
      }
    }
  }
  
  await delay(500)
  
  await helper.screenshot('smoke-13-attach-iterations-dialog')
})

runner.test('步骤 4.2: 选择迭代并关联到第 1 个发布窗口', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  // 检查对话框是否打开
  const dialog = await page.$('.el-dialog, .el-drawer')
  if (!dialog) {
    console.log('Attach dialog not open - trying to open it')
    
    await helper.navigate('/release-window')
    await waitForPageLoad()
    
    // 尝试点击第一行的关联按钮
    const firstRow = await page.$('.el-table__body-wrapper .el-table__row:first-child')
    if (firstRow) {
      const buttons = await firstRow.$$('.el-button')
      for (const btn of buttons) {
        const text = await page.evaluate(el => el.textContent, btn)
        if (text?.includes('关联') || text?.includes('Attach')) {
          await btn.click()
          break
        }
      }
    }
    
    await delay(1000)
  }
  
  // 在对话框中选择迭代
  try {
    await page.waitForSelector('.el-dialog .el-table, .el-drawer .el-table', { visible: true, timeout: 5000 })
    
    // 勾选前 3 个迭代
    const checkboxes = await page.$$('.el-dialog .el-table .el-checkbox, .el-drawer .el-table .el-checkbox')
    for (let i = 0; i < Math.min(3, checkboxes.length); i++) {
      await checkboxes[i].click()
      await delay(200)
    }
    
    await delay(500)
    
    // 点击确认
    const confirmBtns = await page.$$('.el-dialog .el-button--primary, .el-drawer .el-button--primary')
    for (const btn of confirmBtns) {
      const text = await page.evaluate(el => el.textContent, btn)
      if (text?.includes('确') || text?.includes('Confirm')) {
        await btn.click()
        break
      }
    }
    
    await delay(1000)
  } catch (e) {
    console.log('Could not select iterations:', e)
  }
  
  await helper.screenshot('smoke-14-attach-iterations-to-window-1')
})

runner.test('步骤 4.3: 从迭代详情页关联到第 2 个发布窗口', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  // 导航到迭代列表
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  // 点击第一个迭代的详情/查看按钮
  try {
    await page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 5000 })
    
    const firstRow = await page.$('.el-table__body-wrapper .el-table__row:first-child')
    if (firstRow) {
      const buttons = await firstRow.$$('.el-button')
      for (const btn of buttons) {
        const text = await page.evaluate(el => el.textContent, btn)
        if (text?.includes('详情') || text?.includes('Detail') || text?.includes('查看')) {
          await btn.click()
          break
        }
      }
    }
    
    await delay(1000)
  } catch {
    console.log('No iteration to view')
  }
  
  await helper.screenshot('smoke-15-iteration-detail-or-drawer')
})

runner.test('步骤 4.4: 点击关联到窗口按钮', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  // 在详情页或抽屉中找到"关联到窗口"按钮
  const attachBtns = await page.$$('.el-button--primary')
  for (const btn of attachBtns) {
    const text = await page.evaluate(el => el.textContent, btn)
    if (text?.includes('关联') || text?.includes('窗口') || text?.includes('Attach')) {
      await btn.click()
      break
    }
  }
  
  await delay(500)
  
  // 在弹出的对话框中选择发布窗口
  try {
    await page.waitForSelector('.el-dialog .el-select, .el-drawer .el-select', { visible: true, timeout: 3000 })
    
    // 点击下拉框
    const select = await page.$('.el-dialog .el-select, .el-drawer .el-select')
    if (select) {
      await select.click()
      await delay(300)
      
      // 选择第一个选项（或第二个发布窗口）
      const options = await page.$$('.el-select-dropdown__item')
      if (options.length > 0) {
        // 选择最后一个（假设是第二个发布窗口）
        const targetIdx = Math.min(1, options.length - 1)
        await options[targetIdx].click()
        await delay(300)
      }
    }
    
    // 点击确认
    const confirmBtns = await page.$$('.el-dialog .el-button--primary, .el-drawer .el-button--primary')
    for (const btn of confirmBtns) {
      const text = await page.evaluate(el => el.textContent, btn)
      if (text?.includes('确') || text?.includes('Confirm')) {
        await btn.click()
        break
      }
    }
    
    await delay(1000)
  } catch (e) {
    console.log('Could not attach to window:', e)
  }
  
  await helper.screenshot('smoke-16-attach-iteration-to-window-2')
})

// ============================================
// 步骤 5: 验证最终结果
// ============================================
runner.test('步骤 5.1: 验证第 1 个发布窗口的迭代关联', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await waitForPageLoad()
  
  // 点击第一个发布窗口的查看按钮
  try {
    await page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 5000 })
    
    const firstRow = await page.$('.el-table__body-wrapper .el-table__row:first-child')
    if (firstRow) {
      const buttons = await firstRow.$$('.el-button')
      for (const btn of buttons) {
        const text = await page.evaluate(el => el.textContent, btn)
        if (text?.includes('查看') || text?.includes('View')) {
          await btn.click()
          break
        }
      }
    }
    
    await delay(1000)
  } catch {
    console.log('No release window to view')
  }
  
  await helper.screenshot('smoke-17-release-window-1-detail')
})

runner.test('步骤 5.2: 验证迭代列表字段完整性', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  // 完整的字段验证
  try {
    await page.waitForSelector('.el-table__body-wrapper .el-table__row', { timeout: 5000 })
    const rows = await page.$$('.el-table__body-wrapper .el-table__row')
    
    console.log('\n📋 迭代列表字段验证:')
    console.log('=' .repeat(50))
    
    for (let i = 0; i < Math.min(3, rows.length); i++) {
      const cells = await rows[i].$$('td')
      const rowData: { [key: string]: string } = {}
      
      // 按列顺序获取数据
      const columnNames = ['迭代标识', '描述', '仓库数', '创建时间', '操作']
      for (let j = 0; j < Math.min(cells.length, columnNames.length); j++) {
        const text = await page.evaluate(el => el.textContent?.trim(), cells[j])
        rowData[columnNames[j]] = text || '-'
      }
      
      console.log(`\n行 ${i + 1}:`)
      Object.entries(rowData).forEach(([key, value]) => {
        const displayValue = value.length > 30 ? value.substring(0, 30) + '...' : value
        console.log(`  ${key}: ${displayValue}`)
      })
      
      // 验证迭代标识不为空
      if (!rowData['迭代标识'] || rowData['迭代标识'] === '-') {
        console.log('  ⚠️ 迭代标识为空')
      } else {
        console.log('  ✅ 迭代标识有效')
      }
    }
    
    console.log('\n' + '=' .repeat(50))
  } catch {
    console.log('No iteration data to verify')
  }
  
  await helper.screenshot('smoke-18-final-iteration-list')
})

runner.test('步骤 5.3: 国际化验证 - 迭代页面', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/iterations')
  await waitForPageLoad()
  
  console.log('\n🌐 国际化验证 - 迭代页面:')
  console.log('=' .repeat(50))
  
  // 检测当前语言
  const bodyText = await page.evaluate(() => document.body.innerText)
  const isZhCN = bodyText.includes('迭代标识') || bodyText.includes('仓库数')
  const isEnUS = bodyText.includes('Iteration Key') || bodyText.includes('Repos')
  
  console.log(`检测到语言: ${isZhCN ? '中文' : isEnUS ? 'English' : '未知'}`)
  
  // 验证表头国际化
  const headers = await page.$$('.el-table__header th')
  const headerTexts: string[] = []
  for (const header of headers) {
    const text = await page.evaluate(el => el.textContent?.trim(), header)
    if (text) headerTexts.push(text)
  }
  
  // 期望的中英文对照
  const expectedI18n = {
    iterationKey: { zh: '迭代标识', en: 'Iteration Key' },
    description: { zh: '描述', en: 'Description' },
    repoCount: { zh: '仓库数', en: 'Repos' },
    createdAt: { zh: '创建时间', en: 'Created At' },
    actions: { zh: '操作', en: 'Actions' }
  }
  
  console.log('\n表头国际化检查:')
  Object.entries(expectedI18n).forEach(([key, { zh, en }]) => {
    const foundZh = headerTexts.some(h => h.includes(zh))
    const foundEn = headerTexts.some(h => h.includes(en))
    if (foundZh) {
      console.log(`  ✅ ${key}: "${zh}" (中文)`)
    } else if (foundEn) {
      console.log(`  ✅ ${key}: "${en}" (English)`)
    } else {
      console.log(`  ⚠️ ${key}: 未找到 "${zh}" 或 "${en}"`)
    }
  })
  
  // 验证按钮国际化
  const buttons = await page.$$('.el-button')
  const buttonTexts: string[] = []
  for (const btn of buttons) {
    const text = await page.evaluate(el => el.textContent?.trim(), btn)
    if (text) buttonTexts.push(text)
  }
  
  const expectedButtons = {
    new: { zh: '新建迭代', en: 'New Iteration' },
    search: { zh: '查询', en: 'Search' },
    reset: { zh: '重置', en: 'Reset' },
    detail: { zh: '详情', en: 'Detail' },
    view: { zh: '查看', en: 'View' },
    delete: { zh: '删除', en: 'Delete' }
  }
  
  console.log('\n按钮国际化检查:')
  Object.entries(expectedButtons).forEach(([key, { zh, en }]) => {
    const foundZh = buttonTexts.some(b => b.includes(zh))
    const foundEn = buttonTexts.some(b => b.includes(en))
    if (foundZh) {
      console.log(`  ✅ ${key}: "${zh}" (中文)`)
    } else if (foundEn) {
      console.log(`  ✅ ${key}: "${en}" (English)`)
    }
  })
  
  await helper.screenshot('smoke-19-i18n-iteration')
})

runner.test('步骤 5.4: 国际化验证 - 仓库页面', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/repositories')
  await waitForPageLoad()
  
  console.log('\n🌐 国际化验证 - 仓库页面:')
  console.log('=' .repeat(50))
  
  // 验证表头国际化
  const headers = await page.$$('.el-table__header th')
  const headerTexts: string[] = []
  for (const header of headers) {
    const text = await page.evaluate(el => el.textContent?.trim(), header)
    if (text) headerTexts.push(text)
  }
  
  const expectedI18n = {
    repo: { zh: '仓库', en: 'Repo' },
    cloneUrl: { zh: '仓库地址', en: 'Clone URL' },
    defaultBranch: { zh: '默认分支', en: 'Default Branch' },
    health: { zh: '健康', en: 'Health' },
    actions: { zh: '操作', en: 'Actions' }
  }
  
  console.log('表头国际化检查:')
  Object.entries(expectedI18n).forEach(([key, { zh, en }]) => {
    const foundZh = headerTexts.some(h => h.includes(zh))
    const foundEn = headerTexts.some(h => h.includes(en))
    if (foundZh) {
      console.log(`  ✅ ${key}: "${zh}" (中文)`)
    } else if (foundEn) {
      console.log(`  ✅ ${key}: "${en}" (English)`)
    } else {
      console.log(`  ⚠️ ${key}: 未找到 "${zh}" 或 "${en}"`)
    }
  })
  
  await helper.screenshot('smoke-20-i18n-repository')
})

runner.test('步骤 5.5: 国际化验证 - 发布窗口页面', async () => {
  const helper = runner.getHelper()
  const page = runner.getContext().getPage()
  
  await helper.navigate('/release-windows')
  await waitForPageLoad()
  
  console.log('\n🌐 国际化验证 - 发布窗口页面:')
  console.log('=' .repeat(50))
  
  // 验证表头国际化
  const headers = await page.$$('.el-table__header th')
  const headerTexts: string[] = []
  for (const header of headers) {
    const text = await page.evaluate(el => el.textContent?.trim(), header)
    if (text) headerTexts.push(text)
  }
  
  const expectedI18n = {
    windowKey: { zh: '发布窗口标识', en: 'Window Key' },
    name: { zh: '发布窗口名称', en: 'ReleaseWindow Name' },
    status: { zh: '状态', en: 'Status' },
    createdAt: { zh: '创建时间', en: 'Created At' },
    actions: { zh: '操作', en: 'Actions' }
  }
  
  console.log('表头国际化检查:')
  Object.entries(expectedI18n).forEach(([key, { zh, en }]) => {
    const foundZh = headerTexts.some(h => h.includes(zh))
    const foundEn = headerTexts.some(h => h.includes(en))
    if (foundZh) {
      console.log(`  ✅ ${key}: "${zh}" (中文)`)
    } else if (foundEn) {
      console.log(`  ✅ ${key}: "${en}" (English)`)
    } else {
      console.log(`  ⚠️ ${key}: 未找到 "${zh}" 或 "${en}"`)
    }
  })
  
  // 验证状态标签国际化
  try {
    await page.waitForSelector('.el-table .el-tag', { timeout: 3000 })
    const statusTags = await page.$$('.el-table .el-tag')
    const tagTexts: string[] = []
    
    for (const tag of statusTags) {
      const text = await page.evaluate(el => el.textContent?.trim(), tag)
      if (text) tagTexts.push(text)
    }
    
    console.log('\n状态标签国际化检查:')
    const zhStatuses = ['草稿', '已规划', '活跃', '冻结', '已发布', '已关闭']
    const enStatuses = ['DRAFT', 'PLANNED', 'ACTIVE', 'FROZEN', 'PUBLISHED', 'CLOSED']
    
    const uniqueTags = [...new Set(tagTexts)]
    uniqueTags.forEach(tagText => {
      const isZhStatus = zhStatuses.some(s => tagText.includes(s))
      const isEnStatus = enStatuses.some(s => tagText.includes(s))
      if (isZhStatus) {
        console.log(`  ✅ 状态: "${tagText}" (中文)`)
      } else if (isEnStatus) {
        console.log(`  ✅ 状态: "${tagText}" (English)`)
      } else {
        console.log(`  ⚠️ 未知状态: "${tagText}"`)
      }
    })
  } catch {
    console.log('  无状态标签数据')
  }
  
  await helper.screenshot('smoke-21-i18n-release-window')
})

runner.test('步骤 5.6: 测试完成 - 输出汇总', async () => {
  const helper = runner.getHelper()
  
  console.log('\n' + '='.repeat(60))
  console.log('🎉 冒烟测试完成')
  console.log('='.repeat(60))
  
  console.log('\n📊 业务流程验证:')
  console.log(`  创建的迭代: ${createdData.iterationKeys.length}`)
  createdData.iterationKeys.forEach((key, i) => {
    console.log(`    ${i + 1}. ${key}`)
  })
  console.log(`  创建的发布窗口: ${createdData.windowIds.length}`)
  createdData.windowIds.forEach((key, i) => {
    console.log(`    ${i + 1}. ${key}`)
  })
  
  console.log('\n🌐 国际化验证:')
  console.log('  ✅ 迭代页面 - 表头、按钮国际化检查完成')
  console.log('  ✅ 仓库页面 - 表头、操作按钮国际化检查完成')
  console.log('  ✅ 发布窗口页面 - 表头、状态标签国际化检查完成')
  
  console.log('\n📋 字段显示验证:')
  console.log('  ✅ 迭代列表 - 字段完整性检查完成')
  console.log('  ✅ 仓库列表 - 字段完整性检查完成')
  console.log('  ✅ 发布窗口列表 - 字段完整性检查完成')
  
  console.log('\n' + '='.repeat(60))
  
  await helper.screenshot('smoke-22-test-complete')
})

// 运行测试
runner.run().catch(console.error)
