import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DataQualityReviewQueue from '../DataQualityReviewQueue.vue'
import { dataQualityApi } from '@/api/dataQualityApi'
import { ElMessage } from 'element-plus'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key)
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn()
  }
}))

vi.mock('@/api/dataQualityApi', () => ({
  dataQualityApi: {
    reviewCleanupActions: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>'
  },
  ElForm: {
    template: '<form><slot /></form>'
  },
  ElFormItem: {
    template: '<label><slot /></label>'
  },
  ElInput: true,
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElSelect: true,
  ElOption: true,
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElAlert: true,
  ElStatistic: true,
  ElTable: true,
  ElTableColumn: true
}

describe('DataQualityReviewQueue', () => {
  beforeEach(() => {
    vi.mocked(dataQualityApi.reviewCleanupActions).mockReset()
    vi.mocked(ElMessage.success).mockReset()
  })

  it('loads dry-run JSONL actions and submits filtered manual decisions', async () => {
    vi.mocked(dataQualityApi.reviewCleanupActions).mockResolvedValue({
      reviewer: 'release-manager',
      sourceReport: 'report/actions.jsonl',
      total: 1,
      accepted: 1,
      pending: 0,
      rejected: 0,
      actions: [
        {
          resourceType: 'release_window',
          resourceId: 'window-1',
          riskType: 'DRAFT_WINDOW_REMAINS',
          reviewStatus: 'ACCEPTED',
          reason: 'ok',
          applicationEntry: '/release-windows/{resourceId}',
          preExecutionCheck: 'check',
          postExecutionVerification: 'verify',
          executionPermitted: false
        }
      ]
    })

    const wrapper = shallowMount(DataQualityReviewQueue, { global: { stubs } })
    const vm = wrapper.vm as any
    vm.rawJsonl = [
      JSON.stringify({
        resourceType: 'release_window',
        resourceId: 'window-1',
        riskType: 'DRAFT_WINDOW_REMAINS',
        applicationEntry: '/release-windows/{resourceId}',
        preExecutionCheck: 'check',
        postExecutionVerification: 'verify'
      }),
      JSON.stringify({
        resourceType: 'window_iteration',
        resourceId: 'window-1::repo-1::ITER-1',
        riskType: 'ATTACH_BRANCH_NOT_CREATED',
        applicationEntry: '/release-windows/{windowId}',
        preExecutionCheck: 'check',
        postExecutionVerification: 'verify'
      })
    ].join('\n')

    vm.loadQueue()
    vm.markAllApproved()
    vm.filters.resourceType = 'release_window'
    vm.filters.reviewStatus = 'ACCEPTED'
    await vm.reviewQueue()

    expect(vm.queueActions).toHaveLength(2)
    expect(dataQualityApi.reviewCleanupActions).toHaveBeenCalledWith({
      reviewer: 'release-manager',
      sourceReport: '.ai/reports/sa002-safe-cleanup/20260523-aligned-baseline/actions.jsonl',
      resourceTypeFilter: 'release_window',
      riskTypeFilter: undefined,
      reviewStatusFilter: 'ACCEPTED',
      actions: [
        expect.objectContaining({
          resourceType: 'release_window',
          reviewDecision: 'APPROVE_FOR_APPLICATION_ENTRY'
        }),
        expect.objectContaining({
          resourceType: 'window_iteration',
          reviewDecision: 'APPROVE_FOR_APPLICATION_ENTRY'
        })
      ]
    })
    expect(vm.reviewResult.total).toBe(1)
    expect(ElMessage.success).toHaveBeenCalledWith('dataQuality.review.reviewComplete:{"count":1}')
  })

  it('rejects invalid JSONL rows before calling the API', () => {
    const wrapper = shallowMount(DataQualityReviewQueue, { global: { stubs } })
    const vm = wrapper.vm as any

    vm.rawJsonl = JSON.stringify({ resourceType: 'release_window' })
    vm.loadQueue()

    expect(vm.queueActions).toHaveLength(0)
    expect(dataQualityApi.reviewCleanupActions).not.toHaveBeenCalled()
  })
})
