import { flushPromises, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import BlockBoard from '../BlockBoard.vue'
import { runApi, type RunItem } from '@/api/runApi'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/api/runApi', () => ({ runApi: { list: vi.fn(), getRunById: vi.fn() } }))

const item = (id: string, result: RunItem['finalResult'], metadata: Record<string, string>, message: string): RunItem => ({
  id, windowKey: 'W1', repoId: 'repo-1', iterationKey: 'I1', plannedOrder: 1, executedOrder: 1,
  finalResult: result, metadata, steps: [{ actionType: 'TRY_MERGE', result: result!, message }]
})

describe('blocking evidence board', () => {
  it('groups explicit reasons and keeps absent evidence unknown instead of guessing from text', async () => {
    const run = { id: 'run-1', runType: 'WINDOW_ORCHESTRATION' as const, status: 'PARTIAL_FAILED' as const,
      operator: 'tester', startedAt: '', finishedAt: '' }
    vi.mocked(runApi.list).mockResolvedValue({ list: [run], total: 1 })
    vi.mocked(runApi.getRunById).mockResolvedValue({ ...run, executionVersion: 1, items: [
      item('conflict', 'MERGE_BLOCKED', { 'git.blockReason': 'CONFLICT' }, 'pipeline failed'),
      item('pending', 'MERGE_BLOCKED', { 'git.blockReason': 'PIPELINE_PENDING' }, 'waiting'),
      item('missing', 'FAILED', {}, 'conflict cannot establish actual reason'),
      item('unknown', 'UNKNOWN', { 'git.blockReason': 'UNKNOWN' }, 'external effect unresolved')
    ] })
    const wrapper = shallowMount(BlockBoard, { global: { directives: { loading: {} }, stubs: { ElButton: true, ElTag: true, ElEmpty: true, ElScrollbar: true, ElCard: true, ElCol: true, ElRow: true } } })
    await flushPromises()
    const groups = (wrapper.vm as any).groups as Array<{ reasonKey: string; items: Array<{ id: string }> }>
    expect(groups.find(group => group.reasonKey === 'conflict')?.items.map(item => item.id)).toEqual(['conflict'])
    expect(groups.find(group => group.reasonKey === 'pipelinePending')?.items.map(item => item.id)).toEqual(['pending'])
    expect(groups.find(group => group.reasonKey === 'pipelineFailed')?.items).toEqual([])
    expect(groups.find(group => group.reasonKey === 'unknown')?.items.map(item => item.id)).toEqual(['missing', 'unknown'])
  })

  it('includes review and cleanup failures even though ordinary retry is unavailable', async () => {
    const base = { operator: 'tester', startedAt: '', finishedAt: '' }
    vi.mocked(runApi.list).mockResolvedValue({ total: 2, list: [
      { ...base, id: 'review', runType: 'WINDOW_ORCHESTRATION', status: 'NEEDS_REVIEW' },
      { ...base, id: 'cleanup', runType: 'WINDOW_CLEANUP', status: 'FAILED' }
    ] })
    vi.mocked(runApi.getRunById).mockImplementation(async id => ({
      ...base, id: String(id), executionVersion: 1, runType: id === 'cleanup' ? 'WINDOW_CLEANUP' : 'WINDOW_ORCHESTRATION',
      status: id === 'cleanup' ? 'FAILED' : 'NEEDS_REVIEW', items: []
    }))
    vi.mocked(runApi.getRunById).mockClear()
    shallowMount(BlockBoard, { global: { directives: { loading: {} }, stubs: { ElButton: true, ElTag: true, ElEmpty: true, ElScrollbar: true, ElCard: true, ElCol: true, ElRow: true } } })
    await flushPromises()
    expect(runApi.getRunById).toHaveBeenCalledWith('review')
    expect(runApi.getRunById).toHaveBeenCalledWith('cleanup')
  })
})
