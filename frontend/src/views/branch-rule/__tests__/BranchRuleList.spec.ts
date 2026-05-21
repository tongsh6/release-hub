import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BranchRuleList from '../BranchRuleList.vue'
import { branchRuleApi } from '@/api/branchRuleApi'
import { ElMessage } from 'element-plus'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn()
  }
}))

vi.mock('@/api/branchRuleApi', () => ({
  branchRuleApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    enable: vi.fn(),
    disable: vi.fn(),
    test: vi.fn()
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
  ElForm: {
    template: '<form><slot /></form>'
  },
  ElFormItem: {
    template: '<label><slot /></label>'
  },
  ElInput: true,
  ElRadioGroup: {
    emits: ['change'],
    template: '<div><slot /></div>'
  },
  ElRadio: {
    template: '<span><slot /></span>'
  },
  ElDialog: {
    template: '<section><slot /><slot name="footer" /></section>'
  },
  ElButton: {
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
  },
  ElTableColumn: true,
  ElTag: {
    template: '<span><slot /></span>'
  },
  ElSwitch: true,
  ElPopconfirm: {
    template: '<span><slot name="reference" /></span>'
  },
  ElAlert: true
}

describe('BranchRuleList', () => {
  beforeEach(() => {
    vi.mocked(branchRuleApi.list).mockReset()
    vi.mocked(branchRuleApi.create).mockReset()
    vi.mocked(branchRuleApi.test).mockReset()
    vi.mocked(ElMessage.success).mockReset()
    vi.mocked(branchRuleApi.list).mockResolvedValue({ list: [], total: 0 })
  })

  it('requires project id for project scoped rules', async () => {
    const wrapper = mount(BranchRuleList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.scopeLevel = 'PROJECT'
    const callback = vi.fn()

    vm.rules.scopeProjectId[0].validator({}, '', callback)

    expect(callback).toHaveBeenCalledWith(expect.any(Error))
    expect(callback.mock.calls[0][0].message).toBe('branchRule.scopeProjectRequired')
  })

  it('clears stale scope ids when scope level changes', async () => {
    const wrapper = mount(BranchRuleList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.scopeProjectId = 'project-1'
    vm.form.scopeSubProjectId = 'sub-1'
    vm.formRef = { clearValidate: vi.fn() }

    vm.onScopeLevelChange('GLOBAL')

    expect(vm.form.scopeProjectId).toBe('')
    expect(vm.form.scopeSubProjectId).toBe('')
    expect(vm.formRef.clearValidate).toHaveBeenCalledWith(['scopeProjectId', 'scopeSubProjectId'])
  })

  it('submits project scope when creating a branch rule', async () => {
    vi.mocked(branchRuleApi.create).mockResolvedValue({
      id: 'br-1',
      name: 'Feature',
      pattern: 'feature/{key}',
      type: 'TEMPLATE',
      scope: { level: 'PROJECT', projectId: 'project-1' },
      status: 'ENABLED'
    })
    const wrapper = mount(BranchRuleList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    vm.form.name = 'Feature'
    vm.form.pattern = 'feature/{key}'
    vm.form.type = 'TEMPLATE'
    vm.form.scopeLevel = 'PROJECT'
    vm.form.scopeProjectId = 'project-1'

    await vm.handleSave()

    expect(branchRuleApi.create).toHaveBeenCalledWith({
      name: 'Feature',
      pattern: 'feature/{key}',
      type: 'TEMPLATE',
      description: undefined,
      scopeLevel: 'PROJECT',
      scopeProjectId: 'project-1',
      scopeSubProjectId: undefined
    })
    expect(ElMessage.success).toHaveBeenCalledWith('common.createSuccess')
  })

  it('runs the rule test with the selected pattern and type', async () => {
    vi.mocked(branchRuleApi.test).mockResolvedValue({ ok: true, errors: [] })
    const wrapper = mount(BranchRuleList, { global: { stubs } })
    await flushPromises()
    const vm = wrapper.vm as any

    vm.handleTest({
      id: 'br-1',
      name: 'Release',
      pattern: 'release/*',
      type: 'TEMPLATE',
      scope: { level: 'GLOBAL' },
      status: 'ENABLED'
    })
    vm.testForm.branchName = 'release/RW-1'
    await vm.runTest()

    expect(branchRuleApi.test).toHaveBeenCalledWith({
      pattern: 'release/*',
      type: 'TEMPLATE',
      branchName: 'release/RW-1'
    })
    expect(vm.testResult.ok).toBe(true)
  })
})
