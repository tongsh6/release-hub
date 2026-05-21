import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReleaseWindowList from '../ReleaseWindowList.vue'
import { releaseWindowApi } from '@/api/modules/releaseWindow'
import { groupApi } from '@/api/modules/group'
import { ApiError } from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { handleError } from '@/utils/error'

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      locale: { value: 'zh-CN' },
      t: (key: string) => key
    }
  }),
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn()
  })
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

vi.mock('@/api/modules/releaseWindow', () => ({
  releaseWindowApi: {
    list: vi.fn(),
    freeze: vi.fn(),
    unfreeze: vi.fn(),
    publish: vi.fn(),
    close: vi.fn(),
    delete: vi.fn()
  }
}))

vi.mock('@/api/modules/group', () => ({
  groupApi: {
    listTree: vi.fn()
  }
}))

vi.mock('@/utils/perm', () => ({
  hasPerm: vi.fn(() => true)
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  SearchForm: {
    emits: ['search', 'reset'],
    template: `
      <form>
        <slot />
        <button class="search" type="button" @click="$emit('search')">search</button>
        <button class="reset" type="button" @click="$emit('reset')">reset</button>
      </form>
    `
  },
  DataTable: {
    props: ['data', 'total', 'loading'],
    template: '<section><slot name="actions" /><slot /></section>'
  },
  GroupTreeSelect: {
    props: ['modelValue', 'leafOnly', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<button class="group-filter" type="button" @click="$emit(\'update:modelValue\', \'G001\')">group</button>'
  },
  ReleaseWindowDialog: true,
  AttachIterationsDialog: true,
  ElFormItem: {
    props: ['label'],
    template: '<label>{{ label }}<slot /></label>'
  },
  ElInput: true,
  ElSelect: true,
  ElOption: true,
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElTableColumn: true,
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElTooltip: {
    template: '<span><slot /></span>'
  }
}

describe('ReleaseWindowList', () => {
  beforeEach(() => {
    vi.mocked(releaseWindowApi.list).mockReset()
    vi.mocked(releaseWindowApi.delete).mockReset()
    vi.mocked(groupApi.listTree).mockReset()
    vi.mocked(releaseWindowApi.list).mockResolvedValue({ list: [], total: 0 })
    vi.mocked(ElMessage.success).mockReset()
    vi.mocked(ElMessage.warning).mockReset()
    vi.mocked(ElMessageBox.confirm).mockReset()
    vi.mocked(handleError).mockReset()
    vi.mocked(groupApi.listTree).mockResolvedValue([
      {
        code: 'G001',
        name: 'Customer A',
        children: [
          {
            code: 'G001001',
            name: 'Line X',
            children: [
              { code: 'G001001001', name: 'Project Y', children: [] }
            ]
          }
        ]
      }
    ])
  })

  it('submits selected groupCode when filtering release windows', async () => {
    const wrapper = mount(ReleaseWindowList, { global: { stubs, directives: { perm: {} } } })
    await flushPromises()

    await wrapper.find('.group-filter').trigger('click')
    await wrapper.find('.search').trigger('click')
    await flushPromises()

    expect(releaseWindowApi.list).toHaveBeenLastCalledWith({
      name: '',
      status: '',
      groupCode: 'G001',
      page: 1,
      pageSize: 10
    })
  })

  it('resolves group path from loaded group tree', async () => {
    const wrapper = mount(ReleaseWindowList, { global: { stubs, directives: { perm: {} } } })
    await flushPromises()

    expect((wrapper.vm as any).resolveWindowGroupPath({ groupCode: 'G001001001' })).toBe(
      'Customer A / Line X / Project Y'
    )
    expect((wrapper.vm as any).resolveWindowGroupPath({ groupCode: 'UNKNOWN' })).toBe('UNKNOWN')
  })

  it('hides release plan mutation actions while a draft window is frozen', async () => {
    const wrapper = mount(ReleaseWindowList, { global: { stubs, directives: { perm: {} } } })
    await flushPromises()
    const vm = wrapper.vm as any
    const frozenDraft = { status: 'DRAFT', frozen: true }
    const editableDraft = { status: 'DRAFT', frozen: false }

    expect(vm.canAttachIterations(frozenDraft)).toBe(false)
    expect(vm.canFreeze(frozenDraft)).toBe(false)
    expect(vm.canUnfreeze(frozenDraft)).toBe(true)
    expect(vm.canAttachIterations(editableDraft)).toBe(true)
    expect(vm.canFreeze(editableDraft)).toBe(true)
    expect(vm.canUnfreeze(editableDraft)).toBe(false)
  })

  it('shows delete protection copy when backend rejects a non-empty draft window', async () => {
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as any)
    vi.mocked(releaseWindowApi.delete).mockRejectedValue(
      new ApiError({ code: 'RW_014', message: 'blocked' })
    )
    const wrapper = mount(ReleaseWindowList, { global: { stubs, directives: { perm: {} } } })
    await flushPromises()

    await (wrapper.vm as any).handleDelete({ id: 'window-1', windowKey: 'RW-1', status: 'DRAFT' })
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('releaseWindow.deleteBlocked')
    expect(handleError).not.toHaveBeenCalled()
  })
})
