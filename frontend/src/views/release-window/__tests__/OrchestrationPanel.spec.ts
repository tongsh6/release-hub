import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrchestrationPanel from '../OrchestrationPanel.vue'
import { releaseWindowApi } from '@/api/modules/releaseWindow'
import { runApi } from '@/api/runApi'
import { ElMessageBox } from 'element-plus'

const confirmDialog = vi.hoisted(() => vi.fn<() => Promise<'confirm'>>())
const authenticatedUser = vi.hoisted(() => ({ profile: { username: 'admin' } as { username: string } | null }))
vi.mock('@/stores/user', () => ({ useUserStore: () => authenticatedUser }))

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: confirmDialog }
}))
vi.mock('@/api/modules/releaseWindow', () => ({ releaseWindowApi: { orchestrate: vi.fn() } }))
vi.mock('@/api/runApi', () => ({ runApi: { list: vi.fn(), getRunById: vi.fn() } }))
vi.mock('@/utils/error', () => ({ handleError: vi.fn() }))
vi.mock('@/api/runPlanApi', () => ({ runPlanApi: { list: vi.fn(), get: vi.fn(), create: vi.fn() } }))

const planItems = [
  { windowKey: 'RW-1', repoId: 'repo-1', iterationKey: 'ITER-1', plannedOrder: 1 },
  { windowKey: 'RW-1', repoId: 'repo-1', iterationKey: 'ITER-2', plannedOrder: 2 },
  { windowKey: 'RW-1', repoId: 'repo-2', iterationKey: 'ITER-2', plannedOrder: 1 }
]
function render(overrides = {}) {
  return mount(OrchestrationPanel, {
    props: { windowId: 'window-1', windowKey: 'RW-1', windowStatus: 'DRAFT', frozen: false,
      planItems, scopeReady: true, ...overrides },
    global: { directives: { perm: {}, loading: {} }, stubs: {
      RunPlanPreviewDialog: true,
      ElCard: { template: '<section><slot name="header" /><slot /></section>' },
      ElButton: { emits: ['click'], props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' },
      ElTag: { template: '<span><slot /></span>' },
      ElAlert: { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
      ElTable: { template: '<table><slot /></table>' }, ElTableColumn: true
    } }
  })
}
const execute = async (wrapper: ReturnType<typeof render>) => {
  await wrapper.get('[data-action="integrate"]').trigger('click')
  await flushPromises()
}

describe('window integration', () => {
  beforeEach(() => {
    sessionStorage.clear()
    authenticatedUser.profile = { username: 'admin' }
    vi.clearAllMocks()
    confirmDialog.mockReset().mockResolvedValue('confirm')
    vi.mocked(runApi.list).mockResolvedValue({ list: [], total: 0 })
    vi.mocked(runApi.getRunById).mockResolvedValue({ id: 'run-1', executionVersion: 3, runType: 'WINDOW_ORCHESTRATION',
      status: 'DONE', startedAt: '', finishedAt: '', operator: 'tester', items: [] })
    vi.mocked(releaseWindowApi.orchestrate).mockResolvedValue('run-1')
  })

  it('integrates feature into release with FAIL_FAST by default and never claims closeout', async () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('orchestration.executeIntegration')
    expect(wrapper.text()).not.toContain('orchestration.executeFinish')
    expect(wrapper.text()).toContain('feature → release')
    await execute(wrapper)
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledWith('window-1', expect.objectContaining({
      repoIds: ['repo-1', 'repo-2'], iterationKeys: ['ITER-1', 'ITER-2'], failFast: true
    }), expect.any(String))
    expect(runApi.getRunById).toHaveBeenCalledWith('run-1')
  })

  it('continues on block only after an explicit choice and shares it with the preview', async () => {
    const wrapper = render()
    await wrapper.get('[data-field="block-policy"]').setValue('CONTINUE_ON_BLOCK')
    await execute(wrapper)
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledWith('window-1', expect.objectContaining({ failFast: false }), expect.any(String))
    expect(wrapper.findComponent({ name: 'RunPlanPreviewDialog' }).props('failFast')).toBe(false)
  })

  it('selects iterations for one repository without including other repository writes', async () => {
    const wrapper = render()
    await wrapper.get('[data-field="repository"]').setValue('repo-1')
    await wrapper.get('input[value="ITER-1"]').setValue(false)
    await execute(wrapper)
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledWith('window-1', expect.objectContaining({
      repoIds: ['repo-1'], iterationKeys: ['ITER-2'], failFast: true
    }), expect.any(String))
  })

  it.each([{ scopeReady: false }, { frozen: true }, { windowStatus: 'CLOSED' }])(
    'does not execute when scope or window state forbids it: %j', async (overrides) => {
      const wrapper = render(overrides)
      await execute(wrapper)
      expect(releaseWindowApi.orchestrate).not.toHaveBeenCalled()
    }
  )

  it('shows the authoritative partial failure and blocked step', async () => {
    vi.mocked(runApi.getRunById).mockResolvedValue({ id: 'run-1', executionVersion: 3, runType: 'WINDOW_ORCHESTRATION',
      status: 'PARTIAL_FAILED', startedAt: '', finishedAt: '', operator: 'tester', items: [{
        id: 'item-blocked', metadata: {}, windowKey: 'RW-1', repoId: 'repo-1', iterationKey: 'ITER-1', plannedOrder: 1, executedOrder: 1,
        finalResult: 'MERGE_BLOCKED', steps: [{ actionType: 'TRY_MERGE', result: 'MERGE_BLOCKED', message: 'approval required' }]
      }] })
    const wrapper = render()
    await execute(wrapper)
    expect(wrapper.text()).toContain('PARTIAL_FAILED')
    expect(wrapper.text()).toContain('approval required')
  })

  it('keeps version updates as a separate action', async () => {
    const wrapper = render()
    await wrapper.findAll('button').find(button => button.text() === 'releaseWindow.versionUpdate.execute')!.trigger('click')
    expect(wrapper.emitted('open-version-update')).toHaveLength(1)
    expect(releaseWindowApi.orchestrate).not.toHaveBeenCalled()
  })

  it('preserves the selected subset when the same plan is refreshed', async () => {
    const wrapper = render()
    await wrapper.get('[data-field="repository"]').setValue('repo-1')
    await wrapper.get('input[value="ITER-1"]').setValue(false)
    await wrapper.setProps({ planItems: planItems.map(item => ({ ...item })) })
    await execute(wrapper)
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledWith('window-1', expect.objectContaining({
      repoIds: ['repo-1'], iterationKeys: ['ITER-2']
    }), expect.any(String))
  })
  it('does not add newly attached iterations to an existing selected scope', async () => {
    const wrapper = render()
    await wrapper.get('[data-field="repository"]').setValue('repo-1')
    await wrapper.setProps({ scopeReady: false })
    await wrapper.setProps({ scopeReady: true, planItems: [...planItems,
      { windowKey: 'RW-1', repoId: 'repo-1', iterationKey: 'ITER-3', plannedOrder: 3 }
    ] })
    await execute(wrapper)
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledWith('window-1', expect.objectContaining({
      repoIds: ['repo-1'], iterationKeys: ['ITER-1', 'ITER-2']
    }), expect.any(String))
  })


  it('reuses an unconfirmed idempotency key after response loss and remount', async () => {
    vi.mocked(releaseWindowApi.orchestrate).mockRejectedValueOnce(new Error('response lost'))
    const first = render()
    await execute(first)
    const firstKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]?.[2]
    expect(firstKey).toMatch(/^[0-9a-f]{32}$/)
    first.unmount()
    const remounted = render()
    await execute(remounted)
    expect(vi.mocked(releaseWindowApi.orchestrate).mock.calls[1]?.[2]).toBe(firstKey)
  })

  it('requires an explicit new intent when an unconfirmed payload changes', async () => {
    vi.mocked(releaseWindowApi.orchestrate).mockRejectedValueOnce(new Error('response lost'))
    const wrapper = render()
    await execute(wrapper)
    const firstKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]?.[2]
    await wrapper.get('[data-field="repository"]').setValue('repo-1')
    await execute(wrapper)
    const nextKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[1]?.[2]
    expect(firstKey).toMatch(/^[0-9a-f]{32}$/)
    expect(nextKey).toMatch(/^[0-9a-f]{32}$/)
    expect(nextKey).not.toBe(firstKey)
    expect(ElMessageBox.confirm).toHaveBeenLastCalledWith(
      'run.request.replaceUnconfirmed', 'common.confirm', { type: 'warning' })
  })

  it('isolates pending execution receipts by authenticated subject', async () => {
    vi.mocked(releaseWindowApi.orchestrate).mockRejectedValueOnce(new Error('response lost'))
    const first = render()
    await execute(first)
    const firstKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]?.[2]
    first.unmount()
    authenticatedUser.profile = { username: 'another-user' }
    const remounted = render()
    await execute(remounted)
    const secondKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[1]?.[2]
    expect(firstKey).toMatch(/^[0-9a-f]{32}$/)
    expect(secondKey).toMatch(/^[0-9a-f]{32}$/)
    expect(secondKey).not.toBe(firstKey)
  })

  it('acknowledges the receipt before detail loading and keeps its Run ID visible on read failure', async () => {
    vi.mocked(runApi.getRunById).mockRejectedValueOnce(new Error('details unavailable'))
    const wrapper = render()
    await execute(wrapper)
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('run-1')
    expect(sessionStorage.length).toBe(0)
    const firstKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]?.[2]
    await execute(wrapper)
    const secondKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[1]?.[2]
    expect(firstKey).toMatch(/^[0-9a-f]{32}$/)
    expect(secondKey).not.toBe(firstKey)
  })

  it('does not dispatch after the authenticated subject changes during confirmation', async () => {
    let confirm!: (value: 'confirm') => void
    confirmDialog.mockImplementationOnce(() => new Promise(resolve => { confirm = resolve }))
    const wrapper = render()
    await wrapper.get('[data-action="integrate"]').trigger('click')
    authenticatedUser.profile = { username: 'another-user' }
    confirm('confirm')
    await flushPromises()
    expect(releaseWindowApi.orchestrate).not.toHaveBeenCalled()
  })

  it('does not dispatch a confirmation resolved after the panel unmounts', async () => {
    let confirm!: (value: 'confirm') => void
    confirmDialog.mockImplementationOnce(() => new Promise(resolve => { confirm = resolve }))
    const wrapper = render()
    await wrapper.get('[data-action="integrate"]').trigger('click')
    wrapper.unmount()
    confirm('confirm')
    await flushPromises()
    expect(releaseWindowApi.orchestrate).not.toHaveBeenCalled()
    expect(sessionStorage.length).toBe(0)
  })

  it('acknowledges a sent receipt after unmount without loading or emitting into the old view', async () => {
    let receipt!: (value: string) => void
    vi.mocked(releaseWindowApi.orchestrate).mockImplementationOnce(() => new Promise(resolve => { receipt = resolve }))
    const wrapper = render()
    await execute(wrapper)
    expect(sessionStorage.length).toBe(1)
    wrapper.unmount()
    vi.mocked(runApi.list).mockClear()
    vi.mocked(runApi.getRunById).mockClear()
    receipt('run-1')
    await flushPromises()
    expect(sessionStorage.length).toBe(0)
    expect(runApi.list).not.toHaveBeenCalled()
    expect(runApi.getRunById).not.toHaveBeenCalled()
    expect(wrapper.emitted('refresh')).toBeUndefined()
  })

  it('does not expand to all repositories if the selected repository leaves the plan', async () => {
    const wrapper = render()
    await wrapper.get('[data-field="repository"]').setValue('repo-1')
    await wrapper.setProps({ planItems: planItems.filter(item => item.repoId === 'repo-2') })
    await execute(wrapper)
    expect(releaseWindowApi.orchestrate).not.toHaveBeenCalled()
    expect(wrapper.get('[data-action="integrate"]').attributes('disabled')).toBeDefined()
  })

})
