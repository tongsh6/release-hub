import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { i18n } from '@/i18n'
import { groupApi, type GroupNode } from '@/api/modules/group'

export function useGroupTree() {
  const loading = ref(false)
  const treeData = ref<GroupNode[]>([])
  const selected = ref<GroupNode | null>(null)
  const selectedCode = ref<string | null>(null)

  const findByCode = (nodes: GroupNode[], code?: string | null): GroupNode | null => {
    if (!code) return null
    for (const n of nodes) {
      if (n.code === code) return n
      const child = findByCode(n.children || [], code)
      if (child) return child
    }
    return null
  }

  const restoreSelection = () => {
    if (selectedCode.value) {
      const found = findByCode(treeData.value, selectedCode.value)
      if (found) {
        selected.value = found
        return
      }
    }
    if (treeData.value.length > 0) {
      selected.value = treeData.value[0]
      selectedCode.value = treeData.value[0].code
    } else {
      selected.value = null
    }
  }

  const loadTree = async () => {
    loading.value = true
    try {
      const data = await groupApi.listTree()
      treeData.value = Array.isArray(data) ? data : []
      restoreSelection()
    } catch {
      ElMessage.error(i18n.global.t('common.requestFailed'))
    } finally {
      loading.value = false
    }
  }

  const onNodeClick = (node: GroupNode) => {
    selected.value = node
    selectedCode.value = node.code
  }

  return {
    loading,
    treeData,
    selected,
    selectedCode,
    loadTree,
    onNodeClick
  }
}
