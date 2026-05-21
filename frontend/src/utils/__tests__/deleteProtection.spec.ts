import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/http'
import { deleteProtectionMessageKey } from '../deleteProtection'

describe('deleteProtectionMessageKey', () => {
  it('maps backend delete guard errors to frontend copy keys', () => {
    expect(deleteProtectionMessageKey(new ApiError({ code: 'REPO_011', message: 'attached' })))
      .toBe('repository.deleteBlocked')
    expect(deleteProtectionMessageKey(new ApiError({ code: 'ITER_002', message: 'attached' })))
      .toBe('iteration.deleteBlocked')
    expect(deleteProtectionMessageKey(new ApiError({ code: 'RW_014', message: 'blocked' })))
      .toBe('releaseWindow.deleteBlocked')
    expect(deleteProtectionMessageKey(new ApiError({ code: 'GROUP_008', message: 'children' })))
      .toBe('group.deleteBlocked')
    expect(deleteProtectionMessageKey(new ApiError({ code: 'GROUP_013', message: 'referenced' })))
      .toBe('group.deleteReferenced')
  })

  it('ignores non delete-protection errors', () => {
    expect(deleteProtectionMessageKey(new ApiError({ code: 'COMMON_001', message: 'invalid' })))
      .toBeNull()
    expect(deleteProtectionMessageKey(new Error('boom'))).toBeNull()
  })
})
