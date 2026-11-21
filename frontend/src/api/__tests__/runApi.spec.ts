import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from '@/api/http'
import { runApi } from '@/api/runApi'

vi.mock('@/api/http', () => ({ http: { get: vi.fn(), post: vi.fn() }, apiGet: vi.fn(), apiPost: vi.fn() }))

describe('run API contract', () => {
  beforeEach(() => vi.clearAllMocks())

  it('maps the exact export contract and keeps the authoritative review state', async () => {
    const metadata = { 'git.projectId': '42', 'git.sourceBranch': 'feature/I1', 'git.targetBranch': 'release/W1', 'git.mrIid': '9', 'git.mrUrl': 'https://gitlab.example/project/-/merge_requests/9' }
    vi.mocked(http.get).mockResolvedValue({ data: {
      runId: 'run-1', runType: 'WINDOW_ORCHESTRATION', executionVersion: 1,
      status: 'NEEDS_REVIEW', operator: 'authenticated-user', startedAt: 1000, finishedAt: 2000,
      items: [{ id: 'item-1', windowKey: 'W1', repo: 'repo-1', iterationKey: 'I1',
        plannedOrder: 2, executedOrder: 1, finalResult: 'UNKNOWN', metadata,
        steps: [{ actionType: 'TRY_MERGE', result: 'UNKNOWN', startAt: 1000, endAt: 2000, message: 'merge outcome unknown' }] }]
    } })
    const detail = await runApi.getRunById('run-1')
    expect(detail).toEqual({
      id: 'run-1', runType: 'WINDOW_ORCHESTRATION', executionVersion: 1,
      status: 'NEEDS_REVIEW', operator: 'authenticated-user',
      startedAt: '1970-01-01T00:00:01.000Z', finishedAt: '1970-01-01T00:00:02.000Z',
      items: [{ id: 'item-1', windowKey: 'W1', repoId: 'repo-1', iterationKey: 'I1',
        plannedOrder: 2, executedOrder: 1, finalResult: 'UNKNOWN', metadata,
        steps: [{ actionType: 'TRY_MERGE', result: 'UNKNOWN',
          startedAt: '1970-01-01T00:00:01.000Z', finishedAt: '1970-01-01T00:00:02.000Z', message: 'merge outcome unknown' }] }]
    })
  })

  it('sends only item selection when retrying; the authenticated server supplies the actor', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: 'retry-1' })
    expect(await runApi.retry('run-1', ['item-1'])).toBe('retry-1')
    expect(http.post).toHaveBeenCalledWith('/v1/runs/run-1/retry', { items: ['item-1'] })
    expect(vi.mocked(http.post).mock.calls[0]?.[1]).not.toHaveProperty('operator')
  })
})