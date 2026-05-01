<template>
  <div class="triple-badge">
    <el-tag size="small" type="info">{{ windowKey }}</el-tag>
    <el-tag size="small" type="success">{{ repo }}</el-tag>
    <el-tag size="small" type="warning">{{ iterationKey }}</el-tag>
    <el-button link type="primary" size="small" @click="copy">{{ t('common.copy') }}</el-button>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ windowKey: string; repo: string; iterationKey: string }>()
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
function copy() {
  const text = `${props.windowKey} / ${props.repo} / ${props.iterationKey}`
  navigator.clipboard?.writeText(text)
}
</script>

<style scoped>
.triple-badge { display: inline-flex; gap: 6px; align-items: center; }
</style>
