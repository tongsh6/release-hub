import { apiGet, apiPost, apiPut, apiDel } from '@/api/http'
import type { components } from '@/api/schema'

const BASE = '/v1'

export type GroupView = components['schemas']['GroupView']
export type GroupNodeView = components['schemas']['GroupNodeView']
export type CreateGroupReq = components['schemas']['CreateGroupRequest']
export type UpdateGroupReq = { name: string; parentCode?: string }
export type GroupNode = GroupNodeView & { children?: GroupNode[] }

export async function list(): Promise<GroupView[]> {
  return apiGet<GroupView[]>(`${BASE}/groups`)
}

export async function listTree(): Promise<GroupNode[]> {
  return apiGet<GroupNode[]>(`${BASE}/groups/tree`)
}

export async function create(req: CreateGroupReq): Promise<string> {
  return apiPost<string>(`${BASE}/groups`, req)
}

// 以下接口暂未在 OpenAPI 中声明，先通过 http 封装保持可用；待后端补充后切换到 apiClient
export function getById(id: string): Promise<GroupView> {
  return apiGet<GroupView>(`${BASE}/groups/${encodeURIComponent(id)}`)
}

export function update(id: string, req: UpdateGroupReq): Promise<GroupView> {
  return apiPut<GroupView>(`${BASE}/groups/${encodeURIComponent(id)}`, req)
}

export function remove(id: string): Promise<void> {
  return apiDel<void>(`${BASE}/groups/${encodeURIComponent(id)}`)
}

export const groupApi = {
  list,
  listTree,
  get: getById,
  create,
  update,
  remove
}
