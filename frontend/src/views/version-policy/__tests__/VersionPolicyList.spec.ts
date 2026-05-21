import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import VersionPolicyList from '../VersionPolicyList.vue'
import { versionPolicyApi } from '@/api/versionPolicyApi'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    info: vi.fn(),
    success: vi.fn()
  }
}))

vi.mock('@/api/versionPolicyApi', () => ({
  versionPolicyApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn()
  }
}))

vi.mock('@/utils/error', () => ({
  handleError: vi.fn()
}))

const stubs = {
  SearchForm: {
    template: '<form><slot /></form>'
  },
  DataTable: {
    template: '<section><slot name="actions" /><slot /></section>'
  },
  ElAlert: true,
  ElDialog: {
    template: '<section><slot /><slot name="footer" /></section>'
  },
  ElForm: {
    template: '<form><slot /></form>'
  },
  ElFormItem: {
    template: '<label><slot /></label>'
  },
  ElInput: true,
  ElSelect: {
    template: '<select><slot /></select>'
  },
  ElOption: true,
  ElRadioGroup: {
    emits: ['change'],
    template: '<div><slot /></div>'
  },
  ElRadio: {
    template: '<span><slot /></span>'
  },
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElTableColumn: true,
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElPopconfirm: {
    template: '<span><slot name="reference" /></span>'
  }
}

describe('VersionPolicyList', () => {
  beforeEach(() => {
    vi.mocked(versionPolicyApi.list).mockReset()
    vi.mocked(versionPolicyApi.create).mockReset()
    vi.mocked(versionPolicyApi.update).mockReset()
    vi.mocked(versionPolicyApi.remove).mockReset()
    vi.mocked(ElMessage.success).mockReset()
    vi.mocked(versionPolicyApi.list).mockResolvedValue({ list: [], total: 0 })
  })

  it('requires project id for project scoped policies', async () => {
    const wrapper = mount(VersionPolicyList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.scopeLevel = 'PROJECT'
    const callback = vi.fn()

    vm.rules.scopeProjectId[0].validator({}, '', callback)

    expect(callback).toHaveBeenCalledWith(expect.any(Error))
    expect(callback.mock.calls[0][0].message).toBe('versionPolicy.scopeProjectRequired')
  })

  it('clears stale scope ids when scope level changes', async () => {
    const wrapper = mount(VersionPolicyList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.scopeProjectId = 'project-1'
    vm.form.scopeSubProjectId = 'repo-1'
    vm.formRef = { clearValidate: vi.fn() }

    vm.onScopeLevelChange('GLOBAL')

    expect(vm.form.scopeProjectId).toBe('')
    expect(vm.form.scopeSubProjectId).toBe('')
    expect(vm.formRef.clearValidate).toHaveBeenCalledWith(['scopeProjectId', 'scopeSubProjectId'])
  })

  it('submits sub-project scope when creating a version policy', async () => {
    vi.mocked(versionPolicyApi.create).mockResolvedValue({
      id: 'vp-1',
      name: 'App SemVer',
      strategy: 'SEMVER (MINOR)',
      scheme: 'SEMVER',
      bumpRule: 'MINOR',
      scope: { level: 'SUB_PROJECT', projectId: 'group-1', subProjectId: 'repo-1' }
    })
    const wrapper = mount(VersionPolicyList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    vm.form.name = 'App SemVer'
    vm.form.scheme = 'SEMVER'
    vm.form.bumpRule = 'MINOR'
    vm.form.scopeLevel = 'SUB_PROJECT'
    vm.form.scopeProjectId = 'group-1'
    vm.form.scopeSubProjectId = 'repo-1'

    await vm.handleSave()

    expect(versionPolicyApi.create).toHaveBeenCalledWith({
      name: 'App SemVer',
      scheme: 'SEMVER',
      bumpRule: 'MINOR',
      scopeLevel: 'SUB_PROJECT',
      scopeProjectId: 'group-1',
      scopeSubProjectId: 'repo-1'
    })
    expect(ElMessage.success).toHaveBeenCalledWith('common.createSuccess')
  })

  it('prefills form when editing a scoped version policy', async () => {
    const wrapper = mount(VersionPolicyList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any

    vm.handleEdit({
      id: 'vp-1',
      name: 'Project SemVer',
      strategy: 'SEMVER (PATCH)',
      scheme: 'SEMVER',
      bumpRule: 'PATCH',
      scope: { level: 'PROJECT', projectId: 'group-1' }
    })

    expect(vm.isEdit).toBe(true)
    expect(vm.editId).toBe('vp-1')
    expect(vm.form.name).toBe('Project SemVer')
    expect(vm.form.scheme).toBe('SEMVER')
    expect(vm.form.bumpRule).toBe('PATCH')
    expect(vm.form.scopeLevel).toBe('PROJECT')
    expect(vm.form.scopeProjectId).toBe('group-1')
    expect(vm.form.scopeSubProjectId).toBe('')
  })

  it('updates scoped policy with the edited payload', async () => {
    vi.mocked(versionPolicyApi.update).mockResolvedValue({
      id: 'vp-1',
      name: 'Updated SemVer',
      strategy: 'SEMVER (MAJOR)',
      scheme: 'SEMVER',
      bumpRule: 'MAJOR',
      scope: { level: 'SUB_PROJECT', projectId: 'group-1', subProjectId: 'repo-1' }
    })
    const wrapper = mount(VersionPolicyList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    vm.handleEdit({
      id: 'vp-1',
      name: 'Project SemVer',
      strategy: 'SEMVER (PATCH)',
      scheme: 'SEMVER',
      bumpRule: 'PATCH',
      scope: { level: 'PROJECT', projectId: 'group-1' }
    })
    vm.form.name = 'Updated SemVer'
    vm.form.bumpRule = 'MAJOR'
    vm.form.scopeLevel = 'SUB_PROJECT'
    vm.form.scopeSubProjectId = 'repo-1'

    await vm.handleSave()

    expect(versionPolicyApi.update).toHaveBeenCalledWith('vp-1', {
      name: 'Updated SemVer',
      scheme: 'SEMVER',
      bumpRule: 'MAJOR',
      scopeLevel: 'SUB_PROJECT',
      scopeProjectId: 'group-1',
      scopeSubProjectId: 'repo-1'
    })
    expect(versionPolicyApi.create).not.toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('common.updateSuccess')
  })

  it('deletes selected policy and reloads the list', async () => {
    vi.mocked(versionPolicyApi.remove).mockResolvedValue()
    const wrapper = mount(VersionPolicyList, { global: { stubs } })
    await flushPromises()

    await (wrapper.vm as any).handleDelete({
      id: 'vp-1',
      name: 'App SemVer',
      strategy: 'SEMVER (PATCH)',
      scheme: 'SEMVER',
      bumpRule: 'PATCH',
      scope: { level: 'PROJECT', projectId: 'group-1' }
    })
    await flushPromises()

    expect(versionPolicyApi.remove).toHaveBeenCalledWith('vp-1')
    expect(ElMessage.success).toHaveBeenCalledWith('common.deleteSuccess')
    expect(versionPolicyApi.list).toHaveBeenCalledTimes(2)
  })
})
