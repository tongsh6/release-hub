<template>
  <div class="evidence-panel">
    <el-descriptions :column="2" border>
      <el-descriptions-item :label="t('evidence.projectId')">{{ evidence.projectId || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="t('evidence.mrIid')">{{ evidence.mrIid || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="t('evidence.source')">{{ evidence.source || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="t('evidence.target')">{{ evidence.target || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="t('evidence.mrUrl')" :span="2">
        <a :href="evidence.mrUrl" target="_blank">{{ evidence.mrUrl || '-' }}</a>
      </el-descriptions-item>
      <el-descriptions-item :label="t('evidence.blockReason')" :span="2">{{ evidence.blockReason || '-' }}</el-descriptions-item>
    </el-descriptions>
    <div class="actions">
      <el-button size="small" @click="copy">{{ t('common.copy') }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ evidence: Record<string, any> }>()
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
function copy() {
  navigator.clipboard?.writeText(JSON.stringify(props.evidence || {}, null, 2))
}
</script>

<style scoped>
.evidence-panel { background: #fff; padding: 12px; border-radius: 4px; }
.actions { display: flex; justify-content: flex-end; margin-top: 10px; }
</style>
