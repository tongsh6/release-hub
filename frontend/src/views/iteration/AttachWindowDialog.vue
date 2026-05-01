<template>
  <EntityDialog
    ref="entityRef"
    :title="t('iteration.detail.attachToWindow')"
    :confirm-text="t('common.confirm')"
    :cancel-text="t('common.cancel')"
    @confirm="submit"
    @opened="onOpened"
  >
    <template #default>
      <el-form ref="formRef" :model="form" label-width="120px">
        <el-form-item :label="t('releaseWindow.name')">
          <el-select v-model="form.windowId" :placeholder="t('common.pleaseSelect') + t('releaseWindow.name')">
            <el-option v-for="w in windows" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </template>
  </EntityDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import EntityDialog from '@/components/common/EntityDialog.vue'
import { releaseWindowApi, type ReleaseWindowView } from '@/api/modules/releaseWindow'
import { handleError } from '@/utils/error'

const { t } = useI18n()
const emit = defineEmits<{ (e: 'success'): void }>()

const entityRef = ref<InstanceType<typeof EntityDialog>>()
const formRef = ref<FormInstance>()

const windows = ref<ReleaseWindowView[]>([])
const iterationKeyRef = ref<string>('')
const loading = ref(false)
const saving = ref(false)
const form = ref<{ windowId: string | null }>({ windowId: null })

const open = async (iterationKey: string) => {
  iterationKeyRef.value = iterationKey
  form.value.windowId = null
  entityRef.value?.open()
  await loadWindows()
}

const loadWindows = async () => {
  loading.value = true
  try {
    const result = await releaseWindowApi.list({ page: 1, pageSize: 100 } as any)
    windows.value = result.list
  } catch (err) {
    handleError(err)
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!form.value.windowId) return
  saving.value = true
  try {
    await releaseWindowApi.attach(form.value.windowId, iterationKeyRef.value)
    ElMessage.success(t('common.save'))
    emit('success')
    entityRef.value?.close()
  } catch (err) {
    handleError(err)
  } finally {
    saving.value = false
  }
}

const onOpened = () => {
  formRef.value?.clearValidate()
}

defineExpose({ open })
</script>

<style scoped>
</style>
