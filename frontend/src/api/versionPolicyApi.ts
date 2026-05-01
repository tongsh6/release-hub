import { http } from '@/api/http'
import type { PageResult, PageQuery } from '@/types/crud'
import type { ApiResponse } from '@/types/dto'
import type { ApiPageResponse } from '@/api/repositoryApi'

export interface VersionPolicy {
  id: string
  name: string
  scheme: string
  bumpRule: string
  createdAt?: string
  updatedAt?: string
}

// 用于前端展示的策略类型
export interface VersionPolicyDisplay {
  id: string
  name: string
  strategy: string  // 组合 scheme + bumpRule 用于展示
  scheme: string
  bumpRule: string
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

  /**
   * 获取所有版本策略（不分页，用于下拉选择）
   */
  async listAll(): Promise<VersionPolicyDisplay[]> {
    const res = await http.get<ApiResponse<VersionPolicy[]>>('/v1/version-policies')
    return (res.data.data || []).map(toDisplay)
  }
}
