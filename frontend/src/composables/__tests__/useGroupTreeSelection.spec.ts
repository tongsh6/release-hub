import { beforeEach, describe, expect, it, vi } from 'vitest'
import { groupApi } from '@/api/modules/group'
import { useGroupTree } from '../useGroupTree'

vi.mock('@/api/modules/group', () => ({ groupApi: { listTree: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
vi.mock('@/i18n', () => ({ i18n: { global: { t: (key: string) => key } } }))

describe('分组树选择状态', () => {
  beforeEach(() => vi.mocked(groupApi.listTree).mockReset())

  it('刷新为空树时清除旧节点和编码', async () => {
    const tree = useGroupTree()
    tree.onNodeClick({ code: 'old', name: '旧分组' })
    vi.mocked(groupApi.listTree).mockResolvedValue([])
    await tree.loadTree()
    expect(tree.selected.value).toBeNull()
    expect(tree.selectedCode.value).toBeNull()
  })

  it('缺少编码时保持空选择编码', async () => {
    const tree = useGroupTree()
    vi.mocked(groupApi.listTree).mockResolvedValue([{ name: '缺少编码的分组' }])
    await tree.loadTree()
    expect(tree.selected.value?.name).toBe('缺少编码的分组')
    expect(tree.selectedCode.value).toBeNull()
    tree.onNodeClick({ name: '另一个分组' })
    expect(tree.selectedCode.value).toBeNull()
  })

  it('刷新后按编码恢复嵌套选择，节点删除后回到首项', async () => {
    const tree = useGroupTree()
    tree.onNodeClick({ code: 'child', name: '旧名称' })
    vi.mocked(groupApi.listTree).mockResolvedValue([
      { code: 'root', children: [{ code: 'child', name: '新名称' }] }
    ])
    await tree.loadTree()
    expect(tree.selected.value?.name).toBe('新名称')
    vi.mocked(groupApi.listTree).mockResolvedValue([{ code: 'root' }])
    await tree.loadTree()
    expect(tree.selectedCode.value).toBe('root')
    expect(tree.loading.value).toBe(false)
  })
})
