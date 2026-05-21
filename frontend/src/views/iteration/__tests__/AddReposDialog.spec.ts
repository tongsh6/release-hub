import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AddReposDialog from '../AddReposDialog.vue'
import { repositoryApi, type Repository } from '@/api/repositoryApi'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, args?: Record<string, unknown>) => (args ? `${key}:${JSON.stringify(args)}` : key)
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn()
  }
}))

vi.mock('@/api/repositoryApi', () => ({
  repositoryApi: {
    list: vi.fn(),
    listBranches: vi.fn()
  }
}))

vi.mock('@/api/iterationApi', () => ({
  iterationApi: {
    addRepos: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  EntityDialog: {
    setup(_props: unknown, { expose }: { expose: (exposed: unknown) => void }) {
      expose({
        open: vi.fn(),
        close: vi.fn()
      })
      return {}
    },
    template: '<section><slot /><slot name="footer" /></section>'
  },
  ElInput: true,
  ElCheckboxGroup: {
    template: '<div><slot /></div>'
  },
  ElCheckbox: {
    props: ['value', 'disabled'],
    template: '<input type="checkbox" :value="value" :disabled="disabled" />'
  },
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElEmpty: true,
  ElDivider: true,
  ElRadioGroup: {
    template: '<div><slot /></div>'
  },
  ElRadio: {
    template: '<span><slot /></span>'
  },
  ElAlert: {
    template: '<div><slot name="title" /></div>'
  },
  ElSelect: {
    template: '<div><slot /></div>'
  },
  ElOption: {
    props: ['label'],
    template: '<span>{{ label }}</span>'
  }
}

const repo = (id: string, name: string, groupCode: string): Repository => ({
  id,
  name,
  cloneUrl: `https://gitlab.example.com/customer/${name}.git`,
  defaultBranch: 'main',
  groupCode,
  repoType: 'SERVICE',
  monoRepo: false,
  gitProvider: 'GITLAB',
  branchCount: 0,
  activeBranchCount: 0,
  nonCompliantBranchCount: 0,
  mrCount: 0,
  openMrCount: 0,
  mergedMrCount: 0,
  closedMrCount: 0,
  lastSyncAt: '',
  createdAt: '',
  updatedAt: ''
})

describe('AddReposDialog', () => {
  beforeEach(() => {
    vi.mocked(repositoryApi.list).mockReset()
  })

  it('shows only repositories from the current iteration group', async () => {
    vi.mocked(repositoryApi.list).mockResolvedValue({
      list: [
        repo('repo-1', 'same-group-repo', 'G001'),
        repo('repo-2', 'other-group-repo', 'G002')
      ],
      total: 2
    })
    const wrapper = mount(AddReposDialog, {
      global: {
        stubs,
        directives: {
          loading: {}
        }
      }
    })

    await (wrapper.vm as any).open('ITER-1', [], 'G001')
    await flushPromises()

    expect(wrapper.text()).toContain('same-group-repo')
    expect(wrapper.text()).not.toContain('other-group-repo')
  })
})
