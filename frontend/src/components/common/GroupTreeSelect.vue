<template>
  <el-tree-select
    v-model="modelValue"
    :data="treeData"
    :props="treeProps"
    :placeholder="placeholder || t('group.selectGroup')"
    :clearable="clearable"
    :disabled="disabled"
    :check-strictly="true"
    :render-after-expand="false"
    :filter-node-method="filterNode"
    filterable
    style="width: 100%"
    @change="handleChange"
  >
    <template #default="{ data }">
      <span class="tree-node">
        <span>{{ data.name }}</span>
        <span v-if="showCode" class="tree-node-code">({{ data.code }})</span>
        <el-tag v-if="!data.isLeaf" type="info" size="small" class="tree-node-tag">
          {{ t('group.hasChildren') }}
        </el-tag>
      </span>
    </template>
  </el-tree-select>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { groupApi, type GroupNode } from '@/api/modules/group'

interface TreeNode {
  code: string
  name: string
  isLeaf: boolean
  children?: TreeNode[]
}

const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  leafOnly?: boolean
  showCode?: boolean
}>(), {
  modelValue: undefined,
  placeholder: '',
  clearable: true,
  disabled: false,
  leafOnly: true,
  showCode: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | undefined): void
  (e: 'change', value: string | undefined, node: TreeNode | null): void
}>()

const { t } = useI18n()

const treeData = ref<TreeNode[]>([])
const loading = ref(false)

const treeProps = {
  value: 'code',
  label: 'name',
  children: 'children',
  disabled: (data: TreeNode) => props.leafOnly && !data.isLeaf
}

const modelValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const transformNode = (node: GroupNode): TreeNode => {
  const children = node.children?.map(transformNode) || []
  return {
    code: node.code!,
    name: node.name!,
    isLeaf: children.length === 0,
    children: children.length > 0 ? children : undefined
  }
}

const loadTree = async () => {
  loading.value = true
  try {
    const tree = await groupApi.listTree()
    treeData.value = tree.map(transformNode)
  } catch (error) {
    console.error('Failed to load group tree:', error)
    treeData.value = []
  } finally {
    loading.value = false
  }
}

const filterNode = (value: string, data: TreeNode) => {
  if (!value) return true
  const lowerValue = value.toLowerCase()
  return data.name.toLowerCase().includes(lowerValue) || data.code.toLowerCase().includes(lowerValue)
}

const findNode = (code: string, nodes: TreeNode[]): TreeNode | null => {
  for (const node of nodes) {
    if (node.code === code) return node
    if (node.children) {
      const found = findNode(code, node.children)
      if (found) return found
    }
  }
  return null
}

const handleChange = (value: string | undefined) => {
  const node = value ? findNode(value, treeData.value) : null
  emit('change', value, node)
}

onMounted(() => {
  loadTree()
})

// Expose reload method for external use
defineExpose({
  reload: loadTree
})
</script>

<style scoped>
.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-node-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.tree-node-tag {
  margin-left: 4px;
}
</style>
