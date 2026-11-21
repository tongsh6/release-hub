import { http, apiGet, apiPost } from '@/api/http'
import type { PageResult, PageQuery, Id } from '@/types/crud'
import type { RunStatus, RunType, RunItemResult } from '@/types/dto'
import type { ApiPageResponse } from '@/api/repositoryApi'

export interface Run {
  id: string
  executionVersion: number
  runType: RunType
  status: RunStatus
  startedAt: string
  finishedAt: string | null
  operator: string
}

export interface RunDetail extends Run {
  items: RunItem[]
}

export interface RunItem {
  id: string
  windowKey: string
  repoId: string
  iterationKey: string | null
  plannedOrder: number
  executedOrder: number
  finalResult: RunItemResult | null
  metadata: Record<string, string>
  steps: RunStep[]
}

export interface RunStep {
  actionType: string
  result: RunItemResult
  startedAt?: string
  finishedAt?: string
  message: string
}

export interface RunTask {
  taskId: string
  runId: string
  itemId: string
  actionType: 'ENSURE_FEATURE' | 'ENSURE_RELEASE' | 'ENSURE_MR' | 'TRY_MERGE'
  taskOrder: number
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'UNKNOWN' | 'SKIPPED' | 'INHERITED'
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
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
    const res = await http.get<RunExport>(`/v1/runs/${id}/export.json`)
    return normalizeRunDetail(res.data)
  },

  async retry(id: Id, items: string[], key: string): Promise<string> {
    return apiPost<string>(`/v1/runs/${id}/retry`, { items }, { headers: { 'Idempotency-Key': key } })
  },

  // 获取运行任务列表
  async getTasks(runId: string): Promise<RunTask[]> {
    return apiGet<RunTask[]>(`/v1/runs/${runId}/tasks`)
  }
}

interface RunExport extends Omit<RunDetail, 'id' | 'startedAt' | 'finishedAt' | 'items'> {
  runId: string
  startedAt: number
  finishedAt: number | null
  items: Array<Omit<RunItem, 'repoId' | 'steps'> & {
    repo: string
    steps: Array<Omit<RunStep, 'startedAt' | 'finishedAt'> & { startAt: number; endAt: number }>
  }>
}

function normalizeRunDetail(data: RunExport): RunDetail {
  return {
    id: data.runId,
    runType: data.runType,
    executionVersion: data.executionVersion,
    status: data.status,
    startedAt: formatExportTime(data.startedAt),
    finishedAt: data.finishedAt === null ? null : formatExportTime(data.finishedAt),
    operator: data.operator,
    items: data.items.map(item => ({
      id: item.id,
      windowKey: item.windowKey,
      repoId: item.repo,
      iterationKey: item.iterationKey,
      plannedOrder: item.plannedOrder,
      executedOrder: item.executedOrder,
      finalResult: item.finalResult,
      metadata: item.metadata,
      steps: item.steps.map(step => ({
        actionType: step.actionType,
        result: step.result,
        startedAt: formatExportTime(step.startAt),
        finishedAt: formatExportTime(step.endAt),
        message: step.message
      }))
    }))
  }
}

function formatExportTime(value: number): string {
  return new Date(value).toISOString()
}
