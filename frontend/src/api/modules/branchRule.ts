import { apiPost, apiPut, http } from '@/api/http'
import { API_BASE, toQuery } from './_shared'
import type { Status, BranchRuleScopeDTO, BranchRuleScopeReq } from '@/types/dto'
import type { PageQuery, PageResult } from '@/types/crud'
import type { ApiPageResponse } from '@/api/repositoryApi'

export type BranchRuleType = 'TEMPLATE' | 'REGEX'

export interface BranchRuleDTO {
  id: string
  name: string
  type: BranchRuleType
  pattern: string
  description?: string
  scope: BranchRuleScopeDTO
  status: Status
  updatedAt: string
}

export interface CreateBranchRuleReq {
  name: string
  type: BranchRuleType
  pattern: string
  description?: string
  scope: BranchRuleScopeReq
}

export interface UpdateBranchRuleReq {
  name?: string
  type?: BranchRuleType
  pattern?: string
  description?: string
  scope?: BranchRuleScopeReq
  status?: Status
}

export interface BranchRuleTestReq {
  ruleId?: string
  type?: BranchRuleType
  pattern?: string
  input: {
    branchName?: string
    params?: Record<string, any>
  }
}

export interface BranchRuleTestResp {
  ok: boolean
  rendered?: string
  errors?: string[]
}

const MODULE_PATH = `${API_BASE}/branch-rules`

export async function pageBranchRules(query: PageQuery): Promise<PageResult<BranchRuleDTO>> {
  const params = toQuery({
    page: query.page,
    size: query.pageSize
  })
  const res = await http.get<ApiPageResponse<BranchRuleDTO[]>>(`${MODULE_PATH}/paged`, { params })
  return {
    list: res.data.data,
    total: res.data.page.total
  }
}

export function createBranchRule(data: CreateBranchRuleReq): Promise<BranchRuleDTO> {
  return apiPost<BranchRuleDTO>(MODULE_PATH, data)
}

export function updateBranchRule(id: string, data: UpdateBranchRuleReq): Promise<BranchRuleDTO> {
  return apiPut<BranchRuleDTO>(`${MODULE_PATH}/${id}`, data)
}

export function enableBranchRule(id: string): Promise<void> {
  return apiPost<void>(`${MODULE_PATH}/${id}/enable`)
}

export function disableBranchRule(id: string): Promise<void> {
  return apiPost<void>(`${MODULE_PATH}/${id}/disable`)
}

export function testBranchRule(data: BranchRuleTestReq): Promise<BranchRuleTestResp> {
  return apiPost<BranchRuleTestResp>(`${MODULE_PATH}/test`, data)
}
