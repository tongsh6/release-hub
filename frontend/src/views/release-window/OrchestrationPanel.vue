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
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { releaseWindowApi, type PlanItemView } from '@/api/modules/releaseWindow'
import { runApi, type Run, type RunDetail } from '@/api/runApi'
import { isFailedResult, runStatusTagType, runTypeKey } from '@/utils/runPresentation'
import { handleError } from '@/utils/error'
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
const selectedRepoId = ref('')
const selectedIterationKeys = ref<string[]>([])
const blockPolicy = ref<'FAIL_FAST' | 'CONTINUE_ON_BLOCK'>('FAIL_FAST')
const failFast = computed(() => blockPolicy.value === 'FAIL_FAST')
const orchestrating = ref(false)
const savedPlanVisible = ref(false)
const recentRuns = ref<Run[]>([])
const latestRunDetail = ref<RunDetail>()

const availableRepoIds = computed(() => [...new Set(props.planItems.map(item => item.repoId))].sort())
const repositoryPlan = computed(() => props.planItems.filter(item => !selectedRepoId.value || item.repoId === selectedRepoId.value))
const availableIterationKeys = computed(() => [...new Set(repositoryPlan.value.map(item => item.iterationKey))])
const selectedPlan = computed(() => repositoryPlan.value.filter(item => selectedIterationKeys.value.includes(item.iterationKey)))
const repoIds = computed(() => [...new Set(selectedPlan.value.map(item => item.repoId))].sort())
const iterationKeys = computed(() => [...new Set(selectedPlan.value.map(item => item.iterationKey))])
const canExecute = computed(() => props.scopeReady && !props.frozen && !orchestrating.value
  && ['DRAFT', 'PUBLISHED'].includes(props.windowStatus) && selectedPlan.value.length > 0)
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
watch(() => props.windowId, () => {
  selectedRepoId.value = ''
  blockPolicy.value = 'FAIL_FAST'
  latestRunDetail.value = undefined
  recentRuns.value = []
})

function requestScope() {
  return { repoIds: [...repoIds.value], iterationKeys: [...iterationKeys.value], failFast: failFast.value }
}
async function handleOrchestrate() {
  if (!canExecute.value) return
  const windowId = props.windowId
  const request = requestScope()
  try {
    await ElMessageBox.confirm(t('orchestration.confirmIntegrate'), t('common.confirm'), { type: 'warning' })
    if (!canExecute.value || props.windowId !== windowId || JSON.stringify(request) !== JSON.stringify(requestScope())) {
      ElMessage.warning(t('orchestration.scopeChanged'))
      return
    }
    orchestrating.value = true
    const runId = await releaseWindowApi.orchestrate(windowId, request)
    if (props.windowId !== windowId) return
    if (runId) latestRunDetail.value = await runApi.getRunById(String(runId))
    await loadRecentRuns()
    emit('refresh')
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  } finally { orchestrating.value = false }
}
async function loadRecentRuns() {
  const windowKey = props.windowKey
  try {
    const result = await runApi.list({ page: 1, pageSize: 5, windowKey })
    if (props.windowKey !== windowKey) return
    recentRuns.value = result.list
    if (result.list[0]?.id) {
      const detail = await runApi.getRunById(result.list[0].id)
      if (props.windowKey === windowKey) latestRunDetail.value = detail
    }
  } catch (error) { handleError(error) }
}
function viewRunDetail(run: Run | RunDetail) { router.push(`/runs/${run.id}`) }
watch(() => props.windowKey, loadRecentRuns, { immediate: true })
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
.failed-run-evidence { color: var(--el-color-danger); overflow-wrap: anywhere; }
</style>
