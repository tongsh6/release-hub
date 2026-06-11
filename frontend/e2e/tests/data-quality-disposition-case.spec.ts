/**
 * SA-002: Data quality disposition case page journey.
 *
 * No business API route stubs are used here. The user journey starts from the
 * real page, submits a dry-run action through the review UI, creates an audit
 * case, and records manual handling state. API calls are used only as
 * post-journey evidence.
 */
import { test, expect } from '@playwright/test'
import { ensureLoggedIn, loadLabels, FORCE } from './helpers.js'

test.describe('SA-002: data quality disposition case', () => {
  let L: Record<string, string> = {}

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await ensureLoggedIn(page)
    L = await loadLabels(page, [
      'dataQuality.review.title',
      'dataQuality.review.executionBoundary',
      'dataQuality.review.jsonlPlaceholder',
      'dataQuality.review.loadQueue',
      'dataQuality.review.markAllApproved',
      'dataQuality.review.reviewQueue',
      'dataQuality.review.createCase',
      'dataQuality.review.caseTitle',
      'dataQuality.review.caseDetail',
      'dataQuality.review.preStateSnapshot',
      'dataQuality.review.postStateSnapshot',
      'dataQuality.review.startCase',
      'dataQuality.review.verifyCase',
      'common.no'
    ])
    await page.close()
  })

  test('creates, opens, starts, and verifies an audit case from the page', async ({ page }) => {
    const resourceId = `sa002-ui-window-${Date.now()}`
    const sourceReport = `.ai/reports/sa002-disposition-case-ui/${resourceId}/actions.jsonl`
    const dryRunAction = {
      resourceType: 'release_window',
      resourceId,
      riskType: 'DRAFT_WINDOW_REMAINS',
      dataNamespace: 'acceptance',
      reviewBatchId: `sa002-ui-${Date.now()}`,
      assetScope: 'HISTORICAL_ACCEPTANCE',
      retentionPolicy: 'manual-review-then-archive',
      applicationEntry: '/release-windows/{resourceId}',
      preExecutionCheck: '确认发布窗口仍为 DRAFT，并由发布经理判断继续发布、关闭或删除。',
      postExecutionVerification: '复核窗口状态已符合业务决策；如删除，仅通过应用层删除保护完成。',
      reviewDecision: 'PENDING',
      executed: false
    }

    await ensureLoggedIn(page)
    await page.goto('/data-quality/review')
    await expect(page.getByRole('heading', { name: L['dataQuality.review.title'] })).toBeVisible()
    await expect(page.getByText(L['dataQuality.review.executionBoundary'])).toBeVisible()

    await page.getByRole('textbox', { name: /来源报告|Source Report/ }).fill(sourceReport)
    await page.getByPlaceholder(L['dataQuality.review.jsonlPlaceholder']).fill(JSON.stringify(dryRunAction))
    await page.getByRole('button', { name: L['dataQuality.review.loadQueue'] }).click(FORCE)
    await expect(page.getByText(resourceId)).toBeVisible()

    await page.getByRole('button', { name: L['dataQuality.review.markAllApproved'] }).click(FORCE)
    await page.getByRole('button', { name: L['dataQuality.review.reviewQueue'] }).click(FORCE)
    await expect(page.locator('main')).toContainText('ACCEPTED', { timeout: 10000 })
    await expect(page.locator('main')).toContainText('APPLICATION_MANUAL')
    await expect(page.locator('main')).toContainText(L['common.no'])
    await expect(page.getByRole('button', { name: /执行清理|自动清理/ })).toHaveCount(0)

    await page.getByRole('button', { name: L['dataQuality.review.createCase'] }).click(FORCE)
    const drawer = page.locator('.el-drawer:visible')
    await expect(drawer).toBeVisible({ timeout: 10000 })
    await expect(drawer).toContainText(L['dataQuality.review.caseDetail'])
    await expect(drawer).toContainText('PLANNED')
    await expect(page.getByText(L['dataQuality.review.caseTitle'])).toBeVisible()

    await drawer.getByRole('textbox', { name: L['dataQuality.review.preStateSnapshot'] }).fill('{"status":"DRAFT","source":"page-journey"}')
    await drawer.getByRole('button', { name: L['dataQuality.review.startCase'] }).click(FORCE)
    await expect(drawer).toContainText('IN_PROGRESS', { timeout: 10000 })

    await drawer.getByRole('textbox', { name: L['dataQuality.review.postStateSnapshot'] }).fill('{"status":"REVIEWED","source":"page-journey"}')
    await drawer.getByRole('button', { name: L['dataQuality.review.verifyCase'] }).click(FORCE)
    await expect(drawer).toContainText('VERIFIED', { timeout: 10000 })
    await expect(drawer.getByRole('button', { name: L['dataQuality.review.startCase'] })).toBeDisabled()
    await expect(drawer.getByRole('button', { name: L['dataQuality.review.verifyCase'] })).toBeDisabled()

    const evidence = await page.evaluate(async (targetResourceId) => {
      const token = window.localStorage.getItem('RH_TOKEN')
      const response = await fetch('/api/v1/data-quality/disposition-cases', {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
      })
      const body = await response.json()
      return (body.data || []).find((item: { resourceId?: string }) => item.resourceId === targetResourceId)
    }, resourceId)
    expect(evidence).toMatchObject({
      sourceReport,
      resourceType: 'release_window',
      resourceId,
      riskType: 'DRAFT_WINDOW_REMAINS',
      dispositionLevel: 'APPLICATION_MANUAL',
      status: 'VERIFIED',
      requestedBy: 'release-manager',
      handledBy: 'release-manager',
      verifiedBy: 'release-manager'
    })
    expect(evidence.preStateSnapshot).toContain('page-journey')
    expect(evidence.postStateSnapshot).toContain('page-journey')
  })
})
