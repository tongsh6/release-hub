import { apiPost } from '@/api/http'

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

export const dataQualityApi = {
  reviewCleanupActions(payload: CleanupReviewRequest): Promise<CleanupReviewResult> {
    return apiPost<CleanupReviewResult>('/v1/data-quality/cleanup-review', payload)
  }
}
