import type { PageResult, PageQuery } from '@/types/crud'
import { apiGet, apiPost, apiPut, apiDel, http } from '@/api/http'
import type { ApiPageResponse } from '@/api/repositoryApi'

// 迭代仓库版本信息
export interface IterationRepoVersionInfo {
  repoId: string
  repoName?: string
  baseVersion?: string
  devVersion?: string
  targetVersion?: string
  featureBranch?: string
  versionSource?: 'POM' | 'GRADLE' | 'MANUAL' | 'SYSTEM' | 'REPO'
  versionSyncedAt?: string
}

// 后端返回的原始数据结构
export interface IterationRaw {
  key: string
  name: string
  description: string
  expectedReleaseAt: string | null
  repoIds: string[]
  createdAt: string
  updatedAt: string
}

// 前端使用的数据结构
export interface Iteration {
  iterationKey: string
  name: string
  description: string
  expectedReleaseAt: string | null
  repoIds: string[]
  repoCount: number
  mountedWindows: string  // 暂时使用空字符串
  attachAt: string
  createdAt: string
  updatedAt: string
}

export interface CreateIterationRequest {
  name: string
  description?: string
  expectedReleaseAt?: string | null
  repoIds?: string[]
  groupCode: string
}

export interface UpdateIterationRequest {
  name?: string
  description?: string
  expectedReleaseAt?: string | null
  repoIds?: string[]
  groupCode?: string
}

// 将后端数据转换为前端格式
function transformIteration(raw: IterationRaw): Iteration {
  return {
    iterationKey: raw.key,
    name: raw.name || '',
    description: raw.description || '',
    expectedReleaseAt: raw.expectedReleaseAt,
    repoIds: raw.repoIds || [],
    repoCount: raw.repoIds?.length || 0,
    mountedWindows: '',  // 后端暂未提供此字段
    attachAt: '',  // 后端暂未提供此字段
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt
  }
}

export const iterationApi = {
  async list(query: PageQuery & { keyword?: string }): Promise<PageResult<Iteration>> {
    const params = {
      page: query.page,
      size: query.pageSize,
      keyword: query.keyword
    }
    const res = await http.get<ApiPageResponse<IterationRaw[]>>('/v1/iterations/paged', { params })
    const list = (res.data.data || []).map(transformIteration)
    return {
      list,
      total: res.data.page.total
    }
  },

  async get(key: string): Promise<Iteration> {
    const resp = await apiGet<IterationRaw>(`/v1/iterations/${encodeURIComponent(key)}`)
    return transformIteration(resp)
  },

  async create(payload: CreateIterationRequest): Promise<Iteration> {
    const resp = await apiPost<IterationRaw>('/v1/iterations', payload)
    return transformIteration(resp)
  },

  async update(key: string, payload: UpdateIterationRequest): Promise<Iteration> {
    const resp = await apiPut<IterationRaw>(`/v1/iterations/${encodeURIComponent(key)}`, payload)
    return transformIteration(resp)
  },

  async delete(key: string): Promise<void> {
    await apiDel(`/v1/iterations/${encodeURIComponent(key)}`)
  },

  async addRepos(key: string, repoIds: string[]): Promise<Iteration> {
    const resp = await apiPost<IterationRaw>(`/v1/iterations/${encodeURIComponent(key)}/repos/add`, { repoIds })
    return transformIteration(resp)
  },

  async removeRepos(key: string, repoIds: string[]): Promise<Iteration> {
    const resp = await apiPost<IterationRaw>(`/v1/iterations/${encodeURIComponent(key)}/repos/remove`, { repoIds })
    return transformIteration(resp)
  },

  async listRepos(key: string): Promise<string[]> {
    const resp = await apiGet<string[]>(`/v1/iterations/${encodeURIComponent(key)}/repos`)
    return resp || []
  },

  // 获取迭代的仓库版本信息列表
  async listVersions(key: string): Promise<IterationRepoVersionInfo[]> {
    const resp = await apiGet<IterationRepoVersionInfo[]>(`/v1/iterations/${encodeURIComponent(key)}/versions`)
    return resp || []
  },

  // 获取迭代的单个仓库版本信息
  async getRepoVersion(key: string, repoId: string): Promise<IterationRepoVersionInfo | null> {
    const resp = await apiGet<IterationRepoVersionInfo>(`/v1/iterations/${encodeURIComponent(key)}/repos/${encodeURIComponent(repoId)}/version`)
    return resp || null
  },

  // 获取仓库版本详情
  async getRepoVersionInfo(key: string, repoId: string): Promise<IterationRepoVersionInfo | null> {
    const resp = await apiGet<IterationRepoVersionInfo>(`/v1/iterations/${encodeURIComponent(key)}/repos/${encodeURIComponent(repoId)}/version-info`)
    return resp || null
  },

  // 从仓库同步版本
  async syncVersionFromRepo(key: string, repoId: string): Promise<IterationRepoVersionInfo | null> {
    const resp = await apiPost<IterationRepoVersionInfo>(`/v1/iterations/${encodeURIComponent(key)}/repos/${encodeURIComponent(repoId)}/sync-version`, {})
    return resp || null
  },

  // 检测版本冲突
  async checkVersionConflict(key: string, repoId: string): Promise<VersionConflict | null> {
    const resp = await apiGet<VersionConflict>(`/v1/iterations/${encodeURIComponent(key)}/repos/${encodeURIComponent(repoId)}/check-conflict`)
    return resp || null
  },

  // 解决版本冲突
  async resolveVersionConflict(key: string, repoId: string, resolution: ConflictResolution): Promise<IterationRepoVersionInfo | null> {
    const resp = await apiPost<IterationRepoVersionInfo>(`/v1/iterations/${encodeURIComponent(key)}/repos/${encodeURIComponent(repoId)}/resolve-conflict`, { resolution })
    return resp || null
  }
}

// 版本冲突信息
export interface VersionConflict {
  repoId: string
  iterationKey: string
  systemVersion?: string
  repoVersion?: string
  conflictType?: 'MISMATCH' | 'REPO_AHEAD' | 'SYSTEM_AHEAD'
  message?: string
}

// 冲突解决策略
export type ConflictResolution = 'USE_SYSTEM' | 'USE_REPO' | 'CANCEL'
