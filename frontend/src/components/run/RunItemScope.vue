<template>
  <div v-if="runType === 'WINDOW_CLEANUP'" class="run-item-scope">
    <strong>{{ t('run.detail.repositoryCleanup') }}</strong>
    <div>{{ t('run.detail.participatingIterations') }}: {{ participants?.length ? participants.join(', ') : t('run.detail.notRecorded') }}</div>
    <small v-if="item.metadata['cleanup.referencePolicy'] === 'RETAIN'">{{ t('run.detail.referencesRetained') }}</small>
  </div>
  <span v-else>{{ item.iterationKey ?? t('run.detail.notRecorded') }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RunItem } from '@/api/runApi'
import type { RunType } from '@/types/dto'
import { participatingIterationKeys } from '@/utils/runPresentation'

const props = defineProps<{ item: Pick<RunItem, 'iterationKey' | 'metadata'>; runType?: RunType }>()
const { t } = useI18n()
const participants = computed(() => participatingIterationKeys(props.item))
</script>

<style scoped>
.run-item-scope { overflow-wrap: anywhere; }
.run-item-scope small { color: var(--el-text-color-secondary); }
</style>
