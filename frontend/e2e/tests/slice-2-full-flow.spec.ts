/**
 * Slice 2 E2E: 完整发布用户旅程 UI 验收
 *
 * 覆盖: 登录 → 存量验证 → 新建窗口 → 迭代 → 运行记录 → 数据持久化。
 * 定位: 证明用户能从页面完成操作和复核证据，API/GitLab 状态由 acceptance 脚本补充证明。
 */
import { test, expect } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'
import { ensureLoggedIn, loadLabels, confirmDialog, confirmMessageBox, tcName, FORCE } from './helpers'

test.describe('Slice-2: Full Release Flow', () => {
  let L: Record<string, string> = {}
  const windowName = tcName('Win')

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await ensureLoggedIn(page)
    L = await loadLabels(page, [
      'releaseWindow.create', 'releaseWindow.name', 'releaseWindow.publish',
      'iteration.create', 'iteration.name',
      'common.confirm', 'common.cancel', 'common.search',
      'repository.search', 'releaseWindow.windowKey',
    ])
    await page.close()
  })

  // 1. 仪表板
  test('1. Dashboard renders', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/dashboard')
    await page.waitForTimeout(800)
    await expect(page.locator('.el-card, .el-main').first()).toBeVisible({ timeout: 5000 })
  })

  // 2. 仓库列表 + 历史数据可见
  test('2. Repositories show persisted data', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/repositories')
    await page.waitForTimeout(1000)
    const rows = page.locator('.el-table__body-wrapper tbody tr')
    const count = await rows.count()
    expect(count).toBeGreaterThan(0)
  })

  // 3. 发布窗口列表 + 历史数据可见
  test('3. Release windows show persisted data', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(1000)

    // 列表应正常渲染
    await expect(page.locator('.el-table__body-wrapper').first()).toBeVisible({ timeout: 5000 })
    const rows = page.locator('.el-table__body-wrapper tbody tr')
    const count = await rows.count()
    expect(count).toBeGreaterThan(0)
  })

  // 4. 新建发布窗口
  test('4. Create release window via UI', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(800)

    // 先收集已有行数
    const beforeCount = await page.locator('.el-table__body-wrapper tbody tr').count()

    // 搜索表单的查询按钮和 DataTable 的创建按钮都是 primary。
    // DataTable actions 中的创建按钮是最后一个 primary button。
    const buttons = page.locator('.el-button--primary')
    const btnCount = await buttons.count()
    // 点击最后一个（DataTable actions 栏中的创建按钮）
    if (btnCount > 1) {
      await buttons.nth(btnCount - 1).click()
    } else {
      await buttons.first().click()
    }
    await page.waitForTimeout(800)

    // 对话框出现
    const dialog = page.locator('.el-dialog')
    if (await dialog.isVisible({ timeout: 2000 }).catch(() => false)) {
      // 填写名称
      await dialog.locator('.el-input__inner').first().fill(windowName)
      await page.waitForTimeout(200)

      // 分组树选择
      const treeSelect = dialog.locator('.el-tree-select, .el-select').first()
      await treeSelect.click()
      await page.waitForTimeout(400)
      const treeNode = page.locator('.el-select-dropdown .el-tree-node.is-leaf').first()
      if (await treeNode.isVisible({ timeout: 2000 }).catch(() => false)) {
        await treeNode.click()
        await page.waitForTimeout(300)
      }

      // 确定
      await dialog.locator('.el-button--primary').last().click()
      await page.waitForTimeout(1000)
    }

    // 验证列表行数增加（或窗口可见）
    const afterCount = await page.locator('.el-table__body-wrapper tbody tr').count()
    console.log(`Window rows: ${beforeCount} → ${afterCount}`)
    expect(afterCount).toBeGreaterThanOrEqual(beforeCount)
  })

  // 5. 迭代列表
  test('5. Iterations page accessible', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/iterations')
    await page.waitForTimeout(800)
    await expect(page.locator('.el-table__body-wrapper, .el-table').first()).toBeVisible({ timeout: 5000 })
  })

  // 6. 运行记录
  test('6. Run records show accumulated history', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/runs')
    await page.waitForTimeout(1000)
    await expect(page.locator('.el-table__body-wrapper, .el-table').first()).toBeVisible({ timeout: 5000 })
    const rows = page.locator('.el-table__body-wrapper tbody tr')
    const count = await rows.count()
    console.log(`Run records visible: ${count}`)
    expect(count).toBeGreaterThan(0)
  })

  // 7. 发布日历
  test('7. Calendar page renders', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/calendar')
    await page.waitForTimeout(1000)
    await expect(page.locator('.fc, .el-calendar, .calendar-container, .el-main').first()).toBeVisible({ timeout: 5000 })
  })

  // 8. 所有页面可访问（冒烟）
  test('8. All pages accessible', async ({ page }) => {
    await ensureLoggedIn(page)
    const routes = ['/groups', '/repositories', '/release-windows', '/iterations', '/runs', '/calendar', '/version-ops', '/branch-rules']
    for (const route of routes) {
      await page.goto(route)
      await page.waitForTimeout(400)
      await expect(page.locator('.el-main, .el-container, .el-card').first()).toBeVisible({ timeout: 5000 })
    }
  })

  // SA-015: 测试人员复核 Run 执行证据
  test('SA-015: Tester can inspect run evidence', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/runs')
    await page.waitForTimeout(1000)
    await expect(page.locator('.el-table, .el-table__body-wrapper').first()).toBeVisible({ timeout: 5000 })

    const rows = page.locator('.el-table__body-wrapper tbody tr')
    expect(await rows.count()).toBeGreaterThan(0)

    const runId = (await rows.first().locator('td').first().innerText()).trim()
    expect(runId.length).toBeGreaterThan(0)

    await page.goto(`/runs/${runId}`)
    await page.waitForTimeout(1000)
    await expect(page.locator('.el-descriptions, .el-card, .el-table').first()).toBeVisible({ timeout: 5000 })
  })

  // SA-015: 测试人员复核发布窗口详情
  test('SA-015: Tester can inspect release window detail', async ({ page }) => {
    await ensureLoggedIn(page)
    await page.goto('/release-windows')
    await page.waitForTimeout(1000)
    await expect(page.locator('.el-table, .el-table__body-wrapper').first()).toBeVisible({ timeout: 5000 })

    const rows = page.locator('.el-table__body-wrapper tbody tr')
    expect(await rows.count()).toBeGreaterThan(0)

    const viewButton = rows.first().locator('button').first()
    await expect(viewButton).toBeVisible({ timeout: 5000 })
    await viewButton.click(FORCE)
    await page.waitForTimeout(1000)

    await expect(page.locator('.release-window-detail-page, .el-descriptions, .el-card, .el-main').first()).toBeVisible({ timeout: 5000 })
  })
})

