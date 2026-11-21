<template>
  <el-card class="orchestration-panel">
    <template #header>
      <div class="panel-header">
        <strong>{{ t('orchestration.title') }}</strong>
        <el-tag size="small">{{ windowStatus }}</el-tag>
      </div>
    </template>
    <p class="direction">{{ t('orchestration.mergeDirection') }}: <strong>feature → release</strong></p>
    <p class="notice">{{ t('orchestration.closeoutNotice') }}</p>
    <el-alert v-if="!scopeReady" type="warning" :closable="false" :title="t('orchestration.scopeUnavailable')" />

    <div class="scope-controls">
      <label>
        {{ t('orchestration.repositoryScope') }}
        <select v-model="selectedRepoId" data-field="repository" :disabled="!scopeReady || orchestrating">
          <option value="">{{ t('orchestration.allRepositories') }}</option>
          <option v-if="selectedRepoId && !availableRepoIds.includes(selectedRepoId)" :value="selectedRepoId" disabled>{{ selectedRepoId }}</option>
          <option v-for="repoId in availableRepoIds" :key="repoId" :value="repoId">{{ repoId }}</option>
        </select>
      </label>
      <label>
        {{ t('orchestration.blockPolicy') }}
        <select v-model="blockPolicy" data-field="block-policy" :disabled="orchestrating">
          <option value="FAIL_FAST">{{ t('orchestration.failFast') }}</option>
          <option value="CONTINUE_ON_BLOCK">{{ t('orchestration.continueOnBlock') }}</option>
        </select>
      </label>
    </div>
    <fieldset class="iteration-scope" :disabled="!scopeReady || orchestrating">
      <legend>{{ t('orchestration.iterationScope') }}</legend>
      <label v-for="iterationKey in availableIterationKeys" :key="iterationKey">
        <input v-model="selectedIterationKeys" type="checkbox" :value="iterationKey" />
        {{ iterationKey }}
      </label>
      <span v-if="!availableIterationKeys.length">{{ t('common.noData') }}</span>
    </fieldset>
    <p class="scope-summary">
      {{ t('orchestration.repoCount') }}: {{ repoIds.length }} ·
      {{ t('orchestration.iterationCount') }}: {{ iterationKeys.length }}
    </p>
    <div class="panel-actions">
      <el-button size="small" @click="savedPlanVisible = true">{{ t('runPlan.title') }}</el-button>
      <el-button
        v-perm.disable="'release-window:write'"
        data-action="integrate"
        type="primary"
        :disabled="!canExecute"
        :loading="orchestrating"
        @click="handleOrchestrate"
      >
        {{ t('orchestration.executeIntegration') }}
      </el-button>
      <el-button
        v-if="windowStatus !== 'CLOSED'"
        v-perm.disable="'release-window:write'"
        size="small"
        :disabled="frozen || !scopeReady"
        @click="emit('open-version-update')"
      >
        {{ t('releaseWindow.versionUpdate.execute') }}
      </el-button>
    </div>

    <section v-if="savedIntents.length" class="recent-runs" :aria-label="t('run.request.saved')">
      <div class="section-title">{{ t('run.request.saved') }}</div>
      <div v-for="intent in savedIntents" :key="intent.key" class="saved-request">
        <span>{{ intent.runId ?? t('run.request.unconfirmed') }} · {{ savedScopeLabel(intent) }}</span>
        <el-button
          size="small" data-action="retrieve-receipt" :data-intent-key="intent.key"
          :disabled="orchestrating" @click="retrieveReceipt(intent)">
          {{ t(intent.runId ? 'run.request.openReceipt' : 'run.request.retrieve') }}
        </el-button>
      </div>
    </section>
    <p v-if="observationPaused" data-test="run-observation-paused">
      {{ t('run.observation.paused') }}
      <el-button size="small" @click="observation.refresh">{{ t('common.refresh') }}</el-button>
    </p>
    <p v-else-if="observationActive">{{ t('run.observation.active') }}</p>

    <p v-if="acceptedRunId && latestRunDetail?.id !== acceptedRunId" class="run-review-summary" data-test="accepted-run">
      {{ t('run.request.accepted') }}: {{ acceptedRunId }}
      <el-button link type="primary" @click="router.push(`/runs/${acceptedRunId}`)">{{ t('orchestration.viewRunDetail') }}</el-button>
    </p>
    <div v-if="latestRunDetail" class="run-review-summary">
      <div class="section-title">{{ t('orchestration.latestRun') }}</div>
      <div class="run-review-grid">
        <span>{{ t('run.columns.runId') }}: {{ latestRunDetail.id }}</span>
        <span>{{ t(runTypeKey(latestRunDetail.runType)) }}</span>
        <el-tag :type="runStatusTagType(latestRunDetail.status)" size="small">{{ latestRunDetail.status }}</el-tag>
        <span>{{ t('orchestration.runItems') }}: {{ latestRunDetail.items.length }}</span>
        <span>{{ t('orchestration.failedItems') }}: {{ failedItems.length }}</span>
      </div>
      <el-alert v-if="latestRunDetail.status === 'NEEDS_REVIEW'" type="warning" :closable="false" :title="t('run.detail.needsReview')" />
      <div v-if="firstFailedItem" class="failed-run-evidence">
        <p>{{ t('orchestration.failureContext') }}: {{ firstFailedItem.windowKey }} / {{ firstFailedItem.repoId }} / {{ firstFailedItem.iterationKey }}</p>
        <p v-if="firstFailedStep">{{ t('orchestration.failedStep') }}: {{ firstFailedStep.actionType }} · {{ firstFailedStep.result }}</p>
        <p v-if="firstFailedStep?.message">{{ t('orchestration.failureReason') }}: {{ firstFailedStep.message }}</p>
      </div>
      <el-button link type="primary" @click="viewRunDetail(latestRunDetail)">{{ t('orchestration.viewRunDetail') }}</el-button>
    </div>
    <div v-if="recentRuns.length" class="recent-runs">
      <div class="section-title">{{ t('orchestration.recentRuns') }}</div>
      <el-table :data="recentRuns" size="small" border>
        <el-table-column prop="id" :label="t('run.columns.runId')" min-width="170" />
        <el-table-column prop="runType" :label="t('run.columns.type')" min-width="170">
          <template #default="{ row }">{{ t(runTypeKey(row.runType)) }}</template>
        </el-table-column>
        <el-table-column :label="t('run.columns.status')" width="150">
          <template #default="{ row }"><el-tag :type="runStatusTagType(row.status)" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="startedAt" :label="t('run.columns.start')" min-width="170" />
        <el-table-column :label="t('common.actions')" width="100">
          <template #default="{ row }"><el-button link type="primary" @click="viewRunDetail(row)">{{ t('common.detail') }}</el-button></template>
        </el-table-column>
      </el-table>
    </div>
    <RunPlanPreviewDialog
      v-model="savedPlanVisible"
      :window-id="windowId"
      :repo-ids="repoIds"
      :iteration-keys="iterationKeys"
      :fail-fast="failFast"
      :can-create="canExecute"
      @created="loadRecentRuns"
    />
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { releaseWindowApi, type PlanItemView, type OrchestrateRequest } from '@/api/modules/releaseWindow'
import { runApi, type Run, type RunDetail } from '@/api/runApi'
import { isFailedResult, runStatusTagType, runTypeKey } from '@/utils/runPresentation'
import { handleError } from '@/utils/error'
import { useUserStore } from '@/stores/user'
import { readExecutionIntents, saveExecutionIntent, acknowledgeExecutionIntent, forgetRejectedNewIntent, type ExecutionIntent } from '@/utils/executionIntent'
import { useRunObservation } from '@/composables/useRunObservation'
import { ElMessage, ElMessageBox } from 'element-plus'
import RunPlanPreviewDialog from './RunPlanPreviewDialog.vue'

