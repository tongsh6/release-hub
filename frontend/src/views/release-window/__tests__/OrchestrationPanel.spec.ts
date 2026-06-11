import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrchestrationPanel from '../OrchestrationPanel.vue'
import { releaseWindowApi } from '@/api/modules/releaseWindow'
import { runApi } from '@/api/runApi'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn()
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn()
  },
  ElMessageBox: {
    confirm: vi.fn().mockResolvedValue(true)
  }
}))

vi.mock('@/api/modules/releaseWindow', () => ({
  releaseWindowApi: {
    orchestrate: vi.fn(),
    mergeAll: vi.fn(),
    getDryPlan: vi.fn()
  }
}))

vi.mock('@/api/runApi', () => ({
  runApi: {
    list: vi.fn().mockResolvedValue({ list: [], total: 0 }),
    getRunById: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  Document: true,
  Connection: true,
  Edit: true,
  CircleCheck: true,
  SuccessFilled: true,
  WarningFilled: true,
  CircleCloseFilled: true,
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>'
  },
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElSteps: {
    template: '<div><slot /></div>'
  },
  ElStep: true,
  ElIcon: {
    template: '<span><slot /></span>'
  },
  ElTable: {
    template: '<table><slot /></table>'
  },
  ElTableColumn: true,
  ElDialog: {
    template: '<div><slot /></div>'
  },
  ElEmpty: true
}
const directives = {
  perm: {},
  loading: {}
}

describe('OrchestrationPanel', () => {
  beforeEach(() => {
    vi.mocked(releaseWindowApi.orchestrate).mockReset()
    vi.mocked(releaseWindowApi.orchestrate).mockResolvedValue('run-1')
    vi.mocked(runApi.list).mockReset()
    vi.mocked(runApi.list).mockResolvedValue({ list: [], total: 0 })
    vi.mocked(runApi.getRunById).mockReset()
    vi.mocked(runApi.getRunById).mockResolvedValue({
      id: 'run-1',
      runType: 'WINDOW_ORCHESTRATION',
      status: 'COMPLETED',
      startedAt: '',
      finishedAt: '',
      operator: 'frontend',
      items: []
    })
  })

  it('executes finish orchestration with the current window repository and iteration scope', async () => {
    const wrapper = mount(OrchestrationPanel, {
      props: {
        windowId: 'window-1',
        windowKey: 'RW-1',
        windowStatus: 'PUBLISHED',
        iterationCount: 1,
        repoCount: 1,
        repoIds: ['repo-1'],
        iterationKeys: ['ITER-1']
      },
      global: { stubs, directives }
    })

    await wrapper.findAll('button').at(-1)!.trigger('click')

    expect(releaseWindowApi.orchestrate).toHaveBeenCalledWith('window-1', {
      repoIds: ['repo-1'],
      iterationKeys: ['ITER-1'],
      failFast: false,
      operator: 'frontend'
    })
    expect(runApi.getRunById).toHaveBeenCalledWith('run-1')
  })

  it('shows the latest orchestration run result after execution', async () => {
    vi.mocked(runApi.getRunById).mockResolvedValue({
      id: 'run-1',
      runType: 'WINDOW_ORCHESTRATION',
      status: 'COMPLETED',
      startedAt: '2026-05-23T01:00:00Z',
      finishedAt: '2026-05-23T01:00:05Z',
      operator: 'frontend',
      items: [
        {
          windowKey: 'RW-1',
          repoId: 'repo-1',
          iterationKey: 'ITER-1',
          plannedOrder: 1,
          executedOrder: 1,
          finalResult: 'MERGED',
          steps: [
            {
              actionType: 'TRY_MERGE',
              result: 'MERGED',
              message: 'Merged feature/ITER-1 → release/RW-1'
            }
          ]
        }
      ]
    })

    const wrapper = mount(OrchestrationPanel, {
      props: {
        windowId: 'window-1',
        windowKey: 'RW-1',
        windowStatus: 'PUBLISHED',
        iterationCount: 1,
        repoCount: 1,
        repoIds: ['repo-1'],
        iterationKeys: ['ITER-1']
      },
      global: { stubs, directives }
    })

    await wrapper.findAll('button').at(-1)!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('orchestration.latestRun')
    expect(wrapper.text()).toContain('run-1')
    expect(wrapper.text()).toContain('COMPLETED')
    expect(wrapper.text()).toContain('orchestration.runItems:1')
    expect(wrapper.text()).toContain('orchestration.failedItems:0')
  })

  it('makes failed orchestration run context and first failed step visible', async () => {
    vi.mocked(runApi.list).mockResolvedValue({
      list: [
        {
          id: 'run-failed',
          runType: 'WINDOW_ORCHESTRATION',
          status: 'FAILED',
          startedAt: '2026-05-23T01:00:00Z',
          finishedAt: '2026-05-23T01:00:05Z',
          operator: 'frontend'
        }
      ],
      total: 1
    })
    vi.mocked(runApi.getRunById).mockResolvedValue({
      id: 'run-failed',
      runType: 'WINDOW_ORCHESTRATION',
      status: 'FAILED',
      startedAt: '2026-05-23T01:00:00Z',
      finishedAt: '2026-05-23T01:00:05Z',
      operator: 'frontend',
      items: [
        {
          windowKey: 'RW-1',
          repoId: 'repo-fail',
          iterationKey: 'ITER-FAIL',
          plannedOrder: 1,
          executedOrder: 1,
          finalResult: 'MERGE_BLOCKED',
          steps: [
            {
              actionType: 'TRY_MERGE',
              result: 'MERGE_BLOCKED',
              message: 'Merge conflict in pom.xml'
            }
          ]
        }
      ]
    })

    const wrapper = mount(OrchestrationPanel, {
      props: {
        windowId: 'window-1',
        windowKey: 'RW-1',
        windowStatus: 'PUBLISHED',
        iterationCount: 1,
        repoCount: 1,
        repoIds: ['repo-fail'],
        iterationKeys: ['ITER-FAIL']
      },
      global: { stubs, directives }
    })
    await flushPromises()

    expect(runApi.getRunById).toHaveBeenCalledWith('run-failed')
    expect(wrapper.text()).toContain('FAILED')
    expect(wrapper.text()).toContain('orchestration.failedItems:1')
    expect(wrapper.text()).toContain('RW-1 / repo-fail / ITER-FAIL')
    expect(wrapper.text()).toContain('TRY_MERGE')
    expect(wrapper.text()).toContain('Merge conflict in pom.xml')
  })

  it('emits the version update event name used by the release window detail page', async () => {
    const wrapper = mount(OrchestrationPanel, {
      props: {
        windowId: 'window-1',
        windowKey: 'RW-1',
        windowStatus: 'PUBLISHED',
        iterationCount: 1,
        repoCount: 1,
        repoIds: ['repo-1'],
        iterationKeys: ['ITER-1']
      },
      global: { stubs, directives }
    })

    await wrapper.findAll('button').at(-2)!.trigger('click')

    expect(wrapper.emitted('open-version-update')?.length).toBeGreaterThan(0)
    expect(wrapper.emitted('openVersionUpdate')).toBeUndefined()
  })

  it('loads recent runs by release window key', () => {
    mount(OrchestrationPanel, {
      props: {
        windowId: 'window-1',
        windowKey: 'RW-1',
        windowStatus: 'CLOSED',
        iterationCount: 1,
        repoCount: 1,
        repoIds: ['repo-1'],
        iterationKeys: ['ITER-1']
      },
      global: { stubs, directives }
    })

    expect(runApi.list).toHaveBeenCalledWith({ page: 1, pageSize: 5, windowKey: 'RW-1' })
  })
})
