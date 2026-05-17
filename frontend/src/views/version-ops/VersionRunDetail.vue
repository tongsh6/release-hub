<template>
  <div class="list-page">
    <el-page-header @back="goBack">
      <template #content>
        <span class="text-large font-600 mr-3">Run {{ runId }}</span>
      </template>
    </el-page-header>

    <div v-loading="loading" class="content-area">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="Run ID">{{ detail.runId }}</el-descriptions-item>
        <el-descriptions-item :label="t('versionOps.status')">
          <el-tag :type="statusType(detail.status)" size="small">
            {{ statusLabel(detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('versionOps.date')">
          {{ formatTime(detail.startedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Type">{{ detail.type }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="detail?.items?.length" class="items-section">
        <h3>Items</h3>
        <el-table :data="detail.items" border stripe style="margin-top: 12px">
          <el-table-column prop="windowKey" label="Window" width="180" />
          <el-table-column prop="repoId" label="Repo ID" width="280" />
          <el-table-column prop="iterationKey" label="Iteration" width="200" />
          <el-table-column prop="result" label="Result" width="140">
            <template #default="{ row }">
              <el-tag :type="itemResultType(row.result)" size="small">
                {{ row.result }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="logs-area">
        <h3>Logs</h3>
        <div v-if="logs.length" class="logs-content">
          <p v-for="(line, i) in logs" :key="i" :class="{ 'log-error': line.includes('[ERROR]') }">
            {{ line }}
          </p>
        </div>
        <div v-else-if="!loading" class="logs-empty">{{ t('common.noData') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getRunDetail, getRunLogs, type VersionRunDetailDTO } from '@/api/modules/versionOps'
import dayjs from 'dayjs'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const runId = route.params.runId as string

const loading = ref(false)
const detail = ref<VersionRunDetailDTO | null>(null)
const logs = ref<string[]>([])

const goBack = () => router.back()

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

const itemResultType = (r: string) => {
  if (!r) return 'info'
  if (r.includes('SUCCESS') || r === 'MERGED') return 'success'
  if (r.includes('FAILED') || r.includes('BLOCKED')) return 'danger'
  return 'warning'
}

onMounted(async () => {
  loading.value = true
  try {
    const [detailData, logsData] = await Promise.all([
      getRunDetail(runId),
      getRunLogs(runId)
    ])
    detail.value = detailData
    logs.value = logsData.lines || []
  } catch {
    // silently handle
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.content-area {
  margin-top: 20px;
  background-color: #fff;
  padding: 20px;
  border-radius: 4px;
}
.items-section {
  margin-top: 24px;
}
.logs-area {
  margin-top: 24px;
}
.logs-content {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  font-family: monospace;
  color: #333;
  font-size: 13px;
  max-height: 400px;
  overflow-y: auto;
}
.logs-content p {
  margin: 2px 0;
  line-height: 1.5;
}
.log-error {
  color: var(--el-color-danger);
}
.logs-empty {
  color: var(--el-text-color-secondary);
  text-align: center;
  padding: 20px;
}
</style>
