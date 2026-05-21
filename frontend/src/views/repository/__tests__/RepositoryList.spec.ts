import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RepositoryList from '../RepositoryList.vue'
import { repositoryApi } from '@/api/repositoryApi'
import { ApiError } from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
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

vi.mock('@/api/repositoryApi', () => ({
  repositoryApi: {
    list: vi.fn(),
    sync: vi.fn(),
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
  RepositoryDrawer: true,
  RepositoryEdit: true,
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
  }
}

describe('RepositoryList', () => {
  beforeEach(() => {
    vi.mocked(repositoryApi.list).mockReset()
    vi.mocked(repositoryApi.delete).mockReset()
    vi.mocked(repositoryApi.list).mockResolvedValue({ list: [], total: 0 })
    vi.mocked(ElMessage.warning).mockReset()
    vi.mocked(ElMessageBox.confirm).mockReset()
    vi.mocked(handleError).mockReset()
  })

  it('submits selected groupCode when filtering repositories', async () => {
    const wrapper = mount(RepositoryList, {
      global: {
        stubs,
        directives: {
          perm: {}
        }
      }
    })
    await flushPromises()

    await wrapper.find('.group-filter').trigger('click')
    await wrapper.find('.search').trigger('click')
    await flushPromises()

    expect(repositoryApi.list).toHaveBeenLastCalledWith({
      keyword: '',
      groupCode: 'G001',
      page: 1,
      pageSize: 10
    })
  })

  it('shows a clear delete protection message when backend rejects an attached repository', async () => {
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as any)
    vi.mocked(repositoryApi.delete).mockRejectedValue(
      new ApiError({ code: 'REPO_011', message: 'attached' })
    )
    const wrapper = mount(RepositoryList, {
      global: {
        stubs,
        directives: {
          perm: {}
        }
      }
    })
    await flushPromises()

    await (wrapper.vm as any).handleDelete({ id: 'repo-1' })
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('repository.deleteBlocked')
    expect(handleError).not.toHaveBeenCalled()
  })
})
