import { apiGet, apiPost, http } from '@/api/http'
import { API_BASE, toQuery } from './_shared'
import type { BuildTool, RunStatus } from '@/types/dto'
import type { PageQuery, PageResult } from '@/types/crud'
import type { ApiPageResponse } from '@/api/repositoryApi'

// DTO Definitions
export interface VersionScanReq {
  target: {
    projectId?: string
    subProjectId?: string
    repoUrl?: string
  }
  branchName: string
  buildTool: BuildTool
  policyId: string
  scanRootPath?: string
  pomPath?: string
}

export interface MavenModuleVersionDTO {
  moduleName: string
  groupId?: string
  artifactId?: string
  pomPath: string
  currentVersion: string
  suggestedVersion: string
  updatable: boolean
  reason?: string
}

export interface MavenScanResultDTO {
  modules: MavenModuleVersionDTO[]
  warnings?: string[]
}

export interface VersionScanResp {
  runId: string
  status: RunStatus
  result?: MavenScanResultDTO
}

export interface VersionUpdateReq {
  runId?: string
  target?: {
    projectId?: string
    subProjectId?: string
    repoUrl?: string
  }
  branchName?: string
  policyId?: string
  commitMode: 'NO_COMMIT' | 'COMMIT'
  commitMessage?: string
  modules?: Array<{
    pomPath: string
    newVersion: string
  }>
}

export interface VersionUpdateResp {
  runId: string
  status: RunStatus
}

export interface RunListFilter {
  projectId?: string
  subProjectId?: string
  branchName?: string
  status?: RunStatus
  from?: string
  to?: string
}

export interface VersionRunSummaryDTO {
  runId: string
  type: 'SCAN' | 'UPDATE'
  status: RunStatus
  projectName?: string
  repoUrl?: string
  branchName: string
  policyName?: string
  startedAt: string
  finishedAt?: string
}

// 与 VersionOpsController.RunDetailView 保持一致。
export interface VersionRunDetailDTO {
  runId: string
  type: string
  status: RunStatus
  branchName: string | null
  startedAt: string | null
  finishedAt: string | null
  items: Array<{
    windowKey: string
    repoId: string
    iterationKey: string
    result: string | null
    steps: Array<{
      actionType: string
      result: string
      message: string | null
      startAt: string
      endAt: string
    }>
  }>
}

export interface RunLogsResp {
  runId: string
  lines: string[]
}

const MODULE_PATH = `${API_BASE}/version-ops`

export function scan(data: VersionScanReq): Promise<VersionScanResp> {
  return apiPost<VersionScanResp>(`${MODULE_PATH}/scan`, data)
}

export function update(data: VersionUpdateReq): Promise<VersionUpdateResp> {
  return apiPost<VersionUpdateResp>(`${MODULE_PATH}/update`, data)
}

export function pageRuns(query: PageQuery & RunListFilter): Promise<PageResult<VersionRunSummaryDTO>> {
  const params = toQuery({
    page: query.page,
    size: query.pageSize,
    projectId: query.projectId,
    subProjectId: query.subProjectId,
    branchName: query.branchName,
    status: query.status,
    from: query.from,
    to: query.to
  })
  return http.get<ApiPageResponse<VersionRunSummaryDTO[]>>(`${MODULE_PATH}/runs/paged`, { params })
    .then((res) => ({
      list: res.data.data,
      total: res.data.page.total
    }))
}

export function getRunDetail(runId: string): Promise<VersionRunDetailDTO | null> {
  return apiGet<VersionRunDetailDTO | null>(`${MODULE_PATH}/runs/${runId}`)
}

export function getRunLogs(runId: string): Promise<RunLogsResp> {
  return apiGet<RunLogsResp>(`${MODULE_PATH}/runs/${runId}/logs`)
}
