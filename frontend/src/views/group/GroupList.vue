<template>
  <div class="group-list-page">
    <SearchForm :loading="false" @search="search" @reset="reset">
      <el-form-item :label="t('common.keyword')">
        <el-input v-model="keyword" :placeholder="t('group.searchPlaceholder')" clearable />
      </el-form-item>
      <template #extra-actions>
        <el-button v-perm.disable="'group:write'" type="primary" @click="handleCreateTop">{{ t('group.createTop') }}</el-button>
      </template>
    </SearchForm>

    <el-container class="content">
      <el-aside v-loading="loading" width="320px" class="tree-aside">
        <el-tree
          v-if="hasTreeData"
          ref="treeRef"
          :data="filteredTree"
          node-key="code"
          :props="treeProps"
          default-expand-all
          :filter-node-method="filterNode"
          :highlight-current="true"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="node-label">{{ data.name }}</span>
              <span class="node-code">{{ data.code }}</span>
              <el-dropdown class="node-actions" trigger="click" @command="(cmd: string) => handleCommand(cmd, data)">
                <el-button link type="primary" @click.stop>
                  {{ t('common.actions') }}
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="view">{{ t('common.detail') }}</el-dropdown-item>
                    <el-dropdown-item command="edit" :disabled="!hasPerm('group:write')">{{ t('common.edit') }}</el-dropdown-item>
                    <el-dropdown-item command="createChild" :disabled="!hasPerm('group:write')">{{ t('group.createChild') }}</el-dropdown-item>
                    <el-dropdown-item command="delete" divided :disabled="!hasPerm('group:delete')">
                      <span class="text-danger">{{ t('common.delete') }}</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-tree>
        <el-empty v-else-if="!loading" :description="emptyDescription" />
      </el-aside>
      <el-main>
        <el-card v-if="selected" class="detail-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('group.detail') }}</span>
              <div>
                <el-button type="primary" @click="handleView(selected!)">{{ t('common.detail') }}</el-button>
                <el-button v-perm.disable="'group:write'" @click="handleEdit(selected!)">{{ t('common.edit') }}</el-button>
                <el-button v-perm.disable="'group:write'" type="success" @click="handleCreateChild(selected!)">{{ t('group.createChild') }}</el-button>
                <el-button v-perm.disable="'group:delete'" type="danger" @click="handleDelete(selected!)">{{ t('common.delete') }}</el-button>
              </div>
            </div>
          </template>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="label">{{ t('group.name') }}</span>
              <span class="value">{{ selected!.name }}</span>
            </div>
            <div class="detail-item">
              <span class="label">{{ t('group.code') }}</span>
              <span class="value">{{ selected!.code }}</span>
            </div>
            <div v-if="selected!.parentCode" class="detail-item">
              <span class="label">{{ t('group.parentCode') }}</span>
              <span class="value">{{ selected!.parentCode }}</span>
            </div>
          </div>
        </el-card>
        <el-empty v-else :description="t('group.notFound')" />
      </el-main>
    </el-container>
  </div>
  <GroupDialog ref="dialogRef" @success="loadTree" />
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import type { ElTree } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'
import SearchForm from '@/components/crud/SearchForm.vue'
import { groupApi, type GroupNode } from '@/api/modules/group'
import GroupDialog from './GroupDialog.vue'
import { useGroupTree } from '@/composables/useGroupTree'
import { hasPerm } from '@/utils/perm'
import { ElMessageBox, ElMessage } from 'element-plus'
import { handleError } from '@/utils/error'

const { t } = useI18n()
const router = useRouter()
const { loading, treeData, selected, selectedCode, loadTree, onNodeClick } = useGroupTree()
const treeRef = ref<InstanceType<typeof ElTree>>()
const treeProps = { children: 'children', label: 'name' }
const dialogRef = ref<InstanceType<typeof GroupDialog>>()

const keyword = ref('')
const filteredTree = computed(() => {
  if (!keyword.value) return treeData.value
  const k = keyword.value.toLowerCase()
  const filterFn = (node: GroupNode): GroupNode | null => {
    const match = node.name?.toLowerCase().includes(k) || node.code?.toLowerCase().includes(k)
    const children = (node.children || [])
      .map(filterFn)
      .filter((c): c is GroupNode => c !== null)
    if (match || children.length) {
      return { ...node, children }
    }
    return null
  }
  return treeData.value.map(filterFn).filter((n): n is GroupNode => n !== null)
})

watch(keyword, (val) => {
  treeRef.value?.filter(val)
})

const filterNode = (value: string, data: GroupNode) => {
  if (!value) return true
  const lower = value.toLowerCase()
  return data.name?.toLowerCase().includes(lower) || data.code?.toLowerCase().includes(lower)
}

const hasTreeData = computed(() => filteredTree.value.length > 0)
const emptyDescription = computed(() => keyword.value ? t('group.emptyFiltered') : t('group.empty'))

watch(selectedCode, (code) => {
  if (!code) return
  treeRef.value?.setCurrentKey(code)
})

const handleNodeClick = (node: GroupNode) => {
  onNodeClick(node)
}

const handleCommand = (command: string, node: GroupNode) => {
  switch (command) {
    case 'view':
      handleView(node)
      break
    case 'edit':
      handleEdit(node)
      break
    case 'createChild':
      handleCreateChild(node)
      break
    case 'delete':
      handleDelete(node)
      break
  }
}

const handleView = (node: GroupNode) => {
  router.push({ name: 'GroupDetail', params: { id: node.id } })
}

const handleCreateTop = () => {
  if (!hasPerm('group:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  dialogRef.value?.open()
}

const handleCreateChild = (parent: GroupNode) => {
  if (!hasPerm('group:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  dialogRef.value?.open({ parentCode: parent.code, parentName: parent.name })
}

const handleEdit = (node: GroupNode) => {
  if (!hasPerm('group:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  dialogRef.value?.openEdit(node.id!)
}

const handleDelete = async (node: GroupNode) => {
  if (!hasPerm('group:delete')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  try {
    await ElMessageBox.confirm(t('group.confirmDelete'), t('common.warning'), { type: 'warning' })
    await groupApi.remove(node.id!)
    ElMessage.success(t('group.deleteSuccess'))
    await loadTree()
  } catch (error: any) {
    if (error !== 'cancel') {
      handleError(error)
    }
  }
}

const search = () => {
  // computed 已根据 keyword 过滤，无需额外处理
}
const reset = () => {
  keyword.value = ''
}

onMounted(loadTree)
</script>

<style scoped>
.group-list-page {
  padding: 16px;
}
.content {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  min-height: 520px;
}
.tree-aside {
  border-right: 1px solid #dcdfe6;
  padding: 12px;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.node-label {
  font-weight: 500;
}
.node-code {
  color: #909399;
  margin-left: 6px;
}
.node-actions {
  margin-left: auto;
}

.text-danger {
  color: var(--el-color-danger);
}
.detail-card {
  margin: 12px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.detail-item .label {
  color: #606266;
  margin-right: 8px;
}
.detail-item .value {
  font-weight: 500;
}
</style>
