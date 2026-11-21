<template>
  <div class="page-container">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">{{ t('common.back') }}</el-button>
      <span class="page-title">{{ t('run.detail.title') }}: {{ runId }}</span>
      <div class="page-actions">
        <el-button type="primary" @click="handleExport">{{ t('run.detail.exportJson') }}</el-button>
      </div>
    </div>
    <el-card v-loading="loading">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('run.columns.type')">{{ detail ? t(runTypeKey(detail.runType)) : '' }}</el-descriptions-item>
        <el-descriptions-item :label="t('run.columns.status')">{{ detail?.status }}</el-descriptions-item>
        <el-descriptions-item :label="t('run.columns.start')">{{ detail?.startedAt }}</el-descriptions-item>
        <el-descriptions-item :label="t('run.columns.end')">{{ detail?.finishedAt }}</el-descriptions-item>
        <el-descriptions-item :label="t('run.columns.operator')">{{ detail?.operator }}</el-descriptions-item>
      </el-descriptions>
      <div class="run-summary">
        <div class="summary-item">
          <span class="summary-label">{{ t('run.detail.totalItems') }}</span>
          <strong>{{ itemSummary.total }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('run.detail.successItems') }}</span>
          <strong class="summary-success">{{ itemSummary.succeeded }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('run.detail.failedItems') }}</span>
          <strong class="summary-danger">{{ itemSummary.failed }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('run.detail.retryableItems') }}</span>
          <strong>{{ itemSummary.retryable }}</strong>
        </div>
      </div>
      <el-alert
        v-if="hasPartialFailure"
        class="partial-failure-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="t('run.detail.partialFailure', { success: itemSummary.succeeded, failed: itemSummary.failed })"
      />
    </el-card>

    <el-alert
      v-if="detail?.status === 'NEEDS_REVIEW'"
      class="partial-failure-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="t('run.detail.needsReview')"
    />
    <el-alert
      v-else-if="detail?.runType === 'WINDOW_CLEANUP' && detail.status !== 'DONE'"
      class="partial-failure-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="t('run.detail.cleanupReview')"
    />
    <GitOperationsPanel v-if="detail && detail.status !== 'PLANNED'" :run-id="runId" />
    <el-card v-if="previewPlan" style="margin-top: 16px;">
      <RunPlanSnapshotView :plan="previewPlan" />
    </el-card>

    <!-- 持久任务只展示事实；失败项通过执行项选择重试。 -->
    <el-card v-if="detail && detail.status !== 'PLANNED'" v-loading="tasksLoading" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span>{{ t('run.detail.tasksTitle') }}</span>
          <span class="task-summary">{{ t('run.detail.taskSummary', taskSummary) }}</span>
          <el-button size="small" @click="fetchTasks">{{ t('common.refresh') }}</el-button>
        </div>
      </template>
      <el-table :data="tasks" row-key="taskId" style="width: 100%">
        <el-table-column prop="taskOrder" :label="t('run.task.order')" width="80" />
        <el-table-column prop="actionType" :label="t('run.task.type')" width="200">
          <template #default="{ row }">
            <span class="task-type">{{ formatTaskType(row.actionType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="itemId" :label="t('run.task.itemId')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('run.columns.status')" width="150">
          <template #default="{ row }">
            <el-tag :type="getTaskStatusType(row.status)" size="small">{{ formatTaskStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('run.task.source')" min-width="220">
          <template #default="{ row }">
            <template v-if="row.status === 'INHERITED'">
              <span>{{ t('run.task.inheritedFact') }}</span>
              <el-button
                v-if="inheritedSource(row)" link type="primary"
                @click="router.push({ name: 'RunDetail', params: { runId: inheritedSource(row)!.runId } })">
                {{ inheritedSource(row)!.runId }} / {{ inheritedSource(row)!.itemId }}
              </el-button>
              <span v-else>{{ t('run.detail.notRecorded') }}</span>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('run.columns.start')" min-width="180">
          <template #default="{ row }">{{ row.startedAt ?? '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('run.columns.end')" min-width="180">
          <template #default="{ row }">{{ row.finishedAt ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" :label="t('run.task.error')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-message">{{ row.errorMessage }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 执行项列表（原有） -->
    <el-card v-if="detail?.status !== 'PLANNED'" v-loading="loading" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span>{{ t('run.detail.triplesTitle') }}</span>
          <el-button
            v-if="failedItemKeys.length > 0"
            v-perm.disable="'run:write'"
            size="small"
            type="warning"
            :loading="retryingItems"
            :disabled="selectedRetryItemKeys.length === 0 || missingRetryPredecessors.length > 0"
            @click="handleRetrySelectedItems"
          >
            {{ t('run.retrySelectedItems') }}
          </el-button>
        </div>
      </template>
      <el-alert
        v-if="showRetryFailFastHint"
        class="partial-failure-alert"
        type="info"
        :closable="false"
        :title="t('run.detail.retryFailFastHint')"
      />
      <el-alert
        v-if="missingRetryPredecessors.length > 0"
        class="partial-failure-alert"
        type="warning"
        :closable="false"
        :title="missingRetryPredecessorsMessage"
      />
      <el-table
        data-test="retry-items"
        :data="detail?.items || []"
        row-key="id"
        default-expand-all
        style="width: 100%"
        @selection-change="handleRetrySelectionChange"
      >
        <el-table-column v-if="failedItemKeys.length > 0" type="selection" width="48" :selectable="isSelectableRetryItem" />
        <el-table-column prop="repoId" :label="t('run.filters.repo')" />
        <el-table-column prop="windowKey" :label="t('run.filters.windowKey')" />
        <el-table-column :label="t('run.detail.itemScope')" min-width="220">
          <template #default="{ row }"><RunItemScope :item="row" :run-type="detail?.runType" /></template>
        </el-table-column>
        <el-table-column prop="plannedOrder" :label="t('run.columns.plannedOrder')" width="95" />
        <el-table-column prop="executedOrder" :label="t('run.columns.executedOrder')" width="95" />
        <el-table-column prop="finalResult" :label="t('run.columns.status')" />
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 10px">
              <EvidenceChainPanel :evidence="runItemEvidence(row)" />
              <MRFirstTimeline v-if="detail?.runType === 'WINDOW_ORCHESTRATION' || detail?.runType === 'ATTACH_ITERATION'" :steps="row.steps" />
              <el-divider />
              <h4>{{ t('run.steps') }}</h4>
              <el-timeline>
                <el-timeline-item
                  v-for="(step, index) in row.steps"
                  :key="index"
                  :timestamp="step.startedAt"
                  :type="getStepType(step.result)"
                >
                  <div>
                    <div class="step-header">
                      <strong>{{ step.actionType }}</strong>: 
                      <el-tag :type="getResultTagType(step.result)" size="small">{{ step.result }}</el-tag>
                    </div>
                    <div v-if="step.message" class="step-message">
                      <div v-if="step.actionType === 'UPDATE_VERSION' && extractDiff(step.message)">
                        <div class="version-info">{{ extractVersionInfo(step.message) }}</div>
                        <DiffViewer :diff="extractDiff(step.message) ?? undefined" />
                      </div>
                      <div v-else>{{ step.message }}</div>
                    </div>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import GitOperationsPanel from '@/components/run/GitOperationsPanel.vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { runApi, type RunDetail, type RunItem, type RunTask } from '@/api/runApi'
import MRFirstTimeline from '@/components/run/MRFirstTimeline.vue'
import EvidenceChainPanel from '@/components/common/EvidenceChainPanel.vue'
import DiffViewer from '@/components/run/DiffViewer.vue'
import { handleError } from '@/utils/error'
import { hasPerm } from '@/utils/perm'
import { useUserStore } from '@/stores/user'
import { readExecutionIntent, saveExecutionIntent, acknowledgeExecutionIntent } from '@/utils/executionIntent'
import { runPlanApi, type RunPlanPreview } from '@/api/runPlanApi'
import RunPlanSnapshotView from '@/components/run/RunPlanSnapshotView.vue'
import RunItemScope from '@/components/run/RunItemScope.vue'
import { isFailedResult, isSuccessfulResult, isSkippedResult, isRetryableResult, isRetryableRun, resultTagType, runTypeKey, runItemEvidence } from '@/utils/runPresentation'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const currentRunId = computed(() => typeof route.params.runId === 'string' ? route.params.runId : '')
let viewGeneration = 0
let disposed = false
const { t } = useI18n()
const loading = ref(false)
const tasksLoading = ref(false)
const detail = ref<RunDetail>()
const previewPlan = ref<RunPlanPreview>()
const tasks = ref<RunTask[]>([])
const retryingItems = ref(false)
const selectedRetryItemIds = ref<string[]>([])

const runId = computed(() => currentRunId.value)

const failedItemKeys = computed(() => {
  if (!isRetryableRun(detail.value?.status, detail.value?.runType, detail.value?.executionVersion)) return []
  return (detail.value?.items || [])
    .filter(isRetryableItem)
    .map(item => item.id)
})

const selectedRetryItemKeys = computed(() =>
  failedItemKeys.value.filter(id => selectedRetryItemIds.value.includes(id))
)
const showRetryFailFastHint = computed(() =>
  failedItemKeys.value.length > 0 && detail.value?.items.some(item =>
    item.finalResult === 'SKIPPED_DUE_TO_BLOCK' && item.metadata['plan.failFast'] === 'true')
)
const missingRetryPredecessors = computed(() => {
  const items = detail.value?.items || []
  const selectedSkipped = items.filter(item => selectedRetryItemKeys.value.includes(item.id)
    && item.finalResult === 'SKIPPED_DUE_TO_BLOCK' && item.metadata['plan.failFast'] === 'true')
  return items.filter(item =>
    (item.finalResult === 'FAILED' || item.finalResult === 'MERGE_BLOCKED')
    && !selectedRetryItemKeys.value.includes(item.id)
    && selectedSkipped.some(skipped => skipped.repoId === item.repoId && item.plannedOrder < skipped.plannedOrder)
  )
})
const missingRetryPredecessorsMessage = computed(() => t('run.detail.retryMissingPredecessors', {
  items: missingRetryPredecessors.value.map(item => `#${item.plannedOrder} ${item.iterationKey}`).join(', ')
}))

function isSelectableRetryItem(item: RunItem): boolean {
  return isRetryableRun(detail.value?.status, detail.value?.runType, detail.value?.executionVersion) && isRetryableItem(item)
}
function handleRetrySelectionChange(items: RunItem[]) {
  selectedRetryItemIds.value = items.filter(isSelectableRetryItem).map(item => item.id)
}

const itemSummary = computed(() => {
  const items = detail.value?.items || []
  const succeeded = items.filter(item => isSuccessfulResult(item.finalResult)).length
  const failed = items.filter(item => isFailedResult(item.finalResult)).length
  const skipped = items.filter(item => isSkippedResult(item.finalResult)).length
  return {
    total: items.length,
    succeeded,
    failed,
    skipped,
    retryable: failedItemKeys.value.length
  }
})

const taskSummary = computed(() => {
  return {
    total: tasks.value.length,
    completed: tasks.value.filter(task => task.status === 'COMPLETED').length,
    failed: tasks.value.filter(task => task.status === 'FAILED').length,
    inherited: tasks.value.filter(task => task.status === 'INHERITED').length,
    unknown: tasks.value.filter(task => task.status === 'UNKNOWN').length
  }
})

const hasPartialFailure = computed(() => detail.value?.status === 'PARTIAL_FAILED')

const goBack = () => {
  router.push({ name: 'Runs' })
}

function isCurrentView(id: string, generation: number): boolean {
  return !disposed && currentRunId.value === id && viewGeneration === generation
}

async function loadPlan(id: string, generation: number) {
  try {
    const plan = await runPlanApi.get(id)
    if (isCurrentView(id, generation)) previewPlan.value = plan
  } catch (error) {
    if (isCurrentView(id, generation)) handleError(error)
  }
}

async function loadTasks(id: string, generation: number, status: RunDetail['status']) {
  if (status === 'PLANNED') return
  if (isCurrentView(id, generation)) tasksLoading.value = true
  try {
    const result = await runApi.getTasks(id)
    if (isCurrentView(id, generation)) tasks.value = result
  } catch (error) {
    if (isCurrentView(id, generation)) handleError(error)
  } finally {
    if (isCurrentView(id, generation)) tasksLoading.value = false
  }
}

async function loadRun(id: string) {
  const generation = ++viewGeneration
  detail.value = undefined
  previewPlan.value = undefined
  tasks.value = []
  selectedRetryItemIds.value = []
  retryingItems.value = false
  tasksLoading.value = false
  loading.value = Boolean(id)
  if (!id) return
  try {
    const result = await runApi.getRunById(id)
    if (!isCurrentView(id, generation)) return
    detail.value = result
    const queries = [loadTasks(id, generation, result.status)]
    if (result.executionVersion >= 2
      && (result.runType === 'WINDOW_ORCHESTRATION' || result.runType === 'WINDOW_CLEANUP')) {
      queries.push(loadPlan(id, generation))
    }
    await Promise.all(queries)
  } catch (error) {
    if (isCurrentView(id, generation)) handleError(error)
  } finally {
    if (isCurrentView(id, generation)) loading.value = false
  }
}

async function fetchTasks() {
  if (!detail.value || tasksLoading.value) return
  await loadTasks(currentRunId.value, viewGeneration, detail.value.status)
}

async function handleRetrySelectedItems() {
  if (disposed || retryingItems.value || !currentRunId.value) return
  const subject = user.profile?.username
  if (!subject || !hasPerm('run:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  if (selectedRetryItemKeys.value.length === 0) {
    ElMessage.info(t('run.selectRetryItems'))
    return
  }
  if (missingRetryPredecessors.value.length > 0) {
    ElMessage.warning(missingRetryPredecessorsMessage.value)
    return
  }
  const sourceRunId = currentRunId.value
  const generation = viewGeneration
  const items = [...selectedRetryItemKeys.value].sort()
  const payload = JSON.stringify({ items })
  const operation = `RETRY:${sourceRunId}`
  retryingItems.value = true
  try {
    const pending = readExecutionIntent(subject, operation)
    const message = pending
      ? t(pending.payload === payload ? 'run.request.repeatUnconfirmed' : 'run.request.replaceUnconfirmed')
      : t('run.retrySelectionConfirm', { count: items.length })
    await ElMessageBox.confirm(message, t('common.warning'), { type: 'warning' })
    if (!isCurrentView(sourceRunId, generation)) return
    if (user.profile?.username !== subject || !hasPerm('run:write') || missingRetryPredecessors.value.length > 0
      || JSON.stringify({ items: [...selectedRetryItemKeys.value].sort() }) !== payload) {
      ElMessage.warning(t('run.request.changed'))
      return
    }
    const intent = saveExecutionIntent(subject, operation, payload)
    const retryRunId = await runApi.retry(sourceRunId, items, intent.key)
    if (typeof retryRunId !== 'string' || !retryRunId.trim()) throw new Error(t('run.request.missingReceipt'))
    acknowledgeExecutionIntent(subject, operation, intent.key)
    if (!isCurrentView(sourceRunId, generation) || user.profile?.username !== subject) return
    ElMessage.success(t('run.retrySuccess'))
    await router.push({ name: 'RunDetail', params: { runId: retryRunId } })
  } catch (err) {
    if (isCurrentView(sourceRunId, generation) && err !== 'cancel' && err !== 'close') handleError(err)
  } finally {
    if (isCurrentView(sourceRunId, generation)) retryingItems.value = false
  }
}

function inheritedSource(task: RunTask): { runId: string; itemId: string } | null {
  if (task.status !== 'INHERITED') return null
  const metadata = detail.value?.items.find(item => item.id === task.itemId)?.metadata
  const sourceRunId = metadata?.['retry.sourceRunId']
  const sourceItemId = metadata?.['retry.sourceItemId']
  return sourceRunId && sourceItemId ? { runId: sourceRunId, itemId: sourceItemId } : null
}

function isRetryableItem(item: RunItem): boolean {
  return isRetryableResult(item.finalResult)
}


function getTaskStatusType(status: string): string {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': case 'UNKNOWN': return 'warning'
    case 'SKIPPED': case 'INHERITED': return 'info'
    default: return ''
  }
}

function formatTaskStatus(status: string): string {
  return t(`run.task.status.${status}`) || status
}

function formatTaskType(taskType: string): string {
  return t(`run.task.types.${taskType}`) || taskType
}

function handleExport() {
  window.open(`/api/v1/runs/${currentRunId.value}/export.json`, '_blank')
}

const getStepType = resultTagType
const getResultTagType = resultTagType
function extractVersionInfo(message: string): string {
  // 提取版本更新信息（第一行）
  const lines = message.split('\n')
  return lines[0] || message
}

function extractDiff(message: string): string | null {
  // 提取 diff 部分（支持多种格式）
  // 格式1: "--- Diff ---\n..."
  // 格式2: "Diff preview:\n..."
  // 格式3: 包含 "@@ " 的 unified diff
  
  let diffIndex = message.indexOf('--- Diff ---')
  if (diffIndex !== -1) {
    return message.substring(diffIndex + '--- Diff ---\n'.length).trim()
  }
  
  diffIndex = message.indexOf('Diff preview:')
  if (diffIndex !== -1) {
    return message.substring(diffIndex + 'Diff preview:\n'.length).trim()
  }
  
  // 如果消息中直接包含 unified diff 格式
  diffIndex = message.indexOf('@@ ')
  if (diffIndex !== -1) {
    return message.substring(diffIndex).trim()
  }
  
  return null
}

watch(currentRunId, loadRun, { immediate: true })
onUnmounted(() => {
  disposed = true
  viewGeneration++
})
</script>

<style scoped>
/* 页面特定样式 - 通用样式已移至 index.css */
.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.step-message {
  margin-top: 8px;
  color: #606266;
}
.version-info {
  margin-bottom: 8px;
  font-weight: 500;
}
.task-type {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 12px;
}
.error-message {
  color: var(--el-color-danger);
  font-size: 12px;
}
.run-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-top: 12px;
}
.summary-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
}
.summary-label,
.task-summary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.summary-success {
  color: var(--el-color-success);
}
.summary-danger {
  color: var(--el-color-danger);
}
.partial-failure-alert {
  margin-top: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.card-header .task-summary {
  flex: 1;
}
</style>
