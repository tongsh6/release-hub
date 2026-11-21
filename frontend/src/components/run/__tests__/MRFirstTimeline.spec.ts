import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import MRFirstTimeline from '../MRFirstTimeline.vue'
import type { RunStep } from '@/api/runApi'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
function render(steps: RunStep[]) {
  return mount(MRFirstTimeline, { props: { steps }, global: { stubs: {
    ElSteps: { props: ['active', 'processStatus'], template: '<div :data-active="active" :data-state="processStatus"><slot /></div>' },
    ElStep: { props: ['status', 'title'], template: '<div class="step" :data-state="status">{{ title }}</div>' },
    ElAlert: { props: ['title', 'type'], template: '<p role="alert" :data-type="type">{{ title }}</p>' }
  } } })
}
describe('MR-first result presentation', () => {
  it('finishes the timeline for existing, created, and already-merged evidence', () => {
    const wrapper = render([
      { actionType: 'ENSURE_FEATURE', result: 'ALREADY_EXISTS', message: '' },
      { actionType: 'ENSURE_RELEASE', result: 'CREATED', message: '' },
      { actionType: 'ENSURE_MR', result: 'MR_REUSED', message: '' },
      { actionType: 'TRY_MERGE', result: 'ALREADY_MERGED', message: '' }
    ])
    expect(wrapper.findAll('.step').map(step => step.attributes('data-state'))).toEqual(['success', 'success', 'success', 'success'])
    expect(wrapper.get('[data-active]').attributes('data-active')).toBe('4')
  })
  it('presents an unknown external result as waiting for review, never active processing', () => {
    const wrapper = render([
      { actionType: 'ENSURE_FEATURE', result: 'ALREADY_EXISTS', message: '' },
      { actionType: 'ENSURE_RELEASE', result: 'ALREADY_EXISTS', message: '' },
      { actionType: 'ENSURE_MR', result: 'MR_REUSED', message: '' },
      { actionType: 'TRY_MERGE', result: 'UNKNOWN', message: 'external result requires review' }
    ])
    expect(wrapper.findAll('.step')[3]!.attributes('data-state')).toBe('wait')
    expect(wrapper.get('[data-active]').attributes('data-state')).toBe('wait')
    expect(wrapper.get('[role="alert"]').attributes('data-type')).toBe('warning')
    expect(wrapper.get('[role="alert"]').text()).toBe('external result requires review')
  })
  it('shows merge blocking as an error and preserves its reason', () => {
    const wrapper = render([{ actionType: 'TRY_MERGE', result: 'MERGE_BLOCKED', message: 'approval required' }])
    expect(wrapper.findAll('.step')[3]!.attributes('data-state')).toBe('error')
    expect(wrapper.get('[role="alert"]').text()).toBe('approval required')
  })
})
