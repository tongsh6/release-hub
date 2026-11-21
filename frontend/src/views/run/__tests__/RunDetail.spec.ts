vi.mock('@/api/gitOperationApi', () => ({ gitOperationApi: { list: vi.fn().mockResolvedValue([]), listByRun: vi.fn().mockResolvedValue([]), get: vi.fn(), reconcile: vi.fn() } }))
import { shallowMount, mount, flushPromises, enableAutoUnmount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import { http, ApiError } from '@/api/http'
import { ref } from 'vue'
import { saveExecutionIntent, readExecutionIntent, acknowledgeExecutionIntent } from '@/utils/executionIntent'
import RunDetail from '../RunDetail.vue'
import { runApi, type RunItem, type RunTask } from '@/api/runApi'
import { runPlanApi, type RunPlanPreview } from '@/api/runPlanApi'
import { ElMessageBox } from 'element-plus'
import { hasPerm } from '@/utils/perm'

// 当前 Element Plus 声明错误地将返回值写成交叉类型；confirm 运行时返回 action。
const confirmDialog = vi.hoisted(() => vi.fn<() => Promise<'confirm'>>())

const authenticatedUser = vi.hoisted(() => ({ profile: { username: 'admin' } as { username: string } | null }))
const authenticatedProfile = ref(authenticatedUser.profile)
Object.defineProperty(authenticatedUser, 'profile', {
  get: () => authenticatedProfile.value,
  set: (value: { username: string } | null) => { authenticatedProfile.value = value }
})
vi.mock('@/stores/user', () => ({ useUserStore: () => authenticatedUser }))

const routerPush = vi.fn<Router['push']>()
let router: Router
const originalAdapter = http.defaults.adapter
enableAutoUnmount(afterEach)
afterEach(() => { http.defaults.adapter = originalAdapter; vi.useRealTimers() })

vi.mock('vue-i18n', async importOriginal => ({
  ...await importOriginal<typeof import('vue-i18n')>(),
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key)
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  },
  ElMessageBox: {
    confirm: confirmDialog
  }
}))

vi.mock('@/api/runApi', () => ({
  runApi: {
    getRunById: vi.fn(),
    getTasks: vi.fn(),
    retry: vi.fn()
  }
}))

vi.mock('@/api/runPlanApi', () => ({ runPlanApi: { list: vi.fn(), get: vi.fn(), create: vi.fn() } }))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

vi.mock('@/utils/perm', () => ({
  hasPerm: vi.fn(() => true)
}))

const stubs = {
  GitOperationsPanel: true,
  EvidenceChainPanel: true,
  RunPlanSnapshotView: true,
  RunItemScope: true,
  ArrowLeft: true,
  MRFirstTimeline: true,
  DiffViewer: true,
  ElButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>'
  },
  ElDescriptions: {
    template: '<dl><slot /></dl>'
  },
  ElDescriptionsItem: {
    template: '<div><slot /></div>'
  },
  ElAlert: {
    props: ['title'],
    template: '<div>{{ title }}</div>'
  },
  ElTable: {
    template: '<table><slot /></table>'
  },
  ElTableColumn: true,
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElDivider: true,
  ElTimeline: true,
  ElTimelineItem: true
}

const mountRunDetail = () => shallowMount(RunDetail, {
  global: {
    plugins: [router],
    stubs,
    directives: {
      loading: {},
      perm: {}
    }
  }
})

const mountThroughRouter = () => mount({ template: '<router-view />' }, {
  global: { plugins: [router], stubs, directives: { loading: {}, perm: {} } }
})
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}
const planFor = (runId: string): RunPlanPreview => ({
  runId, windowId: 'window-1', operator: 'admin', createdAt: '', planHash: 'hash', executionVersion: 3,
  status: 'PARTIAL_FAILED', evidenceLevel: 'LOCAL_ONLY', executionPermitted: false,
  snapshot: { windowKey: 'RW-1', groupCode: 'G001', windowStatus: 'DRAFT', frozen: false, failFast: true, items: [] }
})
const taskFor = (runId: string): RunTask => ({
  taskId: `task-${runId}`, runId, itemId: 'item-failed', actionType: 'ENSURE_FEATURE', taskOrder: 1,
  status: 'INHERITED', startedAt: null, finishedAt: null, errorMessage: null
})

const selectRetryItems = async (wrapper: Pick<VueWrapper, 'vm' | 'getComponent'>, ids: string[]) => {
  const items = (wrapper.vm as unknown as { detail: { items: RunItem[] } }).detail.items
  const table = wrapper.getComponent('[data-test="retry-items"]') as VueWrapper
  table.vm.$emit('selection-change', items.filter(item => ids.includes(item.id)))
  await wrapper.vm.$nextTick()
}

