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
import { isFailedResult, isSuccessfulResult, isSkippedResult, needsResultReview } from '@/utils/runPresentation'

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
  if (isSuccessfulResult(step.result)) return 'success'
  if (isFailedResult(step.result)) return 'error'
  if (isSkippedResult(step.result) || needsResultReview(step.result)) return 'wait'
  return 'process'
}

const activeStep = computed(() => {
  if (!props.steps) return 0
  let active = 0
  for (const step of stepKeys) {
    const s = findStep(step.key)
    if (s && isSuccessfulResult(s.result)) {
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
  if (isFailedResult(step?.result)) return 'error'
  if (needsResultReview(step?.result) || isSkippedResult(step?.result)) return 'wait'
  return 'process'
})

const attentionStep = computed(() => props.steps.find(step => needsResultReview(step.result))
  ?? props.steps.find(step => isFailedResult(step.result)))
const currentMessage = computed(() => attentionStep.value?.message ?? '')
const messageType = computed(() => needsResultReview(attentionStep.value?.result) ? 'warning' : 'error')

const getStepDesc = (step: any) => {
  const s = findStep(step.key)
  if (s?.message && (isFailedResult(s.result) || needsResultReview(s.result))) return s.message
  return t(`timeline.desc.${step.key}`)
}

</script>

<style scoped>
.mr-first-timeline { padding: 12px 0; }
.timeline-body { margin-top: 24px; }
</style>
