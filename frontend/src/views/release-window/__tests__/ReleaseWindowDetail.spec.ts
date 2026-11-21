import { createApp, nextTick, type SetupContext, type VNode } from 'vue'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReleaseWindowDetail from '../ReleaseWindowDetail.vue'
import { releaseWindowApi } from '@/api/modules/releaseWindow'
import { iterationApi } from '@/api/iterationApi'
import { repositoryApi } from '@/api/repositoryApi'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPerm } from '@/utils/perm'

// 当前 Element Plus 声明错误地将返回值写成交叉类型；confirm 运行时返回 action。
const confirmDialog = vi.hoisted(() => vi.fn<(message: unknown, ...args: unknown[]) => Promise<'confirm'>>())
const routerPush = vi.hoisted(() => vi.fn())

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
  useRouter: () => ({ back: vi.fn(), push: routerPush })
}))

vi.mock('element-plus', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    ElCheckbox: defineComponent({
      name: 'ElCheckbox',
      props: { modelValue: Boolean },
      emits: ['update:modelValue'],
      setup(props, { emit, slots }) {
        return () => h('label', [
          h('input', { type: 'checkbox', checked: props.modelValue,
            onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).checked) }),
          slots.default?.()
        ])
      }
    }),
    ElMessage: { success: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: confirmDialog }
  }
})
vi.mock('@/api/modules/releaseWindow', () => ({
  releaseWindowApi: {
    get: vi.fn(),
    getParallelScope: vi.fn(),
    getPlan: vi.fn(),
    listIterations: vi.fn(),
    freeze: vi.fn(),
    unfreeze: vi.fn(),
    publish: vi.fn(),
    close: vi.fn(),
    detach: vi.fn()
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
  ElCheckbox: false,
  ArrowLeft: true,
  ArrowDown: true,
  Download: true,
  ElButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  ElIcon: {
    template: '<span><slot /></span>'
  },
  ElDropdown: {
    emits: ['command'],
    template: `
      <div>
        <slot />
        <slot name="dropdown" />
        <button type="button" class="export-csv" @click="$emit('command', 'csv')">csv</button>
        <button type="button" class="export-json" @click="$emit('command', 'json')">json</button>
        <button type="button" class="export-md" @click="$emit('command', 'md')">md</button>
        <button type="button" class="export-zip" @click="$emit('command', 'zip')">zip</button>
      </div>
    `
  },
  ElDropdownMenu: {
    template: '<div><slot /></div>'
  },
  ElDropdownItem: {
    template: '<button type="button"><slot /></button>'
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
  ElAlert: { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
  VersionUpdateDialog: true,
  OrchestrationPanel: true,
  BranchStatusPanel: true,
  ConflictPanel: {
    props: ['windowId'],
    setup(_props: unknown, { expose }: SetupContext) {
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

const mountReleaseWindowDetail = (customStubs = stubs) => shallowMount(ReleaseWindowDetail, {
  global: {
    stubs: customStubs,
    directives: {
      loading: {},
      perm: {}
    }
  }
})

describe('ReleaseWindowDetail', () => {
  const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

  beforeEach(() => {
    vi.mocked(releaseWindowApi.get).mockReset()
    vi.mocked(releaseWindowApi.getParallelScope).mockReset()
    vi.mocked(releaseWindowApi.listIterations).mockReset()
    vi.mocked(releaseWindowApi.detach).mockReset()
    vi.mocked(iterationApi.get).mockReset()
    vi.mocked(iterationApi.resolveVersionConflict).mockReset()
    vi.mocked(repositoryApi.get).mockReset()
    confirmDialog.mockReset()
    vi.mocked(releaseWindowApi.close).mockReset()
    vi.mocked(ElMessage.success).mockClear()
    routerPush.mockReset()
    vi.mocked(releaseWindowApi.close).mockResolvedValue({
      id: 'cleanup-1', runType: 'WINDOW_CLEANUP', status: 'FAILED',
      startedAt: '', finishedAt: '', operator: 'admin'
    })
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
    vi.mocked(releaseWindowApi.getParallelScope).mockResolvedValue({
      currentWindowId: 'window-1',
      currentWindowKey: 'RW-1',
      groupCode: 'G001',
      activeWindowCount: 2,
      windows: [
        {
          windowId: 'window-1',
          windowKey: 'RW-1',
          name: 'Window 1',
          status: 'PUBLISHED',
          iterationCount: 1,
          repoCount: 1,
          planItems: [
            {
              windowKey: 'RW-1',
              iterationKey: 'ITER-1',
              repoId: 'repo-1',
              plannedOrder: 1
            }
          ]
        },
        {
          windowId: 'window-2',
          windowKey: 'RW-2',
          name: 'Window 2',
          status: 'DRAFT',
          iterationCount: 1,
          repoCount: 1,
          planItems: [
            {
              windowKey: 'RW-2',
              iterationKey: 'ITER-2',
              repoId: 'repo-2',
              plannedOrder: 1
            }
          ]
        }
      ]
    })
    vi.mocked(releaseWindowApi.listIterations).mockResolvedValue([{ iterationKey: 'ITER-1', attachAt: '', releaseBranch: null, branchCreated: false, lastMergeAt: null }])
    vi.mocked(releaseWindowApi.getPlan).mockReset().mockResolvedValue([{ windowKey: 'RW-1', repoId: 'repo-1', iterationKey: 'ITER-1', plannedOrder: 1 }])
    vi.mocked(iterationApi.get).mockResolvedValue({
      iterationKey: 'ITER-1',
      name: 'Iteration 1',
      description: '',
      expectedReleaseAt: null,
      groupCode: 'G001',
      attachedToWindow: true,
      attachedWindowIds: ['window-1'],
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
      cloneUrl: 'https://gitlab.example.com/customer/repo-1.git',
      defaultBranch: 'main',
      groupCode: 'G001',
      gitProvider: 'GITLAB',
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
    confirmDialog.mockResolvedValue('confirm')
    vi.mocked(iterationApi.resolveVersionConflict).mockResolvedValue({
      repoId: 'repo-1',
      repoName: 'repo-1',
      targetVersion: '1.0.0',
      versionSource: 'SYSTEM'
    })
    vi.mocked(releaseWindowApi.detach).mockResolvedValue(true)
  })

  it.each(['FAILED', 'PARTIAL_FAILED', 'NEEDS_REVIEW'] as const)('opens the real cleanup Run for %s without a success toast', async status => {
    vi.mocked(releaseWindowApi.close).mockResolvedValue({
      id: 'cleanup-1', runType: 'WINDOW_CLEANUP', status,
      startedAt: '', finishedAt: '', operator: 'admin'
    })
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === 'releaseWindow.close')!.trigger('click')
    await flushPromises()
    expect(releaseWindowApi.close).toHaveBeenCalledExactlyOnceWith('window-1', { triggerCi: false })
    expect(releaseWindowApi.get).toHaveBeenCalledTimes(2)
    expect(routerPush).toHaveBeenCalledWith({ name: 'RunDetail', params: { runId: 'cleanup-1' } })
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('requires explicit CI selection in the existing close confirmation', async () => {
    confirmDialog.mockImplementationOnce(async message => {
      expect(typeof message).toBe('function')
      const container = document.createElement('div')
      const dialog = createApp(message as () => VNode)
      dialog.mount(container)
      const checkbox = container.querySelector<HTMLInputElement>('input[type="checkbox"]')!
      expect(checkbox.checked).toBe(false)
      checkbox.checked = true
      checkbox.dispatchEvent(new Event('change', { bubbles: true }))
      await nextTick()
      dialog.unmount()
      return 'confirm'
    })
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === 'releaseWindow.close')!.trigger('click')
    await flushPromises()
    await expect(confirmDialog.mock.results[0]?.value).resolves.toBe('confirm')
    expect(releaseWindowApi.close).toHaveBeenCalledExactlyOnceWith('window-1', { triggerCi: true })
  })

  it('does not close or navigate when the confirmation is cancelled', async () => {
    confirmDialog.mockRejectedValueOnce('cancel')
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === 'releaseWindow.close')!.trigger('click')
    await flushPromises()
    expect(releaseWindowApi.close).not.toHaveBeenCalled()
    expect(routerPush).not.toHaveBeenCalled()
  })

  it('resolves a version conflict with USE_SYSTEM and refreshes the conflict panel', async () => {
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await flushPromises()

    await wrapper.find('.emit-resolve').trigger('click')
    await flushPromises()

    expect(iterationApi.resolveVersionConflict).toHaveBeenCalledWith('ITER-1', 'repo-1', 'USE_SYSTEM')
    expect(conflictPanelRefresh).toHaveBeenCalled()
  })

  it('resolves a repo-ahead conflict with USE_REPO and refreshes the conflict panel', async () => {
    const repoAheadStubs = {
      ...stubs,
      ConflictPanel: {
        props: ['windowId'],
        setup(_props: unknown, { expose }: SetupContext) {
          expose({ refresh: conflictPanelRefresh })
          return {
            item: {
              repoId: 'repo-1',
              repoName: 'repo-1',
              iterationKey: 'ITER-1',
              conflictType: 'REPO_AHEAD',
              systemVersion: '1.0.0',
              repoVersion: '1.1.0',
              message: 'repo ahead',
              suggestion: 'accept repo version'
            }
          }
        },
        template: '<button type="button" class="emit-resolve-repo" @click="$emit(\'resolve\', item, \'USE_REPO\')">resolve repo</button>'
      }
    }

    const wrapper = mountReleaseWindowDetail(repoAheadStubs)
    await flushPromises()
    await flushPromises()

    await wrapper.find('.emit-resolve-repo').trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      'conflict.confirmUseRepo',
      'common.confirm',
      { type: 'warning' }
    )
    expect(iterationApi.resolveVersionConflict).toHaveBeenCalledWith('ITER-1', 'repo-1', 'USE_REPO')
    expect(conflictPanelRefresh).toHaveBeenCalled()
  })

  it('exports the release window report as CSV from the detail page', async () => {
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await flushPromises()

    await wrapper.find('.export-csv').trigger('click')

    expect(openSpy).toHaveBeenCalledWith('/api/v1/release-windows/window-1/report.csv', '_blank')
  })

  it('exports JSON and Markdown release window report artifacts from the detail page', async () => {
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await flushPromises()

    await wrapper.find('.export-json').trigger('click')
    await wrapper.find('.export-md').trigger('click')

    expect(openSpy).toHaveBeenCalledWith('/api/v1/release-windows/window-1/report.json', '_blank')
    expect(openSpy).toHaveBeenCalledWith('/api/v1/release-windows/window-1/report.md', '_blank')
  })

  it('exports the release window evidence package from the detail page', async () => {
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await flushPromises()

    await wrapper.find('.export-zip').trigger('click')

    expect(openSpy).toHaveBeenCalledWith('/api/v1/release-windows/window-1/report.zip', '_blank')
  })

  it('loads parallel window scope and formats each window plan independently', async () => {
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await flushPromises()

    expect(releaseWindowApi.getParallelScope).toHaveBeenCalledWith('window-1')
    expect((wrapper.vm as any).parallelScope.activeWindowCount).toBe(2)
    expect((wrapper.vm as any).formatParallelPlanItems((wrapper.vm as any).parallelScope.windows[0])).toBe('ITER-1 / repo-1')
    expect((wrapper.vm as any).formatParallelPlanItems((wrapper.vm as any).parallelScope.windows[1])).toBe('ITER-2 / repo-2')
  })

  it('hides release plan mutation controls after publish', async () => {
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await flushPromises()

    const buttonTexts = wrapper.findAll('button').map(button => button.text())
    expect(buttonTexts.some(text => text.includes('releaseWindow.attachIterations'))).toBe(false)
    expect(buttonTexts.some(text => text.includes('common.remove'))).toBe(false)
  })

  it('hides release plan mutation controls while draft window is frozen', async () => {
    vi.mocked(releaseWindowApi.get).mockResolvedValue({
      id: 'window-1',
      windowKey: 'RW-1',
      name: 'Window 1',
      status: 'DRAFT',
      frozen: true
    } as any)

    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await flushPromises()

    const buttonTexts = wrapper.findAll('button').map(button => button.text())
    expect(buttonTexts.some(text => text.includes('releaseWindow.attachIterations'))).toBe(false)
    expect(buttonTexts.some(text => text.includes('common.remove'))).toBe(false)
    expect(buttonTexts.some(text => text.includes('releaseWindow.unfreeze'))).toBe(true)
  })

  it('detaches an associated iteration from the detail page and refreshes the list', async () => {
    vi.mocked(releaseWindowApi.get).mockResolvedValue({
      id: 'window-1',
      windowKey: 'RW-1',
      name: 'Window 1',
      status: 'DRAFT',
      frozen: false
    } as any)

    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    await flushPromises()

    const detachButton = wrapper.findAll('button')
      .find(button => button.text().includes('common.remove'))
    expect(detachButton).toBeTruthy()
    await detachButton!.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      'releaseWindow.confirmDetach',
      'common.warning',
      { type: 'warning' }
    )
    expect(releaseWindowApi.detach).toHaveBeenCalledWith('window-1', 'ITER-1')
    expect(releaseWindowApi.listIterations).toHaveBeenCalledTimes(2)
  })

  it('keeps authoritative scope when repository presentation fails and counts shared repos once', async () => {
    vi.mocked(releaseWindowApi.getPlan).mockResolvedValue([
      { windowKey: 'RW-1', repoId: 'repo-1', iterationKey: 'ITER-1', plannedOrder: 1 },
      { windowKey: 'RW-1', repoId: 'repo-1', iterationKey: 'ITER-2', plannedOrder: 2 },
      { windowKey: 'RW-1', repoId: 'repo-2', iterationKey: 'ITER-2', plannedOrder: 1 }
    ])
    vi.mocked(repositoryApi.get).mockRejectedValue(new Error('detail unavailable'))
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    const panel = wrapper.findComponent({ name: 'OrchestrationPanel' })
    expect(panel.props('planItems').map((item: { repoId: string }) => item.repoId)).toContain('repo-2')
    expect(panel.props('scopeReady')).toBe(true)
    expect((wrapper.vm as any).totalRepoCount).toBe(2)
  })

  it('blocks integration when the authoritative scope request fails', async () => {
    vi.mocked(releaseWindowApi.getPlan).mockRejectedValue(new Error('scope unavailable'))
    const wrapper = mountReleaseWindowDetail()
    await flushPromises()
    expect(wrapper.findComponent({ name: 'OrchestrationPanel' }).props('scopeReady')).toBe(false)
  })

})