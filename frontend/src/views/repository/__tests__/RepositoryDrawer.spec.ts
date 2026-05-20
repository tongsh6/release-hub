import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RepositoryDrawer from '../RepositoryDrawer.vue'
import { repositoryApi } from '@/api/repositoryApi'
import { groupApi } from '@/api/modules/group'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('@/api/repositoryApi', () => ({
  repositoryApi: {
    get: vi.fn(),
    getGateSummary: vi.fn(),
    getBranchSummary: vi.fn(),
    getInitialVersion: vi.fn()
  }
}))

vi.mock('@/api/modules/group', () => ({
  groupApi: {
    listTree: vi.fn()
  }
}))

const stubs = {
  ElDrawer: {
    props: ['modelValue'],
    template: '<section v-if="modelValue"><slot /></section>'
  },
  ElCard: {
    template: '<article><slot name="header" /><slot /></article>'
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
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
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
  cloneUrl: 'git@gitlab.local:customer/line/payment-service.git',
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

describe('RepositoryDrawer', () => {
  beforeEach(() => {
    vi.mocked(repositoryApi.get).mockReset()
    vi.mocked(repositoryApi.getGateSummary).mockReset()
    vi.mocked(repositoryApi.getBranchSummary).mockReset()
    vi.mocked(repositoryApi.getInitialVersion).mockReset()
    vi.mocked(groupApi.listTree).mockReset()

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
    vi.mocked(repositoryApi.getInitialVersion).mockResolvedValue({
      repoId: 'repo-1',
      version: '1.2.3',
      versionSource: 'MANUAL'
    } as any)
    vi.mocked(groupApi.listTree).mockResolvedValue([
      {
        code: 'customer',
        name: 'Customer A',
        children: [
          {
            code: 'line',
            name: 'Line X',
            children: [
              {
                code: 'leaf',
                name: 'Leaf Group Y',
                children: []
              }
            ]
          }
        ]
      }
    ] as any)
  })

  it('shows group path and initial version status in repository detail', async () => {
    const wrapper = mount(RepositoryDrawer, {
      global: { stubs }
    })

    await (wrapper.vm as any).open('repo-1')
    await flushPromises()

    expect(repositoryApi.getInitialVersion).toHaveBeenCalledWith('repo-1')
    expect(groupApi.listTree).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Customer A / Line X / Leaf Group Y')
    expect(wrapper.text()).toContain('1.2.3')
    expect(wrapper.text()).toContain('repository.versionSources.MANUAL')
  })
})
