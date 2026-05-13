import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import VersionUpdateDialog from '../VersionUpdateDialog.vue'
import { repositoryApi, type Repository } from '@/api/repositoryApi'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn()
  }
}))

vi.mock('@/api/modules/releaseWindow', () => ({
  releaseWindowApi: {
    executeVersionUpdate: vi.fn()
  },
  getConflicts: vi.fn().mockResolvedValue({ hasConflicts: false })
}))

vi.mock('@/api/repositoryApi', () => ({
  repositoryApi: {
    list: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  ElDialog: {
    props: ['modelValue'],
    template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>'
  },
  ElForm: {
    template: '<form><slot /></form>'
  },
  ElFormItem: {
    template: '<label><slot /></label>'
  },
  ElSelect: {
    template: '<div><slot /></div>'
  },
  ElOption: {
    props: ['label'],
    template: '<span>{{ label }}</span>'
  },
  ElInput: true,
  ElRadioGroup: {
    template: '<div><slot /></div>'
  },
  ElRadio: {
    template: '<span><slot /></span>'
  },
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  }
}

const repo = (id: string, name: string): Repository => ({
  id,
  name,
  cloneUrl: `mock:///${name}.git`,
  defaultBranch: 'main',
  repoType: 'application',
  monoRepo: false,
  gitProvider: 'MOCK',
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

describe('VersionUpdateDialog', () => {
  beforeEach(() => {
    vi.mocked(repositoryApi.list).mockReset()
    vi.mocked(repositoryApi.list).mockResolvedValue({
      list: [repo('global-repo', 'global-repo')],
      total: 1
    })
  })

  it('uses the current release window repositories instead of loading every repository', async () => {
    const wrapper = mount(VersionUpdateDialog, {
      global: { stubs }
    })

    await (wrapper.vm as any).open('window-1', [repo('scoped-repo', 'scoped-repo')])
    await nextTick()

    expect(repositoryApi.list).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('scoped-repo')
    expect(wrapper.text()).not.toContain('global-repo')
  })
})
