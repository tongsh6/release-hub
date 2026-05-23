import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GroupTreeSelect from '../GroupTreeSelect.vue'
import { groupApi } from '@/api/modules/group'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('@/api/modules/group', () => ({
  groupApi: {
    listTree: vi.fn()
  }
}))

const ElTreeSelectStub = defineComponent({
  name: 'ElTreeSelect',
  props: ['data', 'props'],
  setup(props) {
    return () => h('section', { class: 'tree-select-stub' }, JSON.stringify(props.data))
  }
})

describe('GroupTreeSelect', () => {
  beforeEach(() => {
    vi.mocked(groupApi.listTree).mockReset()
    vi.mocked(groupApi.listTree).mockResolvedValue([
      {
        id: 'ROOT',
        code: 'ROOT',
        name: 'Root',
        children: [{ id: 'LEAF', code: 'LEAF', name: 'Leaf', children: [] }]
      }
    ])
  })

  it('allows non-leaf parents when leafOnly is disabled but still blocks disabled codes', async () => {
    const wrapper = mount(GroupTreeSelect, {
      props: {
        leafOnly: false,
        disabledCodes: ['LEAF']
      },
      global: {
        stubs: {
          ElTreeSelect: ElTreeSelectStub,
          ElTag: true
        }
      }
    })
    await flushPromises()

    const treeProps = wrapper.findComponent(ElTreeSelectStub).props('props') as any

    expect(treeProps.disabled({ code: 'ROOT', isLeaf: false })).toBe(false)
    expect(treeProps.disabled({ code: 'LEAF', isLeaf: true })).toBe(true)
  })
})
