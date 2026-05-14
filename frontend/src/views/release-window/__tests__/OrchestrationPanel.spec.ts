import { mount } from '@vue/test-utils'
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
    list: vi.fn().mockResolvedValue({ list: [], total: 0 })
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

describe('OrchestrationPanel', () => {
  beforeEach(() => {
    vi.mocked(releaseWindowApi.orchestrate).mockReset()
    vi.mocked(releaseWindowApi.orchestrate).mockResolvedValue('run-1')
    vi.mocked(runApi.list).mockReset()
    vi.mocked(runApi.list).mockResolvedValue({ list: [], total: 0 })
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
      global: { stubs }
    })

    await wrapper.findAll('button').at(-1)!.trigger('click')

    expect(releaseWindowApi.orchestrate).toHaveBeenCalledWith('window-1', {
      repoIds: ['repo-1'],
      iterationKeys: ['ITER-1'],
      failFast: false,
      operator: 'frontend'
    })
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
      global: { stubs }
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
      global: { stubs }
    })

    expect(runApi.list).toHaveBeenCalledWith({ page: 1, pageSize: 5, windowKey: 'RW-1' })
  })
})
