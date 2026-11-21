vi.mock('@/api/gitOperationApi', () => ({ gitOperationApi: { list: vi.fn().mockResolvedValue([]), listByRun: vi.fn().mockResolvedValue([]), get: vi.fn(), reconcile: vi.fn() } }))
import { shallowMount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RunDetail from '../RunDetail.vue'
import { runApi, type RunItem } from '@/api/runApi'
import { runPlanApi } from '@/api/runPlanApi'
import { ElMessageBox } from 'element-plus'
import { hasPerm } from '@/utils/perm'

// 当前 Element Plus 声明错误地将返回值写成交叉类型；confirm 运行时返回 action。
const confirmDialog = vi.hoisted(() => vi.fn<() => Promise<'confirm'>>())

const routerPush = vi.fn()

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key)
  })
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { runId: 'run-1' } }),
  useRouter: () => ({ push: routerPush })
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
    retry: vi.fn(),
    retryTask: vi.fn()
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
    stubs,
    directives: {
      loading: {},
      perm: {}
    }
  }
})

const selectRetryItems = async (wrapper: ReturnType<typeof mountRunDetail>, ids: string[]) => {
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
  beforeEach(() => {
    vi.mocked(runApi.getRunById).mockReset()
    vi.mocked(runApi.getTasks).mockReset()
    vi.mocked(runPlanApi.get).mockReset()
    vi.mocked(runApi.retry).mockReset()
    confirmDialog.mockReset()
    vi.mocked(hasPerm).mockReturnValue(true)
    routerPush.mockReset()

    vi.mocked(runApi.getTasks).mockResolvedValue([])
    vi.mocked(runApi.retry).mockResolvedValue('run-retry-1')
    confirmDialog.mockResolvedValue('confirm')
    vi.mocked(runApi.getRunById).mockImplementation(async (id) => ({
      id: String(id),
      executionVersion: 1,
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
      ['item-failed']
    )
    expect(routerPush).toHaveBeenCalledWith({
      name: 'RunDetail',
      params: { runId: 'run-retry-1' }
    })
    expect(runApi.getRunById).toHaveBeenCalledWith('run-retry-1')
  })

  it('summarizes partial success and retryable items for run evidence review', async () => {
    vi.mocked(runApi.getTasks).mockResolvedValue([
      {
        id: 'task-ok',
        runId: 'run-1',
        taskType: 'CREATE_RELEASE_BRANCH',
        taskOrder: 1,
        status: 'COMPLETED',
        retryCount: 0,
        maxRetries: 3,
        createdAt: ''
      },
      {
        id: 'task-fail',
        runId: 'run-1',
        taskType: 'MERGE_FEATURE_TO_RELEASE',
        taskOrder: 2,
        status: 'FAILED',
        retryCount: 1,
        maxRetries: 3,
        errorMessage: 'conflict',
        createdAt: ''
      }
    ])

    const wrapper = mountRunDetail()
    await flushPromises()
    await flushPromises()

    expect((wrapper.vm as any).itemSummary).toEqual({
      total: 2,
      succeeded: 1,
      failed: 1,
      skipped: 0,
      retryable: 1
    })
    expect((wrapper.vm as any).taskSummary).toEqual({
      total: 2,
      completed: 1,
      failed: 1,
      retryable: 1
    })
    expect((wrapper.vm as any).hasPartialFailure).toBe(true)
  })

  it('retries only failed version update items from the detail page', async () => {
    vi.mocked(runApi.getRunById).mockImplementation(async (id) => ({
      id: String(id),
      executionVersion: 1,
      runType: 'VERSION_UPDATE',
      status: 'FAILED',
      startedAt: '',
      finishedAt: '',
      operator: 'tester',
      items: [
        {
          id: 'item-success', metadata: {},
          windowKey: 'WK',
          repoId: 'repo-success',
          iterationKey: 'VERSION_UPDATE',
          plannedOrder: 1,
          executedOrder: 1,
          finalResult: 'VERSION_UPDATE_SUCCESS',
          steps: []
        },
        {
          id: 'item-failed', metadata: {},
          windowKey: 'WK',
          repoId: 'repo-fail',
          iterationKey: 'VERSION_UPDATE',
          plannedOrder: 2,
          executedOrder: 2,
          finalResult: 'VERSION_UPDATE_FAILED',
          steps: []
        }
      ]
    }))

    const wrapper = mountRunDetail()
    await flushPromises()
    await flushPromises()
    await selectRetryItems(wrapper, ['item-success', 'item-failed'])

    const retryButton = wrapper.findAll('button')
      .find(button => button.text().includes('run.retrySelectedItems'))
    expect(retryButton).toBeTruthy()
    await retryButton!.trigger('click')
    await flushPromises()

    expect(runApi.retry).toHaveBeenCalledWith(
      'run-1',
      ['item-failed']
    )
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
      id: 'run-review', executionVersion: 1, runType: 'WINDOW_ORCHESTRATION', status: 'NEEDS_REVIEW',
      startedAt: '', finishedAt: '', operator: 'tester',
      items: [
        { id: 'item-unknown', metadata: {}, windowKey: 'WK', repoId: 'repo-unknown', iterationKey: 'ITER-1', plannedOrder: 1, executedOrder: 1, finalResult: 'UNKNOWN', steps: [] },
        { id: 'item-failed', metadata: {}, windowKey: 'WK', repoId: 'repo-failed', iterationKey: 'ITER-1', plannedOrder: 2, executedOrder: 2, finalResult: 'FAILED', steps: [] }
      ]
    })
    vi.mocked(runApi.getTasks).mockResolvedValue([{
      id: 'task-fail', runId: 'run-review', taskType: 'MERGE_FEATURE_TO_RELEASE', taskOrder: 1,
      status: 'FAILED', retryCount: 0, maxRetries: 3, createdAt: ''
    }])
    const wrapper = mountRunDetail()
    await flushPromises()
    expect(wrapper.findAll('button').some(button => button.text().includes('run.retrySelectedItems'))).toBe(false)
    expect((wrapper.vm as any).taskSummary.retryable).toBe(0)
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
      id: 'run-1', executionVersion: 2, runType: 'WINDOW_ORCHESTRATION', status: 'PARTIAL_FAILED',
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
    expect(runApi.retry).toHaveBeenCalledExactlyOnceWith('run-1', ['first-blocked', 'later-skipped'])
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
      id: 'run-cleanup', executionVersion: 2, runType: 'WINDOW_CLEANUP', status: 'FAILED',
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
})