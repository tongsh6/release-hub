<template>
  <div v-loading="loading" class="release-window-detail-page page-container">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">{{ t('common.back') }}</el-button>
      <span class="page-title">{{ title }}</span>
      <div class="page-actions">
        <!-- 状态操作按钮 -->
        <el-button
          v-if="form.status === 'DRAFT' && !form.frozen"
          v-perm.disable="'release-window:write'"
          type="warning"
          @click="handleFreeze"
        >
          {{ t('releaseWindow.freeze') }}
        </el-button>
        <el-button
          v-if="form.frozen && form.status === 'DRAFT'"
          v-perm.disable="'release-window:write'"
          @click="handleUnfreeze"
        >
          {{ t('releaseWindow.unfreeze') }}
        </el-button>
        <el-button
          v-if="form.status === 'DRAFT'"
          v-perm.disable="'release-window:write'"
          type="success"
          @click="handlePublish"
        >
          {{ t('releaseWindow.publish') }}
        </el-button>
        <el-button
          v-if="form.status === 'PUBLISHED'"
          v-perm.disable="'release-window:write'"
          type="danger"
          @click="handleClose"
        >
          {{ t('releaseWindow.close') }}
        </el-button>
        
        <el-divider direction="vertical" />
        
        <!-- 功能按钮 -->
        <el-button
          v-perm.disable="'release-window:write'"
          type="primary"
          @click="goAttach"
        >
          {{ t('releaseWindow.attachIterations') }}
        </el-button>
        <el-button
          v-if="iterations.length > 0 && form.status === 'DRAFT'"
          v-perm.disable="'release-window:write'"
          type="success"
          @click="openCodeMerge"
        >
          {{ t('releaseWindow.codeMerge.button') }}
        </el-button>
      </div>
    </div>

    <!-- 基本信息卡片 -->
    <el-card v-if="form.id">
      <el-descriptions border :column="2" :title="t('common.basicInfo')">
        <el-descriptions-item :label="t('releaseWindow.windowKey')">{{ form.windowKey }}</el-descriptions-item>
        <el-descriptions-item :label="t('releaseWindow.name')">{{ form.name }}</el-descriptions-item>
        <el-descriptions-item :label="t('releaseWindow.plannedReleaseAt')">
          {{ formatDateTime(form.plannedReleaseAt) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('releaseWindow.status')">
          <el-tag>{{ form.status ? t(`releaseWindow.statusText.${form.status}`) : '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('releaseWindow.description')" :span="2">
          <div class="description-content">{{ form.description || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item :label="t('releaseWindow.frozen')">
          <el-tag :type="form.frozen ? 'danger' : 'success'">{{ form.frozen ? t('common.yes') : t('common.no') }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('releaseWindow.createdAt')">
          {{ formatDateTime(form.createdAt) }}
        </el-descriptions-item>
         <el-descriptions-item :label="t('releaseWindow.updatedAt')">
          {{ formatDateTime(form.updatedAt) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="form.publishedAt" :label="t('releaseWindow.publishedAt')">
          {{ formatDateTime(form.publishedAt) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 关联迭代卡片 -->
    <el-card v-loading="iterationsLoading" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span class="title">{{ t('releaseWindow.associatedIterations') }} ({{ iterations.length }})</span>
        </div>
      </template>
      
      <div v-if="iterations.length === 0" class="empty-tip">
        {{ t('releaseWindow.noIterations') }}
      </div>
      
      <div v-else class="iterations-list">
        <el-collapse v-model="expandedIterations">
          <el-collapse-item v-for="iter in iterations" :key="iter.key" :name="iter.key">
            <template #title>
              <div class="iteration-header">
                <span class="iteration-name">{{ iter.name || iter.key }}</span>
                <el-tag size="small" type="info" style="margin-left: 8px;">{{ iter.key }}</el-tag>
                <span v-if="iter.expectedReleaseAt" class="iteration-meta">
                  {{ t('iteration.columns.expectedReleaseAt') }}: {{ formatDate(iter.expectedReleaseAt) }}
                </span>
                <span class="repo-count">{{ t('iteration.columns.repos') }}: {{ iter.repos?.length || 0 }}</span>
              </div>
            </template>
            
            <div class="iteration-content">
              <div v-if="iter.description" class="iteration-description">
                {{ iter.description }}
              </div>
              
              <div v-if="iter.repos && iter.repos.length > 0" class="repos-section">
                <el-table :data="iter.repos" border size="small" style="width: 100%">
                  <el-table-column prop="name" :label="t('repository.columns.name')" min-width="200" />
                  <el-table-column prop="defaultBranch" :label="t('repository.columns.defaultBranch')" width="150" />
                  <el-table-column prop="cloneUrl" :label="t('repository.columns.cloneUrl')" min-width="300">
                    <template #default="{ row }">
                      <el-tooltip :content="row.cloneUrl" placement="top">
                        <span class="url-text">{{ row.cloneUrl }}</span>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column :label="t('repository.columns.monoRepo')" width="100">
                    <template #default="{ row }">
                      <el-tag :type="row.monoRepo ? 'warning' : 'info'" size="small">
                        {{ row.monoRepo ? t('common.yes') : t('common.no') }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div v-else class="no-repos">
                {{ t('releaseWindow.noRepos') }}
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-card>

    <!-- 编排控制台 -->
    <OrchestrationPanel
      v-if="form.id && iterations.length > 0"
      :window-id="form.id"
      :window-status="form.status || 'DRAFT'"
      :iteration-count="iterations.length"
      :repo-count="totalRepoCount"
      @open-version-update="openVersionUpdate"
      @refresh="handleRefresh"
    />

    <!-- 分支状态面板 -->
    <BranchStatusPanel
      v-if="form.id && iterations.length > 0"
      :window-id="form.id"
    />

    <!-- 冲突检测面板 -->
    <ConflictPanel
      v-if="form.id && iterations.length > 0"
      ref="conflictPanelRef"
      :window-id="form.id"
    />

    <AttachIterationsDialog ref="attachDialogRef" @success="handleAttachSuccess" />
    <CodeMergeDialog ref="codeMergeDialogRef" />
    <VersionUpdateDialog ref="versionUpdateDialogRef" @success="handleRefresh" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import { releaseWindowApi, type ReleaseWindow } from '@/api/modules/releaseWindow'
import { iterationApi } from '@/api/iterationApi'
import { repositoryApi, type Repository } from '@/api/repositoryApi'
import { handleError } from '@/utils/error'
import { hasPerm } from '@/utils/perm'
import { formatDateTime, formatDate } from '@/utils/date'
import { ElMessage, ElMessageBox } from 'element-plus'
import AttachIterationsDialog from './AttachIterationsDialog.vue'
import CodeMergeDialog from './CodeMergeDialog.vue'
import OrchestrationPanel from './OrchestrationPanel.vue'
import VersionUpdateDialog from './VersionUpdateDialog.vue'
import BranchStatusPanel from './BranchStatusPanel.vue'
import ConflictPanel from './ConflictPanel.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const iterationsLoading = ref(false)
const form = ref<Partial<ReleaseWindow>>({})
const attachDialogRef = ref<InstanceType<typeof AttachIterationsDialog>>()
const codeMergeDialogRef = ref<InstanceType<typeof CodeMergeDialog>>()
const versionUpdateDialogRef = ref<InstanceType<typeof VersionUpdateDialog>>()
const conflictPanelRef = ref()

// 迭代信息（包含仓库详情）
interface IterationWithRepos {
  key: string
  name: string
  description: string
  expectedReleaseAt: string | null
  repos: Repository[]
}

const iterations = ref<IterationWithRepos[]>([])
const expandedIterations = ref<string[]>([])

const load = async (id: string) => {
  loading.value = true
  try {
    form.value = await releaseWindowApi.get(id)
    // 加载关联迭代
    await loadIterations(id)
  } catch (err) {
    handleError(err)
  } finally {
    loading.value = false
  }
}

const loadIterations = async (windowId: string) => {
  iterationsLoading.value = true
  try {
    // 获取关联的迭代 key 列表
    const windowIterations = await releaseWindowApi.listIterations(windowId)
    
    // 获取每个迭代的详细信息
    const iterationDetails: IterationWithRepos[] = []
    
    for (const wi of windowIterations) {
      try {
        const iterKey = wi.iterationKey || wi
        const iter = await iterationApi.get(iterKey)
        
        // 获取迭代关联的仓库详情
        const repos: Repository[] = []
        if (iter.repoIds && iter.repoIds.length > 0) {
          for (const repoId of iter.repoIds) {
            try {
              const repo = await repositoryApi.get(repoId)
              repos.push(repo)
            } catch (e) {
              // 忽略单个仓库获取失败
              console.warn(`Failed to get repo ${repoId}:`, e)
            }
          }
        }
        
        iterationDetails.push({
          key: iter.iterationKey,
          name: iter.name,
          description: iter.description,
          expectedReleaseAt: iter.expectedReleaseAt,
          repos
        })
      } catch (e) {
        console.warn(`Failed to get iteration:`, e)
      }
    }
    
    iterations.value = iterationDetails
    
    // 默认展开第一个迭代
    if (iterationDetails.length > 0) {
      expandedIterations.value = [iterationDetails[0].key]
    }
  } catch (err) {
    handleError(err)
  } finally {
    iterationsLoading.value = false
  }
}

onMounted(() => {
  const id = route.params.id as string
  if (id) {
    load(id)
  }
})

const title = computed(() => form.value.name || t('releaseWindow.details'))

const totalRepoCount = computed(() => {
  return iterations.value.reduce((sum, iter) => sum + (iter.repos?.length || 0), 0)
})

const goBack = () => {
  router.back()
}

const goAttach = () => {
  if (!form.value?.id) return
  if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  attachDialogRef.value?.open(form.value.id)
}

const handleAttachSuccess = () => {
  const id = route.params.id as string
  if (id) {
    loadIterations(id)
  }
}

const openCodeMerge = () => {
  if (!form.value?.id || iterations.value.length === 0) return
  codeMergeDialogRef.value?.open(
    form.value.id,
    iterations.value.map(iter => ({ key: iter.key, name: iter.name }))
  )
}

const openVersionUpdate = () => {
  if (!form.value?.id) return
  versionUpdateDialogRef.value?.open(form.value.id, iterations.value.flatMap(iter => iter.repos || []))
}

const handleRefresh = () => {
  const id = route.params.id as string
  if (id) {
    load(id)
  }
}

const handleFreeze = async () => {
  if (!form.value?.id) return
  if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  try {
    await ElMessageBox.confirm(t('releaseWindow.confirmFreeze'), t('common.warning'), { type: 'warning' })
    await releaseWindowApi.freeze(form.value.id)
    ElMessage.success(t('common.success'))
    load(form.value.id)
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  }
}

const handleUnfreeze = async () => {
  if (!form.value?.id) return
  if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  try {
    await releaseWindowApi.unfreeze(form.value.id)
    ElMessage.success(t('common.success'))
    load(form.value.id)
  } catch (error) {
    handleError(error)
  }
}

const handlePublish = async () => {
  if (!form.value?.id) return
  if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  try {
    await ElMessageBox.confirm(t('releaseWindow.confirmPublish'), t('common.warning'), { type: 'warning' })
    await releaseWindowApi.publish(form.value.id)
    ElMessage.success(t('common.success'))
    load(form.value.id)
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  }
}

const handleClose = async () => {
  if (!form.value?.id) return
  if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  try {
    await ElMessageBox.confirm(t('releaseWindow.confirmClose'), t('common.warning'), { type: 'warning' })
    await releaseWindowApi.close(form.value.id)
    ElMessage.success(t('common.success'))
    load(form.value.id)
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  }
}

</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header .title {
  font-weight: 600;
}

.empty-tip {
  color: #909399;
  text-align: center;
  padding: 20px 0;
}

.iterations-list {
  margin-top: 8px;
}

.iteration-header {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.iteration-name {
  font-weight: 500;
}

.iteration-meta {
  color: #909399;
  font-size: 13px;
}

.repo-count {
  margin-left: auto;
  color: #606266;
  font-size: 13px;
}

.iteration-content {
  padding: 12px 0;
}

.iteration-description {
  color: #606266;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.repos-section {
  margin-top: 8px;
}

.no-repos {
  color: #909399;
  font-size: 13px;
}

.url-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}
</style>
