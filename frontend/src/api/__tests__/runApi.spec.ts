import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import { http } from '@/api/http'
import { runApi } from '@/api/runApi'
import { orchestrate } from '@/api/modules/releaseWindow'

const originalAdapter = http.defaults.adapter
function respond(data: unknown, status = 200) {
  const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => ({
    data, status, statusText: status === 202 ? 'Accepted' : 'OK', headers: {}, config
  }))
  http.defaults.adapter = adapter
  return adapter
}
const envelope = (data: unknown) => ({ success: true, code: '0', message: 'OK', data })

describe('run API contract through the real HTTP client', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => { http.defaults.adapter = originalAdapter })

  it('maps the exact export contract and keeps the authoritative review state', async () => {
    const metadata = { 'git.projectId': '42', 'git.sourceBranch': 'feature/I1', 'git.targetBranch': 'release/W1', 'git.mrIid': '9', 'git.mrUrl': 'https://gitlab.example/project/-/merge_requests/9' }
    respond({
      runId: 'run-1', runType: 'WINDOW_ORCHESTRATION', executionVersion: 1,
      status: 'NEEDS_REVIEW', operator: 'authenticated-user', startedAt: 1000, finishedAt: 2000,
      items: [{ id: 'item-1', windowKey: 'W1', repo: 'repo-1', iterationKey: 'I1',
        plannedOrder: 2, executedOrder: 1, finalResult: 'UNKNOWN', metadata,
        steps: [{ actionType: 'TRY_MERGE', result: 'UNKNOWN', startAt: 1000, endAt: 2000, message: 'merge outcome unknown' }] }]
    })
    expect(await runApi.getRunById('run-1')).toEqual({
      id: 'run-1', runType: 'WINDOW_ORCHESTRATION', executionVersion: 1,
      status: 'NEEDS_REVIEW', operator: 'authenticated-user',
      startedAt: '1970-01-01T00:00:01.000Z', finishedAt: '1970-01-01T00:00:02.000Z',
      items: [{ id: 'item-1', windowKey: 'W1', repoId: 'repo-1', iterationKey: 'I1',
        plannedOrder: 2, executedOrder: 1, finalResult: 'UNKNOWN', metadata,
        steps: [{ actionType: 'TRY_MERGE', result: 'UNKNOWN',
          startedAt: '1970-01-01T00:00:01.000Z', finishedAt: '1970-01-01T00:00:02.000Z', message: 'merge outcome unknown' }] }]
    })
  })

  it('keeps a running repository cleanup unfinished and without a synthetic iteration', async () => {
    respond({
      runId: 'cleanup-1', runType: 'WINDOW_CLEANUP', executionVersion: 1,
      status: 'RUNNING', operator: 'authenticated-user', startedAt: 1000, finishedAt: null,
      items: [{ id: 'item-1', windowKey: 'W1', repo: 'repo-1', iterationKey: null,
        plannedOrder: 1, executedOrder: 0, finalResult: null,
        metadata: { 'plan.participatingIterationKeys': '["I1","I2"]' }, steps: [] }]
    })
    const detail = await runApi.getRunById('cleanup-1')
    expect(detail.status).toBe('RUNNING')
    expect(detail.finishedAt).toBeNull()
    expect(detail.items[0]?.iterationKey).toBeNull()
    expect(detail.items[0]?.metadata['plan.participatingIterationKeys']).toBe('["I1","I2"]')
  })

  it('queries real task facts without exposing an unsupported task retry write', async () => {
    const inherited = { taskId: 'task-1', runId: 'run-1', itemId: 'item-1', actionType: 'ENSURE_FEATURE',
      taskOrder: 1, status: 'INHERITED', startedAt: null, finishedAt: null, errorMessage: null }
    const transport = respond(envelope([inherited]))
    expect(await runApi.getTasks('run-1')).toEqual([inherited])
    expect(transport).toHaveBeenCalledTimes(1)
    expect(transport.mock.calls[0]?.[0]).toMatchObject({ method: 'get', url: '/v1/runs/run-1/tasks' })
    expect(runApi).not.toHaveProperty('retryTask')
  })

  it('unwraps the real retry receipt while sending only item IDs and the stable intent header', async () => {
    const transport = respond(envelope('retry-1'), 202)
    expect(await runApi.retry('run-1', ['item-1'], 'retry-intent-1')).toBe('retry-1')
    expect(transport).toHaveBeenCalledTimes(1)
    const config = transport.mock.calls[0]![0]
    expect(config).toMatchObject({ method: 'post', url: '/v1/runs/run-1/retry' })
    expect(config.headers.get('Idempotency-Key')).toBe('retry-intent-1')
    expect(JSON.parse(config.data)).toEqual({ items: ['item-1'] })
  })
  it('unwraps the real 202 orchestration envelope and preserves the saved key and payload', async () => {
    const request = { repoIds: ['repo-1'], iterationKeys: ['ITER-1'], failFast: true }
    const transport = respond(envelope('accepted-run'), 202)
    expect(await orchestrate('window-1', request, 'original-key')).toBe('accepted-run')
    const config = transport.mock.calls[0]![0]
    expect(config.headers.get('Idempotency-Key')).toBe('original-key')
    expect(JSON.parse(config.data)).toEqual(request)
  })
})
