<template>
  <el-drawer
    v-model="visible"
    :title="t('common.detail')"
    size="50%"
    destroy-on-close
    @opened="fetchDetail"
  >
    <div v-loading="loading" class="drawer-content">
      <el-card shadow="never" class="mb-4">
        <template #header>
          <div class="card-header">
            <span>{{ t('run.detail.title') }}: {{ runId }}</span>
            <div>
              <el-button type="primary" @click="handleExport">{{ t('run.detail.exportJson') }}</el-button>
            </div>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('run.columns.type')">{{ detail?.runType }}</el-descriptions-item>
          <el-descriptions-item :label="t('run.columns.status')">{{ detail?.status }}</el-descriptions-item>
          <el-descriptions-item :label="t('run.columns.start')">{{ detail?.startedAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('run.columns.end')">{{ detail?.finishedAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('run.columns.operator')">{{ detail?.operator }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
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
                    :type="step.result === 'SUCCESS' ? 'success' : step.result === 'FAILED' ? 'danger' : 'primary'"
                  >
                    {{ step.actionType }}: {{ step.result }} ({{ step.message }})
                  </el-timeline-item>
                </el-timeline>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { runApi, type RunDetail } from '@/api/runApi'
import MRFirstTimeline from '@/components/run/MRFirstTimeline.vue'

const { t } = useI18n()

const visible = ref(false)
const runId = ref('')
const loading = ref(false)
const detail = ref<RunDetail>()

const open = (id: string) => {
  runId.value = id
  visible.value = true
}

async function fetchDetail() {
  if (!runId.value) return
  loading.value = true
  try {
    detail.value = await runApi.getRunById(runId.value)
  } finally {
    loading.value = false
  }
}

function handleExport() {
  window.open(`/api/v1/runs/${runId.value}/export.json`, '_blank')
}

defineExpose({
  open
})
</script>

<style scoped>
/* 页面特定样式 - 通用样式已移至 index.css */
</style>
