import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BranchStatusPanel from '../BranchStatusPanel.vue'
import { releaseWindowApi } from '@/api/modules/releaseWindow'
import { handleError } from '@/utils/error'

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
    vi.mocked(handleError).mockReset()
    vi.mocked(releaseWindowApi.getPlan).mockReset()
    vi.mocked(releaseWindowApi.getBranchStatus).mockReset()
    vi.mocked(releaseWindowApi.getPlan).mockResolvedValue([
      { windowKey: 'RW-1', repoId: 'repo-ok', iterationKey: 'ITER-1', plannedOrder: 1 },
      { windowKey: 'RW-1', repoId: 'repo-missing', iterationKey: 'ITER-1', plannedOrder: 2 },
      { windowKey: 'RW-1', repoId: 'repo-other', iterationKey: 'ITER-2', plannedOrder: 3 }
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
          releaseBranch: { branchName: 'release/RW-1', exists: true, latestCommit: '123456abcdef' }
        },
        {
          repoId: 'repo-missing',
          repoName: 'repo-missing',
          repoCloneUrl: 'https://gitlab.example.com/customer/repo-missing.git',
          iterationKey: 'ITER-1',
          featureBranch: { branchName: 'feature/ITER-1', exists: false },
          releaseBranch: { branchName: 'release/RW-1', exists: false }
        },
        {
          repoId: 'repo-other',
          repoName: 'repo-other',
          repoCloneUrl: 'https://gitlab.example.com/customer/repo-other.git',
          iterationKey: 'ITER-2',
          featureBranch: { branchName: 'feature/ITER-2', exists: true },
          releaseBranch: { branchName: 'release/RW-1', exists: true }
        }
      ]
    })
  })

  it('只显示分支存在性与计划顺序，不从 ref 推断合并结果', async () => {
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
      releaseMissing: 1
    })
    expect((wrapper.vm as any).hasBranchRisk).toBe(true)
    expect(wrapper.findAll('.summary-item')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('releaseWindow.releasePlan.mergedCount')
    expect(wrapper.text()).not.toContain('releaseWindow.releasePlan.conflictCount')
    expect(wrapper.findAll('el-table-column-stub').map(column => column.attributes('label')))
      .not.toContain('releaseWindow.releasePlan.mergeStatus')
    expect((wrapper.vm as any).planRows.map((row: any) => row.repoId)).toEqual([
      'repo-ok',
      'repo-missing',
      'repo-other'
    ])
  })
  it('重叠迭代按仓库和实际 ref 统计，release 缺失不会重复计数', async () => {
    vi.mocked(releaseWindowApi.getBranchStatus).mockResolvedValue({
      windowId: 'window-1',
      windowKey: 'RW-1',
      repos: ['ITER-1', 'ITER-2'].map(iterationKey => ({
        repoId: 'shared-repo',
        repoName: 'shared-repo',
        repoCloneUrl: 'https://gitlab.example.com/customer/shared-repo.git',
        iterationKey,
        featureBranch: { branchName: `feature/${iterationKey}`, exists: false },
        releaseBranch: { branchName: 'release/RW-1', exists: false }
      }))
    })
    const wrapper = shallowMount(BranchStatusPanel, {
      props: { windowId: 'window-1' },
      global: { stubs, directives: { loading: {} } }
    })
    await flushPromises()

    expect((wrapper.vm as any).branchSummary).toEqual({
      total: 1,
      featureMissing: 2,
      releaseMissing: 1
    })
    expect((wrapper.vm as any).planRows).toHaveLength(2)
  })

  it('刷新读取失败时明确显示错误，不展示旧 refs 或空范围提示', async () => {
    const wrapper = shallowMount(BranchStatusPanel, {
      props: { windowId: 'window-1' },
      global: { stubs, directives: { loading: {} } }
    })
    await flushPromises()
    expect((wrapper.vm as any).planRows).toHaveLength(3)
    const failure = new Error('remote read failed')
    vi.mocked(releaseWindowApi.getBranchStatus).mockRejectedValueOnce(failure)

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(handleError).toHaveBeenCalledWith(failure)
    expect((wrapper.vm as any).planRows).toEqual([])
    expect(wrapper.text()).toContain('releaseWindow.releasePlan.readFailed')
    expect(wrapper.find('.empty-tip').exists()).toBe(false)
  })

})
