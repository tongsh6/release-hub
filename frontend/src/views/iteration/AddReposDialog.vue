<template>
  <EntityDialog
    ref="entityRef"
    :title="t('iteration.detail.addRepos')"
    :confirm-text="t('common.confirm')"
    :cancel-text="t('common.cancel')"
    width="650px"
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
          <div
            v-for="repo in filteredRepos"
            :key="repo.id"
            class="repo-item"
            @click.capture.prevent="toggleRepoSelection(repo.id)"
          >
            <el-checkbox
              :value="repo.id"
              :disabled="existingRepoIds.has(repo.id)"
              @mousedown.stop.prevent="toggleRepoSelection(repo.id)"
            />
            <div class="repo-info">
              <span class="repo-name">{{ repo.name }}</span>
              <span class="repo-branch">{{ repo.defaultBranch }}</span>
              <el-tag v-if="existingRepoIds.has(repo.id)" type="info" size="small">
                {{ t('iteration.detail.alreadyAdded') }}
              </el-tag>
            </div>
          </div>
        </el-checkbox-group>
        <el-empty v-if="filteredRepos.length === 0 && !loading" :description="t('common.noData')" />
      </div>
      <div class="selected-info">
        {{ t('iteration.detail.selectedCount', { count: newSelectedCount }) }}
      </div>

      <el-divider v-if="newSelectedCount > 0" />

      <!-- 分支创建模式：仅在有新仓库时显示 -->
      <div v-if="newSelectedCount > 0" class="branch-mode-section">
        <div class="section-label">{{ t('iteration.branchCreationMode.label') }}</div>
        <el-radio-group v-model="branchCreationMode" class="mode-group" :disabled="saving">
          <el-radio value="AUTO" border>
            <div class="mode-option">
              <div class="mode-title">{{ t('iteration.branchCreationMode.AUTO') }}</div>
              <div class="mode-desc">{{ t('iteration.branchCreationMode.AUTO_desc') }}</div>
            </div>
          </el-radio>
          <el-radio value="NAMED" border>
            <div class="mode-option">
              <div class="mode-title">{{ t('iteration.branchCreationMode.NAMED') }}</div>
              <div class="mode-desc">{{ t('iteration.branchCreationMode.NAMED_desc') }}</div>
            </div>
          </el-radio>
          <el-radio value="EXISTING" border>
            <div class="mode-option">
              <div class="mode-title">{{ t('iteration.branchCreationMode.EXISTING') }}</div>
              <div class="mode-desc">{{ t('iteration.branchCreationMode.EXISTING_desc') }}</div>
            </div>
          </el-radio>
        </el-radio-group>

        <!-- AUTO: 显示预览分支名 -->
        <div v-if="branchCreationMode === 'AUTO'" class="mode-extra">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              {{ t('iteration.branchCreationMode.preview') }}: <code>feature/{{ iterationKeyRef }}</code>
            </template>
          </el-alert>
        </div>

        <!-- NAMED: 输入自定义分支名 -->
        <div v-if="branchCreationMode === 'NAMED'" class="mode-extra">
          <el-input
            v-model="customBranchName"
            :placeholder="t('iteration.branchCreationMode.customName')"
            :disabled="saving"
            @input="validateBranchName"
          />
          <div v-if="branchNameError" class="error-tip">{{ branchNameError }}</div>
        </div>

        <!-- EXISTING: 选择已有分支 -->
        <div v-if="branchCreationMode === 'EXISTING'" class="mode-extra">
          <el-select
            v-model="customBranchName"
            :placeholder="t('iteration.branchCreationMode.selectExisting')"
            style="width: 100%"
            filterable
            :disabled="saving"
            @focus="loadBranchesForRepo"
          >
            <el-option
              v-for="branch in availableBranches"
              :key="branch"
              :label="branch"
              :value="branch"
            />
          </el-select>
          <div v-if="loadingBranches" class="hint-tip">{{ t('iteration.branchCreationMode.loadBranches') }}...</div>
          <div v-else-if="availableBranches.length === 0 && !loadingBranches" class="hint-tip">
            {{ t('common.noData') }}
          </div>
        </div>
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
const iterationGroupCodeRef = ref<string>('')
const existingRepoIds = ref<Set<string>>(new Set())
const selectedRepoIds = ref<string[]>([])
const searchKeyword = ref('')
const loading = ref(false)
const saving = ref(false)

