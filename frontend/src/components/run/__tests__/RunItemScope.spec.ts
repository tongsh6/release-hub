import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import RunItemScope from '../RunItemScope.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

describe('RunItemScope', () => {
  it('shows all recorded participants once for a repository cleanup item', () => {
    const wrapper = mount(RunItemScope, { props: { runType: 'WINDOW_CLEANUP', item: {
      iterationKey: null, metadata: {
        'plan.participatingIterationKeys': '["ITER-A","ITER-B"]',
        'cleanup.referencePolicy': 'RETAIN'
      }
    } } })
    expect(wrapper.text()).toContain('run.detail.repositoryCleanup')
    expect(wrapper.text()).toContain('ITER-A, ITER-B')
    expect(wrapper.text()).toContain('run.detail.referencesRetained')
    expect(wrapper.text()).not.toContain('null')
  })

  it.each([undefined, 'invalid-json', '[17]', '[]'])('does not manufacture participants or retention from %s', value => {
    const metadata: Record<string, string> = value === undefined ? {} : { 'plan.participatingIterationKeys': value }
    const wrapper = mount(RunItemScope, { props: {
      runType: 'WINDOW_CLEANUP', item: { iterationKey: 'OLD-SENTINEL', metadata }
    } })
    expect(wrapper.text()).toContain('run.detail.notRecorded')
    expect(wrapper.text()).not.toContain('OLD-SENTINEL')
    expect(wrapper.text()).not.toContain('run.detail.referencesRetained')
  })

  it('keeps the actual iteration identity for integration', () => {
    const wrapper = mount(RunItemScope, { props: {
      runType: 'WINDOW_ORCHESTRATION', item: { iterationKey: 'ITER-A', metadata: {} }
    } })
    expect(wrapper.text()).toBe('ITER-A')
  })
})
