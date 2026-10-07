<template>
  <el-dialog :model-value="modelValue" :title="t('runPlan.title')" width="90%" @update:model-value="emit('update:modelValue', $event)">
    <div class="plan-toolbar">
      <el-select v-model="selectedId" :placeholder="t('runPlan.recent')" class="plan-selector" @change="loadSelected">
        <el-option v-for="entry in plans" :key="entry.runId" :value="entry.runId" :label="`${entry.createdAt} · ${entry.itemCount} · ${entry.runId}`" />
      </el-select>
      <el-button :disabled="loading || saving" @click="loadList">{{ t('common.refresh') }}</el-button>
      <el-button v-perm.disable="'release-window:write'" type="primary" :disabled="!canCreate || loading" :loading="saving" @click="createPreview">
        {{ t('runPlan.create') }}
      </el-button>
    </div>
    <div v-loading="loading || saving">
      <RunPlanSnapshotView v-if="plan" :plan="plan" />
      <el-empty v-else :description="t('runPlan.empty')" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { runPlanApi, type RunPlanPreview, type RunPlanSummary } from '@/api/runPlanApi'
import RunPlanSnapshotView from '@/components/run/RunPlanSnapshotView.vue'
import { handleError } from '@/utils/error'

const props = defineProps<{ modelValue: boolean; windowId: string; repoIds: string[]; iterationKeys: string[]; canCreate: boolean }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'created'): void }>()
const { t } = useI18n()
const plans = ref<RunPlanSummary[]>([])
const selectedId = ref('')
const plan = ref<RunPlanPreview>()
const loading = ref(false)
const saving = ref(false)
let pendingKey = ''
let pendingSelection = ''

async function loadSelected() {
  const id = selectedId.value
  plan.value = undefined
  if (!id) return
  loading.value = true
  try {
    const result = await runPlanApi.get(id)
    if (selectedId.value === id) plan.value = result
  } catch (error) { handleError(error) } finally { loading.value = false }
}

async function loadList() {
  const windowId = props.windowId
  loading.value = true
  try {
    const entries = await runPlanApi.list(windowId)
    if (props.windowId !== windowId) return
    plans.value = entries
    if (!entries.some(entry => entry.runId === selectedId.value)) selectedId.value = entries[0]?.runId || ''
    await loadSelected()
  } catch (error) { handleError(error) } finally { loading.value = false }
}

async function createPreview() {
  if (saving.value || !props.canCreate) return
  const windowId = props.windowId
  const body = { repoIds: [...new Set(props.repoIds)].sort(), iterationKeys: [...new Set(props.iterationKeys)].sort(), failFast: true }
  const selection = JSON.stringify({ windowId, ...body })
  saving.value = true
  try {
    if (!pendingKey || selection !== pendingSelection) {
      // 不依赖仅在安全上下文提供的 randomUUID，兼容现有 HTTP 自托管入口。
      pendingKey = Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
      pendingSelection = selection
    }
    const saved = await runPlanApi.create(windowId, body, pendingKey)
    if (props.windowId !== windowId) return
    plan.value = saved
    selectedId.value = saved.runId
    if (!plans.value.some(entry => entry.runId === saved.runId)) {
      plans.value = [{ runId: saved.runId, operator: saved.operator, createdAt: saved.createdAt, itemCount: saved.snapshot.items.length }, ...plans.value].slice(0, 20)
    }
    pendingKey = ''
    emit('created')
  } catch (error) {
    // 未收到响应时保留 key，让用户重试得到同一份已保存计划。
    handleError(error)
  } finally { saving.value = false }
}

watch(() => [props.modelValue, props.windowId] as const, ([visible, windowId], previous) => {
  if (previous && windowId !== previous[1]) {
    plans.value = []; selectedId.value = ''; plan.value = undefined; pendingKey = ''
  }
  if (visible) void loadList()
}, { immediate: true })
</script>

<style scoped>
.plan-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 18px; }
.plan-selector { flex: 1; min-width: 240px; }
</style>