describe('RunDetail', () => {
  it('showsSavedPreviewWithoutRequestingExecutionTasks', async () => {
    vi.mocked(runApi.getRunById).mockResolvedValueOnce({ id: 'run-1', executionVersion: 2, runType: 'WINDOW_ORCHESTRATION', status: 'PLANNED',
      startedAt: '', finishedAt: '', operator: 'admin', items: [] })
    vi.mocked(runPlanApi.get).mockResolvedValueOnce({ runId: 'run-1', windowId: 'window-1', operator: 'admin', createdAt: '',
      planHash: 'hash', executionVersion: 2, status: 'PLANNED', evidenceLevel: 'LOCAL_ONLY', executionPermitted: false,
      snapshot: { windowKey: 'RW-1', groupCode: 'G001', windowStatus: 'DRAFT', frozen: false, failFast: true, items: [] } })
    const wrapper = mountRunDetail()
    await flushPromises()
    expect(runPlanApi.get).toHaveBeenCalledWith('run-1')
    expect(runApi.getTasks).not.toHaveBeenCalled()
    expect(wrapper.findComponent({ name: 'RunPlanSnapshotView' }).exists()).toBe(true)
  })
  beforeEach(async () => {
    sessionStorage.clear()
    authenticatedUser.profile = { username: 'admin' }
    vi.mocked(runApi.getRunById).mockReset()
    vi.mocked(runApi.getTasks).mockReset()
    vi.mocked(runPlanApi.get).mockReset()
    vi.mocked(runApi.retry).mockReset()
    confirmDialog.mockReset()
    vi.mocked(hasPerm).mockReturnValue(true)
    routerPush.mockReset()
    router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/runs/:runId', name: 'RunDetail', component: RunDetail },
      { path: '/runs', name: 'Runs', component: { template: '<p>Run list</p>' } }
    ] })
    await router.push({ name: 'RunDetail', params: { runId: 'run-1' } })
    await router.isReady()
    routerPush.mockImplementation(router.push.bind(router))
    router.push = routerPush

    vi.mocked(runApi.getTasks).mockResolvedValue([])
    vi.mocked(runApi.retry).mockResolvedValue('run-retry-1')
    confirmDialog.mockResolvedValue('confirm')
    vi.mocked(runApi.getRunById).mockImplementation(async (id) => ({
      id: String(id),
      executionVersion: 3,
      runType: 'WINDOW_ORCHESTRATION',
      status: 'PARTIAL_FAILED',
      startedAt: '',
      finishedAt: '',
      operator: 'tester',
      items: [
        {
          id: 'item-success', metadata: {},
          windowKey: 'WK',
          repoId: 'repo-success',
          iterationKey: 'ITER-1',
          plannedOrder: 1,
          executedOrder: 1,
          finalResult: 'MERGED',
          steps: []
        },
        {
          id: 'item-failed', metadata: {},
          windowKey: 'WK',
          repoId: 'repo-fail',
          iterationKey: 'ITER-1',
          plannedOrder: 2,
          executedOrder: 2,
          finalResult: 'MERGE_BLOCKED',
          steps: []
        }
      ]
    }))
  })


  it('uses the real API envelope as a string receipt before acknowledging and navigating', async () => {
    const actual = await vi.importActual<typeof import('@/api/runApi')>('@/api/runApi')
    vi.mocked(runApi.retry).mockImplementation(actual.runApi.retry)
    http.defaults.adapter = async config => ({
      config, status: 202, statusText: 'Accepted', headers: {},
      data: { success: true, code: '0', message: 'OK', data: 'run-retry-1' }
    })
    const shell = mountThroughRouter()
    await flushPromises()
    const page = shell.getComponent(RunDetail)
    await selectRetryItems(page, ['item-failed'])
    await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.params.runId).toBe('run-retry-1')
    expect(routerPush).toHaveBeenLastCalledWith({ name: 'RunDetail', params: { runId: 'run-retry-1' } })
    expect(readExecutionIntent('admin', 'RETRY:run-1')?.runId).toBe('run-retry-1')
    expect((shell.getComponent(RunDetail).vm as any).detail.id).toBe('run-retry-1')
  })

  it('keeps an unconfirmed intent when the returned receipt is not a string Run ID', async () => {
    const actual = await vi.importActual<typeof import('@/api/runApi')>('@/api/runApi')
    vi.mocked(runApi.retry).mockImplementation(actual.runApi.retry)
    http.defaults.adapter = async config => ({
      config, status: 202, statusText: 'Accepted', headers: {},
      data: { success: true, code: '0', message: 'OK', data: { runId: 'run-retry-1' } }
    })
    const shell = mountThroughRouter()
    await flushPromises()
    const page = shell.getComponent(RunDetail)
    await selectRetryItems(page, ['item-failed'])
    await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.params.runId).toBe('run-1')
    expect(sessionStorage.length).toBe(1)
    expect(routerPush).not.toHaveBeenCalled()
  })

  it('reloads a source Run and browser-back state while the real router reuses the component', async () => {
    const shell = mountThroughRouter()
    await flushPromises()
    const page = shell.getComponent(RunDetail)
    const instance = page.vm.$
    await selectRetryItems(page, ['item-failed'])
    await router.push({ name: 'RunDetail', params: { runId: 'source-run' } })
    await flushPromises()
    expect(shell.getComponent(RunDetail).vm.$).toBe(instance)
    expect((page.vm as any).detail.id).toBe('source-run')
    expect((page.vm as any).selectedRetryItemKeys).toEqual([])
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    await page.findAll('button').find(button => button.text().includes('run.detail.exportJson'))!.trigger('click')
    expect(open).toHaveBeenCalledWith('/api/v1/runs/source-run/export.json', '_blank')
    open.mockRestore()
    router.back()
    await vi.waitFor(() => expect(router.currentRoute.value.params.runId).toBe('run-1'))
    await flushPromises()
    expect(shell.getComponent(RunDetail).vm.$).toBe(instance)
    expect((page.vm as any).detail.id).toBe('run-1')
  })

  it('ignores a late detail response after navigating to another Run', async () => {
    const oldDetail = await runApi.getRunById('run-1')
    const delayed = deferred<typeof oldDetail>()
    vi.mocked(runApi.getRunById).mockImplementationOnce(() => delayed.promise)
    const shell = mountThroughRouter()
    await flushPromises()
    await router.push({ name: 'RunDetail', params: { runId: 'run-2' } })
    await flushPromises()
    delayed.resolve(oldDetail)
    await flushPromises()
    expect((shell.getComponent(RunDetail).vm as any).detail.id).toBe('run-2')
    expect(runApi.getRunById).toHaveBeenCalledWith('run-2')
  })

  it('ignores late plan and task responses belonging to the previous Run', async () => {
    const oldPlan = deferred<RunPlanPreview>()
    const oldTasks = deferred<RunTask[]>()
    vi.mocked(runPlanApi.get).mockImplementation(id =>
      id === 'run-1' ? oldPlan.promise : Promise.resolve(planFor(id)))
    vi.mocked(runApi.getTasks).mockImplementation(id => id === 'run-1' ? oldTasks.promise : Promise.resolve([taskFor(id)]))
    const shell = mountThroughRouter()
    await flushPromises()
    await router.push({ name: 'RunDetail', params: { runId: 'run-2' } })
    await flushPromises()
    oldPlan.resolve(planFor('run-1'))
    oldTasks.resolve([taskFor('run-1')])
    await flushPromises()
    const state = shell.getComponent(RunDetail).vm as any
    expect(state.detail.id).toBe('run-2')
    expect(state.previewPlan.runId).toBe('run-2')
    expect(state.tasks.map((task: RunTask) => task.runId)).toEqual(['run-2'])
  })

  it('does not dispatch a retry from a confirmation resolved after leaving the page', async () => {
    const confirmation = deferred<'confirm'>()
    confirmDialog.mockImplementationOnce(() => confirmation.promise)
    const shell = mountThroughRouter()
    await flushPromises()
    const page = shell.getComponent(RunDetail)
    await selectRetryItems(page, ['item-failed'])
    await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await router.push({ name: 'Runs' })
    await flushPromises()
    confirmation.resolve('confirm')
    await flushPromises()
    expect(runApi.retry).not.toHaveBeenCalled()
    expect(sessionStorage.length).toBe(0)
    expect(router.currentRoute.value.name).toBe('Runs')
  })

  it('acknowledges an already sent retry receipt after unmount without navigating back', async () => {
    const receipt = deferred<string>()
    vi.mocked(runApi.retry).mockImplementationOnce(() => receipt.promise)
    const shell = mountThroughRouter()
    await flushPromises()
    const page = shell.getComponent(RunDetail)
    await selectRetryItems(page, ['item-failed'])
    await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    expect(runApi.retry).toHaveBeenCalledTimes(1)
    expect(sessionStorage.length).toBe(1)
    await router.push({ name: 'Runs' })
    await flushPromises()
    receipt.resolve('run-retry-1')
    await flushPromises()
    expect(readExecutionIntent('admin', 'RETRY:run-1')?.runId).toBe('run-retry-1')
    expect(router.currentRoute.value.name).toBe('Runs')
    expect(runApi.getRunById).not.toHaveBeenCalledWith('run-retry-1')
  })

  it('retries only failed run items from the detail page', async () => {
    const wrapper = mountRunDetail()
    await flushPromises()
    await flushPromises()
    await selectRetryItems(wrapper, ['item-success', 'item-failed'])

    const retryButton = wrapper.findAll('button')
      .find(button => button.text().includes('run.retrySelectedItems'))
    expect(retryButton).toBeTruthy()
    await retryButton!.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      'run.retrySelectionConfirm:{"count":1}',
      'common.warning',
      { type: 'warning' }
    )
    expect(runApi.retry).toHaveBeenCalledWith(
      'run-1',
      ['item-failed'],
      expect.any(String)
    )
    expect(routerPush).toHaveBeenCalledWith({
      name: 'RunDetail',
      params: { runId: 'run-retry-1' }
    })
    expect(runApi.getRunById).toHaveBeenCalledWith('run-retry-1')
  })

  it('shows persisted task outcomes without treating inherited facts as new execution or retry attempts', async () => {
    const task = { runId: 'run-1', itemId: 'item-failed', startedAt: null, finishedAt: null, errorMessage: null }
    vi.mocked(runApi.getTasks).mockResolvedValue([
      { ...task, taskId: 'task-inherited', actionType: 'ENSURE_FEATURE', taskOrder: 1, status: 'INHERITED' },
      { ...task, taskId: 'task-ok', actionType: 'ENSURE_RELEASE', taskOrder: 2, status: 'COMPLETED', startedAt: '2026-11-21T10:00:00Z', finishedAt: '2026-11-21T10:00:01Z' },
      { ...task, taskId: 'task-fail', actionType: 'ENSURE_MR', taskOrder: 3, status: 'FAILED', errorMessage: 'permission denied' },
      { ...task, taskId: 'task-unknown', actionType: 'TRY_MERGE', taskOrder: 4, status: 'UNKNOWN', errorMessage: 'response lost' }
    ])
    const wrapper = mountRunDetail()
    await flushPromises()
    expect((wrapper.vm as any).itemSummary).toEqual({ total: 2, succeeded: 1, failed: 1, skipped: 0, retryable: 1 })
    expect((wrapper.vm as any).taskSummary).toEqual({ total: 4, completed: 1, inherited: 1, failed: 1, unknown: 1 })
    expect((wrapper.vm as any).hasPartialFailure).toBe(true)
    expect(wrapper.findAll('button').some(button => button.text().includes('run.task.retry'))).toBe(false)
    expect(wrapper.html()).not.toContain('retryCount')
  })

  it.each([1, 2])('keeps historical WINDOW protocol %s read-only even when an item failed', async (executionVersion) => {
    const source = await runApi.getRunById('run-1')
    vi.mocked(runApi.getRunById).mockResolvedValueOnce({ ...source, executionVersion })
    const wrapper = mountRunDetail()
    await flushPromises()
    expect(wrapper.findAll('button').some(button => button.text().includes('run.retrySelectedItems'))).toBe(false)
    expect((wrapper.vm as any).itemSummary.retryable).toBe(0)
    expect(runApi.retry).not.toHaveBeenCalled()
  })

  it('keeps VERSION_UPDATE queryable without exposing the window retry protocol', async () => {
    const source = await runApi.getRunById('run-1')
    vi.mocked(runApi.getRunById).mockResolvedValueOnce({ ...source, executionVersion: 2, runType: 'VERSION_UPDATE', status: 'FAILED',
      items: source.items.map(item => ({ ...item, iterationKey: null, finalResult: item.id === 'item-failed' ? 'VERSION_UPDATE_FAILED' : 'VERSION_UPDATE_SUCCESS' })) })
    const wrapper = mountRunDetail()
    await flushPromises()
    expect(wrapper.text()).toContain('run.types.VERSION_UPDATE')
    expect(runPlanApi.get).not.toHaveBeenCalled()
    expect(runApi.getTasks).toHaveBeenCalledWith('run-1')
    expect(wrapper.findAll('button').some(button => button.text().includes('run.retrySelectedItems'))).toBe(false)
    expect((wrapper.vm as any).itemSummary.retryable).toBe(0)
    expect(runApi.retry).not.toHaveBeenCalled()
  })

  it('retains the same retry intent across response loss and remount', async () => {
    vi.mocked(runApi.retry).mockRejectedValueOnce(new Error('response lost'))
    const first = mountRunDetail()
    await flushPromises()
    await selectRetryItems(first, ['item-failed'])
    await first.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    const key = vi.mocked(runApi.retry).mock.calls[0]![2]
    expect(key).toMatch(/^[a-f0-9]{32}$/)
    first.unmount()
    await router.push({ name: 'RunDetail', params: { runId: 'run-1' } })
    await router.isReady()
    const remounted = mountRunDetail()
    await flushPromises()
    await selectRetryItems(remounted, ['item-failed'])
    await remounted.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    expect(runApi.retry).toHaveBeenLastCalledWith('run-1', ['item-failed'], key)
    expect(confirmDialog).toHaveBeenLastCalledWith('run.request.repeatUnconfirmed', 'common.warning', { type: 'warning' })
    expect(readExecutionIntent('admin', 'RETRY:run-1')?.runId).toBe('run-retry-1')
  })

  it('acknowledges a retry receipt before a failed detail read without making another write', async () => {
    const wrapper = mountRunDetail()
    await flushPromises()
    await selectRetryItems(wrapper, ['item-failed'])
    vi.mocked(runApi.getRunById).mockRejectedValueOnce(new Error('read failed'))
    await wrapper.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    expect(runApi.retry).toHaveBeenCalledTimes(1)
    expect(readExecutionIntent('admin', 'RETRY:run-1')?.runId).toBe('run-retry-1')
    expect(wrapper.text()).toContain('run-retry-1')
    expect(wrapper.findAll('button').some(button => button.text().includes('run.retrySelectedItems'))).toBe(false)
  })

  it('does not submit changed selection after the confirmation has opened', async () => {
    let confirm!: (value: 'confirm') => void
    confirmDialog.mockImplementationOnce(() => new Promise(resolve => { confirm = resolve }))
    const wrapper = mountRunDetail()
    await flushPromises()
    await selectRetryItems(wrapper, ['item-failed'])
    await wrapper.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    await selectRetryItems(wrapper, [])
    confirm('confirm')
    await flushPromises()
    expect(runApi.retry).not.toHaveBeenCalled()
  })

  it('does not submit under another authenticated subject after confirmation has opened', async () => {
    let confirm!: (value: 'confirm') => void
    confirmDialog.mockImplementationOnce(() => new Promise(resolve => { confirm = resolve }))
    const wrapper = mountRunDetail()
    await flushPromises()
    await selectRetryItems(wrapper, ['item-failed'])
    await wrapper.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    authenticatedUser.profile = { username: 'other-user' }
    confirm('confirm')
    await flushPromises()
    expect(runApi.retry).not.toHaveBeenCalled()
  })

  it('hides retry action when there are no failed run items', async () => {
    vi.mocked(runApi.getRunById).mockResolvedValue({
      id: 'run-1',
      executionVersion: 1,
      runType: 'ATTACH_ITERATION',
      status: 'DONE',
      startedAt: '',
      finishedAt: '',
      operator: 'tester',
      items: [
        {
          id: 'item-success', metadata: {},
          windowKey: 'WK',
          repoId: 'repo-success',
          iterationKey: 'ITER-1',
          plannedOrder: 1,
          executedOrder: 1,
          finalResult: 'MERGED',
          steps: []
        }
      ]
    })

    const wrapper = mountRunDetail()
    await flushPromises()
    await flushPromises()

    const buttonTexts = wrapper.findAll('button').map(button => button.text())
    expect(buttonTexts.some(text => text.includes('run.retrySelectedItems'))).toBe(false)
  })

  it('never offers ordinary retries for a run with unresolved external effects', async () => {
    vi.mocked(runApi.getRunById).mockResolvedValue({
      id: 'run-1', executionVersion: 3, runType: 'WINDOW_ORCHESTRATION', status: 'NEEDS_REVIEW',
      startedAt: '', finishedAt: '', operator: 'tester',
      items: [
        { id: 'item-unknown', metadata: {}, windowKey: 'WK', repoId: 'repo-unknown', iterationKey: 'ITER-1', plannedOrder: 1, executedOrder: 1, finalResult: 'UNKNOWN', steps: [] },
        { id: 'item-failed', metadata: {}, windowKey: 'WK', repoId: 'repo-failed', iterationKey: 'ITER-1', plannedOrder: 2, executedOrder: 2, finalResult: 'FAILED', steps: [] }
      ]
    })
    vi.mocked(runApi.getTasks).mockResolvedValue([{
      taskId: 'task-unknown', runId: 'run-1', itemId: 'item-unknown', actionType: 'TRY_MERGE', taskOrder: 4,
      status: 'UNKNOWN', startedAt: null, finishedAt: null, errorMessage: 'response lost'
    }])
    const wrapper = mountRunDetail()
    await flushPromises()
    expect(wrapper.findAll('button').some(button => button.text().includes('run.retrySelectedItems'))).toBe(false)
    expect((wrapper.vm as any).taskSummary.unknown).toBe(1)
    expect(wrapper.text()).toContain('run.detail.needsReview')
  })
  it('starts retry selection empty instead of submitting all failed items', async () => {
    const wrapper = mountRunDetail()
    await flushPromises()
    const retryButton = wrapper.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))
    expect(retryButton).toBeTruthy()
    expect(retryButton!.attributes('disabled')).toBeDefined()
    expect(runApi.retry).not.toHaveBeenCalled()
  })

  it('requires explicit earlier blockers for skipped FAIL_FAST items and never expands selection', async () => {
    const common = { windowKey: 'WK', iterationKey: 'ITER-1', executedOrder: 0, steps: [],
      metadata: { 'plan.failFast': 'true' } }
    vi.mocked(runApi.getRunById).mockResolvedValueOnce({
      id: 'run-1', executionVersion: 3, runType: 'WINDOW_ORCHESTRATION', status: 'PARTIAL_FAILED',
      startedAt: '', finishedAt: '', operator: 'tester',
      items: [
        { ...common, id: 'first-blocked', repoId: 'repo-1', plannedOrder: 1, finalResult: 'MERGE_BLOCKED' },
        { ...common, id: 'later-skipped', repoId: 'repo-1', iterationKey: 'ITER-2', plannedOrder: 2, finalResult: 'SKIPPED_DUE_TO_BLOCK' },
        { ...common, id: 'other-failed', repoId: 'repo-2', plannedOrder: 3, finalResult: 'FAILED' }
      ]
    })
    const wrapper = mountRunDetail()
    await flushPromises()
    expect(wrapper.text()).toContain('run.detail.retryFailFastHint')

    await selectRetryItems(wrapper, ['later-skipped'])
    const retryButton = wrapper.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!
    expect(retryButton.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('run.detail.retryMissingPredecessors')
    expect((wrapper.vm as unknown as { selectedRetryItemKeys: string[] }).selectedRetryItemKeys).toEqual(['later-skipped'])
    expect(runApi.retry).not.toHaveBeenCalled()

    await selectRetryItems(wrapper, ['first-blocked', 'later-skipped'])
    expect(retryButton.attributes('disabled')).toBeUndefined()
    await retryButton.trigger('click')
    await flushPromises()
    expect(runApi.retry).toHaveBeenCalledExactlyOnceWith('run-1', ['first-blocked', 'later-skipped'], expect.any(String))
  })

  it('loads the immutable execution snapshot for a completed cleanup run', async () => {
    vi.mocked(runApi.getRunById).mockResolvedValueOnce({
      id: 'run-1', executionVersion: 2, runType: 'WINDOW_CLEANUP', status: 'DONE',
      startedAt: '', finishedAt: '', operator: 'admin', items: []
    })
    vi.mocked(runPlanApi.get).mockResolvedValueOnce({ runId: 'run-1', windowId: 'window-1', operator: 'admin', createdAt: '',
      planHash: 'hash', executionVersion: 2, status: 'DONE', evidenceLevel: 'LOCAL_ONLY', executionPermitted: false,
      snapshot: { windowKey: 'RW-1', groupCode: 'G001', windowStatus: 'PUBLISHED', frozen: false, failFast: true, items: [] } })
    const wrapper = mountRunDetail()
    await flushPromises()
    expect(runPlanApi.get).toHaveBeenCalledWith('run-1')
    expect(wrapper.findComponent({ name: 'RunPlanSnapshotView' }).props('plan').status).toBe('DONE')
  })

  it('identifies cleanup runs and directs failed closeout to evidence review', async () => {
    vi.mocked(runApi.getRunById).mockResolvedValue({
      id: 'run-1', executionVersion: 2, runType: 'WINDOW_CLEANUP', status: 'FAILED',
      startedAt: '', finishedAt: '', operator: 'tester',
      items: [{ id: 'cleanup-item', metadata: {}, windowKey: 'WK', repoId: 'repo-1',
        iterationKey: null, plannedOrder: 1, executedOrder: 1, finalResult: 'FAILED', steps: [] }]
    })
    const wrapper = mountRunDetail()
    await flushPromises()
    expect(wrapper.text()).toContain('run.types.WINDOW_CLEANUP')
    expect(wrapper.text()).toContain('run.detail.cleanupReview')
    expect(wrapper.findAll('button').some(button => button.text().includes('run.retrySelectedItems'))).toBe(false)
  })
  it('retrieves a saved retry without depending on a new selection or the current retry eligibility', async () => {
    const pending = saveExecutionIntent('admin', 'RETRY:run-1', JSON.stringify({ items: ['item-failed'] }))
    const original = await runApi.getRunById('run-1')
    vi.mocked(runApi.getRunById).mockResolvedValue({ ...original, status: 'NEEDS_REVIEW' })
    const page = mountRunDetail()
    await flushPromises()
    expect((page.vm as unknown as { selectedRetryItemKeys: string[] }).selectedRetryItemKeys).toEqual([])
    const recover = page.find('[data-action="retrieve-receipt"]')
    expect(recover.exists()).toBe(true)
    await recover.trigger('click')
    await flushPromises()
    expect(runApi.retry).toHaveBeenCalledExactlyOnceWith('run-1', ['item-failed'], pending.key)
  })

  it.each(['PENDING', 'RUNNING'] as const)('continues observing NEEDS_REVIEW while another repository has %s work', async status => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const original = await runApi.getRunById('run-1')
    vi.mocked(runApi.getRunById).mockResolvedValue({ ...original, status: 'NEEDS_REVIEW', finishedAt: null })
    const unknown: RunTask = { ...taskFor('run-1'), taskId: 'unknown', itemId: 'item-failed', status: 'UNKNOWN' }
    const active: RunTask = { ...taskFor('run-1'), taskId: 'other', itemId: 'item-success', status }
    vi.mocked(runApi.getTasks).mockResolvedValueOnce([unknown, active])
      .mockResolvedValue([unknown, { ...active, status: 'COMPLETED' }])
    const page = mountRunDetail()
    await flushPromises()
    expect(page.text()).toContain('run.detail.needsReview')
    expect(runApi.getTasks).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(runApi.getTasks).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(8000)
    expect(runApi.getTasks).toHaveBeenCalledTimes(2)
    expect(runApi.retry).not.toHaveBeenCalled()
  })

  it('refreshes the same Run without clearing the user selection or reloading its immutable plan', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.mocked(runApi.getTasks).mockResolvedValue([{ ...taskFor('run-1'), status: 'PENDING' }])
    const page = mountRunDetail()
    await flushPromises()
    await selectRetryItems(page, ['item-failed'])
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(runApi.getTasks).toHaveBeenCalledTimes(2)
    expect((page.vm as unknown as { selectedRetryItemKeys: string[] }).selectedRetryItemKeys).toEqual(['item-failed'])
    expect(runPlanApi.get).toHaveBeenCalledTimes(1)
  })


  it('keeps the real table selection when polling replaces item objects', async () => {
    const { ElTable, ElTableColumn } = await vi.importActual<typeof import('element-plus')>('element-plus')
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const original = await runApi.getRunById('run-1')
    vi.mocked(runApi.getRunById).mockImplementation(async id => ({
      ...original, id: String(id), items: original.items.map(item => ({ ...item }))
    }))
    vi.mocked(runApi.getTasks).mockResolvedValue([{ ...taskFor('run-1'), status: 'PENDING' }])
    const page = mount(RunDetail, {
      global: { plugins: [router], components: { ElTable, ElTableColumn },
        stubs: { ...stubs, ElTable: false, ElTableColumn: false },
        directives: { loading: {}, perm: {} } }
    })
    await flushPromises()
    const table = page.getComponent('[data-test="retry-items"]') as VueWrapper
    const failed = (page.vm as unknown as { detail: { items: RunItem[] } }).detail.items.find(item => item.id === 'item-failed')!
    const tableVm = table.vm as unknown as { toggleRowSelection: (row: RunItem, selected: boolean) => void; getSelectionRows: () => RunItem[] }
    tableVm.toggleRowSelection(failed, true)
    await flushPromises()
    expect(tableVm.getSelectionRows().map(item => item.id)).toEqual(['item-failed'])
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(runApi.getTasks).toHaveBeenCalledTimes(2)
    expect(tableVm.getSelectionRows().map(item => item.id)).toEqual(['item-failed'])
    expect((page.vm as unknown as { selectedRetryItemKeys: string[] }).selectedRetryItemKeys).toEqual(['item-failed'])
    await router.push({ name: 'RunDetail', params: { runId: 'run-other' } })
    await flushPromises()
    expect((page.vm as unknown as { selectedRetryItemKeys: string[] }).selectedRetryItemKeys).toEqual([])
    const nextTable = (page.getComponent('[data-test="retry-items"]') as VueWrapper).vm as unknown as { getSelectionRows: () => RunItem[] }
    expect(nextTable.getSelectionRows()).toEqual([])
  })

  it('opens a confirmed retry receipt without submitting another request', async () => {
    const pending = saveExecutionIntent('admin', 'RETRY:run-1', JSON.stringify({ items: ['item-failed'] }))
    acknowledgeExecutionIntent('admin', 'RETRY:run-1', pending.key, 'run-retry-1')
    const page = mountRunDetail()
    await flushPromises()
    await page.get('[data-action="retrieve-receipt"]').trigger('click')
    await flushPromises()
    expect(runApi.retry).not.toHaveBeenCalled()
    expect(confirmDialog).not.toHaveBeenCalled()
    expect(router.currentRoute.value.params.runId).toBe('run-retry-1')
    expect(runApi.getRunById).toHaveBeenCalledWith('run-retry-1')
  })

  it.each(['retry', 'retrieve-receipt'])('uses a receipt acknowledged while the %s confirmation is open without sending again', async action => {
    const receipt = deferred<string>()
    vi.mocked(runApi.retry).mockReturnValueOnce(receipt.promise)
    const first = mountRunDetail()
    await flushPromises()
    await selectRetryItems(first, ['item-failed'])
    await first.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    const originalKey = vi.mocked(runApi.retry).mock.calls[0]![2]
    first.unmount()
    const confirmation = deferred<'confirm'>()
    confirmDialog.mockReturnValueOnce(confirmation.promise)
    const page = mountRunDetail()
    await flushPromises()
    if (action === 'retry') {
      await selectRetryItems(page, ['item-failed'])
      await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    } else await page.get('[data-action="retrieve-receipt"]').trigger('click')
    receipt.resolve('run-retry-1')
    await flushPromises()
    confirmation.resolve('confirm')
    await flushPromises()
    expect(runApi.retry).toHaveBeenCalledTimes(1)
    expect(readExecutionIntent('admin', 'RETRY:run-1')).toMatchObject({ key: originalKey, runId: 'run-retry-1' })
    expect(router.currentRoute.value.params.runId).toBe('run-retry-1')
  })

  it('preserves the same-key retrieval in flight when a late first rejection arrives from the old page', async () => {
    let rejectOriginal!: (reason: unknown) => void
    const receipt = deferred<string>()
    vi.mocked(runApi.retry)
      .mockImplementationOnce(() => new Promise((_, reject) => { rejectOriginal = reject }))
      .mockReturnValueOnce(receipt.promise)
    const first = mountRunDetail()
    await flushPromises()
    await selectRetryItems(first, ['item-failed'])
    await first.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    const originalKey = vi.mocked(runApi.retry).mock.calls[0]![2]
    first.unmount()
    const page = mountRunDetail()
    await flushPromises()
    await page.get('[data-action="retrieve-receipt"]').trigger('click')
    await flushPromises()
    expect(vi.mocked(runApi.retry).mock.calls.map(call => call[2])).toEqual([originalKey, originalKey])
    rejectOriginal(new ApiError({ httpStatus: 400, code: 'RUN_006', message: 'scope was invalid' }))
    await flushPromises()
    const retainedBeforeReceipt = readExecutionIntent('admin', 'RETRY:run-1')
    receipt.resolve('run-retry-1')
    await flushPromises()
    expect(retainedBeforeReceipt).toMatchObject({ key: originalKey })
    expect(readExecutionIntent('admin', 'RETRY:run-1')).toMatchObject({ key: originalKey, runId: 'run-retry-1' })
    expect(router.currentRoute.value.params.runId).toBe('run-retry-1')
    expect(runApi.retry).toHaveBeenCalledTimes(2)
  })

  it('keeps an unresolved retry when a new selection has a different payload', async () => {
    const pending = saveExecutionIntent('admin', 'RETRY:run-1', JSON.stringify({ items: ['item-blocked'] }))
    const page = mountRunDetail()
    await flushPromises()
    await selectRetryItems(page, ['item-failed'])
    await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    expect(runApi.retry).not.toHaveBeenCalled()
    expect(confirmDialog).not.toHaveBeenCalled()
    expect(readExecutionIntent('admin', 'RETRY:run-1')).toEqual(pending)
    expect(page.find('[data-action="retrieve-receipt"]').exists()).toBe(true)
  })

  it('stops an active poll when routing away and discards the old Run response', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const original = await runApi.getRunById('run-1')
    vi.mocked(runApi.getTasks).mockResolvedValue([{ ...taskFor('run-1'), status: 'PENDING' }])
    const shell = mountThroughRouter()
    await flushPromises()
    const late = deferred<typeof original>()
    vi.mocked(runApi.getRunById).mockImplementationOnce(() => late.promise)
    await vi.advanceTimersByTimeAsync(2000)
    await router.push({ name: 'Runs' })
    await flushPromises()
    late.resolve({ ...original, status: 'DONE' })
    await flushPromises()
    const reads = vi.mocked(runApi.getRunById).mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(runApi.getRunById).toHaveBeenCalledTimes(reads)
    expect(shell.findComponent(RunDetail).exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('Runs')
    expect(runApi.retry).not.toHaveBeenCalled()
  })

  it.each(['empty', 'wrong-run', 'read-error'])('pauses on %s task evidence without dispatching writes', async failure => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const original = await runApi.getRunById('run-1')
    vi.mocked(runApi.getRunById).mockResolvedValue({ ...original, status: 'RUNNING' })
    if (failure === 'empty') vi.mocked(runApi.getTasks).mockResolvedValue([])
    if (failure === 'wrong-run') vi.mocked(runApi.getTasks).mockResolvedValue([taskFor('run-other')])
    if (failure === 'read-error') vi.mocked(runApi.getTasks).mockRejectedValue(new Error('offline'))
    const page = mountRunDetail()
    await flushPromises()
    expect(page.find('[data-test="run-observation-paused"]').exists()).toBe(true)
    expect((page.vm as unknown as { detail: { status: string } }).detail.status).toBe('RUNNING')
    await vi.advanceTimersByTimeAsync(10_000)
    expect(runApi.getTasks).toHaveBeenCalledTimes(1)
    expect(runApi.retry).not.toHaveBeenCalled()
  })

  it('pauses bounded observation of a stuck RUNNING task without retrying or manufacturing failure', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const original = await runApi.getRunById('run-1')
    vi.mocked(runApi.getRunById).mockResolvedValue({ ...original, status: 'RUNNING', finishedAt: null })
    vi.mocked(runApi.getTasks).mockResolvedValue([{ ...taskFor('run-1'), status: 'RUNNING' }])
    const page = mountRunDetail()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(300_000)
    await flushPromises()
    expect(page.find('[data-test="run-observation-paused"]').exists()).toBe(true)
    expect((page.vm as unknown as { detail: { status: string } }).detail.status).toBe('RUNNING')
    const reads = vi.mocked(runApi.getTasks).mock.calls.length
    await vi.advanceTimersByTimeAsync(10_000)
    expect(runApi.getTasks).toHaveBeenCalledTimes(reads)
    expect(runApi.retry).not.toHaveBeenCalled()
  })


  it('permits an explicitly revised retry after its first new intent is rejected before admission', async () => {
    vi.mocked(runApi.retry).mockRejectedValueOnce(new ApiError({ httpStatus: 400, code: 'RUN_006', message: '范围失效' }))
    const page = mountRunDetail()
    await flushPromises()
    await selectRetryItems(page, ['item-failed'])
    await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    const originalKey = vi.mocked(runApi.retry).mock.calls[0]?.[2]
    expect(readExecutionIntent('admin', 'RETRY:run-1')).toBeNull()
    await selectRetryItems(page, ['item-failed'])
    await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    expect(runApi.retry).toHaveBeenCalledTimes(2)
    expect(vi.mocked(runApi.retry).mock.calls[1]?.[2]).not.toBe(originalKey)
    expect(confirmDialog).toHaveBeenCalledTimes(2)
  })

  it('keeps a retry retrieval unresolved even when it receives RUN_006 after response loss', async () => {
    vi.mocked(runApi.retry).mockRejectedValueOnce(new Error('lost'))
      .mockRejectedValueOnce(new ApiError({ httpStatus: 400, code: 'RUN_006', message: '范围失效' }))
    const page = mountRunDetail()
    await flushPromises()
    await selectRetryItems(page, ['item-failed'])
    await page.findAll('button').find(button => button.text().includes('run.retrySelectedItems'))!.trigger('click')
    await flushPromises()
    const original = readExecutionIntent('admin', 'RETRY:run-1')
    await page.get('[data-action="retrieve-receipt"]').trigger('click')
    await flushPromises()
    expect(readExecutionIntent('admin', 'RETRY:run-1')).toEqual(original)
    expect(vi.mocked(runApi.retry).mock.calls.map(call => call[2])).toEqual([original?.key, original?.key])
  })

  it('resumes after visibility changes between an old RUNNING Run snapshot and completed Task snapshots', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    try {
      const original = await runApi.getRunById('run-1')
      vi.mocked(runApi.getRunById).mockResolvedValueOnce({ ...original, status: 'RUNNING' })
        .mockResolvedValue({ ...original, status: 'DONE' })
      vi.mocked(runApi.getTasks).mockResolvedValue([{ ...taskFor('run-1'), status: 'COMPLETED' }])
      const page = mountRunDetail()
      await flushPromises()
      hidden.mockReturnValue(true)
      document.dispatchEvent(new Event('visibilitychange'))
      await flushPromises()
      expect(page.find('[data-test="run-observation-paused"]').exists()).toBe(true)
      hidden.mockReturnValue(false)
      document.dispatchEvent(new Event('visibilitychange'))
      await flushPromises()
      expect(runApi.getTasks).toHaveBeenCalledTimes(2)
      expect((page.vm as unknown as { detail: { status: string } }).detail.status).toBe('DONE')
      await vi.advanceTimersByTimeAsync(10_000)
      expect(runApi.getTasks).toHaveBeenCalledTimes(2)
      expect(runApi.retry).not.toHaveBeenCalled()
    } finally { hidden.mockRestore() }
  })

  it('stops polling and rejects its late response after the authenticated subject changes', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.mocked(runApi.getTasks).mockResolvedValue([{ ...taskFor('run-1'), status: 'PENDING' }])
    const page = mountRunDetail()
    await flushPromises()
    const old = await runApi.getRunById('run-1')
    const late = deferred<typeof old>()
    vi.mocked(runApi.getRunById).mockImplementationOnce(() => late.promise)
    await vi.advanceTimersByTimeAsync(2000)
    authenticatedUser.profile = null
    await flushPromises()
    late.resolve({ ...old, status: 'DONE' })
    await flushPromises()
    expect((page.vm as unknown as { detail?: unknown }).detail).toBeUndefined()
    const reads = vi.mocked(runApi.getRunById).mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(runApi.getRunById).toHaveBeenCalledTimes(reads)
    expect(runApi.retry).not.toHaveBeenCalled()
  })

})
