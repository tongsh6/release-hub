import { apiDel, apiGet, apiPost, http } from '@/api/http'
import type { BuildTool } from '@/types/dto'
import type { PageQuery, PageResult } from '@/types/crud'
import type { ApiPageResponse } from '@/api/repositoryApi'
import type { Run } from '@/api/runApi'

const BASE = '/v1'

// --- DTOs (Matched with Backend) ---

export type ReleaseWindowStatus = 'DRAFT' | 'INIT' | 'OPEN' | 'FROZEN' | 'CLOSED' | 'PUBLISHED'

export interface ReleaseWindowView {
  id: string
  windowKey: string
  name: string
  description?: string
  plannedReleaseAt?: string
  groupCode: string
  status: ReleaseWindowStatus
  createdAt: string
  updatedAt: string
  frozen: boolean
  publishedAt?: string
  parallelActiveWindowCount?: number
  parallelActiveWindowKeys?: string[]
}

// Re-export for compatibility with some UI components using 'ReleaseWindow' name
export type ReleaseWindow = ReleaseWindowView

export interface CreateReleaseWindowReq {
  name: string
  description?: string
  plannedReleaseAt?: string
  groupCode: string
}

export type PublishReleaseWindowReq = Record<string, never>

export type FreezeReleaseWindowReq = Record<string, never>

export interface VersionUpdateRequest {
  repoId: string
  targetVersion: string
  buildTool: BuildTool
  repoPath: string
  pomPath?: string
  gradlePropertiesPath?: string
}

export interface BatchVersionUpdateRequest {
  targetVersion: string
  repositories: Array<{
    repoId: string
    buildTool: BuildTool
    repoPath: string
    pomPath?: string
    gradlePropertiesPath?: string
  }>
}

export interface VersionUpdateResponse {
  runId: string
  status: string
}

export interface VersionValidationRequest {
  policyId: string
  currentVersion: string
}

export interface VersionValidationResponse {
  valid: boolean
  derivedVersion?: string
  derivedBranch?: string
  errorMessage?: string
}

export interface OrchestrateRequest {
  repoIds: string[]
  iterationKeys: string[]
  failFast: boolean
}

export interface PlanItemView {
  windowKey: string
  repoId: string
  iterationKey: string
  plannedOrder: number
  lastExecutedOrder?: number
}

export interface ParallelWindowView {
  windowId: string
  windowKey: string
  name: string
  status: ReleaseWindowStatus
  plannedReleaseAt?: string
  iterationCount: number
  repoCount: number
  planItems: PlanItemView[]
}

export interface ReleaseWindowParallelScopeView {
  currentWindowId: string
  currentWindowKey: string
  groupCode: string
  activeWindowCount: number
  windows: ParallelWindowView[]
}

// --- API Functions ---

export async function list(query: PageQuery & { name?: string; status?: string; groupCode?: string }): Promise<PageResult<ReleaseWindowView>> {
  const params = {
    page: query.page,
    size: query.pageSize,
    name: (query as any).name,
    status: (query as any).status,
    groupCode: (query as any).groupCode
  }
  const res = await http.get<ApiPageResponse<ReleaseWindowView[]>>(`${BASE}/release-windows/paged`, { params })
  return {
    list: res.data.data,
    total: res.data.page.total
  }
}

export function getById(id: string): Promise<ReleaseWindowView> {
  return apiGet<ReleaseWindowView>(`${BASE}/release-windows/${id}`)
}

export function getParallelScope(id: string): Promise<ReleaseWindowParallelScopeView> {
  return apiGet<ReleaseWindowParallelScopeView>(`${BASE}/release-windows/${id}/parallel-scope`)
}

export function create(req: CreateReleaseWindowReq): Promise<ReleaseWindowView> {
  return apiPost<ReleaseWindowView>(`${BASE}/release-windows`, req)
}

export function freeze(id: string): Promise<ReleaseWindowView> {
  return apiPost<ReleaseWindowView>(`${BASE}/release-windows/${id}/freeze`, {})
}

export function unfreeze(id: string): Promise<ReleaseWindowView> {
  return apiPost<ReleaseWindowView>(`${BASE}/release-windows/${id}/unfreeze`, {})
}

export function publish(id: string): Promise<ReleaseWindowView> {
  return apiPost<ReleaseWindowView>(`${BASE}/release-windows/${id}/publish`, {})
}

export function closeWindow(id: string, options: { triggerCi: boolean } = { triggerCi: false }): Promise<Run> {
  return apiPost<Run>(`${BASE}/release-windows/${id}/close`, options)
}