const props = defineProps<{
  windowId: string
  windowKey: string
  windowStatus: string
  frozen: boolean
  planItems: PlanItemView[]
  scopeReady: boolean
}>()
const emit = defineEmits<{ (e: 'open-version-update'): void; (e: 'refresh'): void }>()
const { t } = useI18n()
const router = useRouter()
const user = useUserStore()
const selectedRepoId = ref('')
const selectedIterationKeys = ref<string[]>([])
const blockPolicy = ref<'FAIL_FAST' | 'CONTINUE_ON_BLOCK'>('FAIL_FAST')
const failFast = computed(() => blockPolicy.value === 'FAIL_FAST')
const orchestrating = ref(false)
const savedPlanVisible = ref(false)
const recentRuns = ref<Run[]>([])
const savedIntents = ref<ExecutionIntent[]>([])
const acceptedRunId = ref<string>()
const observedRunId = ref<string>()
const observation = useRunObservation(() => observedRunId.value, () => user.profile?.username)
const latestRunDetail = observation.detail
const observationPaused = observation.paused
const observationActive = observation.active
let disposed = false
let viewGeneration = 0
let recentLoadGeneration = 0

function isCurrentView(windowId: string, generation: number): boolean {
  return !disposed && props.windowId === windowId && viewGeneration === generation
}
onUnmounted(() => { disposed = true; viewGeneration++ })

