<template>
  <section class="git-operations" :aria-label="t('gitOperation.title')">
    <header>
      <h3>{{ t('gitOperation.title') }}</h3>
      <button type="button" :disabled="loading" @click="load">{{ t('common.refresh') }}</button>
    </header>
    <p class="notice">{{ t('gitOperation.readOnlyNotice') }}</p>
    <p v-if="rows.some(row => row.blocked)" class="blocked" role="alert">{{ t('gitOperation.blocked') }}</p>
    <p v-if="!loading && rows.length === 0">{{ t('common.noData') }}</p>
    <table v-if="rows.length">
      <thead><tr><th>{{ t('gitOperation.action') }}</th><th>{{ t('gitOperation.state') }}</th><th>{{ t('gitOperation.time') }}</th><th>{{ t('common.actions') }}</th></tr></thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ t(`gitOperation.actions.${row.action}`) }}</td>
          <td>{{ t(`gitOperation.states.${row.state}`) }}</td>
          <td>{{ row.createdAt }}</td>
          <td><button type="button" data-action="detail" :disabled="busy" @click="select(row.id)">{{ t('common.detail') }}</button></td>
        </tr>
      </tbody>
    </table>
    <nav v-if="rows.length || page > 0" :aria-label="t('gitOperation.pagination')">
      <button type="button" :disabled="page === 0 || loading" @click="changePage(-1)">{{ t('gitOperation.previous') }}</button>
      <span>{{ page + 1 }}</span>
      <button type="button" :disabled="rows.length < 20 || loading" @click="changePage(1)">{{ t('gitOperation.next') }}</button>
    </nav>
    <article v-if="selected" class="operation-detail">
      <header>
        <strong>{{ selected.operation.id }}</strong>
        <button type="button" data-action="reconcile" :disabled="busy" @click="reconcile">{{ t('gitOperation.reconcile') }}</button>
      </header>
      <p>{{ t('gitOperation.operator') }}: {{ selected.operation.operator }}</p>
      <p v-if="selected.operation.blocked" class="blocked">{{ t('gitOperation.blocked') }}</p>
      <p v-if="selected.operation.reason">{{ t('gitOperation.reason') }}: {{ selected.operation.reason }}</p>
      <p v-if="selected.operation.runId">{{ t('gitOperation.sourceRun') }}: {{ selected.operation.runId }}</p>
      <h4>{{ t('gitOperation.timeline') }}</h4>
      <ol>
        <li v-for="event in selected.events" :key="`${event.attemptId}-${event.sequence}`">
          <strong>{{ t(`gitOperation.events.${event.type}`) }}</strong> · {{ event.at }}
          <pre v-if="event.evidenceJson !== '{}'">{{ formatEvidence(event.evidenceJson) }}</pre>
        </li>
      </ol>
      <h4>{{ t('gitOperation.observations') }}</h4>
      <p v-if="selected.observations.length === 0">{{ t('common.noData') }}</p>
      <ol>
        <li v-for="observation in selected.observations" :key="observation.id">
          <strong>{{ t(`gitOperation.observed.${observation.status}`) }}</strong> · {{ observation.observedAt }}
          <pre v-if="observation.evidenceJson !== '{}'">{{ formatEvidence(observation.evidenceJson) }}</pre>
        </li>
      </ol>
      <p v-if="selected.moreObservations">{{ t('gitOperation.latestObservations') }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { gitOperationApi, type GitOperation, type GitOperationDetail } from '@/api/gitOperationApi'
import { handleError } from '@/utils/error'
import { createRequestKey } from '@/utils/executionIntent'

const props = defineProps<{ repoId?: string; runId?: string }>()
const { t } = useI18n()
const rows = ref<GitOperation[]>([])
const selected = ref<GitOperationDetail | null>(null)
const loading = ref(false)
const busy = ref(false)
const page = ref(0)
let generation = 0
let selectionGeneration = 0
let retryRequest: { id: string; version: number; key: string } | null = null

async function load() {
  const current = generation
  loading.value = true
  try {
    const result = props.runId ? await gitOperationApi.listByRun(props.runId, page.value)
      : props.repoId ? await gitOperationApi.list(props.repoId, page.value) : []
    if (current === generation) rows.value = result
  } catch (error) { if (current === generation) handleError(error) }
  finally { if (current === generation) loading.value = false }
}
async function select(id: string) {
  const current = generation
  const selection = ++selectionGeneration
  busy.value = true
  try {
    const detail = await gitOperationApi.get(id)
    if (current === generation && selection === selectionGeneration) {
      selected.value = detail
      if (retryRequest?.id !== id || retryRequest.version !== detail.operation.version) retryRequest = null
    }
  } catch (error) { if (current === generation) handleError(error) }
  finally { if (current === generation && selection === selectionGeneration) busy.value = false }
}
async function reconcile() {
  if (!selected.value || busy.value) return
  const current = generation
  const { id, version } = selected.value.operation
  busy.value = true
  try {
    if (!retryRequest || retryRequest.id !== id || retryRequest.version !== version) {
      retryRequest = { id, version, key: createRequestKey() }
    }
    await gitOperationApi.reconcile(id, version, retryRequest.key)
    if (current !== generation) return
    retryRequest = null
    await select(id)
    await load()
  } catch (error) { if (current === generation) handleError(error) }
  finally { if (current === generation) busy.value = false }
}
function changePage(delta: number) { page.value += delta; void load() }
function formatEvidence(value: string) {
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value }
}
watch(() => [props.repoId, props.runId], () => {
  generation++
  selectionGeneration++
  selected.value = null
  retryRequest = null
  busy.value = false
  rows.value = []
  page.value = 0
  void load()
}, { immediate: true })
</script>

<style scoped>
.git-operations { margin-top: 20px; padding: 20px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-bg-color); }
header, nav { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h3 { margin: 0; }
.notice { color: var(--el-text-color-secondary); }
.blocked { color: var(--el-color-warning-dark-2); }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th, td { text-align: left; padding: 10px 6px; border-bottom: 1px solid var(--el-border-color-lighter); }
button { cursor: pointer; border: 1px solid var(--el-border-color); border-radius: 4px; padding: 6px 12px; color: var(--el-color-primary); background: var(--el-bg-color); }
button:disabled { cursor: default; opacity: .5; }
nav { justify-content: flex-end; margin-top: 12px; }
.operation-detail { margin-top: 20px; border-top: 1px solid var(--el-border-color); padding-top: 16px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; background: var(--el-fill-color-light); padding: 10px; }
li { margin: 10px 0; }
</style>
