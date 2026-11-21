import { get, post } from '@/api/http'

export interface GitOperation {
  id: string
  repoId: string
  runId?: string | null
  action: string
  state: string
  version: number
  operator: string
  blocked: boolean
  createdAt: string
  updatedAt: string
  reason?: string | null
  targetJson?: string
}
export interface GitOperationEvent {
  attemptId: string
  sequence: number
  type: string
  at: string
  evidenceJson: string
  reason?: string | null
}
export interface GitOperationObservation {
  id: string
  status: string
  observedAt: string
  evidenceJson: string
}
export interface GitOperationDetail {
  operation: GitOperation
  events: GitOperationEvent[]
  observations: GitOperationObservation[]
  moreObservations: boolean
}
export const gitOperationApi = {
  list: (repoId: string, page = 0) => get<GitOperation[]>(`/v1/repositories/${encodeURIComponent(repoId)}/operations`, { params: { page, size: 20 } }),
  listByRun: (runId: string, page = 0) => get<GitOperation[]>(`/v1/runs/${encodeURIComponent(runId)}/operations`, { params: { page, size: 20 } }),
  get: (id: string) => get<GitOperationDetail>(`/v1/git-operations/${encodeURIComponent(id)}`),
  reconcile: (id: string, version: number, key: string) => post<GitOperationObservation>(`/v1/git-operations/${encodeURIComponent(id)}/reconcile`,
    { expectedStateVersion: version }, { headers: { 'Idempotency-Key': key } })
}
