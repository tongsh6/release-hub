import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReleaseWindowDetail from '../ReleaseWindowDetail.vue'
import { releaseWindowApi } from '@/api/modules/releaseWindow'
import { iterationApi } from '@/api/iterationApi'
import { repositoryApi } from '@/api/repositoryApi'
import { ElMessageBox } from 'element-plus'
import { hasPerm } from '@/utils/perm'

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      t: (key: string) => key,
      locale: { value: 'zh-CN' }
    }
  }),
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'window-1' } }),
  useRouter: () => ({ back: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn()
  },
  ElMessageBox: {
    confirm: vi.fn()
  }
}))

vi.mock('@/api/modules/releaseWindow', () => ({
  releaseWindowApi: {
    get: vi.fn(),
    listIterations: vi.fn(),
    freeze: vi.fn(),
    unfreeze: vi.fn(),
    publish: vi.fn(),
    close: vi.fn()
  }
}))

vi.mock('@/api/iterationApi', () => ({
  iterationApi: {
    get: vi.fn(),
    resolveVersionConflict: vi.fn()
  }
}))

vi.mock('@/api/repositoryApi', () => ({
  repositoryApi: {
    get: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

vi.mock('@/utils/perm', () => ({
  hasPerm: vi.fn(() => true)
}))

vi.mock('@/utils/date', () => ({
  formatDateTime: (value: string) => value,
  formatDate: (value: string) => value
}))

const conflictPanelRefresh = vi.fn()

const stubs = {
  ArrowLeft: true,
  Download: true,
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElDivider: true,
  ElDescriptions: {
    template: '<dl><slot /></dl>'
  },
  ElDescriptionsItem: {
    template: '<div><slot /></div>'
  },
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>'
  },
  ElCollapse: {
    template: '<div><slot /></div>'
  },
  ElCollapseItem: {
    template: '<div><slot name="title" /><slot /></div>'
  },
  ElTable: {
    template: '<table><slot /></table>'
  },
  ElTableColumn: true,
  ElTooltip: {
    template: '<span><slot /></span>'
  },
  AttachIterationsDialog: true,
  CodeMergeDialog: true,
  VersionUpdateDialog: true,
  OrchestrationPanel: true,
  BranchStatusPanel: true,
  ConflictPanel: {
    props: ['windowId'],
    setup(_props: unknown, { expose }: { expose: (exposed: unknown) => void }) {
      expose({ refresh: conflictPanelRefresh })
      return {
        item: {
          repoId: 'repo-1',
          repoName: 'repo-1',
          iterationKey: 'ITER-1',
          conflictType: 'MISMATCH',
          systemVersion: '1.0.0',
          repoVersion: '1.1.0',
          message: 'version mismatch',
          suggestion: 'sync version'
        }
      }
    },
    template: '<button type="button" class="emit-resolve" @click="$emit(\'resolve\', item)">resolve</button>'
  }
}

describe('ReleaseWindowDetail', () => {
  const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

  beforeEach(() => {
    vi.mocked(releaseWindowApi.get).mockReset()
    vi.mocked(releaseWindowApi.listIterations).mockReset()
    vi.mocked(iterationApi.get).mockReset()
    vi.mocked(iterationApi.resolveVersionConflict).mockReset()
    vi.mocked(repositoryApi.get).mockReset()
    vi.mocked(ElMessageBox.confirm).mockReset()
    vi.mocked(hasPerm).mockReturnValue(true)
    openSpy.mockClear()
    conflictPanelRefresh.mockReset()

    vi.mocked(releaseWindowApi.get).mockResolvedValue({
      id: 'window-1',
      windowKey: 'RW-1',
      name: 'Window 1',
      status: 'PUBLISHED',
      frozen: false
    } as any)
    vi.mocked(releaseWindowApi.listIterations).mockResolvedValue([{ iterationKey: 'ITER-1' }])
    vi.mocked(iterationApi.get).mockResolvedValue({
      iterationKey: 'ITER-1',
      name: 'Iteration 1',
      description: '',
      expectedReleaseAt: null,
      repoIds: ['repo-1'],
      repoCount: 1,
      mountedWindows: '',
      attachAt: '',
      createdAt: '',
      updatedAt: ''
    })
    vi.mocked(repositoryApi.get).mockResolvedValue({
      id: 'repo-1',
      name: 'repo-1',
      cloneUrl: 'mock:///repo-1.git',
      defaultBranch: 'main',
      repoType: 'application',
      monoRepo: false,
      branchCount: 0,
      activeBranchCount: 0,
      nonCompliantBranchCount: 0,
      mrCount: 0,
      openMrCount: 0,
      mergedMrCount: 0,
      closedMrCount: 0,
      lastSyncAt: '',
      createdAt: '',
      updatedAt: ''
    })
    vi.mocked(ElMessageBox.confirm).mockResolvedValue(true)
    vi.mocked(iterationApi.resolveVersionConflict).mockResolvedValue({
      repoId: 'repo-1',
      repoName: 'repo-1',
      targetVersion: '1.0.0',
      versionSource: 'SYSTEM'
    })
  })

  it('resolves a version conflict with USE_SYSTEM and refreshes the conflict panel', async () => {
    const wrapper = shallowMount(ReleaseWindowDetail, {
      global: { stubs }
    })
    await flushPromises()
    await flushPromises()

    await wrapper.find('.emit-resolve').trigger('click')
    await flushPromises()

    expect(iterationApi.resolveVersionConflict).toHaveBeenCalledWith('ITER-1', 'repo-1', 'USE_SYSTEM')
    expect(conflictPanelRefresh).toHaveBeenCalled()
  })

  it('exports the release window report as CSV from the detail page', async () => {
    const wrapper = shallowMount(ReleaseWindowDetail, {
      global: { stubs }
    })
    await flushPromises()
    await flushPromises()

    const exportButton = wrapper.findAll('button')
      .find(button => button.text().includes('releaseWindow.report.export'))
    expect(exportButton).toBeTruthy()
    await exportButton!.trigger('click')

    expect(openSpy).toHaveBeenCalledWith('/api/v1/release-windows/window-1/report.csv', '_blank')
  })
})
