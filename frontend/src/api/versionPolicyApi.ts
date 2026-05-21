import { http } from '@/api/http'
import type { PageResult, PageQuery } from '@/types/crud'
import type { ApiResponse } from '@/types/dto'
import type { ApiPageResponse } from '@/api/repositoryApi'

export interface VersionPolicy {
  id: string
  name: string
  scheme: string
  bumpRule: string
  scope?: {
    level: 'GLOBAL' | 'PROJECT' | 'SUB_PROJECT'
    projectId?: string
    subProjectId?: string
  }
  createdAt?: string
  updatedAt?: string
}

export interface CreateVersionPolicyReq {
  name: string
  scheme: string
  bumpRule: string
  scopeLevel?: 'GLOBAL' | 'PROJECT' | 'SUB_PROJECT'
  scopeProjectId?: string
  scopeSubProjectId?: string
}

export type UpdateVersionPolicyReq = CreateVersionPolicyReq

// 用于前端展示的策略类型
export interface VersionPolicyDisplay {
  id: string
  name: string
  strategy: string  // 组合 scheme + bumpRule 用于展示
  scheme: string
  bumpRule: string
  scope?: VersionPolicy['scope']
  createdAt?: string
  updatedAt?: string
}

// 将后端数据转换为前端展示格式
function toDisplay(policy: VersionPolicy): VersionPolicyDisplay {
  // 生成策略描述
  let strategy = policy.scheme
  if (policy.bumpRule && policy.bumpRule !== 'NONE') {
    strategy += ` (${policy.bumpRule})`
  }
  
  return {
    id: policy.id,
    name: policy.name,
    strategy,
    scheme: policy.scheme,
    bumpRule: policy.bumpRule,
    scope: policy.scope,
    createdAt: policy.createdAt,
    updatedAt: policy.updatedAt
  }
}

export const versionPolicyApi = {
  async list(query: PageQuery & { name?: string }): Promise<PageResult<VersionPolicyDisplay>> {
    const params = {
      page: query.page,
      size: query.pageSize,
      keyword: query.name
    }
    const res = await http.get<ApiPageResponse<VersionPolicy[]>>('/v1/version-policies/paged', { params })
    const list = (res.data.data || []).map(toDisplay)
    return {
      list,
      total: res.data.page.total
    }
  },

  /**
   * 获取版本策略详情
   * @param id 版本策略 ID
   */
  async get(id: string): Promise<VersionPolicyDisplay> {
    const res = await http.get<ApiResponse<VersionPolicy>>(`/v1/version-policies/${id}`)
    return toDisplay(res.data.data)
  },

  async create(req: CreateVersionPolicyReq): Promise<VersionPolicyDisplay> {
    const res = await http.post<ApiResponse<VersionPolicy>>('/v1/version-policies', req)
    return toDisplay(res.data.data)
  },

  async update(id: string, req: UpdateVersionPolicyReq): Promise<VersionPolicyDisplay> {
    const res = await http.put<ApiResponse<VersionPolicy>>(`/v1/version-policies/${id}`, req)
    return toDisplay(res.data.data)
  },

  async remove(id: string): Promise<void> {
    await http.delete(`/v1/version-policies/${id}`)
  },

  /**
   * 获取所有版本策略（不分页，用于下拉选择）
   */
  async listAll(): Promise<VersionPolicyDisplay[]> {
    const res = await http.get<ApiResponse<VersionPolicy[]>>('/v1/version-policies')
    return (res.data.data || []).map(toDisplay)
  },

  async applicable(scope?: { projectId?: string; subProjectId?: string }): Promise<VersionPolicyDisplay[]> {
    const params: Record<string, string> = {}
    if (scope?.projectId) params.scopeProjectId = scope.projectId
    if (scope?.subProjectId) params.scopeSubProjectId = scope.subProjectId
    const res = await http.get<ApiResponse<VersionPolicy[]>>('/v1/version-policies/applicable', { params })
    return (res.data.data || []).map(toDisplay)
  }
}
