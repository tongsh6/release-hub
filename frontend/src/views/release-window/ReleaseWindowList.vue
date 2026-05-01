<template>
  <div class="release-window-list-page list-page">
    <SearchForm :loading="loading" @search="search" @reset="reset">
      <el-form-item :label="t('releaseWindow.name')">
        <el-input v-model="query.name" :placeholder="t('releaseWindow.placeholder.name')" clearable />
      </el-form-item>
      <el-form-item :label="t('releaseWindow.status')">
        <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 150px">
          <el-option value="" :label="t('common.all')" />
          <el-option value="DRAFT" :label="t('releaseWindow.statusText.DRAFT')" />
          <el-option value="PUBLISHED" :label="t('releaseWindow.statusText.PUBLISHED')" />
          <el-option value="CLOSED" :label="t('releaseWindow.statusText.CLOSED')" />
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
      <template #actions>
        <el-button v-perm.disable="'release-window:write'" type="primary" :icon="Plus" @click="handleCreate">{{ t('releaseWindow.create') }}</el-button>
      </template>
      <el-table-column prop="windowKey" :label="t('releaseWindow.windowKey')" width="180" />
      <el-table-column prop="name" :label="t('releaseWindow.name')" min-width="150" />
      <el-table-column prop="description" :label="t('releaseWindow.description')" min-width="150">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.description"
            :content="row.description"
            placement="top"
            :show-after="300"
            effect="dark"
            popper-class="description-tooltip"
          >
            <span class="description-text">{{ row.description }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('releaseWindow.plannedReleaseAt')" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.plannedReleaseAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('releaseWindow.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ t(`releaseWindow.statusText.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('releaseWindow.createdAt')" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="t('releaseWindow.actions')" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">{{ t('releaseWindow.view') }}</el-button>

          <el-button 
            v-perm.disable="'release-window:write'"
            link 
            type="primary"
            @click="openAttachIterations(row)"
          >
            {{ t('releaseWindow.attachIterations') }}
          </el-button>
          
          <el-button 
            v-if="row.status === 'DRAFT' && !row.frozen"
            v-perm.disable="'release-window:write'"
            link 
            type="warning"
            @click="handleFreeze(row)"
          >
            {{ t('releaseWindow.freeze') }}
          </el-button>
          
          <el-button 
            v-if="row.frozen && row.status === 'DRAFT'"
            v-perm.disable="'release-window:write'"
            link 
            @click="handleUnfreeze(row)"
          >
            {{ t('releaseWindow.unfreeze') }}
          </el-button>

          <el-button 
            v-if="row.status === 'DRAFT'"
            v-perm.disable="'release-window:write'"
            link 
            type="success"
            @click="handlePublish(row)"
          >
            {{ t('releaseWindow.publish') }}
          </el-button>
          
          <el-button 
            v-if="row.status === 'PUBLISHED'"
            v-perm.disable="'release-window:write'"
            link 
            type="danger"
            @click="handleClose(row)"
          >
            {{ t('releaseWindow.close') }}
          </el-button>
        </template>
      </el-table-column>
    </DataTable>

    <ReleaseWindowDialog ref="dialogRef" @success="fetch" />
    <AttachIterationsDialog ref="attachDialogRef" @success="fetch" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import { useListPage } from '@/composables/crud/useListPage'
import SearchForm from '@/components/crud/SearchForm.vue'
import DataTable from '@/components/crud/DataTable.vue'
import ReleaseWindowDialog from './ReleaseWindowDialog.vue'
import AttachIterationsDialog from './AttachIterationsDialog.vue'
import { releaseWindowApi, type ReleaseWindow, type ReleaseWindowStatus } from '@/api/modules/releaseWindow'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPerm } from '@/utils/perm'
import { handleError } from '@/utils/error'
import { formatDateTime } from '@/utils/date'

const { t } = useI18n()
const router = useRouter()
const dialogRef = ref<InstanceType<typeof ReleaseWindowDialog>>()
const attachDialogRef = ref<InstanceType<typeof AttachIterationsDialog>>()

const listFetcher = async (q: any) => {
  return releaseWindowApi.list(q)
}

const { query, loading, list, total, fetch, search, reset, onPageChange, onPageSizeChange } = useListPage({
  fetcher: listFetcher,
  defaultQuery: {
    name: '',
    status: ''
  }
})

const handleCreate = () => {
  dialogRef.value?.open({ mode: 'create' })
}

const handleView = (row: ReleaseWindow) => {
  router.push({ name: 'ReleaseWindowDetail', params: { id: row.id } })
}

const handleFreeze = async (row: ReleaseWindow) => {
if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  try {
    await ElMessageBox.confirm(t('releaseWindow.confirmFreeze'), t('common.warning'), {
      type: 'warning'
    })
    await releaseWindowApi.freeze(row.id)
    ElMessage.success(t('common.success'))
    fetch()
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  }
}

const handleUnfreeze = async (row: ReleaseWindow) => {
if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  try {
    await releaseWindowApi.unfreeze(row.id)
    ElMessage.success(t('common.success'))
    fetch()
  } catch (error) {
    handleError(error)
  }
}

const handlePublish = async (row: ReleaseWindow) => {
  if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  try {
    await ElMessageBox.confirm(t('releaseWindow.confirmPublish'), t('common.warning'), {
      type: 'warning'
    })
    await releaseWindowApi.publish(row.id)
    ElMessage.success(t('common.success'))
    fetch()
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  }
}

const handleClose = async (row: ReleaseWindow) => {
if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
   try {
    await ElMessageBox.confirm(t('releaseWindow.confirmClose'), t('common.warning'), {
      type: 'warning'
    })
    await releaseWindowApi.close(row.id)
    ElMessage.success(t('common.success'))
    fetch()
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  }
}

const openAttachIterations = (row: ReleaseWindow) => {
if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  attachDialogRef.value?.open(row.id)
}
const getStatusType = (status: ReleaseWindowStatus) => {
  switch (status) {
    case 'DRAFT': return 'info'
    case 'INIT': return 'info'
    case 'OPEN': return 'primary'
    case 'FROZEN': return 'warning'
    case 'CLOSED': return 'success'
    case 'PUBLISHED': return 'success'
    default: return 'info'
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
