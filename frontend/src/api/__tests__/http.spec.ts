import { describe, it, expect } from 'vitest'
import { ApiError, type ApiPath } from '../http'

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
})
