import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IterationList from '../IterationList.vue'
import { iterationApi } from '@/api/iterationApi'
import { ApiError } from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { handleError } from '@/utils/error'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, string>) => (
      params?.key ? `${key}:${params.key}` : key
    )
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

vi.mock('@/api/http', () => ({
  ApiError: class ApiError extends Error {
    code: string

    constructor(args: { code: string; message: string }) {
      super(args.message)
      this.code = args.code
    }
  }
}))

vi.mock('@/api/iterationApi', () => ({
  iterationApi: {
    list: vi.fn(),
    delete: vi.fn()
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
    props: ['loading'],
    emits: ['search', 'reset'],
    template: '<form><slot /></form>'
  },
  DataTable: {
    props: ['data', 'total', 'loading', 'page', 'pageSize'],
    emits: ['update:page', 'update:pageSize', 'page-change', 'page-size-change'],
    template: '<section><slot name="actions" /><slot /></section>'
  },
  IterationCreateDialog: true,
  ElFormItem: {
    props: ['label'],
    template: '<label>{{ label }}<slot /></label>'
  },
  ElInput: true,
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

describe('IterationList', () => {
  beforeEach(() => {
    vi.mocked(iterationApi.list).mockReset()
    vi.mocked(iterationApi.delete).mockReset()
    vi.mocked(iterationApi.list).mockResolvedValue({ list: [], total: 0 })
    vi.mocked(ElMessage.success).mockReset()
    vi.mocked(ElMessage.warning).mockReset()
    vi.mocked(ElMessageBox.confirm).mockReset()
    vi.mocked(handleError).mockReset()
  })

  it('shows a clear delete protection message when backend rejects an attached iteration', async () => {
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as any)
    vi.mocked(iterationApi.delete).mockRejectedValue(
      new ApiError({ code: 'ITER_002', message: 'attached' })
    )
    const wrapper = mount(IterationList, {
      global: {
        stubs,
        directives: {
          perm: {}
        }
      }
    })
    await flushPromises()

    await (wrapper.vm as any).handleDelete({ iterationKey: 'ITER-20260521-A' })
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('iteration.deleteBlocked')
    expect(handleError).not.toHaveBeenCalled()
    expect(iterationApi.list).toHaveBeenCalledTimes(1)
  })

  it('still delegates unexpected delete failures to the common error handler', async () => {
    const error = new ApiError({ code: 'COMMON_001', message: 'invalid' })
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as any)
    vi.mocked(iterationApi.delete).mockRejectedValue(error)
    const wrapper = mount(IterationList, {
      global: {
        stubs,
        directives: {
          perm: {}
        }
      }
    })
    await flushPromises()

    await (wrapper.vm as any).handleDelete({ iterationKey: 'ITER-20260521-B' })
    await flushPromises()

    expect(ElMessage.warning).not.toHaveBeenCalled()
    expect(handleError).toHaveBeenCalledWith(error)
  })
})
