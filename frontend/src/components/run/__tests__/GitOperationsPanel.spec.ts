import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GitOperationsPanel from '../GitOperationsPanel.vue'
import { gitOperationApi } from '@/api/gitOperationApi'

vi.mock('vue-i18n', async importOriginal => ({ ...await importOriginal<typeof import('vue-i18n')>(), useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/utils/error', () => ({ handleError: vi.fn() }))
vi.mock('@/api/gitOperationApi', () => ({ gitOperationApi: { list: vi.fn(), listByRun: vi.fn(), get: vi.fn(), reconcile: vi.fn() } }))

const operation = { id: 'operation-1', repoId: 'repo-1', action: 'CREATE_MR', state: 'UNKNOWN', version: 4,
  operator: 'tester', blocked: true, createdAt: '2026-10-08T00:00:00Z', updatedAt: '2026-10-08T00:00:00Z', reason: 'RESPONSE_UNCONFIRMED' }
const detail = { operation, events: [{ attemptId: 'attempt-1', sequence: 1, type: 'SEND_STARTED', at: operation.createdAt, evidenceJson: '{}' }], observations: [], moreObservations: false }

describe('Git 操作证据', () => {
  beforeEach(() => {
    vi.mocked(gitOperationApi.list).mockReset().mockResolvedValue([operation])
    vi.mocked(gitOperationApi.listByRun).mockReset().mockResolvedValue([operation])
    vi.mocked(gitOperationApi.get).mockReset().mockResolvedValue(detail)
    vi.mocked(gitOperationApi.reconcile).mockReset()
  })
  it('展示未知状态与阻断，打开页面不自动核对或执行', async () => {
    const wrapper = mount(GitOperationsPanel, { props: { repoId: 'repo-1' } })
    await flushPromises()
    expect(wrapper.text()).toContain('gitOperation.states.UNKNOWN')
    expect(wrapper.text()).toContain('gitOperation.blocked')
    expect(gitOperationApi.reconcile).not.toHaveBeenCalled()
    await wrapper.get('[data-action="detail"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('gitOperation.events.SEND_STARTED')
    expect(wrapper.find('[data-action="resume"]').exists()).toBe(false)
  })
  it('核对响应丢失时重用相同幂等键和版本', async () => {
    vi.mocked(gitOperationApi.reconcile).mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({ id: 'observation', status: 'FOUND', observedAt: operation.createdAt, evidenceJson: '{}' })
    const wrapper = mount(GitOperationsPanel, { props: { repoId: 'repo-1' } })
    await flushPromises()
    await wrapper.get('[data-action="detail"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-action="reconcile"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-action="reconcile"]').trigger('click')
    await flushPromises()
    const calls = vi.mocked(gitOperationApi.reconcile).mock.calls
    expect(calls).toHaveLength(2)
    expect(calls[0]).toEqual(calls[1])
    expect(calls[0]?.[1]).toBe(4)
  })
  it('FOUND核对刷新后仍展示原UNKNOWN与阻断且没有恢复入口', async () => {
    const unknownDetail = {
      ...detail,
      events: [
        { attemptId: 'attempt-1', sequence: 0, type: 'INTENT_RECORDED', at: operation.createdAt, evidenceJson: '{}' },
        { attemptId: 'attempt-1', sequence: 1, type: 'SEND_STARTED', at: operation.createdAt, evidenceJson: '{}' },
        { attemptId: 'attempt-1', sequence: 2, type: 'UNKNOWN', at: operation.createdAt, evidenceJson: '{}' },
      ],
    }
    const observed = {
      id: 'observation-1',
      status: 'FOUND',
      observedAt: '2026-10-08T00:00:01Z',
      evidenceJson: JSON.stringify({
        project_id: 17,
        source_project_id: 17,
        target_project_id: 17,
        iid: 7,
        source_branch: 'feature',
        target_branch: 'release/retargeted',
        sha: 'a'.repeat(40),
        state: 'merged',
        web_url: 'https://gitlab.invalid/acme/repo/-/merge_requests/7',
        merge_commit_sha: null,
      }),
    }
    const observedOperation = { ...operation, action: 'MERGE_MR', version: operation.version + 1 }
    const initialOperation = { ...operation, action: 'MERGE_MR' }
    vi.mocked(gitOperationApi.list).mockResolvedValueOnce([initialOperation]).mockResolvedValueOnce([observedOperation])
    vi.mocked(gitOperationApi.get)
      .mockResolvedValueOnce({ ...unknownDetail, operation: initialOperation })
      .mockResolvedValueOnce({ ...unknownDetail, operation: observedOperation, observations: [observed] })
    vi.mocked(gitOperationApi.reconcile).mockResolvedValueOnce(observed)
    const wrapper = mount(GitOperationsPanel, { props: { repoId: 'repo-1' } })
    await flushPromises()
    await wrapper.get('[data-action="detail"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-action="reconcile"]').trigger('click')
    await flushPromises()

    expect(gitOperationApi.reconcile).toHaveBeenCalledWith(operation.id, operation.version, expect.any(String))
    expect(gitOperationApi.get).toHaveBeenCalledTimes(2)
    expect(gitOperationApi.list).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('gitOperation.observed.FOUND')
    expect(wrapper.text()).toContain('gitOperation.states.UNKNOWN')
    expect(wrapper.text()).toContain('gitOperation.blocked')
    expect(wrapper.text()).toContain('gitOperation.readOnlyNotice')
    expect(wrapper.text()).toContain('gitOperation.events.SEND_STARTED')
    expect(wrapper.text()).toContain('gitOperation.events.UNKNOWN')
    expect(wrapper.text()).toContain('"project_id": 17')
    expect(wrapper.text()).toContain('"target_branch": "release/retargeted"')
    expect(wrapper.text()).toContain('"merge_commit_sha": null')
    expect(wrapper.text()).not.toContain('gitOperation.states.APPLIED')
    expect(wrapper.find('[data-action="resume"]').exists()).toBe(false)
  })

  it('Run 页面只请求该 Run 的证据', async () => {
    mount(GitOperationsPanel, { props: { runId: 'run-1' } })
    await flushPromises()
    expect(gitOperationApi.listByRun).toHaveBeenCalledWith('run-1', 0)
    expect(gitOperationApi.list).not.toHaveBeenCalled()
  })
})
