<template>
  <div class="data-table-wrapper">
    <!-- 操作按钮区域 -->
    <div v-if="$slots.actions" class="table-actions">
      <slot name="actions" />
    </div>

    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <slot />
    </el-table>
    
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="currentPageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  loading?: boolean
  data: any[]
  total: number
  page: number
  pageSize: number
}>()

const emit = defineEmits<{
  (e: 'page-change', page: number): void
  (e: 'page-size-change', size: number): void
  (e: 'update:page', page: number): void
  (e: 'update:pageSize', size: number): void
}>()

// Use computed with getter/setter for v-model to work with props
const currentPage = computed({
  get: () => props.page,
  set: (val) => emit('update:page', val)
})

const currentPageSize = computed({
  get: () => props.pageSize,
  set: (val) => emit('update:pageSize', val)
})

const handlePageChange = (val: number) => {
  emit('page-change', val)
}

const handleSizeChange = (val: number) => {
  emit('page-size-change', val)
}
</script>

<style scoped>
.data-table-wrapper {
  background-color: #fff;
  padding: 16px;
  border-radius: 4px;
}
.table-actions {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
