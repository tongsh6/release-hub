<template>
  <EntityDialog
    ref="entityRef"
    :title="t('releaseWindow.attachIterations')"
    :confirm-text="t('common.confirm')"
    :cancel-text="t('common.cancel')"
    @confirm="submit"
    @opened="onOpened"
  >
    <template #default>
      <div class="toolbar">
        <el-input
          v-model="keyword"
          :placeholder="t('common.keyword')"
          clearable
          style="width: 240px"
          @keyup.enter="doSearch"
        />
        <el-button class="ml-8" type="primary" @click="doSearch">{{ t('common.search') }}</el-button>
        <el-button class="ml-8" @click="clearSelection">{{ t('common.clear') }}</el-button>
      </div>
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="list"
        border
        style="width: 100%; margin-top: 12px;"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="55" :selectable="isSelectable" />
        <el-table-column prop="iterationKey" :label="t('iteration.columns.key')" min-width="160" />
        <el-table-column prop="groupCode" :label="t('group.code')" min-width="120" />
        <el-table-column prop="repoCount" :label="t('iteration.columns.repos')" width="100" />
        <el-table-column prop="mountedWindows" :label="t('iteration.columns.mountedWindows')" width="160" />
        <el-table-column :label="t('iteration.columns.attachAt')" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.attachAt) }}
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onPageSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </template>
  </EntityDialog>
  <div />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import EntityDialog from '@/components/common/EntityDialog.vue'
import { useListPage } from '@/composables/crud/useListPage'
import { iterationApi, type Iteration } from '@/api/iterationApi'
import { releaseWindowApi, type ReleaseWindow } from '@/api/modules/releaseWindow'
import { hasPerm } from '@/utils/perm'
import { handleError } from '@/utils/error'
import { formatDateTime } from '@/utils/date'

const { t } = useI18n()
const emit = defineEmits<{ (e: 'success'): void }>()

const entityRef = ref<InstanceType<typeof EntityDialog>>()
const tableRef = ref()
const windowIdRef = ref<string>('')
const windowGroupCode = ref<string>('')
const keyword = ref('')
const selected = ref<Iteration[]>([])

const { query, loading, list, total, fetch, onPageChange, onPageSizeChange } = useListPage<Iteration, any>({
  fetcher: iterationApi.list as any,
  defaultQuery: {
    keyword: ''
  },
  immediate: false
})

const open = async (windowId: string) => {
  windowIdRef.value = windowId
  windowGroupCode.value = ''
  keyword.value = ''
  selected.value = []
  entityRef.value?.open()
  try {
    const window = await releaseWindowApi.get(windowId) as ReleaseWindow
    windowGroupCode.value = window.groupCode || ''
    await fetch()
  } catch (error) {
    handleError(error)
  }
}

const doSearch = () => {
  query.page = 1
  query.keyword = keyword.value
  fetch()
}

const clearSelection = () => {
  selected.value = []
  tableRef.value?.clearSelection()
}

const onSelectionChange = (rows: Iteration[]) => {
  selected.value = rows.filter(isSelectable)
}

const isSelectable = (row: Iteration) => {
  return Boolean(!windowGroupCode.value || row.groupCode === windowGroupCode.value)
}

const submit = async () => {
  if (!hasPerm('release-window:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  if (!windowIdRef.value) return
  if (!selected.value.length) {
    ElMessage.warning(t('common.pleaseSelect'))
    return
  }
  const tasks = selected.value.map((it) => releaseWindowApi.attach(windowIdRef.value, it.iterationKey))
  const results = await Promise.allSettled(tasks)
  const ok = results.filter(r => r.status === 'fulfilled').length
  const bad = results.length - ok
  if (bad === 0) {
    ElMessage.success(t('common.success'))
  } else {
    ElMessage.warning(`${t('common.partialSuccess')}: ${ok}/${results.length}`)
  }
  emit('success')
  entityRef.value?.close()
}

const onOpened = () => {
  // no-op
}

defineExpose({ open })
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
}
.ml-8 { margin-left: 8px; }
.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
