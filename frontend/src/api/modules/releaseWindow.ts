import { apiGet, apiPost, http } from '@/api/http'
import type { BuildTool } from '@/types/dto'
import type { PageQuery, PageResult } from '@/types/crud'
import type { ApiPageResponse } from '@/api/repositoryApi'

const BASE = '/v1'

// --- DTOs (Matched with Backend) ---

export type ReleaseWindowStatus = 'DRAFT' | 'INIT' | 'OPEN' | 'FROZEN' | 'CLOSED' | 'PUBLISHED'

export interface ReleaseWindowView {
  id: string
  windowKey: string
  name: string
  description?: string
  plannedReleaseAt?: string
  status: ReleaseWindowStatus
  createdAt: string
  updatedAt: string
  frozen: boolean
  publishedAt?: string
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

export interface VersionUpdateResponse {
  runId: string
  status: string
}

// --- API Functions ---

export async function list(query: PageQuery & { name?: string; status?: string }): Promise<PageResult<ReleaseWindowView>> {
  const params = {
    page: query.page,
    size: query.pageSize,
    name: (query as any).name,
    status: (query as any).status
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

export function closeWindow(id: string): Promise<ReleaseWindowView> {
  return apiPost<ReleaseWindowView>(`${BASE}/release-windows/${id}/close`, {})
}

export function attach(id: string, iterationKey: string): Promise<string[]> {
  return apiPost<string[]>(`${BASE}/release-windows/${id}/attach`, { iterationKeys: [iterationKey] })
}

export function detach(id: string, iterationKey: string): Promise<boolean> {
  return apiPost<boolean>(`${BASE}/release-windows/${id}/detach`, { iterationKey })
}

export function listIterations(id: string): Promise<any[]> {
  return apiGet<any[]>(`${BASE}/release-windows/${id}/iterations`)
}

export function orchestrate(id: string): Promise<any> {
  return apiPost<any>(`${BASE}/release-windows/${id}/orchestrate`, {})
}

export function getPlan(id: string): Promise<any> {
  return apiGet<any>(`${BASE}/release-windows/${id}/plan`)
}

export function getDryPlan(id: string): Promise<any> {
  return apiGet<any>(`${BASE}/release-windows/${id}/dry-plan`)
}

export function executeVersionUpdate(id: string, req: VersionUpdateRequest): Promise<VersionUpdateResponse> {
  return apiPost<VersionUpdateResponse>(`${BASE}/release-windows/${id}/execute/version-update`, req)
}

// --- 代码合并相关 ---

export interface CodeMergeResult {
  repoId: string
  repoName: string
  sourceBranch: string
  targetBranch: string
  status: 'SUCCESS' | 'CONFLICT' | 'FAILED'
  message?: string
  mergedAt?: string
}

/**
 * 合并指定迭代的代码到 release 分支
 */
export function mergeIteration(windowId: string, iterationKey: string): Promise<CodeMergeResult[]> {
  return apiPost<CodeMergeResult[]>(`${BASE}/release-windows/${windowId}/iterations/${iterationKey}/merge`, {})
}

/**
 * 批量合并所有迭代的代码到 release 分支
 */
export function mergeAll(windowId: string): Promise<CodeMergeResult[]> {
  return apiPost<CodeMergeResult[]>(`${BASE}/release-windows/${windowId}/merge`, {})
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
  mergeStatus: 'PENDING' | 'MERGED' | 'CONFLICT'
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
  conflictType: 'MISMATCH' | 'REPO_AHEAD' | 'SYSTEM_AHEAD' | 'BRANCH_EXISTS' | 'BRANCH_NONCOMPLIANT' | 'CROSS_REPO_VERSION_MISMATCH' | 'MERGE_CONFLICT'
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

// Alias for compatibility if needed, or prefer using explicit names above
export const releaseWindowApi = {
  list,
  get: getById,
  create,
  freeze,
  unfreeze,
  publish,
  close: closeWindow,
  attach,
  detach,
  listIterations,
  orchestrate,
  getPlan,
  getDryPlan,
  executeVersionUpdate,
  mergeIteration,
  mergeAll,
  getBranchStatus,
  checkConflicts,
  getConflicts
}
