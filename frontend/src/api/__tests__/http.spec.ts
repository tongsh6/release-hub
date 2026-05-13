import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, http, request } from '../http'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ApiError', () => {
  it('should create an ApiError with code and message', () => {
    const err = new ApiError({ code: 'TEST', message: 'test error' })

    expect(err).toBeInstanceOf(Error)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.name).toBe('ApiError')
    expect(err.code).toBe('TEST')
    expect(err.message).toBe('test error')
  })

  it('should include optional fields', () => {
    const err = new ApiError({
      code: 'AUTH_FAILED',
      message: 'bad credentials',
      traceId: 'trace-123',
      httpStatus: 401,
      details: { retryAfter: 30 }
    })

    expect(err.traceId).toBe('trace-123')
    expect(err.httpStatus).toBe(401)
    expect(err.details).toEqual({ retryAfter: 30 })
  })

  it('should unwrap successful API response data', async () => {
    vi.spyOn(http, 'request').mockResolvedValue({
      data: {
        code: 'OK',
        message: 'OK',
        data: { id: 'repo-1' }
      }
    })

    await expect(request<{ id: string }>({ url: '/v1/repositories/repo-1', method: 'GET' }))
      .resolves.toEqual({ id: 'repo-1' })
  })

  it('should return raw response data when body is not an API envelope', async () => {
    vi.spyOn(http, 'request').mockResolvedValue({
      data: ['raw-item']
    })

    await expect(request<string[]>({ url: '/v1/raw', method: 'GET' }))
      .resolves.toEqual(['raw-item'])
  })
})
