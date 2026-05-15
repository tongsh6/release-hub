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
  repo?: string
  repoId: string
  iterationKey: string
  plannedOrder: number
  executedOrder: number
  finalResult: string
  steps: RunStep[]
}

export interface RunStep {
  actionType: string
  result: string
  startedAt?: string
  finishedAt?: string
  startAt?: string | number
  endAt?: string | number
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
  async list(query: PageQuery & { windowKey?: string; repoId?: string; iterationKey?: string; status?: string; groupCode?: string; runType?: string; operator?: string }): Promise<PageResult<Run>> {
    const params = {
      page: query.page,
      size: query.pageSize,
      windowKey: query.windowKey,
      repoId: query.repoId,
      iterationKey: query.iterationKey,
      status: query.status,
      groupCode: query.groupCode,
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
    const res = await http.get<any>(`/v1/runs/${id}/export.json`)
    return normalizeRunDetail(res.data, String(id))
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

function normalizeRunDetail(data: any, fallbackId: string): RunDetail {
  const items = (data?.items || []).map((item: any) => ({
    ...item,
    repoId: item.repoId || item.repo || '',
    repo: item.repo || item.repoId || '',
    steps: (item.steps || []).map((step: any) => ({
      ...step,
      startedAt: normalizeTime(step.startedAt ?? step.startAt),
      finishedAt: normalizeTime(step.finishedAt ?? step.endAt)
    }))
  }))

  return {
    id: data?.id || data?.runId || fallbackId,
    runType: data?.runType || '',
    status: data?.status || determineRunStatus(items, data?.finishedAt),
    startedAt: normalizeTime(data?.startedAt),
    finishedAt: normalizeTime(data?.finishedAt),
    operator: data?.operator || '',
    items
  }
}

function normalizeTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'number') return new Date(value).toISOString()
  return String(value)
}

function determineRunStatus(items: RunItem[], finishedAt: unknown): string {
  if (items.some(item => item.finalResult?.includes('FAILED') || item.finalResult === 'MERGE_BLOCKED')) {
    return 'FAILED'
  }
  if (items.length > 0 && items.every(item => item.finalResult?.includes('SUCCESS'))) {
    return 'SUCCESS'
  }
  return finishedAt ? 'COMPLETED' : 'RUNNING'
}
