<template>
  <div class="data-quality-review">
    <div class="page-title">
      <h2>{{ t('dataQuality.review.title') }}</h2>
      <el-tag type="warning">{{ t('dataQuality.review.executionBoundary') }}</el-tag>
    </div>

    <el-card shadow="never" class="import-panel">
      <el-form :inline="true" label-width="96px" class="review-form">
        <el-form-item :label="t('dataQuality.review.reviewer')">
          <el-input v-model="reviewer" class="reviewer-input" />
        </el-form-item>
        <el-form-item :label="t('dataQuality.review.sourceReport')">
          <el-input v-model="sourceReport" class="source-input" />
        </el-form-item>
        <el-form-item>
          <el-button @click="triggerFileImport">{{ t('dataQuality.review.importFile') }}</el-button>
          <el-button type="primary" @click="loadQueue">{{ t('dataQuality.review.loadQueue') }}</el-button>
        </el-form-item>
      </el-form>
      <input ref="fileInputRef" class="file-input" type="file" accept=".jsonl,.json" @change="onFileSelected" />
      <el-input
        v-model="rawJsonl"
        type="textarea"
        :rows="5"
        :placeholder="t('dataQuality.review.jsonlPlaceholder')"
      />
      <el-alert
        v-if="parseError"
        class="parse-error"
        type="error"
        :closable="false"
        :title="parseError"
      />
    </el-card>

    <el-card shadow="never" class="queue-panel">
      <template #header>
        <div class="panel-header">
          <span>{{ t('dataQuality.review.queueTitle') }}</span>
          <div class="panel-actions">
            <el-button size="small" @click="markAllPending">{{ t('dataQuality.review.markAllPending') }}</el-button>
            <el-button size="small" @click="markAllApproved">{{ t('dataQuality.review.markAllApproved') }}</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item :label="t('dataQuality.review.resourceType')">
          <el-select v-model="filters.resourceType" clearable class="filter-select">
            <el-option :label="t('common.all')" value="" />
            <el-option v-for="item in resourceTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dataQuality.review.riskType')">
          <el-select v-model="filters.riskType" clearable class="filter-select">
            <el-option :label="t('common.all')" value="" />
            <el-option v-for="item in riskTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dataQuality.review.reviewStatus')">
          <el-select v-model="filters.reviewStatus" clearable class="filter-select">
            <el-option :label="t('common.all')" value="" />
            <el-option label="ACCEPTED" value="ACCEPTED" />
            <el-option label="PENDING" value="PENDING" />
            <el-option label="REJECTED" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dataQuality.review.assetScope')">
          <el-select v-model="filters.assetScope" clearable class="filter-select">
            <el-option :label="t('common.all')" value="" />
            <el-option v-for="item in assetScopeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="reviewing" :disabled="queueActions.length === 0" @click="reviewQueue">
            {{ t('dataQuality.review.reviewQueue') }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="summary-strip">
        <el-statistic :title="t('dataQuality.review.imported')" :value="queueActions.length" />
        <el-statistic :title="t('dataQuality.review.accepted')" :value="reviewResult?.accepted ?? 0" />
        <el-statistic :title="t('dataQuality.review.pending')" :value="reviewResult?.pending ?? 0" />
        <el-statistic :title="t('dataQuality.review.rejected')" :value="reviewResult?.rejected ?? 0" />
      </div>

      <el-table :data="displayRows" class="review-table" border>
        <el-table-column prop="resourceType" :label="t('dataQuality.review.resourceType')" width="160" />
        <el-table-column prop="resourceId" :label="t('dataQuality.review.resourceId')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="riskType" :label="t('dataQuality.review.riskType')" width="220" />
        <el-table-column prop="dataNamespace" :label="t('dataQuality.review.dataNamespace')" width="160" />
        <el-table-column prop="reviewBatchId" :label="t('dataQuality.review.reviewBatchId')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="assetScope" :label="t('dataQuality.review.assetScope')" width="210" />
        <el-table-column prop="retentionPolicy" :label="t('dataQuality.review.retentionPolicy')" min-width="220" show-overflow-tooltip />
        <el-table-column :label="t('dataQuality.review.decision')" width="240">
          <template #default="{ row }">
            <el-select
              v-if="isQueueAction(row)"
              v-model="row.reviewDecision"
              class="decision-select"
            >
              <el-option :label="t('dataQuality.review.pending')" value="PENDING" />
              <el-option :label="t('dataQuality.review.approve')" value="APPROVE_FOR_APPLICATION_ENTRY" />
            </el-select>
            <el-tag v-else :type="statusTagType(row.reviewStatus)">
              {{ row.reviewStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('dataQuality.review.applicationEntry')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.applicationEntry }}</template>
        </el-table-column>
        <el-table-column :label="t('dataQuality.review.reason')" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || row.suggestedAction }}</template>
        </el-table-column>
        <el-table-column :label="t('dataQuality.review.executionPermitted')" width="130">
          <template #default="{ row }">
            <el-tag :type="row.executionPermitted ? 'danger' : 'info'">
              {{ row.executionPermitted ? t('common.yes') : t('common.no') }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { dataQualityApi, type CleanupActionInput, type CleanupActionReview, type CleanupReviewResult } from '@/api/dataQualityApi'
import { handleError } from '@/utils/error'

type DisplayRow = CleanupActionInput | CleanupActionReview

const { t } = useI18n()

const reviewer = ref('release-manager')
const sourceReport = ref('.ai/reports/sa002-safe-cleanup/20260523-aligned-baseline/actions.jsonl')
const rawJsonl = ref('')
const parseError = ref('')
const queueActions = ref<CleanupActionInput[]>([])
const reviewResult = ref<CleanupReviewResult | null>(null)
const reviewing = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const filters = ref({
  resourceType: '',
  riskType: '',
  reviewStatus: '',
  assetScope: ''
})

const resourceTypeOptions = computed(() => unique(queueActions.value.map(item => item.resourceType)))
const riskTypeOptions = computed(() => unique(queueActions.value.map(item => item.riskType)))
const assetScopeOptions = computed(() => unique(queueActions.value.map(item => item.assetScope)))
const displayRows = computed<DisplayRow[]>(() => reviewResult.value?.actions ?? queueActions.value)

function unique(values: Array<string | undefined>) {
  return Array.from(new Set(values.filter(Boolean) as string[])).sort()
}

function loadQueue() {
  parseError.value = ''
  reviewResult.value = null
  try {
    const parsed = parseJsonl(rawJsonl.value)
    queueActions.value = parsed.map(action => ({
      ...action,
      executed: Boolean(action.executed),
      reviewDecision: action.reviewDecision || 'PENDING'
    }))
    ElMessage.success(t('dataQuality.review.queueLoaded', { count: queueActions.value.length }))
  } catch (error) {
    parseError.value = error instanceof Error ? error.message : t('dataQuality.review.parseFailed')
  }
}

function parseJsonl(input: string): CleanupActionInput[] {
  const lines = input.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
  if (!lines.length) {
    throw new Error(t('dataQuality.review.emptyInput'))
  }
  return lines.map((line, index) => {
    const value = JSON.parse(line) as CleanupActionInput
    if (!value.resourceType || !value.resourceId || !value.riskType) {
      throw new Error(t('dataQuality.review.invalidLine', { line: index + 1 }))
    }
    return value
  })
}

function markAllPending() {
  queueActions.value = queueActions.value.map(action => ({ ...action, reviewDecision: 'PENDING' }))
}

function markAllApproved() {
  queueActions.value = queueActions.value.map(action => ({ ...action, reviewDecision: 'APPROVE_FOR_APPLICATION_ENTRY' }))
}

async function reviewQueue() {
  if (!queueActions.value.length) {
    parseError.value = t('dataQuality.review.emptyInput')
    return
  }
  reviewing.value = true
  try {
    reviewResult.value = await dataQualityApi.reviewCleanupActions({
      reviewer: reviewer.value,
      sourceReport: sourceReport.value,
      resourceTypeFilter: filters.value.resourceType || undefined,
      riskTypeFilter: filters.value.riskType || undefined,
      reviewStatusFilter: filters.value.reviewStatus || undefined,
      assetScopeFilter: filters.value.assetScope || undefined,
      actions: queueActions.value
    })
    ElMessage.success(t('dataQuality.review.reviewComplete', { count: reviewResult.value.total }))
  } catch (error) {
    handleError(error)
  } finally {
    reviewing.value = false
  }
}

function triggerFileImport() {
  fileInputRef.value?.click()
}

function onFileSelected(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    rawJsonl.value = String(reader.result || '')
    loadQueue()
  }
  reader.readAsText(file)
}

function isQueueAction(row: DisplayRow): row is CleanupActionInput {
  return !('reviewStatus' in row)
}

function statusTagType(status?: string) {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

defineExpose({
  parseJsonl,
  loadQueue,
  markAllApproved,
  reviewQueue,
  queueActions,
  reviewResult,
  filters,
  rawJsonl,
  reviewer,
  sourceReport
})
</script>

<style scoped>
.data-quality-review {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-title,
.panel-header,
.panel-actions,
.summary-strip {
  display: flex;
  align-items: center;
}

.page-title,
.panel-header {
  justify-content: space-between;
}

.page-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.review-form,
.filter-form {
  row-gap: 8px;
}

.reviewer-input {
  width: 180px;
}

.source-input {
  width: 460px;
}

.filter-select {
  width: 220px;
}

.decision-select {
  width: 210px;
}

.file-input {
  display: none;
}

.parse-error {
  margin-top: 12px;
}

.panel-actions {
  gap: 8px;
}

.summary-strip {
  gap: 40px;
  padding: 12px 0 18px;
}

.review-table {
  width: 100%;
}
</style>
