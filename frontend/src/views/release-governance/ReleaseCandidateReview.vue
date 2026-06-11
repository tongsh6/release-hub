<template>
  <div v-loading="loading" class="release-candidate-review">
    <div class="page-title">
      <div>
        <h2>{{ summary?.candidateLabel || t('releaseGovernance.review.title') }}</h2>
        <p>{{ summary?.conclusion }}</p>
      </div>
      <el-tag type="warning">{{ t('releaseGovernance.review.boundary') }}</el-tag>
    </div>

    <el-alert
      v-if="summary"
      type="info"
      show-icon
      :closable="false"
      :title="summary.recommendation"
    />

    <el-row v-if="summary" :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>{{ t('releaseGovernance.review.evidence') }}</template>
          <el-table :data="summary.evidence" border>
            <el-table-column prop="label" :label="t('releaseGovernance.review.item')" width="180" />
            <el-table-column prop="status" :label="t('releaseGovernance.review.status')" width="150">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="result" :label="t('releaseGovernance.review.result')" min-width="220" />
            <el-table-column prop="sourcePath" :label="t('releaseGovernance.review.source')" min-width="260" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>{{ t('releaseGovernance.review.risks') }}</template>
          <div v-for="risk in summary.risks" :key="risk.title" class="risk-item">
            <div class="risk-header">
              <el-tag :type="riskType(risk.level)">{{ risk.level }}</el-tag>
              <strong>{{ risk.title }}</strong>
            </div>
            <div class="risk-status">{{ risk.status }}</div>
            <div class="source-path">{{ risk.sourcePath }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="summary" shadow="never">
      <template #header>
        <div class="panel-header">
          <span>{{ t('releaseGovernance.review.checklist') }}</span>
          <el-button size="small" @click="markAllConfirmed">{{ t('releaseGovernance.review.markAllConfirmed') }}</el-button>
        </div>
      </template>
      <el-table :data="checklistRows" border>
        <el-table-column prop="title" :label="t('releaseGovernance.review.checkItem')" min-width="320" />
        <el-table-column prop="sourcePath" :label="t('releaseGovernance.review.source')" min-width="260" show-overflow-tooltip />
        <el-table-column :label="t('releaseGovernance.review.status')" width="220">
          <template #default="{ row }">
            <el-select v-model="row.status" class="status-select">
              <el-option :label="t('releaseGovernance.review.confirmed')" value="CONFIRMED" />
              <el-option :label="t('releaseGovernance.review.needsFollowUp')" value="NEEDS_FOLLOW_UP" />
              <el-option :label="t('releaseGovernance.review.notApplicable')" value="NOT_APPLICABLE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('releaseGovernance.review.note')" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.note" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="summary" shadow="never">
      <template #header>{{ t('releaseGovernance.review.signoff') }}</template>
      <el-form label-width="96px">
        <el-form-item :label="t('releaseGovernance.review.reviewer')">
          <el-input v-model="reviewer" class="reviewer-input" />
        </el-form-item>
        <el-form-item :label="t('releaseGovernance.review.decision')">
          <el-radio-group v-model="decision">
            <el-radio-button label="APPROVE_FOR_CONTROLLED_REVIEW">
              {{ t('releaseGovernance.review.approve') }}
            </el-radio-button>
            <el-radio-button label="HOLD_FOR_FOLLOW_UP">
              {{ t('releaseGovernance.review.hold') }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('releaseGovernance.review.note')">
          <el-input v-model="note" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitSignoff">
            {{ t('releaseGovernance.review.submit') }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="summary.latestSignoff"
        class="latest-signoff"
        type="success"
        :closable="false"
        :title="latestSignoffTitle"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  releaseGovernanceApi,
  type CandidateDecision,
  type ChecklistDecision,
  type ChecklistStatus,
  type ReleaseCandidateReviewSummary
} from '@/api/releaseGovernanceApi'
import { handleError } from '@/utils/error'

interface ChecklistRow extends ChecklistDecision {
  title: string
  sourcePath: string
  required: boolean
}

const { t } = useI18n()
const loading = ref(false)
const submitting = ref(false)
const summary = ref<ReleaseCandidateReviewSummary | null>(null)
const reviewer = ref('release-manager')
const decision = ref<CandidateDecision>('APPROVE_FOR_CONTROLLED_REVIEW')
const note = ref('')
const checklistRows = ref<ChecklistRow[]>([])

const latestSignoffTitle = computed(() => {
  const latest = summary.value?.latestSignoff
  if (!latest) return ''
  return t('releaseGovernance.review.latestSignoff', {
    reviewer: latest.reviewer,
    decision: latest.decision,
    createdAt: latest.createdAt
  })
})

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await releaseGovernanceApi.getCandidateReview()
    checklistRows.value = summary.value.checklist.map(item => ({
      key: item.key,
      title: item.title,
      sourcePath: item.sourcePath,
      required: item.required,
      status: item.expectedStatus,
      note: ''
    }))
  } catch (error) {
    handleError(error)
  } finally {
    loading.value = false
  }
}

function markAllConfirmed() {
  checklistRows.value = checklistRows.value.map(item => ({ ...item, status: 'CONFIRMED' }))
}

async function submitSignoff() {
  if (!summary.value) return
  submitting.value = true
  try {
    const latest = await releaseGovernanceApi.signoff({
      candidateId: summary.value.candidateId,
      reviewer: reviewer.value,
      decision: decision.value,
      note: note.value,
      checklist: checklistRows.value.map(item => ({
        key: item.key,
        status: item.status as ChecklistStatus,
        note: item.note
      }))
    })
    summary.value = { ...summary.value, latestSignoff: latest }
    ElMessage.success(t('releaseGovernance.review.signoffSaved'))
  } catch (error) {
    handleError(error)
  } finally {
    submitting.value = false
  }
}

function statusType(status: string) {
  if (status === 'PASS' || status === 'READY') return 'success'
  if (status === 'REVIEW_REQUIRED') return 'warning'
  return 'info'
}

function riskType(level: string) {
  if (level === 'NON_BLOCKING') return 'warning'
  if (level === 'DEFERRED') return 'info'
  return 'success'
}

onMounted(loadSummary)

defineExpose({
  loadSummary,
  markAllConfirmed,
  submitSignoff,
  summary,
  checklistRows,
  reviewer,
  decision,
  note
})
</script>

<style scoped>
.release-candidate-review {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-title,
.panel-header,
.risk-header {
  display: flex;
  align-items: center;
}

.page-title,
.panel-header {
  justify-content: space-between;
}

.page-title h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.page-title p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.risk-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.risk-item:last-child {
  border-bottom: none;
}

.risk-header {
  gap: 8px;
  margin-bottom: 6px;
}

.risk-status,
.source-path {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.source-path {
  margin-top: 4px;
}

.status-select {
  width: 190px;
}

.reviewer-input {
  width: 240px;
}

.latest-signoff {
  margin-top: 12px;
}
</style>
