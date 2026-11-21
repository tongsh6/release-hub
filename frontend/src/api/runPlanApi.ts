import { get, post } from './http'
import type { RunStatus } from '@/types/dto'

export interface RunPlanItem {
  itemId: string
  plannedOrder: number
  repoId: string
  repoName: string
  iterationKey: string | null
  gitProvider: string
  defaultBranch: string
  featureBranch: string | null
  featureBranchMode: string | null
  releaseBranch: string
  baseVersion: string | null
  devVersion: string | null
  targetVersion: string | null
  steps: string[]
  participatingIterationKeys: string[] | null
  tagName: string | null
  triggerCi: boolean
  ciRef: string | null
}

export interface RunPlanPreview {
  runId: string
  windowId: string
  operator: string
  createdAt: string
  planHash: string
  executionVersion: number
  status: RunStatus
  evidenceLevel: 'LOCAL_ONLY'
  executionPermitted: false
  snapshot: {
    windowKey: string
    groupCode: string
    windowStatus: string
    frozen: boolean
    failFast: boolean
    items: RunPlanItem[]
  }
}

export interface RunPlanSummary {
  runId: string
  operator: string
  createdAt: string
  itemCount: number
}

export interface RunPlanRequest { repoIds: string[]; iterationKeys: string[]; failFast: boolean }

export const runPlanApi = {
  create: (windowId: string, body: RunPlanRequest, key: string) =>
    post<RunPlanPreview>(`/v1/release-windows/${encodeURIComponent(windowId)}/run-plans`, body, { headers: { 'Idempotency-Key': key } }),
  list: (windowId: string) => get<RunPlanSummary[]>(`/v1/release-windows/${encodeURIComponent(windowId)}/run-plans`),
  get: (runId: string) => get<RunPlanPreview>(`/v1/runs/${encodeURIComponent(runId)}/plan`)
}
