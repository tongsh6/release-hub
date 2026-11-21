import { computed, onUnmounted, ref, watch } from 'vue'
import { runApi, type RunDetail, type RunTask } from '@/api/runApi'
import { handleError } from '@/utils/error'
import { i18n } from '@/i18n'

const POLL_INTERVAL_MS = 2_000
const OBSERVATION_LIMIT_MS = 120_000

// Read-only observation shared by the window receipt and Run detail pages.
// A review result can coexist with active work in a different repository.
export function useRunObservation(runId: () => string | undefined, subject: () => string | undefined) {
  const detail = ref<RunDetail>()
  const tasks = ref<RunTask[]>([])
  const loading = ref(false)
  const paused = ref<'timeout' | 'read' | 'evidence' | 'hidden' | null>(null)
  const active = computed(() => tasks.value.some(task => task.status === 'PENDING' || task.status === 'RUNNING'))
  let generation = 0
  let disposed = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let deadline = 0

  function clearTimer() {
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
  }
  function isCurrent(id: string, actor: string, version: number) {
    return !disposed && generation === version && runId() === id && subject() === actor
  }
  async function read(id: string, actor: string, version: number) {
    if (!isCurrent(id, actor, version)) return
    loading.value = true
    try {
      const result = await runApi.getRunById(id)
      if (!isCurrent(id, actor, version)) return
      if (result.id !== id) throw new Error(i18n.global.t('run.request.receiptConflict'))
      detail.value = result
      const rows = result.status === 'PLANNED' ? [] : await runApi.getTasks(id)
      if (!isCurrent(id, actor, version)) return
      if (!Array.isArray(rows) || rows.some(task => task.runId !== id)) {
        throw new Error(i18n.global.t('run.observation.invalidEvidence'))
      }
      tasks.value = rows
      if (result.runType !== 'WINDOW_ORCHESTRATION' || result.executionVersion !== 3 || result.status === 'PLANNED') return
      if (!rows.length) { paused.value = 'evidence'; return }
      if (!active.value && result.status !== 'RUNNING') return
      if (Date.now() >= deadline) { paused.value = 'timeout'; return }
      if (document.hidden) { paused.value = 'hidden'; return }
      timer = setTimeout(() => { timer = undefined; void read(id, actor, version) }, POLL_INTERVAL_MS)
    } catch (error) {
      if (isCurrent(id, actor, version)) {
        paused.value = 'read'
        handleError(error)
      }
    } finally {
      if (isCurrent(id, actor, version)) loading.value = false
    }
  }
  async function refresh() {
    if (disposed || loading.value) return
    clearTimer()
    const id = runId()
    const actor = subject()
    if (!id || !actor) return
    paused.value = null
    deadline = Date.now() + OBSERVATION_LIMIT_MS
    await read(id, actor, generation)
  }
  watch([runId, subject], ([id, actor]) => {
    generation++
    clearTimer()
    detail.value = undefined
    tasks.value = []
    loading.value = false
    paused.value = null
    deadline = Date.now() + OBSERVATION_LIMIT_MS
    if (id && actor) void read(id, actor, generation)
  }, { immediate: true })
  function visibilityChanged() {
    if (document.hidden) {
      clearTimer()
      if ((active.value || detail.value?.status === 'RUNNING') && !paused.value) paused.value = 'hidden'
    } else if (paused.value === 'hidden') {
      void refresh()
    }
  }
  document.addEventListener('visibilitychange', visibilityChanged)
  onUnmounted(() => {
    disposed = true
    generation++
    clearTimer()
    document.removeEventListener('visibilitychange', visibilityChanged)
  })
  return { detail, tasks, loading, paused, active, refresh }
}
