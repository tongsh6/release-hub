import { apiGet, apiPost, apiPut, apiDel, http } from '@/api/http'
import type { PageResult, PageQuery, Id } from '@/types/crud'
import type { ApiPageResponse } from '@/api/repositoryApi'

export interface BranchRule {
  id: string
  name: string
  pattern: string
  type: 'ALLOW' | 'BLOCK'
  createdAt?: string
  updatedAt?: string
}

export interface CreateBranchRuleReq {
  name: string
  pattern: string
  type: 'ALLOW' | 'BLOCK'
}

export interface UpdateBranchRuleReq {
  name: string
  pattern: string
  type: 'ALLOW' | 'BLOCK'
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

  async check(branchName: string): Promise<{ branchName: string; compliant: boolean }> {
    return await apiGet<{ branchName: string; compliant: boolean }>(`/v1/branch-rules/check?branchName=${encodeURIComponent(branchName)}`)
  }
}