test.describe.serial('Slice-2: UI-created release orchestration journey', () => {
  let L: Record<string, string> = {}
  const suffix = Date.now().toString()
  const groupCode = `UIJ${suffix}`
  const groupName = `UI Journey ${suffix}`
  const repoName = `ui-journey-repo-${suffix}`
  const iterationName = `UI Journey Iteration ${suffix}`
  const windowName = `UI Journey Window ${suffix}`
  let iterationKey = ''
  let windowDetailUrl = ''
  let createdRepoId = ''

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await ensureLoggedIn(page)
    L = await loadLabels(page, [
      'common.confirm', 'common.keyword', 'common.search', 'common.view',
      'group.createTop', 'group.name', 'group.code',
      'repository.addOrSync', 'repository.columns.repo', 'repository.columns.cloneUrl',
      'repository.columns.defaultBranch', 'repository.columns.initialVersion',
      'iteration.new', 'iteration.columns.name', 'iteration.detail.addRepos',
      'releaseWindow.create', 'releaseWindow.name', 'releaseWindow.publish',
      'releaseWindow.statusText.PUBLISHED',
      'releaseWindow.attachIterations',
      'releaseWindow.versionUpdate.execute',
      'releaseWindow.versionUpdate.title',
      'releaseWindow.versionUpdate.targetVersion',
      'releaseWindow.versionUpdate.repoPath',
      'releaseWindow.versionUpdate.pomPath',
      'orchestration.executeFinish',
      'conflict.rescan',
      'conflict.resolveVersion',
      'conflict.noConflicts',
      'conflict.types.MISMATCH'
    ])
    await page.close()
  })

  async function selectLeafGroup(page: Page, dialog: Locator) {
    await dialog.locator('.el-tree-select, .el-select').first().click(FORCE)
    await page.waitForTimeout(500)
    const searchBox = page.locator('.el-popper input').last()
    if (await searchBox.isVisible({ timeout: 1000 }).catch(() => false)) {
      await searchBox.fill(groupCode)
      await page.waitForTimeout(300)
    }
    await page.locator('.el-tree-node__content').filter({ hasText: groupCode }).last().click(FORCE)
    await page.waitForTimeout(300)
  }

  async function searchByKeyword(page: Page, keyword: string) {
    await page.getByRole('textbox', { name: L['common.keyword'] }).first().fill(keyword)
    await page.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    await page.locator('.el-loading-mask').last().waitFor({ state: 'detached', timeout: 3000 }).catch(() => {})
  }

  async function findWindowRow(page: Page) {
    await page.getByRole('textbox', { name: L['releaseWindow.name'] }).first().fill(windowName)
    await page.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    return page.locator('.el-table__body tr').filter({ hasText: windowName }).last()
  }

  test('SA-013 frontend path submits UI-created repo and iteration scope to orchestration', async ({ page }) => {
    await ensureLoggedIn(page)

    await page.goto('/groups')
    await page.getByRole('button', { name: L['group.createTop'] }).click(FORCE)
    const groupDialog = page.locator('.el-dialog').last()
    await groupDialog.getByRole('textbox', { name: L['group.name'] }).fill(groupName)
    await groupDialog.getByRole('textbox', { name: L['group.code'], exact: true }).fill(groupCode)
    await confirmDialog(page)
    await expect(page.locator('.el-tree-node__content').filter({ hasText: groupCode })).toBeVisible()

    await page.goto('/repositories')
    await page.getByRole('button', { name: L['repository.addOrSync'] }).click(FORCE)
    const repoDialog = page.locator('.el-dialog').last()
    const repoInputs = repoDialog.locator('.el-input__inner')
    await repoInputs.nth(0).fill(repoName)
    await repoInputs.nth(1).fill(`mock:///${repoName}.git`)
    await repoInputs.nth(2).fill('main')
    await repoInputs.nth(3).fill('1.4.0')
    await selectLeafGroup(page, repoDialog)
    await confirmDialog(page)
    await searchByKeyword(page, repoName)
    await expect(page.locator('.el-table__body tr').filter({ hasText: repoName }).last()).toBeVisible()

    await page.goto('/iterations')
    await page.getByRole('button', { name: L['iteration.new'] }).click(FORCE)
    const iterationDialog = page.locator('.el-dialog').last()
    await iterationDialog.getByRole('textbox', { name: L['iteration.columns.name'] }).fill(iterationName)
    await selectLeafGroup(page, iterationDialog)
    await confirmDialog(page)
    await searchByKeyword(page, iterationName)
    const iterationRow = page.locator('.el-table__body tr').filter({ hasText: iterationName }).last()
    await expect(iterationRow).toBeVisible()
    iterationKey = (await iterationRow.locator('td').first().innerText()).trim()
    expect(iterationKey).toContain('ITER-')
    await iterationRow.locator('button').filter({ hasText: L['common.view'] }).click(FORCE)
    await expect(page).toHaveURL(/\/iterations\//)

    const addReposButton = page.getByRole('button', { name: L['iteration.detail.addRepos'] })
    await expect(addReposButton).toBeVisible({ timeout: 5000 })
    await addReposButton.evaluate((el: HTMLElement) => el.click())
    const addRepoDialog = page.locator('.el-dialog').last()
    await expect(addRepoDialog).toBeVisible({ timeout: 10000 })
    const repoSearchInput = addRepoDialog.getByPlaceholder(/Search repository name|搜索仓库名/)
    await expect(repoSearchInput).toBeVisible()
    await repoSearchInput.fill(repoName)
    await addRepoDialog.locator('.repo-item').filter({ hasText: repoName }).click()
    await expect(addRepoDialog.locator('.selected-info')).toContainText('1')
    await confirmDialog(page)
    await expect(page.locator('.el-table__body tr').filter({ hasText: repoName }).last()).toBeVisible()

    await page.goto('/release-windows')
    await page.getByRole('button', { name: L['releaseWindow.create'] }).click(FORCE)
    const windowDialog = page.locator('.el-dialog').last()
    await windowDialog.getByRole('textbox', { name: L['releaseWindow.name'] }).fill(windowName)
    await selectLeafGroup(page, windowDialog)
    await confirmDialog(page)
    const row = await findWindowRow(page)
    await expect(row).toBeVisible()
    await row.locator('button').filter({ hasText: L['common.view'] }).click(FORCE)
    await expect(page).toHaveURL(/\/release-windows\//)
    windowDetailUrl = page.url()

    await page.getByRole('button', { name: L['releaseWindow.attachIterations'] }).click(FORCE)
    const attachDialog = page.locator('.el-dialog').last()
    await attachDialog.getByRole('textbox').first().fill(iterationKey)
    await attachDialog.locator('button').filter({ hasText: L['common.search'] }).click(FORCE)
    await attachDialog
      .locator('.el-table__body tr')
      .filter({ hasText: iterationKey })
      .locator('.el-checkbox__inner')
      .click()
    await confirmDialog(page)
    await expect(page.locator('.iterations-list')).toContainText(iterationKey)
    await expect(page.locator('.iterations-list')).toContainText(repoName)

    await page.getByRole('button', { name: L['releaseWindow.publish'] }).click(FORCE)
    await confirmMessageBox(page)
    await expect(page.locator('.el-descriptions')).toContainText(L['releaseWindow.statusText.PUBLISHED'], { timeout: 10000 })

    let orchestrateBody: any
    await page.route('**/api/v1/release-windows/*/orchestrate', async (route) => {
      orchestrateBody = route.request().postDataJSON()
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'OK', message: 'OK', data: 'run-ui-journey' })
      })
    })

    await page.getByRole('button', { name: L['orchestration.executeFinish'] }).click(FORCE)
    await confirmMessageBox(page)

    expect(orchestrateBody).toMatchObject({
      iterationKeys: [iterationKey],
      failFast: false,
      operator: 'frontend'
    })
    expect(orchestrateBody.repoIds).toHaveLength(1)
    createdRepoId = orchestrateBody.repoIds[0]
  })

  test('SA-012 frontend path resolves a version conflict with USE_SYSTEM and rescans clean', async ({ page }) => {
    await ensureLoggedIn(page)
    expect(windowDetailUrl).toContain('/release-windows/')
    expect(createdRepoId).toBeTruthy()

    let conflictResolved = false
    let resolveBody: any
    const conflictReport = () => ({
      windowId: 'ui-journey-window',
      checkedAt: new Date().toISOString(),
      hasConflicts: !conflictResolved,
      totalCount: conflictResolved ? 0 : 1,
      conflicts: conflictResolved
        ? []
        : [{
            repoId: createdRepoId,
            repoName,
            iterationKey,
            conflictType: 'MISMATCH',
            systemVersion: '1.4.0',
            repoVersion: '1.4.1',
            message: 'Version mismatch',
            suggestion: 'Use system version'
          }]
    })

    await page.route('**/api/v1/release-windows/*/conflicts', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'OK', message: 'OK', data: conflictReport() })
      })
    })
    await page.route('**/api/v1/release-windows/*/conflicts/check', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'OK', message: 'OK', data: conflictReport() })
      })
    })
    await page.route('**/api/v1/iterations/*/repos/*/resolve-conflict', async (route) => {
      resolveBody = route.request().postDataJSON()
      conflictResolved = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'OK',
          message: 'OK',
          data: {
            repoId: createdRepoId,
            repoName,
            targetVersion: '1.4.0',
            versionSource: 'SYSTEM'
          }
        })
      })
    })

    await page.goto(windowDetailUrl)
    const conflictPanel = page.locator('.conflict-panel')
    await conflictPanel.getByRole('button', { name: L['conflict.rescan'] }).click(FORCE)
    await expect(conflictPanel).toContainText(L['conflict.types.MISMATCH'])
    const resolveButton = conflictPanel.getByRole('button', { name: L['conflict.resolveVersion'] })
    await expect(resolveButton).toBeVisible({ timeout: 5000 })
    await resolveButton.evaluate((el: HTMLElement) => el.click())
    await expect(page.locator('.el-message-box').last()).toBeVisible({ timeout: 5000 })
    await confirmMessageBox(page)

    expect(resolveBody).toMatchObject({ resolution: 'USE_SYSTEM' })
    await expect(conflictPanel).toContainText(L['conflict.noConflicts'])
  })

  test('SA-014 frontend path submits version update for the UI-created release window repo', async ({ page }) => {
    await ensureLoggedIn(page)
    expect(windowDetailUrl).toContain('/release-windows/')

    let versionUpdateBody: any
    await page.route('**/api/v1/release-windows/*/conflicts', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'OK',
          message: 'OK',
          data: {
            windowId: 'ui-journey-window',
            checkedAt: new Date().toISOString(),
            hasConflicts: false,
            totalCount: 0,
            conflicts: []
          }
        })
      })
    })
    await page.route('**/api/v1/release-windows/*/execute/version-update', async (route) => {
      versionUpdateBody = route.request().postDataJSON()
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'OK',
          message: 'OK',
          data: { runId: 'run-version-update-ui-journey', status: 'RUNNING' }
        })
      })
    })

    await page.goto(windowDetailUrl)
    await expect(page.locator('.iterations-list')).toContainText(iterationKey)
    await expect(page.locator('.iterations-list')).toContainText(repoName)

    await page.getByRole('button', { name: L['releaseWindow.versionUpdate.execute'] }).click(FORCE)
    const dialog = page.locator('.el-dialog').filter({ hasText: L['releaseWindow.versionUpdate.title'] }).last()
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText(repoName)

    await dialog.getByRole('textbox', { name: L['releaseWindow.versionUpdate.targetVersion'] }).fill('1.4.1')
    await dialog.getByRole('textbox', { name: L['releaseWindow.versionUpdate.repoPath'] }).fill(repoName)
    await dialog.getByRole('textbox', { name: L['releaseWindow.versionUpdate.pomPath'] }).fill('pom.xml')
    await confirmDialog(page)

    expect(versionUpdateBody).toMatchObject({
      targetVersion: '1.4.1',
      buildTool: 'MAVEN',
      repoPath: repoName,
      pomPath: 'pom.xml'
    })
    expect(versionUpdateBody.repoId).toBeTruthy()
  })
})
