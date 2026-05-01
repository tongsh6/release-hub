<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const { title, width, confirmText, cancelText } = withDefaults(defineProps<{
  title?: string
  width?: string | number
  confirmText?: string
  cancelText?: string
}>(), {
  width: '720px',
  title: '',
  confirmText: '',
  cancelText: ''
})

const emit = defineEmits<{
  (e: 'success', payload?: any): void
  (e: 'opened', payload?: any): void
  (e: 'cancel'): void
  (e: 'confirm'): void
}>()

const visible = ref(false)
const loading = ref(false)
const payloadData = ref<any>(null)

const open = (payload?: any) => {
  payloadData.value = payload
  visible.value = true
}

const close = () => {
  visible.value = false
  loading.value = false
  payloadData.value = null
}

const setLoading = (v: boolean) => {
  loading.value = v
}

const handleOpened = () => {
  emit('opened', payloadData.value)
}

const handleCancel = () => {
  emit('cancel')
  close()
}

const handleConfirm = () => {
  emit('confirm')
}

defineExpose({
  open,
  close,
  setLoading
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title || t('common.edit')"
    :width="width"
    :close-on-click-modal="false"
    destroy-on-close
    @opened="handleOpened"
  >
    <div v-loading="loading" class="dialog-body">
      <slot :payload="payloadData" />
    </div>

    <template #footer>
      <slot name="footer" :loading="loading" :close="close" :confirm="handleConfirm">
        <div class="dialog-footer">
          <el-button :disabled="loading" @click="handleCancel">
            {{ cancelText || t('common.cancel') }}
          </el-button>
          <el-button type="primary" :loading="loading" @click="handleConfirm">
            {{ confirmText || t('common.save') }}
          </el-button>
        </div>
      </slot>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-body {
  padding: 10px 0;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
