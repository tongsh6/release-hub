<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SearchSchema } from '@/types/ui'

const { t } = useI18n()

const props = defineProps<{
  modelValue: Record<string, any>
  schema: SearchSchema
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'submit'): void
  (e: 'reset'): void
}>()

const formData = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const handleReset = () => {
  emit('reset')
}

const handleSubmit = () => {
  emit('submit')
}
</script>

<template>
  <el-form :model="formData" label-width="100px" @submit.prevent="handleSubmit">
    <el-row :gutter="20">
      <template v-for="field in schema.fields" :key="field.prop">
        <el-col :span="field.span || 6">
          <el-form-item :label="field.label" :prop="field.prop">
            <!-- Input -->
            <el-input
              v-if="field.type === 'input'"
              v-model="formData[field.prop]"
              :placeholder="field.placeholder || t('common.pleaseEnter') + field.label"
              :clearable="field.clearable !== false"
              @keyup.enter="handleSubmit"
            />

            <!-- Select -->
            <el-select
              v-else-if="field.type === 'select'"
              v-model="formData[field.prop]"
              :placeholder="field.placeholder || t('common.pleaseSelect') + field.label"
              :clearable="field.clearable !== false"
              style="width: 100%"
            >
              <el-option
                v-for="opt in field.options"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>

            <!-- DateRange -->
            <el-date-picker
              v-else-if="field.type === 'dateRange'"
              v-model="formData[field.prop]"
              type="daterange"
              :range-separator="t('common.to')"
              :start-placeholder="t('common.startDate')"
              :end-placeholder="t('common.endDate')"
              :clearable="field.clearable !== false"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />

            <!-- DatetimeRange -->
            <el-date-picker
              v-else-if="field.type === 'datetimeRange'"
              v-model="formData[field.prop]"
              type="datetimerange"
              :range-separator="t('common.to')"
              :start-placeholder="t('common.startTime')"
              :end-placeholder="t('common.endTime')"
              :clearable="field.clearable !== false"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </template>

      <!-- Buttons -->
      <el-col :span="6" class="search-actions">
        <el-form-item label-width="0">
          <el-button type="primary" :loading="loading" @click="handleSubmit">
            {{ t('common.search') }}
          </el-button>
          <el-button :disabled="loading" @click="handleReset">
            {{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>

<style scoped>
.search-actions {
  display: flex;
  align-items: flex-start;
}
</style>
