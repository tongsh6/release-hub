import { describe, expect, it, vi } from 'vitest'
import { groupMoveProtectionMessageKey } from '../groupMoveProtection'
import { ApiError } from '@/api/http'

vi.mock('@/api/http', () => ({
  ApiError: class ApiError extends Error {
    code: string

    constructor(args: { code: string; message: string }) {
      super(args.message)
      this.code = args.code
    }
  }
}))

describe('groupMoveProtectionMessageKey', () => {
  it('maps group movement governance errors to user-facing messages', () => {
    expect(groupMoveProtectionMessageKey(new ApiError({ code: 'GROUP_015', message: 'children' })))
      .toBe('group.moveBlockedByChildren')
    expect(groupMoveProtectionMessageKey(new ApiError({ code: 'GROUP_016', message: 'referenced' })))
      .toBe('group.moveBlockedByReference')
    expect(groupMoveProtectionMessageKey(new ApiError({ code: 'GROUP_017', message: 'target referenced' })))
      .toBe('group.moveTargetReferenced')
  })

  it('ignores unrelated errors', () => {
    expect(groupMoveProtectionMessageKey(new ApiError({ code: 'GROUP_013', message: 'referenced' })))
      .toBeNull()
    expect(groupMoveProtectionMessageKey(new Error('network'))).toBeNull()
  })
})
