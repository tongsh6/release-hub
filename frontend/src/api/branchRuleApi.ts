import { apiGet, apiPost, apiPut, apiDel, http } from '@/api/http'
import type { PageResult, PageQuery, Id } from '@/types/crud'
import type { ApiPageResponse } from '@/api/repositoryApi'

export type BranchRuleType = 'TEMPLATE' | 'REGEX'
export type Status = 'ENABLED' | 'DISABLED'
export type ScopeLevel = 'GLOBAL' | 'PROJECT' | 'SUB_PROJECT'

export interface ScopeView {
  level: ScopeLevel
  projectId?: string
  subProjectId?: string
}

export interface BranchRule {
  id: string
  name: string
  pattern: string
  type: BranchRuleType
  description?: string
  scope: ScopeView
  status: Status
  createdAt?: string
  updatedAt?: string
}

export interface CreateBranchRuleReq {
  name: string
  pattern: string
  type: BranchRuleType
  description?: string
  scopeLevel?: ScopeLevel
  scopeProjectId?: string
  scopeSubProjectId?: string
}

export interface UpdateBranchRuleReq {
  name: string
  pattern: string
  type: BranchRuleType
  description?: string
  scopeLevel?: ScopeLevel
  scopeProjectId?: string
  scopeSubProjectId?: string
}

export interface BranchRuleTestReq {
  pattern: string
  type: BranchRuleType
  branchName: string
}

export interface BranchRuleTestResp {
  ok: boolean
  rendered?: string
  errors?: string[]
}

export const branchRuleApi = {
  async list(query: PageQuery & { name?: string }): Promise<PageResult<BranchRule>> {
    const params = {
      page: query.page,
      size: query.pageSize,
      name: query.name
    }
    const res = await http.get<ApiPageResponse<BranchRule[]>>('/v1/branch-rules/paged', { params })
    return {
      list: res.data.data,
      total: res.data.page.total
    }
  },

  async get(id: Id): Promise<BranchRule> {
    return await apiGet<BranchRule>(`/v1/branch-rules/${id}`)
  },

  async create(req: CreateBranchRuleReq): Promise<BranchRule> {
    return await apiPost<BranchRule>('/v1/branch-rules', req)
  },

  async update(id: Id, req: UpdateBranchRuleReq): Promise<BranchRule> {
    return await apiPut<BranchRule>(`/v1/branch-rules/${id}`, req)
  },

  async remove(id: Id): Promise<void> {
    await apiDel<void>(`/v1/branch-rules/${id}`)
  },

  async enable(id: Id): Promise<void> {
    await apiPost<void>(`/v1/branch-rules/${id}/enable`, {})
  },

  async disable(id: Id): Promise<void> {
    await apiPost<void>(`/v1/branch-rules/${id}/disable`, {})
  },

  async check(branchName: string, scope?: { projectId?: string; subProjectId?: string }): Promise<{ branchName: string; compliant: boolean }> {
    const params = new URLSearchParams({ branchName })
    if (scope?.projectId) params.set('scopeProjectId', scope.projectId)
    if (scope?.subProjectId) params.set('scopeSubProjectId', scope.subProjectId)
    return await apiGet<{ branchName: string; compliant: boolean }>(
      `/v1/branch-rules/check?${params.toString()}`)
  },

  async test(req: BranchRuleTestReq): Promise<BranchRuleTestResp> {
    return await apiPost<BranchRuleTestResp>('/v1/branch-rules/test', req)
  }
}
