import { ApiError } from '@/api/http'

const DELETE_PROTECTION_MESSAGES: Record<string, string> = {
  REPO_011: 'repository.deleteBlocked',
  ITER_002: 'iteration.deleteBlocked',
  RW_014: 'releaseWindow.deleteBlocked',
  GROUP_008: 'group.deleteBlocked',
  GROUP_013: 'group.deleteReferenced'
}

export function deleteProtectionMessageKey(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  return DELETE_PROTECTION_MESSAGES[error.code] || null
}
