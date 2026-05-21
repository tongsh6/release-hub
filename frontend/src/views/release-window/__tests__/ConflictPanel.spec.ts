/* eslint-disable vue/one-component-per-file */
import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, inject, provide } from 'vue'
import ConflictPanel from '../ConflictPanel.vue'
import { getConflicts } from '@/api/modules/releaseWindow'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('@/api/modules/releaseWindow', () => ({
  checkConflicts: vi.fn(),
  getConflicts: vi.fn()
}))

const stubs = {
  ElAlert: {
    props: ['title'],
    template: '<div>{{ title }}</div>'
  },
  ElButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  ElRadioGroup: {
    template: '<div><slot /></div>'
  },
  ElRadioButton: {
    props: ['value'],
    template: '<span><slot /></span>'
  },
  ElTable: defineComponent({
    props: {
      data: {
        type: Array,
        default: () => []
      }
    },
    setup(props, { slots }) {
      provide('tableRows', props.data)
      return () => h('table', slots.default?.())
    }
  }),
  ElTableColumn: defineComponent({
    setup(_props, { slots }) {
      const rows = inject<any[]>('tableRows', [])
      return () => h('tbody', rows.map(row => h('tr', slots.default?.({ row }))))
    }
  }),
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElTooltip: {
    template: '<span><slot /></span>'
  }
}

describe('ConflictPanel', () => {
  beforeEach(() => {
    vi.mocked(getConflicts).mockReset()
  })

  it('shows Git access risk types as filterable blocking conflicts', async () => {
    vi.mocked(getConflicts).mockResolvedValue({
      windowId: 'window-1',
      checkedAt: '2026-05-19T00:00:00Z',
      hasConflicts: true,
      totalCount: 2,
      conflicts: [
        {
          repoId: 'repo-1',
          repoName: 'repo-1',
          iterationKey: 'ITER-1',
          conflictType: 'GIT_PERMISSION_DENIED',
          sourceBranch: 'feature/ITER-1',
          targetBranch: 'release/RW-1',
          message: 'Git platform access was denied',
          suggestion: 'Check repository token permissions and Git platform access, then rescan'
        },
        {
          repoId: 'repo-2',
          repoName: 'repo-2',
          iterationKey: 'ITER-1',
          conflictType: 'GIT_UNAVAILABLE',
          sourceBranch: 'feature/ITER-1',
          message: 'Git platform is unavailable',
          suggestion: 'Restore Git platform connectivity and retry the scan'
        }
      ]
    })

    const wrapper = shallowMount(ConflictPanel, {
      props: { windowId: 'window-1' },
      global: {
        directives: { loading: {} },
        stubs
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('conflict.types.GIT_PERMISSION_DENIED')
    expect(wrapper.text()).toContain('conflict.types.GIT_UNAVAILABLE')
  })

  it('emits USE_REPO for repo-ahead conflicts', async () => {
    vi.mocked(getConflicts).mockResolvedValue({
      windowId: 'window-1',
      checkedAt: '2026-05-21T00:00:00Z',
      hasConflicts: true,
      totalCount: 1,
      conflicts: [
        {
          repoId: 'repo-1',
          repoName: 'repo-1',
          iterationKey: 'ITER-1',
          conflictType: 'REPO_AHEAD',
          systemVersion: '1.3.0-SNAPSHOT',
          repoVersion: '1.4.0-SNAPSHOT',
          message: 'repo version is newer'
        }
      ]
    })

    const wrapper = shallowMount(ConflictPanel, {
      props: { windowId: 'window-1' },
      global: {
        directives: { loading: {} },
        stubs
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('conflict.acceptRepoVersion')
    const button = wrapper.findAll('button')
      .find(item => item.text().includes('conflict.acceptRepoVersion'))
    expect(button).toBeTruthy()
    await button!.trigger('click')

    expect(wrapper.emitted('resolve')?.[0]?.[1]).toBe('USE_REPO')
  })
})
