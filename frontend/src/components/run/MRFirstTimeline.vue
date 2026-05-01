<template>
  <div class="mr-first-timeline">
    <el-steps :active="activeStep" :process-status="processStatus" finish-status="success" align-center>
      <el-step 
        v-for="step in stepKeys" 
        :key="step.key" 
        :title="t(`timeline.steps.${step.key}`)" 
        :description="getStepDesc(step)"
        :status="getStepStatus(step.key)"
      />
    </el-steps>
    <div v-if="currentMessage" class="timeline-body">
      <el-alert :title="currentMessage" :type="messageType" show-icon :closable="false" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RunStep } from '@/api/runApi'

const props = defineProps<{
  steps: RunStep[]
}>()

const { t } = useI18n()

const stepKeys = [
  { key: 'ensureFeature' },
  { key: 'ensureRelease' },
  { key: 'ensureMR' },
  { key: 'tryMerge' }
]

// Helper to find the latest execution of a step type
const findStep = (key: string): RunStep | undefined => {
  if (!props.steps || props.steps.length === 0) return undefined
  // Map UI keys to backend ActionType
  const actionTypeMap: Record<string, string> = {
    ensureFeature: 'ENSURE_FEATURE',
    ensureRelease: 'ENSURE_RELEASE',
    ensureMR: 'ENSURE_MR',
    tryMerge: 'TRY_MERGE'
  }
  const targetType = actionTypeMap[key]
  // Return the last occurrence if multiple (e.g. retries)
  return props.steps.filter(s => s.actionType === targetType).pop()
}

const getStepStatus = (key: string) => {
  const step = findStep(key)
  if (!step) return 'wait'
  if (step.result === 'SUCCESS') return 'success'
  if (step.result === 'FAILED') return 'error'
  if (step.result === 'SKIPPED') return 'finish' // or wait
  return 'process'
}

const activeStep = computed(() => {
  if (!props.steps) return 0
  let active = 0
  for (const step of stepKeys) {
    const s = findStep(step.key)
    if (s && s.result === 'SUCCESS') {
      active++
    } else {
      break
    }
  }
  return active
})

const processStatus = computed(() => {
  const currentKey = stepKeys[activeStep.value]?.key
  if (!currentKey) return 'success'
  const step = findStep(currentKey)
  if (step?.result === 'FAILED') return 'error'
  return 'process'
})

const currentMessage = computed(() => {
  if (!props.steps) return ''
  const failedStep = props.steps.find(s => s.result === 'FAILED')
  if (failedStep) return failedStep.message
  return ''
})

const messageType = computed(() => {
  return currentMessage.value ? 'error' : 'info'
})

const getStepDesc = (step: any) => {
  const s = findStep(step.key)
  if (s?.message && s.result === 'FAILED') return s.message
  return t(`timeline.desc.${step.key}`)
}

</script>

<style scoped>
.mr-first-timeline { padding: 12px 0; }
.timeline-body { margin-top: 24px; }
</style>
