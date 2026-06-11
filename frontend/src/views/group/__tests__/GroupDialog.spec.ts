import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GroupDialog from '../GroupDialog.vue'
import { groupApi } from '@/api/modules/group'
import { ApiError } from '@/api/http'
import { ElMessage } from 'element-plus'
import { handleError } from '@/utils/error'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn()
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
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const EntityDialogStub = defineComponent({
  name: 'EntityDialog',
  emits: ['confirm', 'opened'],
  setup(_, { emit, expose, slots }) {
    expose({
      open: () => emit('opened'),
      close: vi.fn()
    })
    return () => h('section', [
      slots.default?.(),
      h('button', { type: 'button', onClick: () => emit('confirm') }, 'confirm')
    ])
  }
})

const ElFormStub = defineComponent({
  name: 'ElForm',
  setup(_, { expose, slots }) {
    expose({
      validate: vi.fn().mockResolvedValue(true),
      clearValidate: vi.fn()
    })
    return () => h('form', slots.default?.())
  }
})

const GroupTreeSelectStub = defineComponent({
  name: 'GroupTreeSelect',
  props: {
    modelValue: String,
    placeholder: String,
    disabled: Boolean,
    leafOnly: Boolean,
    disabledCodes: Array
  },
  template: '<div class="group-tree-select-stub" />'
})

const stubs = {
  EntityDialog: EntityDialogStub,
  GroupTreeSelect: GroupTreeSelectStub,
  ElForm: ElFormStub,
  ElFormItem: {
    template: '<label><slot /></label>'
  },
  ElInput: true,
  ElTag: {
    template: '<span><slot /></span>'
  }
}

describe('GroupDialog', () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    vi.mocked(groupApi.get).mockReset()
    vi.mocked(groupApi.create).mockReset()
    vi.mocked(groupApi.update).mockReset()
    vi.mocked(ElMessage.warning).mockReset()
    vi.mocked(ElMessage.success).mockReset()
    vi.mocked(handleError).mockReset()
  })

  afterEach(() => {
    consoleErrorSpy.mockRestore()
  })

  it('shows a movement governance message when backend rejects group move', async () => {
    vi.mocked(groupApi.get).mockResolvedValue({
      id: 'G001',
      code: 'G001',
      name: 'Group',
      parentCode: undefined
    })
    vi.mocked(groupApi.update).mockRejectedValue(
      new ApiError({ code: 'GROUP_016', message: 'referenced' })
    )
    const wrapper = mount(GroupDialog, { global: { stubs } })

    ;(wrapper.vm as any).openEdit('G001')
    await flushPromises()
    await expect((wrapper.vm as any).submitWithValidation()).rejects.toMatchObject({ code: 'GROUP_016' })
    await flushPromises()

    expect(groupApi.update).toHaveBeenCalledWith('G001', {
      name: 'Group',
      parentCode: undefined
    })
    expect(ElMessage.warning).toHaveBeenCalledWith('group.moveBlockedByReference')
    expect(handleError).not.toHaveBeenCalled()
  })

  it('uses the shared group tree selector for parent movement and disables self', async () => {
    vi.mocked(groupApi.get).mockResolvedValue({
      id: 'G001',
      code: 'G001',
      name: 'Group',
      parentCode: 'ROOT'
    })
    const wrapper = mount(GroupDialog, { global: { stubs } })

    ;(wrapper.vm as any).openEdit('G001')
    await flushPromises()

    const selector = wrapper.findComponent(GroupTreeSelectStub)
    expect(selector.props('modelValue')).toBe('ROOT')
    expect(selector.props('placeholder')).toBe('group.parentPlaceholder')
    expect(selector.props('leafOnly')).toBe(false)
    expect(selector.props('disabledCodes')).toEqual(['G001'])
    expect(wrapper.text()).toContain('group.parentMoveTip')
  })
})
