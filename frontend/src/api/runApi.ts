import { http, apiPost, apiGet } from '@/api/http'
import type { PageResult, PageQuery, Id } from '@/types/crud'
import type { ApiPageResponse } from '@/api/repositoryApi'

export interface Run {
  id: string
  runType: string
  status: string
  startedAt: string
  finishedAt: string
  operator: string
}

export interface RunDetail extends Run {
  items: RunItem[]
}

export interface RunItem {
  windowKey: string
  repo: string
  iterationKey: string
  plannedOrder: number
  executedOrder: number
  finalResult: string
  steps: RunStep[]
}

export interface RunStep {
  actionType: string
  result: string
  startedAt: string
  finishedAt: string
  message: string
}

// 运行任务类型
export interface RunTask {
  id: string
  runId: string
  taskType: string
  taskOrder: number
  targetType?: string
  targetId?: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'
  retryCount: number
  maxRetries: number
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createdAt: string
}

export const runApi = {
  async list(query: PageQuery & { windowKey?: string; repoId?: string; iterationKey?: string; status?: string; runType?: string; operator?: string }): Promise<PageResult<Run>> {
    const params = {
      page: query.page,
      size: query.pageSize,
      windowKey: query.windowKey,
      repoId: query.repoId,
      iterationKey: query.iterationKey,
      status: query.status,
      runType: query.runType,
      operator: query.operator
    }
    const res = await http.get<ApiPageResponse<Run[]>>('/v1/runs/paged', { params })
    return {
      list: res.data.data,
      total: res.data.page.total
    }
  },

  async getRunById(id: Id): Promise<RunDetail> {
    const res = await http.get<RunDetail>(`/v1/runs/${id}/export.json`)
    return res.data
  },

  async retry(id: Id, items: string[], operator: string): Promise<string> {
    const res = await http.post<string>(`/v1/runs/${id}/retry`, { items, operator })
    return res.data
  },

  // 获取运行任务列表
  async getTasks(runId: string): Promise<RunTask[]> {
    return apiGet<RunTask[]>(`/v1/runs/${runId}/tasks`)
  },

  // 重试失败的任务
  async retryTask(runId: string, taskId: string): Promise<RunTask> {
    return apiPost<RunTask>(`/v1/runs/${runId}/tasks/${taskId}/retry`, {})
  }
}
