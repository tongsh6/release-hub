import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RunPlanPreviewDialog from '../RunPlanPreviewDialog.vue'
import { runPlanApi, type RunPlanPreview } from '@/api/runPlanApi'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/utils/error', () => ({ handleError: vi.fn() }))
vi.mock('@/api/runPlanApi', () => ({ runPlanApi: { list: vi.fn(), get: vi.fn(), create: vi.fn() } }))

const plan: RunPlanPreview = {
  runId: 'saved-plan', windowId: 'window-1', operator: 'admin', createdAt: '2026-10-05T00:00:00Z', planHash: 'hash',
  executionVersion: 2, status: 'PLANNED', evidenceLevel: 'LOCAL_ONLY', executionPermitted: false,
  snapshot: { windowKey: 'RW-1', groupCode: 'G001', windowStatus: 'DRAFT', frozen: false, failFast: true,
    items: [{ itemId: 'item-1', plannedOrder: 1, repoId: 'repo-1', repoName: 'repo-one', iterationKey: 'ITER-1',
      gitProvider: 'GITLAB', defaultBranch: 'main', featureBranch: 'feature/saved', releaseBranch: 'release/saved',
      baseVersion: '1.0.0', devVersion: '1.1.0-SNAPSHOT', targetVersion: '1.1.0', steps: ['TRY_MERGE'] }] }
}

function render() {
  return mount(RunPlanPreviewDialog, {
    props: { modelValue: true, windowId: 'window-1', repoIds: ['repo-1'], iterationKeys: ['ITER-1'], canCreate: true },
    global: { directives: { loading: {}, perm: {} }, stubs: {
      ElDialog: { template: '<section><slot /></section>' },
      ElAlert: { props: ['title'], template: '<p>{{ title }}</p>' },
      ElButton: { emits: ['click'], props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' },
      ElSelect: { props: ['modelValue'], emits: ['update:modelValue', 'change'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>' },
      ElOption: { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
      ElEmpty: true
    } }
  })
}

describe('RunPlanPreviewDialog', () => {
  afterEach(() => vi.unstubAllGlobals())
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(runPlanApi.list).mockResolvedValue([{ runId: plan.runId, operator: 'admin', createdAt: plan.createdAt, itemCount: 1 }])
    vi.mocked(runPlanApi.get).mockResolvedValue(plan)
    vi.mocked(runPlanApi.create).mockResolvedValue(plan)
  })

  it('loadsSavedSnapshotWithoutCreatingOrExecuting', async () => {
    const wrapper = render()
    await flushPromises()
    expect(runPlanApi.list).toHaveBeenCalledWith('window-1')
    expect(runPlanApi.get).toHaveBeenCalledWith('saved-plan')
    expect(runPlanApi.create).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('feature/saved')
    expect(wrapper.text()).toContain('release/saved')
    expect(wrapper.text()).toContain('runPlan.steps.TRY_MERGE')
    expect(wrapper.text()).toContain('runPlan.readOnlyNotice')
  })

  it('reusesKeyAfterLostResponseAndChangesKeyForNewScope', async () => {
    vi.mocked(runPlanApi.create).mockRejectedValueOnce(new Error('response lost'))
    const wrapper = render()
    await flushPromises()
    const save = () => wrapper.findAll('button').find(b => b.text() === 'runPlan.create')!
    await save().trigger('click'); await flushPromises()
    await save().trigger('click'); await flushPromises()
    const calls = vi.mocked(runPlanApi.create).mock.calls
    expect(calls[0]![2]).toBe(calls[1]![2])
    expect(calls[0]![1]).toEqual({ repoIds: ['repo-1'], iterationKeys: ['ITER-1'], failFast: true })
    await wrapper.setProps({ repoIds: ['repo-2'] })
    await save().trigger('click'); await flushPromises()
    expect(calls[2]![2]).not.toBe(calls[1]![2])
    expect(wrapper.emitted('created')).toHaveLength(2)
  })

  it('queriesSelectedSavedPlanByRunId', async () => {
    const wrapper = render(); await flushPromises()
    vi.mocked(runPlanApi.list).mockResolvedValue([{ runId: 'older-plan', operator: 'admin', createdAt: plan.createdAt, itemCount: 1 }])
    const refresh = wrapper.findAll('button').find(b => b.text() === 'common.refresh')!
    await refresh.trigger('click'); await flushPromises()
    await wrapper.find('select').setValue('older-plan'); await flushPromises()
    expect(runPlanApi.get).toHaveBeenLastCalledWith('older-plan')
    expect(runPlanApi.create).not.toHaveBeenCalled()
  })

  it('createsAnIdempotencyKeyWithoutRandomUUID', async () => {
    vi.stubGlobal('crypto', { getRandomValues: crypto.getRandomValues.bind(crypto) })
    const wrapper = render(); await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === 'runPlan.create')!.trigger('click')
    await flushPromises()
    expect(vi.mocked(runPlanApi.create).mock.calls[0]![2]).toMatch(/^[0-9a-f]{32}$/)
  })
})
