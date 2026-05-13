import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiPost } from '@/api/http'
import { orchestrate, type OrchestrateRequest } from '../releaseWindow'

vi.mock('@/api/http', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  http: {
    get: vi.fn()
  }
}))

describe('releaseWindowApi.orchestrate', () => {
  beforeEach(() => {
    vi.mocked(apiPost).mockReset()
    vi.mocked(apiPost).mockResolvedValue('run-1')
  })

  it('posts the frontend-selected repositories and iterations for orchestration', async () => {
    const request: OrchestrateRequest = {
      repoIds: ['repo-1'],
      iterationKeys: ['ITER-1'],
      failFast: false,
      operator: 'frontend'
    }

    await orchestrate('window-1', request)

    expect(apiPost).toHaveBeenCalledWith('/v1/release-windows/window-1/orchestrate', request)
  })
})
