import { apiGet, apiPost } from '@/api/http'

export type CleanupReviewDecision = 'PENDING' | 'APPROVE_FOR_APPLICATION_ENTRY' | 'REJECTED'
export type CleanupReviewStatus = 'ACCEPTED' | 'PENDING' | 'REJECTED'

export interface CleanupActionInput {
  resourceType: string
  resourceId: string
  riskType: string
  suggestedAction?: string
  executed?: boolean
  source?: string
  dataNamespace?: string
  reviewBatchId?: string
  assetScope?: string
  retentionPolicy?: string
  applicationEntry?: string
  preExecutionCheck?: string
  postExecutionVerification?: string
  reviewDecision?: CleanupReviewDecision | string
}

export interface CleanupActionReview {
  resourceType: string
  resourceId: string
  riskType: string
  dataNamespace?: string
  reviewBatchId?: string
  assetScope?: string
  retentionPolicy?: string
  reviewStatus: CleanupReviewStatus
  reason: string
  applicationEntry?: string
  preExecutionCheck?: string
  postExecutionVerification?: string
  dispositionLevel?: string
  allowedAction?: string
  rollbackBoundary?: string
  auditRecord?: string
  executionPermitted: boolean
}

export interface CleanupReviewRequest {
  reviewer: string
  sourceReport?: string
  resourceTypeFilter?: string
  riskTypeFilter?: string
  reviewStatusFilter?: CleanupReviewStatus | string
  assetScopeFilter?: string
  actions: CleanupActionInput[]
}

export interface CleanupReviewResult {
  reviewer: string
  sourceReport?: string
  total: number
  accepted: number
  pending: number
  rejected: number
  assetBoundaries: AssetBoundarySummary[]
  assetScopeCounts: AssetScopeCount[]
  actions: CleanupActionReview[]
}

export interface AssetBoundarySummary {
  key: string
  label: string
  description: string
  userVisible: boolean
  manualReviewCandidate: boolean
}

export interface AssetScopeCount {
  assetScope: string
  count: number
}

export type DispositionCaseStatus = 'PLANNED' | 'IN_PROGRESS' | 'VERIFIED' | 'FAILED' | 'CANCELLED'

export interface DataQualityDispositionCase {
  id: string
  caseKey: string
  sourceReport?: string
  dataNamespace?: string
  reviewBatchId?: string
  assetScope?: string
  retentionPolicy?: string
  resourceType: string
  resourceId: string
  riskType: string
  dispositionLevel: string
  applicationEntry?: string
  allowedAction?: string
  preExecutionCheck?: string
  postExecutionVerification?: string
  rollbackBoundary?: string
  auditRecord?: string
  actionSnapshot?: string
  preStateSnapshot?: string
  postStateSnapshot?: string
  status: DispositionCaseStatus | string
  requestedBy: string
  handledBy?: string
  verifiedBy?: string
  failureReason?: string
  rollbackNote?: string
  retryOfCaseId?: string
  createdAt: string
  updatedAt: string
  startedAt?: string
  verifiedAt?: string
  failedAt?: string
  cancelledAt?: string
}

export interface CreateDispositionCaseRequest {
  requestedBy: string
  sourceReport?: string
  action: CleanupActionReview
}

export interface DispositionCaseTransitionRequest {
  operator: string
  preStateSnapshot?: string
  postStateSnapshot?: string
  failureReason?: string
  rollbackNote?: string
}

export const dataQualityApi = {
  reviewCleanupActions(payload: CleanupReviewRequest): Promise<CleanupReviewResult> {
    return apiPost<CleanupReviewResult>('/v1/data-quality/cleanup-review', payload)
  },
  createDispositionCase(payload: CreateDispositionCaseRequest): Promise<DataQualityDispositionCase> {
    return apiPost<DataQualityDispositionCase>('/v1/data-quality/disposition-cases', payload)
  },
  listDispositionCases(): Promise<DataQualityDispositionCase[]> {
    return apiGet<DataQualityDispositionCase[]>('/v1/data-quality/disposition-cases')
  },
  startDispositionCase(id: string, payload: DispositionCaseTransitionRequest): Promise<DataQualityDispositionCase> {
    return apiPost<DataQualityDispositionCase>(`/v1/data-quality/disposition-cases/${id}/start`, payload)
  },
  verifyDispositionCase(id: string, payload: DispositionCaseTransitionRequest): Promise<DataQualityDispositionCase> {
    return apiPost<DataQualityDispositionCase>(`/v1/data-quality/disposition-cases/${id}/verify`, payload)
  },
  failDispositionCase(id: string, payload: DispositionCaseTransitionRequest): Promise<DataQualityDispositionCase> {
    return apiPost<DataQualityDispositionCase>(`/v1/data-quality/disposition-cases/${id}/fail`, payload)
  },
  cancelDispositionCase(id: string, payload: DispositionCaseTransitionRequest): Promise<DataQualityDispositionCase> {
    return apiPost<DataQualityDispositionCase>(`/v1/data-quality/disposition-cases/${id}/cancel`, payload)
  }
}
