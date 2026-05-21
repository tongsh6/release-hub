import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BranchStatusPanel from '../BranchStatusPanel.vue'
import { releaseWindowApi } from '@/api/modules/releaseWindow'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key)
  })
}))

vi.mock('@/api/modules/releaseWindow', () => ({
  releaseWindowApi: {
    getPlan: vi.fn(),
    getBranchStatus: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  RefreshRight: true,
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>'
  },
  ElButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>'
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
  }
}

describe('BranchStatusPanel', () => {
  beforeEach(() => {
    vi.mocked(releaseWindowApi.getPlan).mockReset()
    vi.mocked(releaseWindowApi.getBranchStatus).mockReset()
    vi.mocked(releaseWindowApi.getPlan).mockResolvedValue([
      { windowKey: 'RW-1', repoId: 'repo-ok', iterationKey: 'ITER-1', plannedOrder: 1 },
      { windowKey: 'RW-1', repoId: 'repo-missing', iterationKey: 'ITER-1', plannedOrder: 2 },
      { windowKey: 'RW-1', repoId: 'repo-conflict', iterationKey: 'ITER-2', plannedOrder: 3 }
    ])
    vi.mocked(releaseWindowApi.getBranchStatus).mockResolvedValue({
      windowId: 'window-1',
      windowKey: 'RW-1',
      repos: [
        {
          repoId: 'repo-ok',
          repoName: 'repo-ok',
          repoCloneUrl: 'https://gitlab.example.com/customer/repo-ok.git',
          iterationKey: 'ITER-1',
          featureBranch: { branchName: 'feature/ITER-1', exists: true, latestCommit: 'abcdef123456' },
          releaseBranch: { branchName: 'release/RW-1', exists: true, latestCommit: '123456abcdef', mergeStatus: 'MERGED' }
        },
        {
          repoId: 'repo-missing',
          repoName: 'repo-missing',
          repoCloneUrl: 'https://gitlab.example.com/customer/repo-missing.git',
          iterationKey: 'ITER-1',
          featureBranch: { branchName: 'feature/ITER-1', exists: false },
          releaseBranch: { branchName: 'release/RW-1', exists: false, mergeStatus: 'PENDING' }
        },
        {
          repoId: 'repo-conflict',
          repoName: 'repo-conflict',
          repoCloneUrl: 'https://gitlab.example.com/customer/repo-conflict.git',
          iterationKey: 'ITER-2',
          featureBranch: { branchName: 'feature/ITER-2', exists: true },
          releaseBranch: { branchName: 'release/RW-1', exists: true, mergeStatus: 'CONFLICT' }
        }
      ]
    })
  })

  it('summarizes branch existence and merge status risks', async () => {
    const wrapper = shallowMount(BranchStatusPanel, {
      props: { windowId: 'window-1' },
      global: {
        stubs,
        directives: {
          loading: {}
        }
      }
    })
    await flushPromises()

    expect((wrapper.vm as any).branchSummary).toEqual({
      total: 3,
      featureMissing: 1,
      releaseMissing: 1,
      merged: 1,
      conflict: 1
    })
    expect((wrapper.vm as any).hasBranchRisk).toBe(true)
    expect((wrapper.vm as any).planRows.map((row: any) => row.repoId)).toEqual([
      'repo-ok',
      'repo-missing',
      'repo-conflict'
    ])
  })
})
