import { ApiError } from '@/api/http'

const GROUP_MOVE_MESSAGES: Record<string, string> = {
  GROUP_015: 'group.moveBlockedByChildren',
  GROUP_016: 'group.moveBlockedByReference',
  GROUP_017: 'group.moveTargetReferenced'
}

export function groupMoveProtectionMessageKey(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  return GROUP_MOVE_MESSAGES[error.code] || null
}
