import { describe, expect, it, vi } from 'vitest'
import { useDialogForm } from '../useDialogForm'

vi.mock('@/i18n', () => ({ i18n: { global: { t: (key: string) => key } } }))

describe('对话框能力边界', () => {
  it('仅配置创建时可提交预设表单并通知结果', async () => {
    const create = vi.fn().mockResolvedValue({ id: 'new' })
    const form = useDialogForm({ create, defaultForm: { name: '' } })
    const onSuccess = vi.fn()
    form.onSuccess(onSuccess)
    await form.open({ preset: { name: '新窗口' } })
    await form.submit()
    expect(create).toHaveBeenCalledWith({ name: '新窗口' })
    expect(onSuccess).toHaveBeenCalledWith({ id: 'new' })
    expect(form.visible.value).toBe(false)
    expect(form.saving.value).toBe(false)
  })

  it('仅配置创建时拒绝打开编辑模式', async () => {
    const create = vi.fn()
    const form = useDialogForm({ create, defaultForm: { name: '' } })
    await expect(form.open({ mode: 'edit', id: 'old' })).rejects.toThrow('common.formEditUnsupported')
    expect(form.visible.value).toBe(false)
    expect(create).not.toHaveBeenCalled()
  })

  it('编辑模式加载并更新原记录', async () => {
    const fetchById = vi.fn().mockResolvedValue({ name: '旧名称' })
    const update = vi.fn().mockResolvedValue({ id: 'old' })
    const form = useDialogForm({ create: vi.fn(), fetchById, update, defaultForm: { name: '' } })
    await form.open({ mode: 'edit', id: 'old' })
    form.form.value.name = '新名称'
    await form.submit()
    expect(fetchById).toHaveBeenCalledWith('old')
    expect(update).toHaveBeenCalledWith('old', { name: '新名称' })
  })

  it('只读模式加载记录，提交只关闭对话框', async () => {
    const create = vi.fn()
    const fetchById = vi.fn().mockResolvedValue({ name: '已有窗口' })
    const form = useDialogForm({ create, fetchById, defaultForm: { name: '' } })
    await form.open({ mode: 'view', id: 'old' })
    await form.submit()
    expect(fetchById).toHaveBeenCalledWith('old')
    expect(create).not.toHaveBeenCalled()
    expect(form.visible.value).toBe(false)
  })
})
