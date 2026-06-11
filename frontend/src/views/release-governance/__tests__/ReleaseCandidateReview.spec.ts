import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReleaseCandidateReview from '../ReleaseCandidateReview.vue'
import { releaseGovernanceApi } from '@/api/releaseGovernanceApi'
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

vi.mock('@/api/releaseGovernanceApi', () => ({
  releaseGovernanceApi: {
    getCandidateReview: vi.fn(),
    signoff: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  ElAlert: true,
  ElRow: {
    template: '<div><slot /></div>'
  },
  ElCol: {
    template: '<div><slot /></div>'
  },
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>'
  },
  ElTable: true,
  ElTableColumn: true,
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElForm: {
    template: '<form><slot /></form>'
  },
  ElFormItem: {
    template: '<label><slot /></label>'
  },
  ElInput: true,
  ElRadioGroup: {
    template: '<div><slot /></div>'
  },
  ElRadioButton: {
    template: '<span><slot /></span>'
  },
  ElSelect: true,
  ElOption: true
}

const summary = {
  candidateId: 'release-candidate-2026-05-23',
  candidateLabel: 'ReleaseHub RC 2026-05-23',
  conclusion: 'Controlled review is allowed',
  recommendation: 'Confirm data quality risks',
  evidence: [
    { label: 'Full scenario acceptance', status: 'PASS', sourcePath: 'tasks/records/full.md', result: '170 PASS' }
  ],
  risks: [
    { level: 'NON_BLOCKING', title: 'Data quality residuals', status: 'Manual review required', sourcePath: 'summary.md' }
  ],
  checklist: [
    {
      key: 'acceptance-baseline',
      title: 'Confirm full scenario acceptance',
      expectedStatus: 'CONFIRMED',
      sourcePath: 'tasks/records/full.md',
      required: true
    },
    {
      key: 'data-quality-boundary',
      title: 'Confirm data quality boundary',
      expectedStatus: 'CONFIRMED',
      sourcePath: 'tasks/records/sa002.md',
      required: true
    }
  ],
  latestSignoff: null
} as const

describe('ReleaseCandidateReview', () => {
  beforeEach(() => {
    vi.mocked(releaseGovernanceApi.getCandidateReview).mockReset()
    vi.mocked(releaseGovernanceApi.signoff).mockReset()
    vi.mocked(ElMessage.success).mockReset()
    vi.mocked(releaseGovernanceApi.getCandidateReview).mockResolvedValue(summary as any)
  })

  it('loads release candidate evidence and checklist from the API', async () => {
    const wrapper = shallowMount(ReleaseCandidateReview, { global: { stubs, directives: { loading: {} } } })
    await flushPromises()

    expect(releaseGovernanceApi.getCandidateReview).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).summary.candidateId).toBe('release-candidate-2026-05-23')
    expect((wrapper.vm as any).checklistRows).toHaveLength(2)
  })

  it('submits signoff without triggering release actions', async () => {
    vi.mocked(releaseGovernanceApi.signoff).mockResolvedValue({
      id: 'signoff-1',
      candidateId: 'release-candidate-2026-05-23',
      candidateLabel: 'ReleaseHub RC 2026-05-23',
      reviewer: 'release-manager',
      decision: 'APPROVE_FOR_CONTROLLED_REVIEW',
      note: 'Proceed to staging',
      checklist: [{ key: 'acceptance-baseline', status: 'CONFIRMED', note: '' }],
      createdAt: '2026-05-23T12:00:00Z'
    })

    const wrapper = shallowMount(ReleaseCandidateReview, { global: { stubs, directives: { loading: {} } } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.note = 'Proceed to staging'
    vm.markAllConfirmed()
    await vm.submitSignoff()

    expect(releaseGovernanceApi.signoff).toHaveBeenCalledWith({
      candidateId: 'release-candidate-2026-05-23',
      reviewer: 'release-manager',
      decision: 'APPROVE_FOR_CONTROLLED_REVIEW',
      note: 'Proceed to staging',
      checklist: [
        { key: 'acceptance-baseline', status: 'CONFIRMED', note: '' },
        { key: 'data-quality-boundary', status: 'CONFIRMED', note: '' }
      ]
    })
    expect(vm.summary.latestSignoff.id).toBe('signoff-1')
    expect(ElMessage.success).toHaveBeenCalledWith('releaseGovernance.review.signoffSaved')
  })
})
