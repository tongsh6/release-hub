<template>
  <div class="run-list-page list-page">
    <SearchForm :loading="loading" @search="search" @reset="reset">
      <el-form-item :label="t('run.filters.windowKey')">
        <el-input v-model="query.windowKey" />
      </el-form-item>
      <el-form-item :label="t('run.filters.repo')">
        <el-input v-model="query.repoId" />
      </el-form-item>
      <el-form-item :label="t('run.filters.iterationKey')">
        <el-input v-model="query.iterationKey" />
      </el-form-item>
      <el-form-item :label="t('run.filters.group')">
        <GroupTreeSelect v-model="query.groupCode" :placeholder="t('group.selectGroup')" clearable />
      </el-form-item>
      <el-form-item :label="t('run.filters.status')">
        <el-select v-model="query.status" clearable style="width: 160px">
          <el-option label="FAILED" value="FAILED" />
          <el-option label="DONE" value="DONE" />
          <el-option label="PARTIAL_FAILED" value="PARTIAL_FAILED" />
          <el-option label="RUNNING" value="RUNNING" />
          <el-option label="NEEDS_REVIEW" value="NEEDS_REVIEW" />
          <el-option :label="t('runPlan.status')" value="PLANNED" />
        </el-select>
      </el-form-item>
    </SearchForm>

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :loading="loading"
      :data="list"
      :total="total"
      @page-change="onPageChange"
      @page-size-change="onPageSizeChange"
    >
      <el-table-column prop="id" :label="t('run.columns.runId')" width="120" />
      <el-table-column prop="runType" :label="t('run.columns.type')" width="220">
        <template #default="{ row }">{{ t(runTypeKey(row.runType)) }}</template>
      </el-table-column>
      <el-table-column prop="status" :label="t('run.columns.status')" width="120" />
      <el-table-column prop="startedAt" :label="t('run.columns.start')" width="180" />
      <el-table-column prop="finishedAt" :label="t('run.columns.end')" width="180" />
      <el-table-column :label="t('common.actions')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">{{ t('run.actions.detail') }}</el-button>
          <el-button 
            v-perm.disable="'run:write'" 
            link 
            type="warning" 
            size="small" 
            :disabled="!canRetry(row)"
            @click="handleRetry(row)"
          >
            {{ t('run.actions.retry') }}
          </el-button>
          <el-button 
            v-perm.disable="'run:write'" 
            link 
            type="primary" 
            size="small"
            @click="handleExport(row)"
          >
            {{ t('run.actions.export') }}
          </el-button>
        </template>
      </el-table-column>
    </DataTable>

    <RunDrawer ref="drawerRef" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useListPage } from '@/composables/crud/useListPage'
import SearchForm from '@/components/crud/SearchForm.vue'
import DataTable from '@/components/crud/DataTable.vue'
import GroupTreeSelect from '@/components/common/GroupTreeSelect.vue'
import RunDrawer from './RunDrawer.vue'
import { runApi, type Run } from '@/api/runApi'
import { isRetryableRun, runTypeKey } from '@/utils/runPresentation'
import { hasPerm } from '@/utils/perm'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const router = useRouter()
const drawerRef = ref<InstanceType<typeof RunDrawer>>()

const { query, loading, list, total, search, reset, onPageChange, onPageSizeChange } = useListPage({
  fetcher: runApi.list,
  defaultQuery: {
    windowKey: '',
    repoId: '',
    iterationKey: '',
    groupCode: '',
    status: ''
  }
})

function canRetry(row: Run) { return isRetryableRun(row.status, row.runType) }

function openDetail(row: any) {
  drawerRef.value?.open(row.id)
}

async function handleRetry(row: Run) {
  if (!canRetry(row)) return
  if (!hasPerm('run:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  await router.push({ name: 'RunDetail', params: { runId: row.id } })
}
function handleExport(row: any) {
  if (!hasPerm('run:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  window.open(`/api/v1/runs/${row.id}/export.csv`, '_blank')
}
</script>

<style scoped>
</style>
