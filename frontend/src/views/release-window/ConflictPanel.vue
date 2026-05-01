<template>
  <div v-loading="scanning" class="conflict-panel">
    <!-- 状态概览 -->
    <el-alert
      v-if="!report"
      type="info"
      :title="t('conflict.notScanned')"
      :description="t('conflict.notScannedHint')"
      show-icon
      :closable="false"
    />

    <el-alert
      v-else-if="report.hasConflicts"
      type="error"
      :title="t('conflict.hasConflicts', { count: report.totalCount })"
      show-icon
      :closable="false"
    />

    <el-alert
      v-else
      type="success"
      :title="t('conflict.noConflicts')"
      show-icon
      :closable="false"
    />

    <!-- 操作栏 -->
    <div class="panel-toolbar">
      <el-button :loading="scanning" type="primary" @click="handleScan">
        {{ t('conflict.rescan') }}
      </el-button>
      <span v-if="report" class="scan-time">
        {{ t('conflict.lastScanAt') }}: {{ formatTime(report.checkedAt) }}
      </span>
    </div>

    <!-- 冲突列表 -->
    <template v-if="report && filteredConflicts.length > 0">
      <!-- 类型过滤 Tab -->
      <el-radio-group v-model="activeFilter" class="filter-bar">
        <el-radio-button value="ALL">
          {{ t('conflict.filterAll') }} ({{ report.totalCount }})
        </el-radio-button>
        <el-radio-button
          v-for="ct in conflictTypes"
          :key="ct"
          :value="ct"
        >
          {{ t(`conflict.types.${ct}`) }} ({{ getCount(ct) }})
        </el-radio-button>
      </el-radio-group>

      <el-table
        :data="filteredConflicts"
        border
        stripe
        style="width: 100%; margin-top: 16px"
      >
        <el-table-column prop="repoName" :label="t('conflict.repoName')" width="140" />
        <el-table-column prop="iterationKey" :label="t('conflict.iteration')" width="170" />
        <el-table-column :label="t('conflict.type')" width="160">
          <template #default="{ row }">
            <el-tag :type="getTagType(row.conflictType)" size="small">
              {{ t(`conflict.types.${row.conflictType}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('conflict.branches')" width="200">
          <template #default="{ row }">
            <span v-if="row.sourceBranch">
              {{ row.sourceBranch }} → {{ row.targetBranch }}
            </span>
            <span v-else-if="row.systemVersion">
              {{ row.systemVersion }} ≠ {{ row.repoVersion }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="message" :label="t('conflict.message')" min-width="250" />
        <el-table-column :label="t('conflict.action')" width="140">
          <template #default="{ row }">
            <el-tooltip :content="row.suggestion" placement="top">
              <el-button
                v-if="isResolvableInApp(row.conflictType)"
                type="primary"
                size="small"
                link
                @click="$emit('resolve', row)"
              >
                {{ t('conflict.resolveVersion') }}
              </el-button>
              <span v-else class="external-hint">
                {{ getExternalHint(row.conflictType) }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { checkConflicts, getConflicts, type ConflictReportView, type ConflictItemView } from '@/api/modules/releaseWindow'
import dayjs from 'dayjs'

const props = defineProps<{ windowId: string }>()
defineEmits<{
  resolve: [item: ConflictItemView]
}>()
const { t } = useI18n()

const report = ref<ConflictReportView | null>(null)
const scanning = ref(false)
const activeFilter = ref<string>('ALL')

const conflictTypes = ['MISMATCH', 'MERGE_CONFLICT', 'BRANCH_EXISTS', 'BRANCH_NONCOMPLIANT', 'CROSS_REPO_VERSION_MISMATCH', 'REPO_AHEAD', 'SYSTEM_AHEAD']

const getCount = (ct: string) =>
  report.value?.conflicts.filter(c => c.conflictType === ct).length ?? 0

const filteredConflicts = computed(() => {
  if (!report.value) return []
  if (activeFilter.value === 'ALL') return report.value.conflicts
  return report.value.conflicts.filter(c => c.conflictType === activeFilter.value)
})

const handleScan = async () => {
  scanning.value = true
  try {
    report.value = await checkConflicts(props.windowId)
  } finally {
    scanning.value = false
  }
}

const formatTime = (iso: string) => dayjs(iso).format('YYYY-MM-DD HH:mm:ss')

const getTagType = (type: string) => {
  switch (type) {
    case 'MISMATCH':
    case 'MERGE_CONFLICT':
      return 'danger'
    case 'BRANCH_EXISTS':
    case 'BRANCH_NONCOMPLIANT':
      return 'warning'
    case 'CROSS_REPO_VERSION_MISMATCH':
      return 'info'
    default:
      return ''
  }
}

const isResolvableInApp = (type: string) =>
  type === 'MISMATCH' || type === 'REPO_AHEAD' || type === 'SYSTEM_AHEAD'

const getExternalHint = (type: string) => {
  if (type === 'MERGE_CONFLICT') return t('conflict.resolveInGit')
  if (type === 'BRANCH_EXISTS' || type === 'BRANCH_NONCOMPLIANT') return t('conflict.resolveBranch')
  return ''
}

defineExpose({ report })

onMounted(async () => {
  try {
    report.value = await getConflicts(props.windowId)
  } catch {
    // 首次访问，尚未扫描
  }
})
</script>

<style scoped>
.conflict-panel { padding: 16px 0; }
.panel-toolbar { margin-top: 16px; display: flex; align-items: center; gap: 12px; }
.scan-time { color: var(--el-text-color-secondary); font-size: 13px; }
.filter-bar { margin-top: 16px; }
.external-hint { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
