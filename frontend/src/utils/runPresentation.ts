import type { RunStatus, RunType, RunItemResult } from '@/types/dto'
import type { RunItem } from '@/api/runApi'

type ResultKind = 'success' | 'failure' | 'skipped' | 'review' | 'pending'
const resultKinds: Record<RunItemResult, ResultKind> = {
  CREATED: 'success', ALREADY_EXISTS: 'success', MR_REUSED: 'success',
  MERGED: 'success', ALREADY_MERGED: 'success', SUCCESS: 'success',
  VERSION_UPDATE_SUCCESS: 'success', BRANCH_EXISTS: 'success', BRANCH_CREATED: 'success',
  TAG_CREATED: 'success', CI_TRIGGERED: 'success', ITERATION_CLOSED: 'success',
  UNKNOWN: 'review', FAILED: 'failure', MERGE_BLOCKED: 'failure', VERSION_UPDATE_FAILED: 'failure',
  SKIPPED_DUE_TO_BLOCK: 'skipped', SKIPPED: 'skipped', CI_NOT_CONFIGURED: 'skipped'
}
export function resultKind(result?: string | null): ResultKind { return result ? resultKinds[result as RunItemResult] ?? 'pending' : 'pending' }
export function needsResultReview(result?: string | null): boolean { return resultKind(result) === 'review' }
export function isFailedResult(result?: string | null): boolean { return resultKind(result) === 'failure' }
export function isSuccessfulResult(result?: string | null): boolean { return resultKind(result) === 'success' }
export function isSkippedResult(result?: string | null): boolean { return resultKind(result) === 'skipped' }
export function isRetryableResult(result?: string | null): boolean { return isFailedResult(result) || result === 'SKIPPED_DUE_TO_BLOCK' }
export function resultTagType(result?: string | null): 'success' | 'danger' | 'warning' | 'info' {
  switch (resultKind(result)) {
    case 'success': return 'success'
    case 'failure': return 'danger'
    case 'skipped': case 'review': return 'warning'
    default: return 'info'
  }
}
export function runStatusTagType(status: RunStatus): 'success' | 'danger' | 'warning' | 'info' {
  switch (status) {
    case 'DONE': return 'success'
    case 'FAILED': return 'danger'
    case 'PARTIAL_FAILED': case 'RUNNING': case 'NEEDS_REVIEW': return 'warning'
    default: return 'info'
  }
}
export function isRetryableRun(status: RunStatus | undefined, runType: RunType | undefined): boolean {
  return (status === 'FAILED' || status === 'PARTIAL_FAILED')
    && (runType === 'WINDOW_ORCHESTRATION' || runType === 'VERSION_UPDATE')
}
const runTypeKeys: Record<RunType, string> = {
  WINDOW_ORCHESTRATION: 'run.types.WINDOW_ORCHESTRATION',
  WINDOW_CLEANUP: 'run.types.WINDOW_CLEANUP',
  ATTACH_ITERATION: 'run.types.ATTACH_ITERATION',
  VERSION_UPDATE: 'run.types.VERSION_UPDATE'
}
export function runTypeKey(runType: RunType): string { return runTypeKeys[runType] }

const blockReasonKeys: Record<string, string> = {
  CONFLICT: 'conflict',
  PIPELINE_PENDING: 'pipelinePending',
  PIPELINE_FAILED: 'pipelineFailed',
  APPROVAL_REQUIRED: 'approvalRequired',
  PROTECTED_BRANCH: 'protectedBranch',
  PERMISSION_DENIED: 'permissionDenied',
  UNKNOWN: 'unknown'
}
export function blockReasonKey(reason: string | undefined): string {
  return reason ? blockReasonKeys[reason] ?? 'unknown' : 'unknown'
}
export function participatingIterationKeys(item: Pick<RunItem, 'metadata'>): string[] | null {
  const value = item.metadata['plan.participatingIterationKeys']
  if (!value) return null
  try {
    const parsed: unknown = JSON.parse(value)
    return Array.isArray(parsed) && parsed.every(key => typeof key === 'string' && key.trim().length > 0)
      ? parsed : null
  } catch { return null }
}

export function runItemEvidence(item: RunItem) {
  return {
    itemId: item.id, windowKey: item.windowKey, repoId: item.repoId, iterationKey: item.iterationKey,
    participatingIterationKeys: participatingIterationKeys(item),
    referencePolicy: item.metadata['cleanup.referencePolicy'],
    plannedOrder: item.plannedOrder, executedOrder: item.executedOrder, finalResult: item.finalResult,
    projectId: item.metadata['git.projectId'],
    source: item.metadata['git.sourceBranch'],
    target: item.metadata['git.targetBranch'],
    mrIid: item.metadata['git.mrIid'],
    mrUrl: item.metadata['git.mrUrl'],
    blockReason: item.metadata['git.blockReason'],
    sourceSha: item.metadata['git.sourceSha'],
    mergeSha: item.metadata['git.mergeSha'],
    mrDisposition: item.metadata['git.mrDisposition'],
    requestId: item.metadata['git.requestId']
  }
}
