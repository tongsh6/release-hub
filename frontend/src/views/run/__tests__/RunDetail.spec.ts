import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RunDetail from '../RunDetail.vue'
import { runApi } from '@/api/runApi'
import { ElMessageBox } from 'element-plus'
import { hasPerm } from '@/utils/perm'

const routerPush = vi.fn()
const profile = { username: 'tester' }

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
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
    confirm: vi.fn()
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

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ profile })
}))

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

describe('RunDetail', () => {
  beforeEach(() => {
    vi.mocked(runApi.getRunById).mockReset()
    vi.mocked(runApi.getTasks).mockReset()
    vi.mocked(runApi.retry).mockReset()
    vi.mocked(ElMessageBox.confirm).mockReset()
    vi.mocked(hasPerm).mockReturnValue(true)
    routerPush.mockReset()

    vi.mocked(runApi.getTasks).mockResolvedValue([])
    vi.mocked(runApi.retry).mockResolvedValue('run-retry-1')
    vi.mocked(ElMessageBox.confirm).mockResolvedValue(true)
    vi.mocked(runApi.getRunById).mockImplementation(async (id) => ({
      id: String(id),
      runType: 'ATTACH_ITERATION',
      status: 'FAILED',
      startedAt: '',
      finishedAt: '',
      operator: 'tester',
      items: [
        {
          windowKey: 'WK',
          repoId: 'repo-success',
          iterationKey: 'ITER-1',
          plannedOrder: 1,
          executedOrder: 1,
          finalResult: 'MERGED',
          steps: []
        },
        {
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
    const wrapper = shallowMount(RunDetail, {
      global: { stubs }
    })
    await flushPromises()
    await flushPromises()

    const retryButton = wrapper.findAll('button')
      .find(button => button.text().includes('run.retryFailedItems'))
    expect(retryButton).toBeTruthy()
    await retryButton!.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      'run.retryConfirm',
      'common.warning',
      { type: 'warning' }
    )
    expect(runApi.retry).toHaveBeenCalledWith(
      'run-1',
      ['WK::repo-fail::ITER-1'],
      'tester'
    )
    expect(routerPush).toHaveBeenCalledWith({
      name: 'RunDetail',
      params: { runId: 'run-retry-1' }
    })
    expect(runApi.getRunById).toHaveBeenCalledWith('run-retry-1')
  })

  it('hides retry action when there are no failed run items', async () => {
    vi.mocked(runApi.getRunById).mockResolvedValue({
      id: 'run-1',
      runType: 'ATTACH_ITERATION',
      status: 'SUCCESS',
      startedAt: '',
      finishedAt: '',
      operator: 'tester',
      items: [
        {
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

    const wrapper = shallowMount(RunDetail, {
      global: { stubs }
    })
    await flushPromises()
    await flushPromises()

    const buttonTexts = wrapper.findAll('button').map(button => button.text())
    expect(buttonTexts.some(text => text.includes('run.retryFailedItems'))).toBe(false)
  })
})
