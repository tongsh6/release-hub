<template>
  <div v-loading="loading" class="page-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">{{ t('common.back') }}</el-button>
      <span class="page-title">{{ t('iteration.detail.title') }}</span>
    </div>

    <!-- 基本信息卡片 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">{{ t('iteration.columns.key') }}: {{ iterationKey }}</span>
        </div>
      </template>
      <div v-if="iteration">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('iteration.columns.key')">
            {{ iteration.iterationKey }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('iteration.columns.name')">
            {{ iteration.name || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('iteration.columns.expectedReleaseAt')" :span="2">
            {{ formatDate(iteration.expectedReleaseAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('iteration.columns.description')" :span="2">
            <div class="description-content">{{ iteration.description || '-' }}</div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('iteration.columns.createdAt')">
            {{ formatDateTime(iteration.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('iteration.columns.updatedAt')">
            {{ formatDateTime(iteration.updatedAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else :description="t('common.noData')" />
    </el-card>

    <!-- 关联仓库卡片 -->
    <el-card style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span class="title">{{ t('iteration.detail.associatedRepos') }} ({{ repos.length }})</span>
          <div class="actions">
            <el-button v-perm.disable="'iteration:write'" type="primary" size="small" @click="openAddRepos">{{ t('iteration.detail.addRepos') }}</el-button>
          </div>
        </div>
      </template>
      <el-table v-if="repos.length > 0" v-loading="reposLoading" :data="repos" stripe>
        <el-table-column prop="name" :label="t('repository.columns.name')" min-width="140">
          <template #default="{ row }">
            <span class="repo-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cloneUrl" :label="t('repository.columns.cloneUrl')" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" :href="row.cloneUrl" target="_blank" :underline="false" class="clone-url">
              {{ row.cloneUrl }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column :label="t('iteration.version.featureBranch')" width="180">
          <template #default="{ row }">
            <el-tag v-if="versionMap[row.id]?.featureBranch" size="small" type="info">
              {{ versionMap[row.id].featureBranch }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('iteration.version.baseVersion')" width="110">
          <template #default="{ row }">
            <span>{{ versionMap[row.id]?.baseVersion || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('iteration.version.devVersion')" width="110">
          <template #default="{ row }">
            <span class="version-dev">{{ versionMap[row.id]?.devVersion || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('iteration.version.targetVersion')" width="110">
          <template #default="{ row }">
            <span class="version-target">{{ versionMap[row.id]?.targetVersion || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button 
              v-perm.disable="'iteration:write'" 
              link 
              type="primary" 
              size="small" 
              :loading="syncingRepos[row.id]"
              @click="handleSyncVersion(row.id)"
            >
              {{ t('iteration.version.sync') }}
            </el-button>
            <el-button 
              v-perm.disable="'iteration:write'" 
              link 
              type="danger" 
              size="small" 
              @click="handleRemoveRepo(row.id)"
            >
              {{ t('common.remove') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :description="t('iteration.detail.noRepos')" />
    </el-card>

    <!-- 操作卡片 -->
    <el-card style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span class="title">{{ t('iteration.detail.operations') }}</span>
        </div>
      </template>
      <div class="operations">
        <el-button v-perm.disable="'iteration:write'" type="primary" @click="openAttachWindow">{{ t('iteration.detail.attachToWindow') }}</el-button>
      </div>
    </el-card>
  </div>
  <AttachWindowDialog ref="attachRef" @success="fetchDetail" />
  <AddReposDialog ref="addReposRef" @success="fetchDetail" />
  <VersionConflictDialog ref="conflictDialogRef" @resolved="fetchDetail" />
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ref, reactive, onMounted } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { iterationApi, type Iteration, type IterationRepoVersionInfo } from '@/api/iterationApi'
import { repositoryApi, type Repository } from '@/api/repositoryApi'
import { handleError } from '@/utils/error'
import AttachWindowDialog from './AttachWindowDialog.vue'
import AddReposDialog from './AddReposDialog.vue'
import VersionConflictDialog from './VersionConflictDialog.vue'
import { hasPerm } from '@/utils/perm'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDate, formatDateTime } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const { t } = useI18n({ useScope: 'global' })
const iterationKey = route.params.iterationKey as string

const goBack = () => {
  router.push({ name: 'Iterations' })
}

const loading = ref(false)
const reposLoading = ref(false)
const iteration = ref<Iteration | null>(null)
const repos = ref<Repository[]>([])
const versionMap = reactive<Record<string, IterationRepoVersionInfo>>({})
const syncingRepos = reactive<Record<string, boolean>>({})
const attachRef = ref<InstanceType<typeof AttachWindowDialog>>()
const addReposRef = ref<InstanceType<typeof AddReposDialog>>()
const conflictDialogRef = ref<InstanceType<typeof VersionConflictDialog>>()

const fetchDetail = async () => {
  loading.value = true
  iteration.value = null
  repos.value = []
  try {
    const res = await iterationApi.get(iterationKey)
    iteration.value = res
    // 获取仓库详细信息
    if (res.repoIds && res.repoIds.length > 0) {
      await fetchRepoDetails(res.repoIds)
    }
  } catch (err) {
    handleError(err)
  } finally {
    loading.value = false
  }
}

const fetchRepoDetails = async (repoIds: string[]) => {
  reposLoading.value = true
  try {
    const repoDetails = await Promise.all(
      repoIds.map(id => repositoryApi.get(id).catch(() => null))
    )
    repos.value = repoDetails.filter((r): r is Repository => r !== null)
    
    // 获取每个仓库的版本信息
    await fetchVersionInfo(repoIds)
  } catch (err) {
    handleError(err)
  } finally {
    reposLoading.value = false
  }
}

const fetchVersionInfo = async (repoIds: string[]) => {
  for (const repoId of repoIds) {
    try {
      const versionInfo = await iterationApi.getRepoVersionInfo(iterationKey, repoId)
      if (versionInfo) {
        versionMap[repoId] = versionInfo
      }
    } catch (err) {
      // 忽略单个仓库版本获取失败
      console.warn(`Failed to fetch version info for repo ${repoId}:`, err)
    }
  }
}

const handleSyncVersion = async (repoId: string) => {
  if (!hasPerm('iteration:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  
  syncingRepos[repoId] = true
  try {
    const versionInfo = await iterationApi.syncVersionFromRepo(iterationKey, repoId)
    if (versionInfo) {
      versionMap[repoId] = versionInfo
      ElMessage.success(t('iteration.version.syncSuccess'))
    }
  } catch (err) {
    handleError(err)
  } finally {
    syncingRepos[repoId] = false
  }
}

onMounted(fetchDetail)

const openAttachWindow = () => {
  if (!hasPerm('iteration:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  attachRef.value?.open(iterationKey)
}

const openAddRepos = () => {
  if (!hasPerm('iteration:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  // 打开添加仓库对话框，传入当前已关联的仓库 ID
  const currentRepoIds = iteration.value?.repoIds || []
  addReposRef.value?.open(iterationKey, currentRepoIds)
}

const handleRemoveRepo = async (repoId: string) => {
  if (!hasPerm('iteration:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  
  const repo = repos.value.find(r => r.id === repoId)
  const repoName = repo?.name || repoId
  
  try {
    await ElMessageBox.confirm(
      t('iteration.detail.confirmRemoveRepo', { repoId: repoName }),
      t('common.warning'),
      { type: 'warning' }
    )
    
    await iterationApi.removeRepos(iterationKey, [repoId])
    ElMessage.success(t('common.success'))
    fetchDetail()
  } catch (error) {
    if (error !== 'cancel') {
      handleError(error)
    }
  }
}
</script>

<style scoped>
/* 页面特定样式 - 通用样式已移至 index.css */
.card-header .title {
  font-weight: 500;
}

.card-header .actions {
  display: flex;
  gap: 8px;
}

.operations {
  display: flex;
  gap: 12px;
}

.repo-name {
  font-weight: 500;
}

.description-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
}

.version-dev {
  color: var(--el-color-primary);
  font-family: 'Monaco', 'Menlo', monospace;
}

.version-target {
  color: var(--el-color-success);
  font-weight: 500;
  font-family: 'Monaco', 'Menlo', monospace;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

.clone-url {
  display: block;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
