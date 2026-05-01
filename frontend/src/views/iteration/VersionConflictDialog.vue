<template>
  <el-dialog
    v-model="dialogVisible"
    :title="t('iteration.version.resolveConflict')"
    width="500px"
    :before-close="handleClose"
    destroy-on-close
  >
    <div v-if="conflict" class="conflict-info">
      <el-alert :title="conflict.message || t('iteration.version.conflictDetected')" type="warning" show-icon :closable="false" />
      
      <el-descriptions :column="1" border style="margin-top: 16px;">
        <el-descriptions-item :label="t('iteration.version.systemVersion')">
          <span class="version-system">{{ conflict.systemVersion || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('iteration.version.repoVersion')">
          <span class="version-repo">{{ conflict.repoVersion || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" @click="handleResolve('USE_SYSTEM')">
          {{ t('iteration.version.useSystem') }}
        </el-button>
        <el-button type="success" :loading="loading" @click="handleResolve('USE_REPO')">
          {{ t('iteration.version.useRepo') }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { iterationApi, type VersionConflict, type ConflictResolution } from '@/api/iterationApi'
import { handleError } from '@/utils/error'

const { t } = useI18n({ useScope: 'global' })

const emit = defineEmits<{
  (e: 'resolved'): void
}>()

const dialogVisible = ref(false)
const loading = ref(false)
const iterationKey = ref('')
const repoId = ref('')
const conflict = ref<VersionConflict | null>(null)

const open = async (key: string, rId: string) => {
  iterationKey.value = key
  repoId.value = rId
  dialogVisible.value = true
  
  try {
    conflict.value = await iterationApi.checkVersionConflict(key, rId)
  } catch (err) {
    handleError(err)
    dialogVisible.value = false
  }
}

const handleResolve = async (resolution: ConflictResolution) => {
  loading.value = true
  try {
    await iterationApi.resolveVersionConflict(iterationKey.value, repoId.value, resolution)
    ElMessage.success(t('common.success'))
    emit('resolved')
    handleClose()
  } catch (err) {
    handleError(err)
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  dialogVisible.value = false
  conflict.value = null
}

defineExpose({
  open
})
</script>

<style scoped>
.conflict-info {
  padding: 8px 0;
}

.version-system {
  font-family: 'Monaco', 'Menlo', monospace;
  color: var(--el-color-primary);
}

.version-repo {
  font-family: 'Monaco', 'Menlo', monospace;
  color: var(--el-color-success);
}
</style>
