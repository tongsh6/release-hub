import { apiGet, apiPost } from '@/api/http'

export type CandidateDecision = 'APPROVE_FOR_CONTROLLED_REVIEW' | 'HOLD_FOR_FOLLOW_UP'
export type ChecklistStatus = 'CONFIRMED' | 'NEEDS_FOLLOW_UP' | 'NOT_APPLICABLE'

export interface EvidenceItem {
  label: string
  status: string
  sourcePath: string
  result: string
}

export interface RiskItem {
  level: string
  title: string
  status: string
  sourcePath: string
}

export interface ChecklistItem {
  key: string
  title: string
  expectedStatus: ChecklistStatus
  sourcePath: string
  required: boolean
}

export interface ChecklistDecision {
  key: string
  status: ChecklistStatus
  note?: string
}

export interface ReleaseCandidateSignoff {
  id: string
  candidateId: string
  candidateLabel: string
  reviewer: string
  decision: CandidateDecision
  note?: string
  checklist: ChecklistDecision[]
  createdAt: string
}

export interface ReleaseCandidateReviewSummary {
  candidateId: string
  candidateLabel: string
  conclusion: string
  recommendation: string
  evidence: EvidenceItem[]
  risks: RiskItem[]
  checklist: ChecklistItem[]
  latestSignoff?: ReleaseCandidateSignoff | null
}

export interface ReleaseCandidateSignoffRequest {
  candidateId: string
  reviewer?: string
  decision: CandidateDecision
  note?: string
  checklist: ChecklistDecision[]
}

export const releaseGovernanceApi = {
  getCandidateReview(): Promise<ReleaseCandidateReviewSummary> {
    return apiGet<ReleaseCandidateReviewSummary>('/v1/release-governance/candidate-review')
  },

  signoff(payload: ReleaseCandidateSignoffRequest): Promise<ReleaseCandidateSignoff> {
    return apiPost<ReleaseCandidateSignoff>('/v1/release-governance/candidate-review/signoffs', payload)
  }
}
