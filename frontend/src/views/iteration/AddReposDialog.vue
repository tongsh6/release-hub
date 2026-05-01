<template>
  <EntityDialog
    ref="entityRef"
    :title="t('iteration.detail.addRepos')"
    :confirm-text="t('common.confirm')"
    :cancel-text="t('common.cancel')"
    width="600px"
    @confirm="submit"
    @opened="onOpened"
  >
    <template #default>
      <div class="search-box">
        <el-input
          v-model="searchKeyword"
          :placeholder="t('iteration.detail.searchRepos')"
          clearable
          prefix-icon="Search"
          @input="handleSearch"
        />
      </div>
      <div v-loading="loading" class="repo-list">
        <el-checkbox-group v-model="selectedRepoIds">
          <div v-for="repo in filteredRepos" :key="repo.id" class="repo-item">
            <el-checkbox :value="repo.id" :disabled="existingRepoIds.has(repo.id)">
              <div class="repo-info">
                <span class="repo-name">{{ repo.name }}</span>
                <span class="repo-branch">{{ repo.defaultBranch }}</span>
                <el-tag v-if="existingRepoIds.has(repo.id)" type="info" size="small">
                  {{ t('iteration.detail.alreadyAdded') }}
                </el-tag>
              </div>
            </el-checkbox>
          </div>
        </el-checkbox-group>
        <el-empty v-if="filteredRepos.length === 0 && !loading" :description="t('common.noData')" />
      </div>
      <div class="selected-info">
        {{ t('iteration.detail.selectedCount', { count: newSelectedCount }) }}
      </div>
    </template>
  </EntityDialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import EntityDialog from '@/components/common/EntityDialog.vue'
import { repositoryApi, type Repository } from '@/api/repositoryApi'
import { iterationApi } from '@/api/iterationApi'
import { handleError } from '@/utils/error'

const { t } = useI18n()
const emit = defineEmits<{ (e: 'success'): void }>()

const entityRef = ref<InstanceType<typeof EntityDialog>>()

const repos = ref<Repository[]>([])
const iterationKeyRef = ref<string>('')
const existingRepoIds = ref<Set<string>>(new Set())
const selectedRepoIds = ref<string[]>([])
const searchKeyword = ref('')
const loading = ref(false)
const saving = ref(false)

// 过滤仓库列表
const filteredRepos = computed(() => {
  if (!searchKeyword.value) return repos.value
  const keyword = searchKeyword.value.toLowerCase()
  return repos.value.filter(repo => 
    repo.name.toLowerCase().includes(keyword) ||
    repo.defaultBranch?.toLowerCase().includes(keyword)
  )
})

// 新选中的数量（不包括已存在的）
const newSelectedCount = computed(() => {
  return selectedRepoIds.value.filter(id => !existingRepoIds.value.has(id)).length
})

const open = async (iterationKey: string, currentRepoIds: string[] = []) => {
  iterationKeyRef.value = iterationKey
  existingRepoIds.value = new Set(currentRepoIds)
  selectedRepoIds.value = [...currentRepoIds] // 预选已存在的
  searchKeyword.value = ''
  entityRef.value?.open()
  await loadRepos()
}

const loadRepos = async () => {
  loading.value = true
  try {
    const result = await repositoryApi.list({ page: 1, pageSize: 500 })
    repos.value = result.list || []
  } catch (err) {
    handleError(err)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  // 搜索是响应式的，通过 computed 自动过滤
}

const submit = async () => {
  // 只提交新选中的仓库
  const newRepoIds = selectedRepoIds.value.filter(id => !existingRepoIds.value.has(id))
  
  if (newRepoIds.length === 0) {
    ElMessage.warning(t('iteration.detail.noNewRepos'))
    return
  }
  
  saving.value = true
  try {
    await iterationApi.addRepos(iterationKeyRef.value, newRepoIds)
    ElMessage.success(t('common.success'))
    emit('success')
    entityRef.value?.close()
  } catch (err) {
    handleError(err)
  } finally {
    saving.value = false
  }
}

const onOpened = () => {
  // 对话框打开时的回调
}

defineExpose({ open })
</script>

<style scoped>
.search-box {
  margin-bottom: 16px;
}

.repo-list {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
}

.repo-item {
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.repo-item:last-child {
  border-bottom: none;
}

.repo-item:hover {
  background-color: #f5f7fa;
}

.repo-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.repo-name {
  font-weight: 500;
}

.repo-branch {
  color: #909399;
  font-size: 12px;
}

.selected-info {
  margin-top: 12px;
  color: #606266;
  font-size: 14px;
}
</style>
