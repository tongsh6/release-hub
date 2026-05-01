<template>
  <div class="list-page">
    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :loading="loading"
      :data="list"
      :total="total"
      @page-change="onPageChange"
      @page-size-change="onPageSizeChange"
    >
      <el-table-column prop="runId" :label="t('versionOps.runId')" width="280" />
      <el-table-column prop="type" :label="t('versionOps.scanType')" width="140">
        <template #default="{ row }">
          <el-tag size="small">{{ row.type === 'VERSION_UPDATE' ? 'UPDATE' : row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startedAt" :label="t('versionOps.date')" width="180">
        <template #default="{ row }">
          {{ formatTime(row.startedAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('versionOps.status')" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('versionOps.viewDetails')" width="120">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleViewDetail(row)">
            {{ t('versionOps.viewDetails') }}
          </el-button>
        </template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useListPage } from '@/composables/crud/useListPage'
import DataTable from '@/components/crud/DataTable.vue'
import { pageRuns, type VersionRunSummaryDTO } from '@/api/modules/versionOps'
import dayjs from 'dayjs'

const { t } = useI18n()
const router = useRouter()

const { query, loading, list, total, onPageChange, onPageSizeChange } = useListPage({
  fetcher: (q: any) => pageRuns({
    page: q.page,
    pageSize: q.pageSize
  }),
  defaultQuery: {}
})

const formatTime = (iso?: string) => {
  if (!iso) return '-'
  return dayjs(iso).format('YYYY-MM-DD HH:mm:ss')
}

const statusType = (s: string) => {
  switch (s) {
    case 'SUCCEEDED': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'warning'
    default: return 'info'
  }
}

const statusLabel = (s: string) => {
  switch (s) {
    case 'SUCCEEDED': return t('versionOps.succeeded')
    case 'FAILED': return t('versionOps.failed')
    case 'RUNNING': return t('versionOps.running')
    default: return s
  }
}

const handleViewDetail = (row: VersionRunSummaryDTO) => {
  router.push(`/version-ops/runs/${row.runId}`)
}
</script>

<style scoped>
/* page styles inherited from list-page */
</style>
