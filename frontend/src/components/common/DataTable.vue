<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TableColumn } from '@/types/ui'

const { t } = useI18n()

const props = defineProps<{
  rows: any[]
  columns: TableColumn[]
  loading?: boolean
  page: number
  size: number
  total: number
}>()

const emit = defineEmits<{
  (e: 'update:page', val: number): void
  (e: 'update:size', val: number): void
  (e: 'page-change', val: number): void
  (e: 'size-change', val: number): void
  (e: 'row-action', action: string, row: any): void
}>()

const currentPage = computed({
  get: () => props.page,
  set: (val) => {
    emit('update:page', val)
    emit('page-change', val)
  }
})

const currentSize = computed({
  get: () => props.size,
  set: (val) => {
    emit('update:size', val)
    emit('size-change', val)
  }
})
</script>

<template>
  <div class="data-table-wrapper">
    <!-- Toolbar -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <slot name="header-left" />
      </div>
      <div class="toolbar-right">
        <slot name="top-right" />
      </div>
    </div>

    <!-- Table -->
    <el-table
      v-loading="loading"
      :data="rows"
      style="width: 100%"
      border
    >
      <template v-for="col in columns" :key="col.prop || col.label">
        <!-- Slot Column -->
        <el-table-column
          v-if="col.slot"
          v-bind="col"
        >
          <template #default="scope">
            <slot :name="col.slot" :row="scope.row" :index="scope.$index" />
          </template>
        </el-table-column>

        <!-- Standard Column -->
        <el-table-column
          v-else
          v-bind="col"
        />
      </template>

      <!-- Empty State -->
      <template #empty>
        <el-empty :description="t('common.noData')" />
      </template>
    </el-table>

    <!-- Pagination -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="currentSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
      />
    </div>
  </div>
</template>

<style scoped>
.data-table-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 32px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