const availableRepoIds = computed(() => [...new Set(props.planItems.map(item => item.repoId))].sort())
const repositoryPlan = computed(() => props.planItems.filter(item => !selectedRepoId.value || item.repoId === selectedRepoId.value))
const availableIterationKeys = computed(() => [...new Set(repositoryPlan.value.map(item => item.iterationKey))])
const selectedPlan = computed(() => repositoryPlan.value.filter(item => selectedIterationKeys.value.includes(item.iterationKey)))
const repoIds = computed(() => [...new Set(selectedPlan.value.map(item => item.repoId))].sort())
const iterationKeys = computed(() => [...new Set(selectedPlan.value.map(item => item.iterationKey))])
const scopeExecutable = computed(() => props.scopeReady && !props.frozen
  && ['DRAFT', 'PUBLISHED'].includes(props.windowStatus) && selectedPlan.value.length > 0)
const canExecute = computed(() => scopeExecutable.value && !orchestrating.value)
const failedItems = computed(() => latestRunDetail.value?.items.filter(item => isFailedResult(item.finalResult)) ?? [])
const firstFailedItem = computed(() => failedItems.value[0])
const firstFailedStep = computed(() => firstFailedItem.value?.steps.find(step => isFailedResult(step.result)))

let selectionContext: string | undefined
watch([() => props.windowId, selectedRepoId, availableIterationKeys, () => props.scopeReady],
  ([windowId, repoId, keys, ready]) => {
    if (!ready) return
    const context = JSON.stringify([windowId, repoId])
    // Refreshes may remove choices, but may never silently broaden the user's scope.
    selectedIterationKeys.value = selectionContext === context
      ? selectedIterationKeys.value.filter(key => keys.includes(key))
      : [...keys]
    selectionContext = context
  }, { immediate: true })

