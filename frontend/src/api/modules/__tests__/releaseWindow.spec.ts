import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiPost } from '@/api/http'
import { closeWindow, orchestrate, type OrchestrateRequest } from '../releaseWindow'

vi.mock('@/api/http', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  http: {
    get: vi.fn()
  }
}))

describe('releaseWindowApi.orchestrate', () => {
  it('closes with CI disabled by default and keeps the returned cleanup Run', async () => {
    const run = { id: 'cleanup-1', runType: 'WINDOW_CLEANUP', status: 'NEEDS_REVIEW' }
    vi.mocked(apiPost).mockResolvedValueOnce(run)
    expect(await closeWindow('window-1')).toBe(run)
    expect(apiPost).toHaveBeenCalledWith('/v1/release-windows/window-1/close', { triggerCi: false })
  })

  it('sends CI only when explicitly selected', async () => {
    await closeWindow('window-1', { triggerCi: true })
    expect(apiPost).toHaveBeenCalledWith('/v1/release-windows/window-1/close', { triggerCi: true })
  })

  beforeEach(() => {
    vi.mocked(apiPost).mockReset()
    vi.mocked(apiPost).mockResolvedValue('run-1')
  })

  it('posts the frontend-selected repositories and iterations for orchestration', async () => {
    const request: OrchestrateRequest = {
      repoIds: ['repo-1'],
      iterationKeys: ['ITER-1'],
      failFast: true
    }

    await orchestrate('window-1', request)

    expect(apiPost).toHaveBeenCalledWith('/v1/release-windows/window-1/orchestrate', request)
  })
})
