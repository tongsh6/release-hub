<template>
  <el-card class="orchestration-panel">
    <template #header>
      <div class="panel-header">
        <span class="title">{{ t('orchestration.title') }}</span>
        <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
      </div>
    </template>

    <!-- 编排流程步骤 -->
    <div class="orchestration-steps">
      <el-steps :active="activeStep" finish-status="success" align-center>
        <el-step :title="t('orchestration.steps.prepare')" :icon="Document" />
        <el-step :title="t('orchestration.steps.merge')" :icon="Connection" />
        <el-step :title="t('orchestration.steps.version')" :icon="Edit" />
        <el-step :title="t('orchestration.steps.finish')" :icon="CircleCheck" />
      </el-steps>
    </div>

    <!-- 操作面板 -->
    <div class="action-panels">
      <!-- 准备阶段 -->
      <div class="action-panel" :class="{ active: activeStep === 0, completed: activeStep > 0 }">
        <div class="panel-title">
          <el-icon><Document /></el-icon>
          <span>{{ t('orchestration.steps.prepare') }}</span>
        </div>
        <div class="panel-content">
          <div class="info-row">
            <span class="label">{{ t('orchestration.iterationCount') }}:</span>
            <span class="value">{{ iterationCount }}</span>
          </div>
          <div class="info-row">
            <span class="label">{{ t('orchestration.repoCount') }}:</span>
            <span class="value">{{ repoCount }}</span>
          </div>
          <div class="panel-actions">
            <el-button
              v-if="canPreview"
              size="small"
              @click="handlePreviewPlan"
            >
              {{ t('orchestration.previewPlan') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 代码合并阶段 -->
      <div class="action-panel" :class="{ active: activeStep === 1, completed: activeStep > 1 }">
        <div class="panel-title">
          <el-icon><Connection /></el-icon>
          <span>{{ t('orchestration.steps.merge') }}</span>
        </div>
        <div class="panel-content">
          <div class="info-row">
            <span class="label">{{ t('orchestration.mergeDirection') }}:</span>
            <span class="value">feature → release</span>
          </div>
          <div class="panel-actions">
            <el-button
              v-if="canMerge"
              v-perm.disable="'release-window:write'"
              type="primary"
              size="small"
              :loading="merging"
              @click="handleMergeAll"
            >
              {{ t('orchestration.mergeAll') }}
            </el-button>
          </div>
          <div v-if="mergeResults.length > 0" class="merge-results">
            <div v-for="result in mergeResults" :key="result.repoId" class="merge-result-item">
              <el-icon :class="getMergeStatusClass(result.status)">
                <component :is="getMergeStatusIcon(result.status)" />
              </el-icon>
              <span class="repo-name">{{ result.repoName }}</span>
              <el-tag :type="getMergeTagType(result.status)" size="small">
                {{ t(`releaseWindow.codeMerge.status.${result.status}`) }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 版本更新阶段 -->
      <div class="action-panel" :class="{ active: activeStep === 2, completed: activeStep > 2 }">
        <div class="panel-title">
          <el-icon><Edit /></el-icon>
          <span>{{ t('orchestration.steps.version') }}</span>
        </div>
        <div class="panel-content">
          <div class="info-row">
            <span class="label">{{ t('orchestration.versionAction') }}:</span>
            <span class="value">{{ t('orchestration.updatePomGradle') }}</span>
          </div>
          <div class="panel-actions">
            <el-button
              v-if="canUpdateVersion"
              v-perm.disable="'release-window:write'"
              type="primary"
              size="small"
              @click="handleVersionUpdate"
            >
              {{ t('releaseWindow.versionUpdate.execute') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 收尾阶段 -->
      <div class="action-panel" :class="{ active: activeStep === 3, completed: activeStep > 3 }">
        <div class="panel-title">
          <el-icon><CircleCheck /></el-icon>
          <span>{{ t('orchestration.steps.finish') }}</span>
        </div>
        <div class="panel-content">
          <div class="info-row">
            <span class="label">{{ t('orchestration.finishActions') }}:</span>
          </div>
          <ul class="finish-action-list">
            <li>{{ t('orchestration.finishAction.mergeToMaster') }}</li>
            <li>{{ t('orchestration.finishAction.createTag') }}</li>
            <li>{{ t('orchestration.finishAction.archiveBranch') }}</li>
          </ul>
          <div class="panel-actions">
            <el-button
              v-if="canOrchestrate"
              v-perm.disable="'release-window:write'"
              type="success"
              size="small"
              :loading="orchestrating"
              @click="handleOrchestrate"
            >
              {{ t('orchestration.executeFinish') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 最近执行记录 -->
    <div v-if="recentRuns.length > 0" class="recent-runs">
      <div class="section-title">{{ t('orchestration.recentRuns') }}</div>
      <el-table :data="recentRuns" size="small" border>
        <el-table-column prop="id" :label="t('run.columns.runId')" width="100" />
        <el-table-column prop="runType" :label="t('run.columns.type')" width="200" />
        <el-table-column prop="status" :label="t('run.columns.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="getRunStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" :label="t('run.columns.start')" width="160" />
        <el-table-column :label="t('common.actions')" width="100">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewRunDetail(row)">
              {{ t('common.detail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 执行计划预览对话框 -->
    <el-dialog v-model="planDialogVisible" :title="t('orchestration.planPreview')" width="600px">
      <div v-loading="planLoading" class="plan-content">
        <div v-if="planData" class="plan-items">
          <div v-for="(item, index) in planData.items" :key="index" class="plan-item">
            <div class="plan-item-header">
              <span class="item-order">{{ index + 1 }}</span>
              <span class="item-repo">{{ item.repo }}</span>
              <span class="item-iteration">{{ item.iterationKey }}</span>
            </div>
            <div class="plan-item-steps">
              <el-tag v-for="step in item.steps" :key="step" size="small" class="step-tag">
                {{ step }}
              </el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else :description="t('common.noData')" />
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Document, Connection, Edit, CircleCheck, SuccessFilled, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { releaseWindowApi, type CodeMergeResult } from '@/api/modules/releaseWindow'
import { runApi, type Run } from '@/api/runApi'
import { handleError } from '@/utils/error'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  windowId: string
  windowStatus: string
  iterationCount: number
  repoCount: number
}>()

const emit = defineEmits<{
  (e: 'openVersionUpdate'): void
  (e: 'refresh'): void
}>()

const { t } = useI18n()
const router = useRouter()

// 状态
const merging = ref(false)
const orchestrating = ref(false)
const planLoading = ref(false)
const planDialogVisible = ref(false)
const mergeResults = ref<CodeMergeResult[]>([])
const recentRuns = ref<Run[]>([])
const planData = ref<any>(null)

// 计算属性
const activeStep = computed(() => {
  if (props.windowStatus === 'CLOSED') return 4
  if (props.windowStatus === 'PUBLISHED') return 3
  if (mergeResults.value.length > 0) return 2
  if (props.iterationCount > 0) return 1
  return 0
})

const statusText = computed(() => {
  if (props.windowStatus === 'CLOSED') return t('orchestration.status.completed')
  if (props.windowStatus === 'PUBLISHED') return t('orchestration.status.finishing')
  if (props.windowStatus === 'DRAFT') return t('orchestration.status.preparing')
  return t('orchestration.status.unknown')
})

const statusTagType = computed(() => {
  if (props.windowStatus === 'CLOSED') return 'success'
  if (props.windowStatus === 'PUBLISHED') return 'warning'
  return 'info'
})

const canPreview = computed(() => props.iterationCount > 0)
const canMerge = computed(() => props.windowStatus === 'DRAFT' && props.iterationCount > 0)
const canUpdateVersion = computed(() => props.windowStatus === 'DRAFT' || props.windowStatus === 'PUBLISHED')
const canOrchestrate = computed(() => props.windowStatus === 'PUBLISHED')

// 方法
async function handlePreviewPlan() {
  planDialogVisible.value = true
  planLoading.value = true
  try {
    planData.value = await releaseWindowApi.getDryPlan(props.windowId)
  } catch (e) {
    handleError(e)
  } finally {
    planLoading.value = false
  }
}

async function handleMergeAll() {
  try {
    await ElMessageBox.confirm(
      t('orchestration.confirmMergeAll'),
      t('common.confirm'),
      { type: 'warning' }
    )
    
    merging.value = true
    const results = await releaseWindowApi.mergeAll(props.windowId)
    mergeResults.value = results
    
    const hasConflict = results.some(r => r.status === 'CONFLICT')
    const hasFailed = results.some(r => r.status === 'FAILED')
    
    if (hasConflict) {
      ElMessage.warning(t('releaseWindow.codeMerge.hasConflict'))
    } else if (hasFailed) {
      ElMessage.error(t('releaseWindow.codeMerge.hasFailed'))
    } else {
      ElMessage.success(t('releaseWindow.codeMerge.allSuccess'))
    }
    
    loadRecentRuns()
  } catch (e) {
    if (e !== 'cancel') handleError(e)
  } finally {
    merging.value = false
  }
}

function handleVersionUpdate() {
  emit('openVersionUpdate')
}

async function handleOrchestrate() {
  try {
    await ElMessageBox.confirm(
      t('orchestration.confirmOrchestrate'),
      t('common.confirm'),
      { type: 'warning' }
    )
    
    orchestrating.value = true
    await releaseWindowApi.orchestrate(props.windowId)
    ElMessage.success(t('common.success'))
    
    loadRecentRuns()
    emit('refresh')
  } catch (e) {
    if (e !== 'cancel') handleError(e)
  } finally {
    orchestrating.value = false
  }
}

async function loadRecentRuns() {
  try {
    const result = await runApi.list({ page: 1, pageSize: 5, windowKey: props.windowId })
    recentRuns.value = result.list
  } catch (e) {
    // 静默失败
    console.warn('Failed to load recent runs:', e)
  }
}

function viewRunDetail(run: Run) {
  router.push(`/runs/${run.id}`)
}

function getMergeStatusClass(status: string) {
  if (status === 'SUCCESS') return 'status-success'
  if (status === 'CONFLICT') return 'status-warning'
  return 'status-error'
}

function getMergeStatusIcon(status: string) {
  if (status === 'SUCCESS') return SuccessFilled
  if (status === 'CONFLICT') return WarningFilled
  return CircleCloseFilled
}

function getMergeTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'CONFLICT') return 'warning'
  return 'danger'
}

function getRunStatusType(status: string) {
  if (status === 'SUCCESS' || status === 'COMPLETED') return 'success'
  if (status === 'RUNNING') return 'primary'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

onMounted(() => {
  loadRecentRuns()
})
</script>

<style scoped>
.orchestration-panel {
  margin-top: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header .title {
  font-weight: 600;
  font-size: 16px;
}

.orchestration-steps {
  padding: 20px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 20px;
}

.action-panels {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.action-panel {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  background-color: #fafafa;
  transition: all 0.3s;
}

.action-panel.active {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.action-panel.completed {
  border-color: #67c23a;
  background-color: #f0f9eb;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  margin-bottom: 12px;
  color: #303133;
}

.panel-content {
  font-size: 13px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.info-row .label {
  color: #909399;
}

.info-row .value {
  color: #303133;
  font-weight: 500;
}

.panel-actions {
  margin-top: 12px;
  text-align: center;
}

.finish-action-list {
  margin: 8px 0;
  padding-left: 20px;
  font-size: 12px;
  color: #606266;
}

.finish-action-list li {
  margin-bottom: 4px;
}

.merge-results {
  margin-top: 12px;
  max-height: 150px;
  overflow-y: auto;
}

.merge-result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
}

.merge-result-item:last-child {
  border-bottom: none;
}

.merge-result-item .repo-name {
  flex: 1;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-success {
  color: #67c23a;
}

.status-warning {
  color: #e6a23c;
}

.status-error {
  color: #f56c6c;
}

.recent-runs {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.section-title {
  font-weight: 500;
  margin-bottom: 12px;
  color: #303133;
}

.plan-content {
  min-height: 200px;
}

.plan-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.plan-item {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
}

.plan-item-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.item-order {
  width: 24px;
  height: 24px;
  background-color: #409eff;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.item-repo {
  font-weight: 500;
}

.item-iteration {
  color: #909399;
  font-size: 12px;
}

.plan-item-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.step-tag {
  font-size: 11px;
}

/* 响应式 */
@media (max-width: 1200px) {
  .action-panels {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .action-panels {
    grid-template-columns: 1fr;
  }
}
</style>
