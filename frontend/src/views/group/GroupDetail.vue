<template>
  <div v-loading="loading" class="group-detail-page page-container">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="handleBack">{{ t('common.back') }}</el-button>
      <span class="page-title">{{ detail?.name || t('group.detail') }}</span>
      <div class="page-actions">
        <el-button v-perm.disable="'group:write'" type="primary" @click="openEdit()">{{ t('common.edit') }}</el-button>
        <el-button v-perm.disable="'group:delete'" type="danger" @click="handleDelete()">{{ t('common.delete') }}</el-button>
      </div>
    </div>
    <el-card>
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('group.name')">
          {{ detail?.name }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('group.code')">
          {{ detail?.code }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detail?.parentCode" :label="t('group.parentCode')">
          {{ detail?.parentCode }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-empty v-if="!loading && !detail" :description="t('group.notFound')" />
    <GroupDialog ref="dialogRef" @success="load" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { groupApi, type GroupView } from '@/api/modules/group'
import GroupDialog from './GroupDialog.vue'
import { handleError } from '@/utils/error'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref<GroupView | null>(null)
const dialogRef = ref<InstanceType<typeof GroupDialog>>()

const load = async () => {
  const id = route.params.id as string
  if (!id) return
  loading.value = true
  try {
    detail.value = await groupApi.get(id)
  } catch (e) {
    handleError(e)
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push({ name: 'Groups' })
}

const openEdit = () => {
  if (!detail.value) return
  dialogRef.value?.openEdit(detail.value.id!)
}

const handleDelete = async () => {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(t('group.confirmDelete'), t('common.warning'), { type: 'warning' })
    await groupApi.remove(detail.value.id!)
    ElMessage.success(t('common.success'))
    handleBack()
  } catch (error) {
    if (error !== 'cancel') {
      handleError(error)
    }
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
/* 页面特定样式 - 通用样式已移至 index.css */
</style>