// 分支创建模式
const branchCreationMode = ref<'AUTO' | 'NAMED' | 'EXISTING'>('AUTO')
const customBranchName = ref('')
const branchNameError = ref('')
const availableBranches = ref<string[]>([])
const loadingBranches = ref(false)

const filteredRepos = computed(() => {
  if (!searchKeyword.value) return repos.value
  const keyword = searchKeyword.value.toLowerCase()
  return repos.value.filter(repo =>
    repo.name.toLowerCase().includes(keyword) ||
    repo.defaultBranch?.toLowerCase().includes(keyword)
  )
})

const newSelectedCount = computed(() => {
  return selectedRepoIds.value.filter(id => !existingRepoIds.value.has(id)).length
})

const open = async (iterationKey: string, currentRepoIds: string[] = [], iterationGroupCode = '') => {
  iterationKeyRef.value = iterationKey
  iterationGroupCodeRef.value = iterationGroupCode
  existingRepoIds.value = new Set(currentRepoIds)
  selectedRepoIds.value = [...currentRepoIds]
  searchKeyword.value = ''
  branchCreationMode.value = 'AUTO'
  customBranchName.value = ''
  branchNameError.value = ''
  availableBranches.value = []
  entityRef.value?.open()
  await loadRepos()
}

const loadRepos = async () => {
  loading.value = true
  try {
    const result = await repositoryApi.list({ page: 1, pageSize: 500 })
    const allRepos = result.list || []
    repos.value = iterationGroupCodeRef.value
      ? allRepos.filter(repo => repo.groupCode === iterationGroupCodeRef.value)
      : allRepos
  } catch (err) {
    handleError(err)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  // 搜索是响应式的，通过 computed 自动过滤
}

const toggleRepoSelection = (repoId: string) => {
  if (existingRepoIds.value.has(repoId)) return
  if (selectedRepoIds.value.includes(repoId)) {
    selectedRepoIds.value = selectedRepoIds.value.filter(id => id !== repoId)
  } else {
    selectedRepoIds.value = [...selectedRepoIds.value, repoId]
  }
}

const validateBranchName = () => {
  if (!customBranchName.value) {
    branchNameError.value = ''
    return
  }
  if (!customBranchName.value.startsWith('feature/')) {
    branchNameError.value = t('iteration.branchCreationMode.featurePrefix')
    return
  }
  branchNameError.value = ''
}

const loadBranchesForRepo = async () => {
  if (availableBranches.value.length > 0 || selectedRepoIds.value.length === 0) return
  loadingBranches.value = true
  try {
    const firstRepoId = selectedRepoIds.value.find(id => !existingRepoIds.value.has(id))
    if (firstRepoId) {
      availableBranches.value = await repositoryApi.listBranches(firstRepoId, 'feature/')
    }
  } catch (err) {
    handleError(err)
  } finally {
    loadingBranches.value = false
  }
}

const submit = async () => {
  if (saving.value) return  // 防止重复提交
  const newRepoIds = selectedRepoIds.value.filter(id => !existingRepoIds.value.has(id))

  if (newRepoIds.length === 0) {
    ElMessage.warning(t('iteration.detail.noNewRepos'))
    return
  }

  if (branchCreationMode.value === 'NAMED') {
    if (!customBranchName.value) {
      ElMessage.warning(t('iteration.branchCreationMode.featurePrefix'))
      return
    }
    validateBranchName()
    if (branchNameError.value) {
      ElMessage.warning(branchNameError.value)
      return
    }
  }

  if (branchCreationMode.value === 'EXISTING' && !customBranchName.value) {
    ElMessage.warning(t('iteration.branchCreationMode.selectExisting'))
    return
  }

  saving.value = true
  try {
    await iterationApi.addRepos(
      iterationKeyRef.value,
      newRepoIds,
      branchCreationMode.value,
      branchCreationMode.value !== 'AUTO' ? customBranchName.value : undefined
    )
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
  max-height: 300px;
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

.branch-mode-section {
  margin-top: 8px;
}

.section-label {
  font-weight: 500;
  margin-bottom: 12px;
  color: #303133;
}

.mode-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.mode-group :deep(.el-radio) {
  margin-right: 0;
  width: 100%;
  height: auto;
  padding: 12px 16px;
}

.mode-option {
  width: 100%;
}

.mode-title {
  font-weight: 500;
  margin-bottom: 2px;
}

.mode-desc {
  font-size: 12px;
  color: #909399;
}

.mode-extra {
  margin-top: 12px;
}

.error-tip {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}

.hint-tip {
  color: #909399;
  font-size: 13px;
}
</style>
