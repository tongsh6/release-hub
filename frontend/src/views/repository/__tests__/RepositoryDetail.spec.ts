import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RepositoryDetail from '../RepositoryDetail.vue'
import { repositoryApi } from '@/api/repositoryApi'
import { groupApi } from '@/api/modules/group'
import { ElMessage } from 'element-plus'

const routerPush = vi.fn()

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { repo: 'repo-1' } }),
  useRouter: () => ({ push: routerPush })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn()
  }
}))

vi.mock('@/api/repositoryApi', () => ({
  repositoryApi: {
    get: vi.fn(),
    getGateSummary: vi.fn(),
    getBranchSummary: vi.fn(),
    getInitialVersion: vi.fn(),
    syncInitialVersion: vi.fn(),
    sync: vi.fn()
  }
}))

vi.mock('@/api/modules/group', () => ({
  groupApi: {
    listTree: vi.fn()
  }
}))

vi.mock('@/api/http', () => ({
  ApiError: class ApiError extends Error {
    code: string

    constructor(args: { code: string; message: string }) {
      super(args.message)
      this.code = args.code
    }
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  ArrowLeft: true,
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
    props: ['label'],
    template: '<div><dt>{{ label }}</dt><dd><slot /></dd></div>'
  },
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElRow: {
    template: '<div><slot /></div>'
  },
  ElCol: {
    template: '<div><slot /></div>'
  },
  ElStatistic: {
    props: ['title', 'value'],
    template: '<div>{{ title }} {{ value }}</div>'
  }
}

const repo = {
  id: 'repo-1',
  name: 'payment-service',
  cloneUrl: 'git@gitlab.local:customer/payment-service.git',
  defaultBranch: 'main',
  groupCode: 'leaf',
  repoType: 'SERVICE',
  monoRepo: false,
  gitProvider: 'GITLAB',
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
}

describe('RepositoryDetail', () => {
  beforeEach(() => {
    routerPush.mockReset()
    vi.mocked(repositoryApi.get).mockReset()
    vi.mocked(repositoryApi.getGateSummary).mockReset()
    vi.mocked(repositoryApi.getBranchSummary).mockReset()
    vi.mocked(repositoryApi.getInitialVersion).mockReset()
    vi.mocked(repositoryApi.syncInitialVersion).mockReset()
    vi.mocked(groupApi.listTree).mockReset()
    vi.mocked(ElMessage.success).mockReset()

    vi.mocked(repositoryApi.get).mockResolvedValue(repo as any)
    vi.mocked(repositoryApi.getGateSummary).mockResolvedValue({
      protectedBranch: true,
      approvalRequired: false,
      pipelineGate: false,
      permissionDenied: false
    })
    vi.mocked(repositoryApi.getBranchSummary).mockResolvedValue({
      totalBranches: 3,
      activeBranches: 2,
      nonCompliantBranches: 0,
      activeMrs: 1,
      mergedMrs: 0,
      closedMrs: 0
    })
    vi.mocked(groupApi.listTree).mockResolvedValue([])
  })

  it('lets admins rescan initial version when repository version is unresolved', async () => {
    vi.mocked(repositoryApi.getInitialVersion).mockResolvedValue({
      repoId: 'repo-1',
      version: null,
      versionSource: 'VERSION_UNRESOLVED'
    })
    vi.mocked(repositoryApi.syncInitialVersion).mockResolvedValue({
      repoId: 'repo-1',
      version: '1.2.3',
      versionSource: 'POM'
    })

    const wrapper = shallowMount(RepositoryDetail, {
      global: { stubs }
    })
    await flushPromises()

    const syncButton = wrapper.findAll('button')
      .find(button => button.text().includes('repository.syncVersion'))
    expect(syncButton).toBeTruthy()
    await syncButton!.trigger('click')
    await flushPromises()

    expect(repositoryApi.syncInitialVersion).toHaveBeenCalledWith('repo-1')
    expect(ElMessage.success).toHaveBeenCalledWith('repository.versionSyncSuccess')
    expect(wrapper.text()).toContain('1.2.3')
    expect(wrapper.text()).toContain('repository.versionSources.POM')
  })

  it('hides version rescan action when the initial version is resolved', async () => {
    vi.mocked(repositoryApi.getInitialVersion).mockResolvedValue({
      repoId: 'repo-1',
      version: '1.0.0',
      versionSource: 'MANUAL'
    })

    const wrapper = shallowMount(RepositoryDetail, {
      global: { stubs }
    })
    await flushPromises()

    const buttonTexts = wrapper.findAll('button').map(button => button.text())
    expect(buttonTexts.some(text => text.includes('repository.syncVersion'))).toBe(false)
  })
})
