import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import GitOperationsPanel from '../GitOperationsPanel.vue'
import { gitOperationApi } from '@/api/gitOperationApi'
import zhCN from '@/i18n/messages/zh-CN'
import enUS from '@/i18n/messages/en-US'

vi.mock('@/utils/error', () => ({ handleError: vi.fn() }))
vi.mock('@/api/gitOperationApi', () => ({
  gitOperationApi: { list: vi.fn(), listByRun: vi.fn(), get: vi.fn(), reconcile: vi.fn() },
}))

describe('未发送操作的本地结果证据', () => {
  beforeEach(() => {
    vi.mocked(gitOperationApi.list).mockReset()
    vi.mocked(gitOperationApi.listByRun).mockReset()
    vi.mocked(gitOperationApi.get).mockReset()
    vi.mocked(gitOperationApi.reconcile).mockReset()
  })

  it.each([
    { locale: 'zh-CN', scope: 'run', result: 'UNKNOWN', blocked: false,
      stateLabel: '已结束（未发送）', eventLabel: '未发送操作已结束' },
    { locale: 'en-US', scope: 'repo', result: 'MERGE_BLOCKED', blocked: true,
      stateLabel: 'Ended without sending', eventLabel: 'Unsent operation ended' },
    { locale: 'zh-CN', scope: 'repo', result: 'ALREADY_MERGED', blocked: false,
      stateLabel: '已结束（未发送）', eventLabel: '未发送操作已结束' },
  ])('$locale/$scope 保留 $result 与当前仓库保护事实', async ({
    locale, scope, result, blocked, stateLabel, eventLabel,
  }) => {
    const operation = {
      id: 'original-operation', repoId: 'repo-1', runId: 'original-run', action: 'MERGE_MR',
      state: 'NOT_SENT', version: 7, operator: 'tester', blocked,
      createdAt: '2026-11-22T00:00:00Z', updatedAt: '2026-11-22T00:01:00Z',
    }
    const metadata = result === 'ALREADY_MERGED'
      ? { 'git.mrDisposition': 'ALREADY_MERGED', 'git.mergeSha': null, 'git.blockReason': null }
      : { 'git.blockReason': result === 'UNKNOWN' ? 'UNKNOWN' : 'APPROVAL_REQUIRED' }
    vi.mocked(gitOperationApi.list).mockResolvedValue([operation])
    vi.mocked(gitOperationApi.listByRun).mockResolvedValue([operation])
    vi.mocked(gitOperationApi.get).mockResolvedValue({
      operation,
      events: [
        { attemptId: 'original-attempt', sequence: 0, type: 'INTENT_RECORDED',
          at: operation.createdAt, evidenceJson: '{}' },
        { attemptId: 'original-attempt', sequence: 1, type: 'NOT_SENT',
          at: operation.updatedAt,
          evidenceJson: JSON.stringify({ taskId: 'original-task', claimToken: 'current-owner', result, metadata }) },
      ],
      observations: [], moreObservations: false,
    })
    const i18n = createI18n({ legacy: false, locale, messages: { 'zh-CN': zhCN, 'en-US': enUS } })
    const wrapper = mount(GitOperationsPanel, {
      props: scope === 'run' ? { runId: 'original-run' } : { repoId: 'repo-1' },
      global: { plugins: [i18n] },
    })
    try {
      await flushPromises()
      expect(wrapper.find('[role="alert"]').exists()).toBe(blocked)
      if (scope === 'run') {
        expect(gitOperationApi.listByRun).toHaveBeenCalledWith('original-run', 0)
        expect(gitOperationApi.list).not.toHaveBeenCalled()
      } else {
        expect(gitOperationApi.list).toHaveBeenCalledWith('repo-1', 0)
        expect(gitOperationApi.listByRun).not.toHaveBeenCalled()
      }
      await wrapper.get('[data-action="detail"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('pre').text()).toContain(result)
      expect(wrapper.get('pre').text()).toContain('current-owner')
      expect(wrapper.get('pre').text()).not.toContain('httpStatus')
      expect(wrapper.find('[data-action="resume"]').exists()).toBe(false)
      expect(gitOperationApi.reconcile).not.toHaveBeenCalled()
      expect(wrapper.get('tbody').text()).toContain(stateLabel)
      expect(wrapper.get('article ol').text()).toContain(eventLabel)
      expect(wrapper.text()).not.toContain('gitOperation.states.NOT_SENT')
      expect(wrapper.text()).not.toContain('gitOperation.events.NOT_SENT')
    } finally {
      wrapper.unmount()
    }
  })
})
