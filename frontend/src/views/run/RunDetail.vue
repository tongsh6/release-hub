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
        <el-descriptions-item :label="t('run.columns.type')">{{ detail?.runType }}</el-descriptions-item>
        <el-descriptions-item :label="t('run.columns.status')">{{ detail?.status }}</el-descriptions-item>
        <el-descriptions-item :label="t('run.columns.start')">{{ detail?.startedAt }}</el-descriptions-item>
        <el-descriptions-item :label="t('run.columns.end')">{{ detail?.finishedAt }}</el-descriptions-item>
        <el-descriptions-item :label="t('run.columns.operator')">{{ detail?.operator }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 运行任务列表 -->
    <el-card v-loading="tasksLoading" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span>{{ t('run.detail.tasksTitle') }}</span>
          <el-button size="small" @click="fetchTasks">{{ t('common.refresh') }}</el-button>
        </div>
      </template>
      <el-table :data="tasks" style="width: 100%">
        <el-table-column prop="taskOrder" :label="t('run.task.order')" width="80" />
        <el-table-column prop="taskType" :label="t('run.task.type')" width="200">
          <template #default="{ row }">
            <span class="task-type">{{ formatTaskType(row.taskType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" :label="t('run.task.targetType')" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.targetType" size="small" type="info">{{ row.targetType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" :label="t('run.task.targetId')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('run.columns.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="getTaskStatusType(row.status)" size="small">
              {{ formatTaskStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" :label="t('run.task.retries')" width="100">
          <template #default="{ row }">
            {{ row.retryCount }} / {{ row.maxRetries }}
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" :label="t('run.task.error')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-message">{{ row.errorMessage }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button 
              v-if="row.status === 'FAILED' && row.retryCount < row.maxRetries"
              link 
              type="primary" 
              size="small" 
              :loading="retryingTasks[row.id]"
              @click="handleRetryTask(row.id)"
            >
              {{ t('run.task.retry') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 执行项列表（原有） -->
    <el-card v-loading="loading" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span>{{ t('run.detail.triplesTitle') }}</span>
        </div>
      </template>
      <el-table :data="detail?.items || []" style="width: 100%">
        <el-table-column prop="repo" :label="t('run.filters.repo')" />
        <el-table-column prop="windowKey" :label="t('run.filters.windowKey')" />
        <el-table-column prop="iterationKey" :label="t('run.filters.iterationKey')" />
        <el-table-column prop="finalResult" :label="t('run.columns.status')" />
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 10px">
              <MRFirstTimeline :steps="row.steps" />
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
                        <DiffViewer :diff="extractDiff(step.message)" />
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
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { runApi, type RunDetail, type RunTask } from '@/api/runApi'
import MRFirstTimeline from '@/components/run/MRFirstTimeline.vue'
import DiffViewer from '@/components/run/DiffViewer.vue'
import { handleError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const runId = route.params.runId as string
const { t } = useI18n()
const loading = ref(false)
const tasksLoading = ref(false)
const detail = ref<RunDetail>()
const tasks = ref<RunTask[]>([])
const retryingTasks = reactive<Record<string, boolean>>({})

const goBack = () => {
  router.push({ name: 'Runs' })
}

async function fetchDetail() {
  loading.value = true
  try {
    detail.value = await runApi.getRunById(runId)
  } finally {
    loading.value = false
  }
}

async function fetchTasks() {
  tasksLoading.value = true
  try {
    tasks.value = await runApi.getTasks(runId)
  } catch (err) {
    handleError(err)
  } finally {
    tasksLoading.value = false
  }
}

async function handleRetryTask(taskId: string) {
  retryingTasks[taskId] = true
  try {
    await runApi.retryTask(runId, taskId)
    ElMessage.success(t('common.success'))
    await fetchTasks()
  } catch (err) {
    handleError(err)
  } finally {
    retryingTasks[taskId] = false
  }
}

function getTaskStatusType(status: string): string {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'warning'
    case 'SKIPPED': return 'info'
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
  window.open(`/api/v1/runs/${runId}/export.json`, '_blank')
}

function getStepType(result: string): string {
  if (result === 'VERSION_UPDATE_SUCCESS' || result === 'SUCCESS') return 'success'
  if (result === 'VERSION_UPDATE_FAILED' || result === 'FAILED') return 'danger'
  return 'primary'
}

function getResultTagType(result: string): string {
  if (result === 'VERSION_UPDATE_SUCCESS' || result === 'SUCCESS') return 'success'
  if (result === 'VERSION_UPDATE_FAILED' || result === 'FAILED') return 'danger'
  return 'info'
}

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

onMounted(() => {
  fetchDetail()
  fetchTasks()
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
</style>