export function deleteWindow(id: string): Promise<boolean> {
  return apiDel<boolean>(`${BASE}/release-windows/${id}`)
}

export interface WindowIterationView {
  iterationKey: string
  attachAt: string
  releaseBranch: string | null
  branchCreated: boolean
  lastMergeAt: string | null
}

export function attach(id: string, iterationKey: string): Promise<WindowIterationView[]> {
  return apiPost<WindowIterationView[]>(`${BASE}/release-windows/${id}/attach`, { iterationKeys: [iterationKey] })
}

export function detach(id: string, iterationKey: string): Promise<boolean> {
  return apiPost<boolean>(`${BASE}/release-windows/${id}/detach`, { iterationKey })
}

export function listIterations(id: string): Promise<WindowIterationView[]> {
  return apiGet<WindowIterationView[]>(`${BASE}/release-windows/${id}/iterations`)
}

export function orchestrate(id: string, req: OrchestrateRequest, key: string): Promise<string> {
  return apiPost<string>(`${BASE}/release-windows/${id}/orchestrate`, req, { headers: { 'Idempotency-Key': key } })
}

export function getPlan(id: string): Promise<PlanItemView[]> {
  return apiGet<PlanItemView[]>(`${BASE}/release-windows/${id}/plan`)
}


export function executeVersionUpdate(id: string, req: VersionUpdateRequest): Promise<VersionUpdateResponse> {
  return apiPost<VersionUpdateResponse>(`${BASE}/release-windows/${id}/execute/version-update`, req)
}

export function executeBatchVersionUpdate(id: string, req: BatchVersionUpdateRequest): Promise<VersionUpdateResponse> {
  return apiPost<VersionUpdateResponse>(`${BASE}/release-windows/${id}/execute/batch-version-update`, req)
}

export function validateVersion(id: string, req: VersionValidationRequest): Promise<VersionValidationResponse> {
  return apiPost<VersionValidationResponse>(`${BASE}/release-windows/${id}/validate`, req)
}

// --- 分支状态相关 ---

export interface FeatureBranchInfo {
  branchName: string
  exists: boolean
  latestCommit?: string
}

export interface ReleaseBranchInfo {
  branchName: string
  exists: boolean
  latestCommit?: string
}

export interface RepoBranchStatus {
  repoId: string
  repoName: string
  repoCloneUrl: string
  iterationKey: string
  featureBranch: FeatureBranchInfo
  releaseBranch: ReleaseBranchInfo
}

export interface BranchStatusView {
  windowId: string
  windowKey: string
  repos: RepoBranchStatus[]
}

export function getBranchStatus(id: string): Promise<BranchStatusView> {
  return apiGet<BranchStatusView>(`${BASE}/release-windows/${id}/branch-status`)
}

// --- 冲突检测相关 ---

export interface ConflictItemView {
  repoId: string
  repoName: string
  iterationKey: string
  conflictType: 'MISMATCH' | 'REPO_AHEAD' | 'SYSTEM_AHEAD' | 'BRANCH_EXISTS' | 'BRANCH_NONCOMPLIANT' | 'CROSS_REPO_VERSION_MISMATCH' | 'MERGE_CONFLICT' | 'GIT_PERMISSION_DENIED' | 'GIT_UNAVAILABLE'
  sourceBranch?: string
  targetBranch?: string
  systemVersion?: string
  repoVersion?: string
  message: string
  suggestion: string
}

export interface ConflictReportView {
  windowId: string
  checkedAt: string
  hasConflicts: boolean
  totalCount: number
  conflicts: ConflictItemView[]
}

export function checkConflicts(windowId: string): Promise<ConflictReportView> {
  return apiPost<ConflictReportView>(`${BASE}/release-windows/${windowId}/conflicts/check`, {})
}

export function getConflicts(windowId: string): Promise<ConflictReportView> {
  return apiGet<ConflictReportView>(`${BASE}/release-windows/${windowId}/conflicts`)
}

// Window operations
export const releaseWindowApi = {
  list,
  get: getById,
  getParallelScope,
  create,
  freeze,
  unfreeze,
  publish,
  close: closeWindow,
  delete: deleteWindow,
  attach,
  detach,
  listIterations,
  orchestrate,
  getPlan,
  executeVersionUpdate,
  executeBatchVersionUpdate,
  validateVersion,
  getBranchStatus,
  checkConflicts,
  getConflicts
}
