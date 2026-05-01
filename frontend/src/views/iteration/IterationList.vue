<template>
  <div class="iteration-list-page list-page">
    <SearchForm :loading="loading" @search="search" @reset="reset">
      <el-form-item :label="t('common.keyword')">
        <el-input v-model="query.keyword" :placeholder="t('common.keyword')" clearable />
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
      <template #actions>
        <el-button v-perm.disable="'iteration:write'" type="primary" @click="openCreate">{{ t('iteration.new') }}</el-button>
      </template>
      <el-table-column prop="iterationKey" :label="t('iteration.columns.key')" width="200" />
      <el-table-column prop="name" :label="t('iteration.columns.name')" min-width="200" />
      <el-table-column prop="description" :label="t('iteration.columns.description')" min-width="200">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.description"
            :content="row.description"
            placement="top"
            :show-after="300"
            :hide-after="0"
            effect="dark"
            popper-class="description-tooltip"
          >
            <span class="description-text">{{ row.description }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="repoCount" :label="t('iteration.columns.repos')" width="100">
        <template #default="{ row }">
          <el-tag type="info" size="small">{{ row.repoCount }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('iteration.columns.expectedReleaseAt')" width="120">
        <template #default="{ row }">
          {{ formatDate(row.expectedReleaseAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="t('iteration.columns.createdAt')" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="goDetailPage(row)">{{ t('common.view') }}</el-button>
          <el-button v-perm.disable="'iteration:write'" link type="danger" size="small" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </DataTable>

    <IterationCreateDialog ref="createRef" @success="fetch" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useListPage } from '@/composables/crud/useListPage'
import { useRouter } from 'vue-router'
import SearchForm from '@/components/crud/SearchForm.vue'
import DataTable from '@/components/crud/DataTable.vue'
import IterationCreateDialog from './IterationCreateDialog.vue'
import { iterationApi, type Iteration } from '@/api/iterationApi'
import { hasPerm } from '@/utils/perm'
import { ElMessage, ElMessageBox } from 'element-plus'
import { handleError } from '@/utils/error'
import { formatDate, formatDateTime } from '@/utils/date'

const { t } = useI18n()
const router = useRouter()
const createRef = ref<InstanceType<typeof IterationCreateDialog>>()

const { query, loading, list, total, fetch, search, reset, onPageChange, onPageSizeChange } = useListPage({
  fetcher: iterationApi.list,
  defaultQuery: {
    keyword: ''
  }
})

function openCreate() {
  if (!hasPerm('iteration:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  createRef.value?.open()
}

function goDetailPage(row: Iteration) {
  router.push({ name: 'IterationDetail', params: { iterationKey: row.iterationKey } })
}

async function handleDelete(row: Iteration) {
  if (!hasPerm('iteration:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  
  try {
    await ElMessageBox.confirm(
      t('iteration.confirmDelete', { key: row.iterationKey }),
      t('common.warning'),
      { type: 'warning' }
    )
    
    await iterationApi.delete(row.iterationKey)
    ElMessage.success(t('common.success'))
    fetch()
  } catch (error) {
    if (error !== 'cancel') {
      handleError(error)
    }
  }
}
</script>

<style scoped>
/* 页面特定样式 - 通用样式已移至 index.css */
.description-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  word-break: break-all;
}
</style>
