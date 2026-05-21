<template>
  <el-card v-loading="loading" class="release-plan-panel" style="margin-top: 16px;">
    <template #header>
      <div class="panel-header">
        <span class="title">{{ t('releaseWindow.releasePlan.title') }}</span>
        <el-button size="small" :icon="RefreshRight" @click="refresh">{{ t('common.refresh') }}</el-button>
      </div>
    </template>

    <div v-if="planRows.length === 0" class="empty-tip">
      {{ t('releaseWindow.releasePlan.empty') }}
    </div>

    <template v-else>
      <div class="branch-summary">
        <div class="summary-item">
          <span class="summary-label">{{ t('releaseWindow.releasePlan.totalRepos') }}</span>
          <strong>{{ branchSummary.total }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('releaseWindow.releasePlan.featureMissing') }}</span>
          <strong :class="{ 'summary-danger': branchSummary.featureMissing > 0 }">{{ branchSummary.featureMissing }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('releaseWindow.releasePlan.releaseMissing') }}</span>
          <strong :class="{ 'summary-danger': branchSummary.releaseMissing > 0 }">{{ branchSummary.releaseMissing }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('releaseWindow.releasePlan.mergedCount') }}</span>
          <strong class="summary-success">{{ branchSummary.merged }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('releaseWindow.releasePlan.conflictCount') }}</span>
          <strong :class="{ 'summary-danger': branchSummary.conflict > 0 }">{{ branchSummary.conflict }}</strong>
        </div>
      </div>
      <el-alert
        v-if="hasBranchRisk"
        class="branch-risk-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="t('releaseWindow.releasePlan.branchRisk', {
          featureMissing: branchSummary.featureMissing,
          releaseMissing: branchSummary.releaseMissing,
          conflict: branchSummary.conflict
        })"
      />
    </template>

    <el-table v-if="planRows.length > 0" :data="planRows" border stripe style="width: 100%">
      <el-table-column :label="t('releaseWindow.releasePlan.plannedOrder')" width="120" align="center">
        <template #default="{ row }">
          <el-tag type="info" size="small">{{ row.plannedOrderLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="repoName" :label="t('repository.columns.name')" min-width="160" />
      <el-table-column prop="iterationKey" :label="t('iteration.columns.key')" width="150" />
      <el-table-column :label="t('releaseWindow.releasePlan.featureBranch')" min-width="200">
        <template #default="{ row }">
          <div class="branch-cell">
            <span class="branch-name">{{ row.featureBranch.branchName }}</span>
            <el-tag
              :type="row.featureBranch.exists ? 'success' : 'info'"
              size="small"
              style="margin-left: 8px;"
            >
              {{ row.featureBranch.exists ? t('releaseWindow.releasePlan.exists') : t('releaseWindow.releasePlan.missing') }}
            </el-tag>
            <span v-if="row.featureBranch.latestCommit" class="commit-sha">
              {{ row.featureBranch.latestCommit.substring(0, 8) }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="t('releaseWindow.releasePlan.releaseBranch')" min-width="200">
        <template #default="{ row }">
          <div class="branch-cell">
            <span class="branch-name">{{ row.releaseBranch.branchName }}</span>
            <el-tag
              :type="row.releaseBranch.exists ? 'success' : 'info'"
              size="small"
              style="margin-left: 8px;"
            >
              {{ row.releaseBranch.exists ? t('releaseWindow.releasePlan.exists') : t('releaseWindow.releasePlan.missing') }}
            </el-tag>
            <span v-if="row.releaseBranch.latestCommit" class="commit-sha">
              {{ row.releaseBranch.latestCommit.substring(0, 8) }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="t('releaseWindow.releasePlan.mergeStatus')" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="mergeStatusType(row.releaseBranch.mergeStatus)" size="small">
            {{ mergeStatusLabel(row.releaseBranch.mergeStatus) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { RefreshRight } from '@element-plus/icons-vue'
import { releaseWindowApi, type BranchStatusView, type PlanItemView, type RepoBranchStatus } from '@/api/modules/releaseWindow'
import { handleError } from '@/utils/error'

const props = defineProps<{
  windowId: string
}>()

const { t } = useI18n()
const loading = ref(false)
const branchStatus = ref<BranchStatusView | null>(null)
const planItems = ref<PlanItemView[]>([])

interface ReleasePlanRow extends RepoBranchStatus {
  plannedOrder?: number
  plannedOrderLabel: string
  lastExecutedOrder?: number
}

const planRows = computed<ReleasePlanRow[]>(() => {
  const orderByScope = new Map(
    planItems.value.map(item => [`${item.iterationKey}:${item.repoId}`, item])
  )
  return (branchStatus.value?.repos ?? [])
    .map(repo => {
      const plan = orderByScope.get(`${repo.iterationKey}:${repo.repoId}`)
      return {
        ...repo,
        plannedOrder: plan?.plannedOrder,
        plannedOrderLabel: plan?.plannedOrder ? String(plan.plannedOrder) : '-',
        lastExecutedOrder: plan?.lastExecutedOrder
      }
    })
    .sort((a, b) => (a.plannedOrder ?? Number.MAX_SAFE_INTEGER) - (b.plannedOrder ?? Number.MAX_SAFE_INTEGER) || a.iterationKey.localeCompare(b.iterationKey) || a.repoName.localeCompare(b.repoName))
})

const branchSummary = computed(() => {
  const rows = planRows.value
  return {
    total: rows.length,
    featureMissing: rows.filter(row => !row.featureBranch.exists).length,
    releaseMissing: rows.filter(row => !row.releaseBranch.exists).length,
    merged: rows.filter(row => row.releaseBranch.mergeStatus === 'MERGED').length,
    conflict: rows.filter(row => row.releaseBranch.mergeStatus === 'CONFLICT').length
  }
})

const hasBranchRisk = computed(() => (
  branchSummary.value.featureMissing > 0 ||
  branchSummary.value.releaseMissing > 0 ||
  branchSummary.value.conflict > 0
))

const load = async () => {
  loading.value = true
  try {
    const [plan, status] = await Promise.all([
      releaseWindowApi.getPlan(props.windowId),
      releaseWindowApi.getBranchStatus(props.windowId)
    ])
    planItems.value = plan
    branchStatus.value = status
  } catch (err) {
    handleError(err)
  } finally {
    loading.value = false
  }
}

const refresh = () => load()

const mergeStatusType = (status: string) => {
  switch (status) {
    case 'MERGED': return 'success'
    case 'CONFLICT': return 'danger'
    default: return 'info'
  }
}

const mergeStatusLabel = (status: string) => {
  switch (status) {
    case 'MERGED': return t('releaseWindow.releasePlan.mergeStatusText.MERGED')
    case 'CONFLICT': return t('releaseWindow.releasePlan.mergeStatusText.CONFLICT')
    default: return t('releaseWindow.releasePlan.mergeStatusText.PENDING')
  }
}

onMounted(() => load())
</script>

<style scoped>
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header .title {
  font-weight: 600;
}

.empty-tip {
  color: #909399;
  text-align: center;
  padding: 20px 0;
}

.branch-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.branch-name {
  font-family: monospace;
  font-size: 13px;
  color: #303133;
}

.commit-sha {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 0 4px;
  border-radius: 2px;
}

.branch-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
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

.summary-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.summary-success {
  color: var(--el-color-success);
}

.summary-danger {
  color: var(--el-color-danger);
}

.branch-risk-alert {
  margin-bottom: 12px;
}
</style>
