<template>
  <el-dialog
    v-model="visible"
    :title="t('releaseWindow.codeMerge.title')"
    width="700px"
    :close-on-click-modal="false"
  >
    <div v-if="!results.length" class="merge-info">
      <el-alert
        :title="t('releaseWindow.codeMerge.info')"
        type="info"
        :closable="false"
        show-icon
      />
      
      <div class="merge-options" style="margin-top: 16px;">
        <el-radio-group v-model="mergeType">
          <el-radio value="all">{{ t('releaseWindow.codeMerge.mergeAll') }}</el-radio>
          <el-radio value="single">{{ t('releaseWindow.codeMerge.mergeSingle') }}</el-radio>
        </el-radio-group>
        
        <el-select
          v-if="mergeType === 'single'"
          v-model="selectedIteration"
          :placeholder="t('releaseWindow.codeMerge.selectIteration')"
          style="margin-left: 16px; width: 300px;"
        >
          <el-option
            v-for="iter in iterations"
            :key="iter.key"
            :label="iter.name || iter.key"
            :value="iter.key"
          />
        </el-select>
      </div>
    </div>
    
    <!-- 合并结果 -->
    <div v-else class="merge-results">
      <el-table :data="results" border size="small">
        <el-table-column prop="repoName" :label="t('repository.columns.name')" min-width="180" />
        <el-table-column prop="sourceBranch" :label="t('releaseWindow.codeMerge.sourceBranch')" width="200" />
        <el-table-column prop="targetBranch" :label="t('releaseWindow.codeMerge.targetBranch')" width="200" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ t(`releaseWindow.codeMerge.status.${row.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" :label="t('common.message')" min-width="200">
          <template #default="{ row }">
            <el-tooltip v-if="row.message" :content="row.message" placement="top">
              <span class="message-text">{{ row.message }}</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="merge-summary">
        <el-statistic :title="t('common.total')" :value="results.length" />
        <el-statistic :title="t('releaseWindow.codeMerge.status.SUCCESS')" :value="successCount" />
        <el-statistic :title="t('releaseWindow.codeMerge.status.CONFLICT')" :value="conflictCount" />
        <el-statistic :title="t('releaseWindow.codeMerge.status.FAILED')" :value="failedCount" />
      </div>
    </div>
    
    <template #footer>
      <el-button @click="handleClose">{{ t('common.close') }}</el-button>
      <el-button
        v-if="!results.length"
        type="primary"
        :loading="merging"
        :disabled="mergeType === 'single' && !selectedIteration"
        @click="handleMerge"
      >
        {{ t('releaseWindow.codeMerge.execute') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { releaseWindowApi, type CodeMergeResult } from '@/api/modules/releaseWindow'
import { handleError } from '@/utils/error'

interface IterationInfo {
  key: string
  name: string
}

const { t } = useI18n()

const visible = ref(false)
const windowId = ref('')
const iterations = ref<IterationInfo[]>([])
const mergeType = ref<'all' | 'single'>('all')
const selectedIteration = ref('')
const merging = ref(false)
const results = ref<CodeMergeResult[]>([])

const successCount = computed(() => results.value.filter(r => r.status === 'SUCCESS').length)
const conflictCount = computed(() => results.value.filter(r => r.status === 'CONFLICT').length)
const failedCount = computed(() => results.value.filter(r => r.status === 'FAILED').length)

const open = (wId: string, iters: IterationInfo[]) => {
  windowId.value = wId
  iterations.value = iters
  mergeType.value = 'all'
  selectedIteration.value = ''
  results.value = []
  visible.value = true
}

const handleClose = () => {
  visible.value = false
}

const handleMerge = async () => {
  merging.value = true
  try {
    if (mergeType.value === 'all') {
      results.value = await releaseWindowApi.mergeAll(windowId.value)
    } else {
      results.value = await releaseWindowApi.mergeIteration(windowId.value, selectedIteration.value)
    }
    
    if (successCount.value === results.value.length) {
      ElMessage.success(t('releaseWindow.codeMerge.allSuccess'))
    } else if (conflictCount.value > 0) {
      ElMessage.warning(t('releaseWindow.codeMerge.hasConflict'))
    } else if (failedCount.value > 0) {
      ElMessage.error(t('releaseWindow.codeMerge.hasFailed'))
    }
  } catch (err) {
    handleError(err)
  } finally {
    merging.value = false
  }
}

const getStatusType = (status: string) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'CONFLICT': return 'warning'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

defineExpose({ open })
</script>

<style scoped>
.merge-options {
  display: flex;
  align-items: center;
}

.merge-results {
  margin-top: 8px;
}

.merge-summary {
  display: flex;
  gap: 24px;
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

.message-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}
</style>
