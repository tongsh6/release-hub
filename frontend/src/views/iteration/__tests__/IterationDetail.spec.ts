import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IterationDetail from '../IterationDetail.vue'
import { iterationApi } from '@/api/iterationApi'
import { repositoryApi } from '@/api/repositoryApi'

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      t: (key: string, args?: Record<string, unknown>) => (args ? `${key}:${JSON.stringify(args)}` : key),
      locale: { value: 'zh-CN' }
    }
  }),
  useI18n: () => ({
    t: (key: string, args?: Record<string, unknown>) => (args ? `${key}:${JSON.stringify(args)}` : key)
  })
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { iterationKey: 'ITER-1' } }),
  useRouter: () => ({ push: vi.fn() })
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

vi.mock('@/api/iterationApi', () => ({
  iterationApi: {
    get: vi.fn(),
    getRepoVersionInfo: vi.fn(),
    syncVersionFromRepo: vi.fn(),
    removeRepos: vi.fn()
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
  formatDate: (value: string) => value || '-',
  formatDateTime: (value: string) => value || '-'
}))

const stubs = {
  ArrowLeft: true,
  ElButton: {
    template: '<button type="button"><slot /></button>'
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
  ElLink: {
    template: '<a><slot /></a>'
  },
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElTable: true,
  ElTableColumn: true,
  ElEmpty: true,
  AttachWindowDialog: true,
  AddReposDialog: true,
  VersionConflictDialog: true
}

describe('IterationDetail', () => {
  beforeEach(() => {
    vi.mocked(iterationApi.get).mockReset()
    vi.mocked(iterationApi.getRepoVersionInfo).mockReset()
    vi.mocked(repositoryApi.get).mockReset()
  })

  it('locks repository scope controls after the iteration is attached to a release window', async () => {
    vi.mocked(iterationApi.get).mockResolvedValue({
      iterationKey: 'ITER-1',
      name: 'Iteration 1',
      description: '',
      expectedReleaseAt: null,
      groupCode: 'G001',
      repoIds: [],
      repoCount: 0,
      attachedToWindow: true,
      attachedWindowIds: ['window-1'],
      mountedWindows: '1',
      attachAt: '',
      createdAt: '',
      updatedAt: ''
    })

    const wrapper = shallowMount(IterationDetail, {
      global: {
        stubs,
        directives: {
          loading: {},
          perm: {}
        }
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('iteration.detail.repoScopeLocked')
    expect(wrapper.findAll('button').some(button => button.text().includes('iteration.detail.addRepos'))).toBe(false)
    expect((wrapper.vm as any).canChangeRepos).toBe(false)
  })

  it('exposes branch mode and version metadata for associated repositories', async () => {
    vi.mocked(iterationApi.get).mockResolvedValue({
      iterationKey: 'ITER-1',
      name: 'Iteration 1',
      description: '',
      expectedReleaseAt: null,
      groupCode: 'G001',
      repoIds: ['repo-1'],
      repoCount: 1,
      attachedToWindow: false,
      attachedWindowIds: [],
      mountedWindows: '',
      attachAt: '',
      createdAt: '',
      updatedAt: ''
    })
    vi.mocked(repositoryApi.get).mockResolvedValue({
      id: 'repo-1',
      name: 'repo-one',
      cloneUrl: 'git@gitlab.com:test/repo-one.git',
      defaultBranch: 'main',
      groupCode: 'G001',
      repoType: 'SERVICE',
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
    vi.mocked(iterationApi.getRepoVersionInfo).mockResolvedValue({
      repoId: 'repo-1',
      baseVersion: '1.0.0',
      devVersion: '1.1.0-SNAPSHOT',
      targetVersion: '1.1.0',
      featureBranch: 'feature/custom',
      branchCreationMode: 'NAMED',
      versionSource: 'SYSTEM',
      versionSyncedAt: '2026-05-21T10:00:00Z'
    })

    const wrapper = shallowMount(IterationDetail, {
      global: {
        stubs,
        directives: {
          loading: {},
          perm: {}
        }
      }
    })
    await flushPromises()

    expect((wrapper.vm as any).repoRows[0].versionInfo).toMatchObject({
      branchCreationMode: 'NAMED',
      featureBranch: 'feature/custom',
      baseVersion: '1.0.0',
      devVersion: '1.1.0-SNAPSHOT',
      targetVersion: '1.1.0',
      versionSource: 'SYSTEM'
    })
    expect((wrapper.vm as any).branchCreationModeLabel('NAMED')).toBe('iteration.branchCreationMode.NAMED')
  })
})