function requestScope(): OrchestrateRequest {
  return { repoIds: [...repoIds.value].sort(), iterationKeys: [...iterationKeys.value].sort(), failFast: failFast.value }
}
function storedScope(intent: ExecutionIntent): OrchestrateRequest {
  const value: unknown = JSON.parse(intent.payload)
  if (!value || typeof value !== 'object' || !('repoIds' in value) || !Array.isArray(value.repoIds)
    || !value.repoIds.length || !value.repoIds.every(id => typeof id === 'string' && id.trim())
    || !('iterationKeys' in value) || !Array.isArray(value.iterationKeys)
    || !value.iterationKeys.every(key => typeof key === 'string' && key.trim())
    || !('failFast' in value) || typeof value.failFast !== 'boolean') {
    throw new Error(t('run.request.invalidSaved'))
  }
  return { repoIds: value.repoIds, iterationKeys: value.iterationKeys, failFast: value.failFast }
}
function savedScopeLabel(intent: ExecutionIntent): string {
  try {
    const scope = storedScope(intent)
    return t('run.request.scope', { repos: scope.repoIds.join(', '), iterations: scope.iterationKeys.join(', ') })
  } catch { return t('run.request.invalidSaved') }
}
function loadSavedIntents(subject: string, windowId: string) {
  savedIntents.value = readExecutionIntents(subject, `ORCHESTRATE:${windowId}`)
}
async function receiveIntent(intent: ExecutionIntent, subject: string, windowId: string, generation: number, firstAttempt = false) {
  const operation = `ORCHESTRATE:${windowId}`
  // Confirmation can outlive an earlier response from the previous view.
  const saved = readExecutionIntents(subject, operation).find(value => value.key === intent.key)
  if (!saved || saved.payload !== intent.payload) throw new Error(t('run.request.invalidSaved'))
  // A known receipt is only read. No current scope is used to reconstruct an old request.
  let runId = saved.runId
  if (!runId) {
    try {
      runId = await releaseWindowApi.orchestrate(windowId, storedScope(intent), intent.key)
    } catch (error) {
      if (firstAttempt && isCurrentView(windowId, generation) && user.profile?.username === subject
        && forgetRejectedNewIntent(subject, operation, intent.key, error)) loadSavedIntents(subject, windowId)
      throw error
    }
  }
  acknowledgeExecutionIntent(subject, operation, intent.key, runId)
  if (!isCurrentView(windowId, generation) || user.profile?.username !== subject) return
  loadSavedIntents(subject, windowId)
  acceptedRunId.value = runId
  recentLoadGeneration++
  if (observedRunId.value === runId) await observation.refresh()
  else observedRunId.value = runId
  await loadRecentRuns()
  if (isCurrentView(windowId, generation) && user.profile?.username === subject) emit('refresh')
}
async function handleOrchestrate() {
  if (disposed || !canExecute.value) return
  const subject = user.profile?.username
  if (!subject) { ElMessage.warning(t('common.permissionDenied')); return }
  const windowId = props.windowId
  const generation = viewGeneration
  const payload = JSON.stringify(requestScope())
  const operation = `ORCHESTRATE:${windowId}`
  orchestrating.value = true
  try {
    const pending = readExecutionIntents(subject, operation).find(intent => !intent.runId)
    if (pending && pending.payload !== payload) {
      ElMessage.warning(t('run.request.pendingFirst'))
      return
    }
    const confirmKey = pending ? 'run.request.repeatUnconfirmed' : 'orchestration.confirmIntegrate'
    await ElMessageBox.confirm(t(confirmKey), t('common.confirm'), { type: 'warning' })
    if (!isCurrentView(windowId, generation)) return
    if (!scopeExecutable.value || user.profile?.username !== subject || payload !== JSON.stringify(requestScope())) {
      ElMessage.warning(t('orchestration.scopeChanged'))
      return
    }
    const previousKey = readExecutionIntents(subject, operation)[0]?.key
    const intent = pending ?? saveExecutionIntent(subject, operation, payload)
    loadSavedIntents(subject, windowId)
    await receiveIntent(intent, subject, windowId, generation, !pending && intent.key !== previousKey)
  } catch (error) {
    if (isCurrentView(windowId, generation) && user.profile?.username === subject
      && error !== 'cancel' && error !== 'close') handleError(error)
  } finally { if (isCurrentView(windowId, generation)) orchestrating.value = false }
}
async function retrieveReceipt(saved: ExecutionIntent) {
  const subject = user.profile?.username
  if (disposed || orchestrating.value || !subject) return
  const windowId = props.windowId
  const generation = viewGeneration
  orchestrating.value = true
  try {
    const intent = readExecutionIntents(subject, `ORCHESTRATE:${windowId}`).find(value => value.key === saved.key)
    if (!intent) throw new Error(t('run.request.invalidSaved'))
    if (!intent.runId) await ElMessageBox.confirm(t('run.request.repeatUnconfirmed'), t('common.confirm'), { type: 'warning' })
    if (!isCurrentView(windowId, generation) || user.profile?.username !== subject) return
    await receiveIntent(intent, subject, windowId, generation)
  } catch (error) {
    if (isCurrentView(windowId, generation) && user.profile?.username === subject
      && error !== 'cancel' && error !== 'close') handleError(error)
  } finally { if (isCurrentView(windowId, generation)) orchestrating.value = false }
}
async function loadRecentRuns() {
  const subject = user.profile?.username
  if (!subject) return
  const windowId = props.windowId
  const windowKey = props.windowKey
  const generation = viewGeneration
  const requestGeneration = ++recentLoadGeneration
  const isCurrent = () => isCurrentView(windowId, generation) && user.profile?.username === subject
    && props.windowKey === windowKey && requestGeneration === recentLoadGeneration
  try {
    const result = await runApi.list({ page: 1, pageSize: 5, windowKey })
    if (!isCurrent()) return
    recentRuns.value = result.list
    if (!acceptedRunId.value) observedRunId.value = result.list[0]?.id
  } catch (error) { if (isCurrent()) handleError(error) }
}
function viewRunDetail(run: Run | RunDetail) { router.push(`/runs/${run.id}`) }
watch([() => props.windowId, () => props.windowKey, () => user.profile?.username], ([windowId, , subject]) => {
  viewGeneration++
  recentLoadGeneration++
  orchestrating.value = false
  selectedRepoId.value = ''
  blockPolicy.value = 'FAIL_FAST'
  acceptedRunId.value = undefined
  observedRunId.value = undefined
  recentRuns.value = []
  savedIntents.value = []
  if (!subject) return
  try {
    loadSavedIntents(subject, windowId)
    acceptedRunId.value = savedIntents.value.find(intent => intent.runId)?.runId
    observedRunId.value = acceptedRunId.value
  } catch (error) { handleError(error) }
  void loadRecentRuns()
}, { immediate: true })
</script>

<style scoped>
.orchestration-panel { margin-top: 16px; }
.panel-header, .panel-actions, .scope-controls, .run-review-grid { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; }
.panel-header { justify-content: space-between; }
.direction { margin: 0 0 8px; }
.notice, .scope-summary { color: var(--el-text-color-secondary); }
.scope-controls { margin: 20px 0 16px; }
.scope-controls label { display: flex; flex-direction: column; gap: 8px; min-width: 240px; }
.scope-controls select { padding: 8px 12px; border: 1px solid var(--el-border-color); border-radius: 4px; color: var(--el-text-color-primary); background: var(--el-bg-color); }
.iteration-scope { display: flex; flex-wrap: wrap; gap: 16px; border: 1px solid var(--el-border-color); border-radius: 4px; padding: 16px; }
.iteration-scope label { display: flex; align-items: center; gap: 8px; }
.run-review-summary, .recent-runs { margin-top: 24px; border-top: 1px solid var(--el-border-color-lighter); padding-top: 16px; }
.section-title { font-weight: 600; margin-bottom: 12px; }
.saved-request { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; overflow-wrap: anywhere; }
.failed-run-evidence { color: var(--el-color-danger); overflow-wrap: anywhere; }
</style>
