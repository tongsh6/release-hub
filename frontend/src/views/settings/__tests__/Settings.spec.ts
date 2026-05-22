import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from '../Settings.vue'
import { settingsApi } from '@/api/settingsApi'
import { handleError } from '@/utils/error'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('@/api/http', () => {
  class MockApiError extends Error {
    public readonly code: string

    constructor(args: { code: string; message: string }) {
      super(args.message)
      this.name = 'ApiError'
      this.code = args.code
    }
  }

  return { ApiError: MockApiError }
})

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn()
  }
}))

vi.mock('@/api/settingsApi', () => ({
  settingsApi: {
    getGitLab: vi.fn(),
    saveGitLab: vi.fn(),
    testGitLab: vi.fn(),
    getNaming: vi.fn(),
    saveNaming: vi.fn(),
    getBlocking: vi.fn(),
    saveBlocking: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  ElTabs: {
    template: '<div><slot /></div>'
  },
  ElTabPane: {
    template: '<section><slot /></section>'
  },
  ElForm: {
    template: '<form><slot /></form>'
  },
  ElFormItem: {
    template: '<label><slot /></label>'
  },
  ElInput: true,
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElAlert: {
    props: ['title'],
    template: '<div class="gitlab-diagnostic">{{ title }}</div>'
  },
  ElEmpty: true,
  ElRadioGroup: {
    template: '<div><slot /></div>'
  },
  ElRadio: {
    template: '<span><slot /></span>'
  }
}

describe('Settings', () => {
  beforeEach(() => {
    vi.mocked(settingsApi.getGitLab).mockReset()
    vi.mocked(settingsApi.testGitLab).mockReset()
    vi.mocked(handleError).mockReset()
    vi.mocked(ElMessage.success).mockReset()
    vi.mocked(settingsApi.getGitLab).mockResolvedValue({ baseUrl: 'http://gitlab.local', token: 'gl****en' })
  })

  it('shows a dedicated success message after GitLab connection test passes', async () => {
    vi.mocked(settingsApi.testGitLab).mockResolvedValue(true)
    const wrapper = mount(Settings, { global: { stubs } })

    await (wrapper.vm as any).testGitLab()

    expect(settingsApi.testGitLab).toHaveBeenCalledTimes(1)
    expect(ElMessage.success).toHaveBeenCalledWith('settings.messages.connectionSuccess')
  })

  it('uses the shared error handler when GitLab connection test fails', async () => {
    const error = new Error('invalid token')
    vi.mocked(settingsApi.testGitLab).mockRejectedValue(error)
    const wrapper = mount(Settings, { global: { stubs } })

    await (wrapper.vm as any).testGitLab()

    expect(handleError).toHaveBeenCalledWith(error)
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('renders classified GitLab connection diagnostics on the settings page', async () => {
    const error = new ApiError({
      code: 'GITLAB_004',
      message: 'GitLab token is invalid or expired'
    })
    vi.mocked(settingsApi.testGitLab).mockRejectedValue(error)
    const wrapper = mount(Settings, { global: { stubs } })

    await (wrapper.vm as any).testGitLab()

    expect(wrapper.find('.gitlab-diagnostic').text()).toBe('GitLab token is invalid or expired')
    expect(handleError).toHaveBeenCalledWith(error)
  })
})
