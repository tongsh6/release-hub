import { flushPromises, mount, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import OrchestrationPanel from '../OrchestrationPanel.vue'
import { releaseWindowApi } from '@/api/modules/releaseWindow'
import { runApi } from '@/api/runApi'
import { http, ApiError } from '@/api/http'
import { AxiosError } from 'axios'
import { readExecutionIntent, readExecutionIntents, saveExecutionIntent, acknowledgeExecutionIntent } from '@/utils/executionIntent'

enableAutoUnmount(afterEach)
const originalAdapter = http.defaults.adapter
afterEach(() => { http.defaults.adapter = originalAdapter; vi.useRealTimers() })

const confirmDialog = vi.hoisted(() => vi.fn<() => Promise<'confirm'>>())
const authenticatedUser = vi.hoisted(() => ({ profile: { username: 'admin' } as { username: string } | null }))
const authenticatedProfile = ref(authenticatedUser.profile)
Object.defineProperty(authenticatedUser, 'profile', {
  get: () => authenticatedProfile.value,
  set: (value: { username: string } | null) => { authenticatedProfile.value = value }
})
vi.mock('@/stores/user', () => ({ useUserStore: () => authenticatedUser }))

vi.mock('vue-i18n', async importOriginal => ({ ...await importOriginal<typeof import('vue-i18n')>(), useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: confirmDialog }
}))
vi.mock('@/api/modules/releaseWindow', () => ({ releaseWindowApi: { orchestrate: vi.fn() } }))
vi.mock('@/api/runApi', () => ({ runApi: { list: vi.fn(), getRunById: vi.fn(), getTasks: vi.fn() } }))
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
    vi.mocked(runApi.getTasks).mockResolvedValue([])
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

  it('keeps the original unresolved request when the user changes selection', async () => {
    vi.mocked(releaseWindowApi.orchestrate).mockRejectedValueOnce(new Error('response lost'))
    const wrapper = render()
    await execute(wrapper)
    const firstKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]?.[2]
    await wrapper.get('[data-field="repository"]').setValue('repo-1')
    await execute(wrapper)
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledTimes(1)
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')?.key).toBe(firstKey)
    expect(wrapper.find('[data-action="retrieve-receipt"]').exists()).toBe(true)
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
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')?.runId).toBe('run-1')
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
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')?.runId).toBe('run-1')
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

  it.each([{ frozen: true }, { scopeReady: false, planItems: [] }, { windowStatus: 'CLOSED' }])(
    'retrieves the saved request without rebuilding its scope when new execution is disabled: %j', async (overrides) => {
      vi.mocked(releaseWindowApi.orchestrate).mockRejectedValueOnce(new Error('response lost'))
      const first = render()
      await execute(first)
      const [, originalPayload, originalKey] = vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]!
      first.unmount()
      const page = render(overrides)
      await flushPromises()
      expect(page.get('[data-action="integrate"]').attributes('disabled')).toBeDefined()
      const recover = page.find('[data-action="retrieve-receipt"]')
      expect(recover.exists()).toBe(true)
      await recover.trigger('click')
      await flushPromises()
      expect(releaseWindowApi.orchestrate).toHaveBeenLastCalledWith('window-1', originalPayload, originalKey)
      expect(page.text()).toContain('run-1')
    }
  )

  it('keeps the confirmed receipt after remount and retrieves it using GET only', async () => {
    const first = render()
    await execute(first)
    first.unmount()
    const page = render({ frozen: true, scopeReady: false, planItems: [] })
    await flushPromises()
    expect(page.text()).toContain('run-1')
    const recover = page.find('[data-action="retrieve-receipt"]')
    expect(recover.exists()).toBe(true)
    await recover.trigger('click')
    await flushPromises()
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledTimes(1)
    expect(runApi.getRunById).toHaveBeenCalledWith('run-1')
  })



  it.each(['integrate', 'retrieve-receipt'])('uses a receipt acknowledged while the %s confirmation is open without sending again', async action => {
    let receipt!: (value: string) => void
    vi.mocked(releaseWindowApi.orchestrate).mockImplementationOnce(() => new Promise(resolve => { receipt = resolve }))
    const first = render()
    await execute(first)
    const originalKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]![2]
    first.unmount()
    let confirm!: (value: 'confirm') => void
    confirmDialog.mockImplementationOnce(() => new Promise(resolve => { confirm = resolve }))
    const page = render()
    await page.get(`[data-action="${action}"]`).trigger('click')
    receipt('run-1')
    await flushPromises()
    confirm('confirm')
    await flushPromises()
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledTimes(1)
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')).toMatchObject({ key: originalKey, runId: 'run-1' })
    expect(runApi.getRunById).toHaveBeenCalledWith('run-1')
  })

  it('preserves the same-key retrieval in flight when a late first rejection arrives from the old page', async () => {
    let rejectOriginal!: (reason: unknown) => void
    let receipt!: (value: string) => void
    vi.mocked(releaseWindowApi.orchestrate)
      .mockImplementationOnce(() => new Promise((_, reject) => { rejectOriginal = reject }))
      .mockImplementationOnce(() => new Promise(resolve => { receipt = resolve }))
    const first = render()
    await execute(first)
    const originalKey = vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]![2]
    first.unmount()
    const page = render()
    await page.get('[data-action="retrieve-receipt"]').trigger('click')
    await flushPromises()
    expect(vi.mocked(releaseWindowApi.orchestrate).mock.calls.map(call => call[2])).toEqual([originalKey, originalKey])
    rejectOriginal(new ApiError({ httpStatus: 400, code: 'RUN_006', message: 'scope was invalid' }))
    await flushPromises()
    const retainedBeforeReceipt = readExecutionIntent('admin', 'ORCHESTRATE:window-1')
    receipt('run-1')
    await flushPromises()
    expect(retainedBeforeReceipt).toMatchObject({ key: originalKey })
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')).toMatchObject({ key: originalKey, runId: 'run-1' })
    expect(page.text()).toContain('run-1')
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledTimes(2)
  })

  it('allows an explicitly corrected scope after a new request is definitively rejected with HTTP 400 RUN_006', async () => {
    const actual = await vi.importActual<typeof import('@/api/modules/releaseWindow')>('@/api/modules/releaseWindow')
    vi.mocked(releaseWindowApi.orchestrate).mockImplementation(actual.releaseWindowApi.orchestrate)
    const sent: Array<{ key: string; payload: { repoIds: string[] } }> = []
    http.defaults.adapter = async config => {
      sent.push({ key: String(config.headers['Idempotency-Key']), payload: JSON.parse(String(config.data)) })
      const response = { config, headers: {}, status: sent.length === 1 ? 400 : 202,
        statusText: sent.length === 1 ? 'Bad Request' : 'Accepted',
        data: sent.length === 1 ? { code: 'RUN_006', message: '范围已失效', data: null }
          : { code: '0', message: '', data: 'run-1' } }
      if (response.status === 400) throw new AxiosError('Bad Request', 'ERR_BAD_REQUEST', config, undefined, response)
      return response
    }
    const panel = render()
    await execute(panel)
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')).toBeNull()
    await panel.get('[data-field="repository"]').setValue('repo-2')
    await execute(panel)
    expect(sent).toHaveLength(2)
    expect(sent[1]?.key).not.toBe(sent[0]?.key)
    expect(sent[1]?.payload.repoIds).toEqual(['repo-2'])
    expect(confirmDialog).toHaveBeenCalledTimes(2)
  })

  it('retains the original request when a retrieval gets 400 after an uncertain first attempt', async () => {
    vi.mocked(releaseWindowApi.orchestrate).mockRejectedValueOnce(new Error('response lost'))
      .mockRejectedValueOnce(new ApiError({ httpStatus: 400, code: 'RUN_006', message: '范围失效' }))
    const panel = render()
    await execute(panel)
    const original = readExecutionIntent('admin', 'ORCHESTRATE:window-1')
    await panel.get('[data-action="retrieve-receipt"]').trigger('click')
    await flushPromises()
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')).toEqual(original)
    expect(vi.mocked(releaseWindowApi.orchestrate).mock.calls.map(call => call[2])).toEqual([original?.key, original?.key])
    await panel.get('[data-field="repository"]').setValue('repo-2')
    await execute(panel)
    expect(releaseWindowApi.orchestrate).toHaveBeenCalledTimes(2)
  })

  it.each([[409, 'RUN_005'], [409, 'RUN_007'], [500, 'RUN_006'], [400, 'RUN_007']] as const)(
    'preserves an unresolved first attempt for HTTP %s / %s', async (httpStatus, code) => {
      vi.mocked(releaseWindowApi.orchestrate).mockRejectedValueOnce(new ApiError({ httpStatus, code, message: '未确认受理结果' }))
      const panel = render()
      await execute(panel)
      const saved = readExecutionIntent('admin', 'ORCHESTRATE:window-1')
      expect(saved?.key).toBe(vi.mocked(releaseWindowApi.orchestrate).mock.calls[0]?.[2])
      expect(saved?.runId).toBeUndefined()
    })

  it.each(['confirmed', 'new-pending'] as const)('does not let a late rejection erase %s evidence', async state => {
    let reject!: (error: unknown) => void
    vi.mocked(releaseWindowApi.orchestrate).mockImplementationOnce(() => new Promise((_resolve, fail) => { reject = fail }))
    const panel = render()
    await execute(panel)
    const original = readExecutionIntent('admin', 'ORCHESTRATE:window-1')!
    acknowledgeExecutionIntent('admin', 'ORCHESTRATE:window-1', original.key, 'run-1')
    if (state === 'new-pending') saveExecutionIntent('admin', 'ORCHESTRATE:window-1',
      JSON.stringify({ repoIds: ['repo-2'], iterationKeys: ['ITER-2'], failFast: true }))
    const before = readExecutionIntents('admin', 'ORCHESTRATE:window-1')
    reject(new ApiError({ httpStatus: 400, code: 'RUN_006', message: 'late rejection' }))
    await flushPromises()
    expect(readExecutionIntents('admin', 'ORCHESTRATE:window-1')).toEqual(before)
  })

  it('keeps the previous confirmed receipt after rejecting a fresh request', async () => {
    const old = saveExecutionIntent('admin', 'ORCHESTRATE:window-1',
      JSON.stringify({ repoIds: ['repo-1'], iterationKeys: ['ITER-1'], failFast: true }))
    acknowledgeExecutionIntent('admin', 'ORCHESTRATE:window-1', old.key, 'run-1')
    vi.mocked(releaseWindowApi.orchestrate).mockRejectedValueOnce(new ApiError({ httpStatus: 400, code: 'RUN_006', message: '范围失效' }))
    const panel = render()
    await execute(panel)
    expect(readExecutionIntents('admin', 'ORCHESTRATE:window-1')).toEqual([{ ...old, runId: 'run-1' }])
    expect(panel.findAll('[data-action="retrieve-receipt"]')).toHaveLength(1)
  })

  it('retains the latest confirmed Run while a newer request has no receipt', async () => {
    const payload = JSON.stringify({ repoIds: ['repo-1'], iterationKeys: ['ITER-1'], failFast: true })
    const confirmed = saveExecutionIntent('admin', 'ORCHESTRATE:window-1', payload)
    acknowledgeExecutionIntent('admin', 'ORCHESTRATE:window-1', confirmed.key, 'run-1')
    const unresolved = saveExecutionIntent('admin', 'ORCHESTRATE:window-1',
      JSON.stringify({ repoIds: ['repo-2'], iterationKeys: ['ITER-2'], failFast: true }))
    vi.mocked(runApi.list).mockResolvedValue({ list: [{
      id: 'another-run', executionVersion: 3, runType: 'WINDOW_ORCHESTRATION', status: 'DONE',
      startedAt: '', finishedAt: '', operator: 'another-user'
    }], total: 1 })
    const panel = render()
    await flushPromises()
    expect(runApi.getRunById).toHaveBeenCalledWith('run-1')
    expect(runApi.getRunById).not.toHaveBeenCalledWith('another-run')
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')).toEqual(unresolved)
    expect(panel.findAll('[data-action="retrieve-receipt"]')).toHaveLength(2)
    expect(releaseWindowApi.orchestrate).not.toHaveBeenCalled()
  })

  it('keeps the accepted Run under observation when another Run becomes the newest list entry', async () => {
    vi.mocked(runApi.list).mockResolvedValue({ list: [{ id: 'someone-elses-run', executionVersion: 3,
      runType: 'WINDOW_ORCHESTRATION', status: 'DONE', startedAt: '', finishedAt: '', operator: 'other' }], total: 1 })
    vi.mocked(runApi.getRunById).mockImplementation(async id => ({ id: String(id), executionVersion: 3,
      runType: 'WINDOW_ORCHESTRATION', status: 'DONE', startedAt: '', finishedAt: '', operator: 'admin', items: [] }))
    const page = render()
    await flushPromises()
    await execute(page)
    expect((page.vm as unknown as { latestRunDetail: { id: string } }).latestRunDetail.id).toBe('run-1')
  })

})
