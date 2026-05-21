/**
 * SA-007: Version update entrance uses inherited version policy.
 *
 * This focused UI journey stubs the release window detail data and verifies the
 * real VersionUpdateDialog loads scoped policies, defaults to the most specific
 * policy, derives the target version, and re-derives when the user changes
 * policy.
 */
import { test, expect } from '@playwright/test'
import type { Page, Route } from '@playwright/test'
import { loadLabels, seedAuthenticatedSession, FORCE } from './helpers.js'

const windowId = 'vp-window-1'
const windowKey = 'RW-VP-001'
const iterationKey = 'ITER-VP-001'
const repoId = 'repo-vp-1'
const repoName = 'repo-vp-policy'
const groupCode = 'G001001'

test.describe('SA-007: Version update policy selection', () => {
  let L: Record<string, string> = {}

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await seedAuthenticatedSession(page)
    L = await loadLabels(page, [
      'releaseWindow.versionUpdate.execute',
      'releaseWindow.versionUpdate.title',
      'releaseWindow.versionUpdate.policy',
      'releaseWindow.versionUpdate.targetVersion'
    ])
    await page.close()
  })

  async function mockReleaseWindowDetail(page: Page) {
    await page.route('**/api/v1/**', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const path = url.pathname

      if (request.method() === 'GET' && path === `/api/v1/release-windows/${windowId}`) {
        await json(route, {
          id: windowId,
          windowKey,
          name: 'Version Policy Window',
          groupCode,
          status: 'DRAFT',
          frozen: false,
          createdAt: '2026-05-22T00:00:00Z',
          updatedAt: '2026-05-22T00:00:00Z'
        })
        return
      }

      if (request.method() === 'GET' && path === `/api/v1/release-windows/${windowId}/iterations`) {
        await json(route, [{ iterationKey }])
        return
      }

      if (request.method() === 'GET' && path === `/api/v1/iterations/${iterationKey}`) {
        await json(route, {
          key: iterationKey,
          name: 'Version Policy Iteration',
          description: '',
          expectedReleaseAt: null,
          groupCode,
          repoIds: [repoId],
          attachedToWindow: true,
          attachedWindowIds: [windowId],
          createdAt: '2026-05-22T00:00:00Z',
          updatedAt: '2026-05-22T00:00:00Z'
        })
        return
      }

      if (request.method() === 'GET' && path === `/api/v1/repositories/${repoId}`) {
        await json(route, repositoryView())
        return
      }

      if (request.method() === 'GET' && path === `/api/v1/repositories/${repoId}/initial-version`) {
        await json(route, {
          repoId,
          version: '1.2.3',
          versionSource: 'POM'
        })
        return
      }

      if (request.method() === 'GET' && path === '/api/v1/version-policies/applicable') {
        expect(url.searchParams.get('scopeProjectId')).toBe(groupCode)
        expect(url.searchParams.get('scopeSubProjectId')).toBe(repoId)
        await json(route, [
          {
            id: 'policy-sub',
            name: 'Repo Minor Policy',
            scheme: 'SEMVER',
            bumpRule: 'MINOR',
            scope: { level: 'SUB_PROJECT', projectId: groupCode, subProjectId: repoId }
          },
          {
            id: 'policy-global',
            name: 'Global Patch Policy',
            scheme: 'SEMVER',
            bumpRule: 'PATCH',
            scope: { level: 'GLOBAL' }
          }
        ])
        return
      }

      if (request.method() === 'POST' && path === `/api/v1/release-windows/${windowId}/validate`) {
        const body = request.postDataJSON()
        await json(route, {
          valid: true,
          derivedVersion: body.policyId === 'policy-global' ? '1.2.4' : '1.3.0',
          derivedBranch: `release/${windowKey}`,
          errorMessage: null
        })
        return
      }

      if (request.method() === 'GET' && path === `/api/v1/release-windows/${windowId}/conflicts`) {
        await json(route, {
          windowId,
          checkedAt: '2026-05-22T00:00:00Z',
          hasConflicts: false,
          totalCount: 0,
          conflicts: []
        })
        return
      }

      if (request.method() === 'GET' && path === `/api/v1/release-windows/${windowId}/plan`) {
        await json(route, [])
        return
      }

      if (request.method() === 'GET' && path === `/api/v1/release-windows/${windowId}/branch-status`) {
        await json(route, {
          windowId,
          windowKey,
          repos: []
        })
        return
      }

      if (request.method() === 'GET' && path === '/api/v1/runs/paged') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 'OK',
            message: 'OK',
            success: true,
            data: [],
            page: { page: 1, size: 5, total: 0 }
          })
        })
        return
      }

      await route.fallback()
    })
  }

  test('loads inherited policy and derives target version in version update dialog', async ({ page }) => {
    await seedAuthenticatedSession(page)
    await mockReleaseWindowDetail(page)

    await page.goto(`/release-windows/${windowId}`)
    const versionUpdateButton = page.getByRole('button', { name: L['releaseWindow.versionUpdate.execute'] })
    await expect(versionUpdateButton).toBeVisible({ timeout: 10000 })
    await versionUpdateButton.evaluate((el: HTMLElement) => el.click())

    const dialog = page.locator('.el-dialog').filter({ hasText: L['releaseWindow.versionUpdate.title'] }).last()
    await expect(dialog).toBeVisible({ timeout: 10000 })
    await expect(dialog).toContainText(repoName)
    await expect(dialog).toContainText('Repo Minor Policy')
    await expect(dialog.getByRole('textbox', { name: L['releaseWindow.versionUpdate.targetVersion'] })).toHaveValue('1.3.0')

    await dialog.locator('.el-form-item').filter({ hasText: L['releaseWindow.versionUpdate.policy'] }).locator('.el-select').click(FORCE)
    const globalPolicyOption = page.getByRole('option', { name: /Global Patch Policy/ })
    await expect(globalPolicyOption).toBeVisible({ timeout: 5000 })
    await globalPolicyOption.click(FORCE)
    await expect(dialog.getByRole('textbox', { name: L['releaseWindow.versionUpdate.targetVersion'] })).toHaveValue('1.2.4')
  })
})

async function json(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      code: 'OK',
      message: 'OK',
      success: true,
      data
    })
  })
}

function repositoryView() {
  return {
    id: repoId,
    name: repoName,
    cloneUrl: `https://gitlab.example.com/customer/${repoName}.git`,
    defaultBranch: 'main',
    groupCode,
    repoType: 'application',
    monoRepo: false,
    gitProvider: 'GITLAB',
    branchCount: 0,
    activeBranchCount: 0,
    nonCompliantBranchCount: 0,
    mrCount: 0,
    openMrCount: 0,
    mergedMrCount: 0,
    closedMrCount: 0,
    lastSyncAt: '2026-05-22T00:00:00Z',
    createdAt: '2026-05-22T00:00:00Z',
    updatedAt: '2026-05-22T00:00:00Z'
  }
}
