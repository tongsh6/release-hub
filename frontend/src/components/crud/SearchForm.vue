<template>
  <el-form class="search-form" @submit.prevent="$emit('search')" @keydown.enter="$emit('search')">
    <div class="search-form-content">
      <slot />
    </div>
    <div class="search-form-actions">
      <el-button type="primary" :loading="loading" @click="$emit('search')">{{ t('common.search') }}</el-button>
      <el-button @click="$emit('reset')">{{ t('common.reset') }}</el-button>
      <slot name="extra-actions" />
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  loading?: boolean
}>()

defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
}>()
</script>

<style scoped>
.search-form {
  background-color: #fff;
  padding: 16px;
  margin-bottom: 16px;
  border-radius: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-start;
}

.search-form-content {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

/* Make direct children (el-form-item) behave nicely */
:deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 0;
}

.search-form-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
