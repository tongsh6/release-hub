import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GroupList from '../GroupList.vue'
import { groupApi } from '@/api/modules/group'
import { ApiError } from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { handleError } from '@/utils/error'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn()
  },
  ElMessageBox: {
    confirm: vi.fn()
  }
}))

vi.mock('@/api/http', () => ({
  ApiError: class ApiError extends Error {
    code: string

    constructor(args: { code: string; message: string }) {
      super(args.message)
      this.code = args.code
    }
  }
}))

vi.mock('@/api/modules/group', () => ({
  groupApi: {
    listTree: vi.fn(),
    remove: vi.fn()
  }
}))

vi.mock('@/composables/useGroupTree', async () => {
  const { ref } = await import('vue')
  return {
    useGroupTree: () => ({
      loading: ref(false),
      treeData: ref([]),
      selected: ref(null),
      selectedCode: ref(''),
      loadTree: vi.fn(),
      onNodeClick: vi.fn()
    })
  }
})

vi.mock('@/utils/perm', () => ({
  hasPerm: vi.fn(() => true)
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  SearchForm: {
    template: '<form><slot /><slot name="extra-actions" /></form>'
  },
  GroupDialog: true,
  ElFormItem: {
    props: ['label'],
    template: '<label>{{ label }}<slot /></label>'
  },
  ElInput: true,
  ElContainer: {
    template: '<section><slot /></section>'
  },
  ElAside: {
    template: '<aside><slot /></aside>'
  },
  ElMain: {
    template: '<main><slot /></main>'
  },
  ElTree: true,
  ElEmpty: true,
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>'
  },
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElDropdown: true,
  ElDropdownMenu: true,
  ElDropdownItem: true,
  ElIcon: true,
  ArrowDown: true
}

describe('GroupList', () => {
  beforeEach(() => {
    vi.mocked(groupApi.listTree).mockReset()
    vi.mocked(groupApi.remove).mockReset()
    vi.mocked(ElMessage.warning).mockReset()
    vi.mocked(ElMessageBox.confirm).mockReset()
    vi.mocked(handleError).mockReset()
    vi.mocked(groupApi.listTree).mockResolvedValue([])
  })

  it('shows a clear delete protection message when backend rejects a referenced group', async () => {
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as any)
    vi.mocked(groupApi.remove).mockRejectedValue(
      new ApiError({ code: 'GROUP_013', message: 'referenced' })
    )
    const wrapper = mount(GroupList, {
      global: {
        stubs,
        directives: {
          perm: {},
          loading: {}
        }
      }
    })
    await flushPromises()

    await (wrapper.vm as any).handleDelete({ id: 'group-1', code: 'G001', name: 'Group' })
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('group.deleteReferenced')
    expect(handleError).not.toHaveBeenCalled()
  })
})
