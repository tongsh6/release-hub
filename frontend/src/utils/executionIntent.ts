import { i18n } from '@/i18n'
import { ApiError } from '@/api/http'

export interface ExecutionIntent {
  key: string
  payload: string
  runId?: string
}

// getRandomValues works on the existing HTTP self-hosted entry point.
export function createRequestKey(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
}

function storageKey(subject: string, operation: string): string {
  if (!subject) throw new Error('An authenticated subject is required')
  return JSON.stringify(['releasehub.execution-intent', subject, operation])
}

function validIntent(value: unknown): value is ExecutionIntent {
  return Boolean(value && typeof value === 'object'
    && 'key' in value && typeof value.key === 'string' && value.key.trim()
    && 'payload' in value && typeof value.payload === 'string'
    && (!('runId' in value) || typeof value.runId === 'string' && value.runId.trim()))
}

export function readExecutionIntents(subject: string, operation: string): ExecutionIntent[] {
  const value = sessionStorage.getItem(storageKey(subject, operation))
  if (value === null) return []
  const saved: unknown = JSON.parse(value)
  if (!validIntent(saved)) throw new Error(i18n.global.t('run.request.invalidSaved'))
  const history = 'history' in saved ? saved.history : []
  if (!Array.isArray(history) || history.length > 1 || !history.every(entry => validIntent(entry) && Boolean(entry.runId))) throw new Error(i18n.global.t('run.request.invalidSaved'))
  const entries = [saved, ...history].map(entry => ({
    key: entry.key, payload: entry.payload, ...(entry.runId ? { runId: entry.runId } : {})
  }))
  if (new Set(entries.map(entry => entry.key)).size !== entries.length) {
    throw new Error(i18n.global.t('run.request.invalidSaved'))
  }
  return entries
}

export function readExecutionIntent(subject: string, operation: string): ExecutionIntent | null {
  return readExecutionIntents(subject, operation)[0] ?? null
}

function persist(subject: string, operation: string, entries: ExecutionIntent[]) {
  const [current, ...history] = entries
  sessionStorage.setItem(storageKey(subject, operation), JSON.stringify({ ...current, ...(history.length ? { history } : {}) }))
}

// Call only after the user has confirmed this exact payload and subject.
// One unresolved request and the latest confirmed receipt are sufficient for both pages.
export function saveExecutionIntent(subject: string, operation: string, payload: string): ExecutionIntent {
  const current = readExecutionIntent(subject, operation)
  if (current && !current.runId) {
    if (current.payload === payload) return current
    throw new Error(i18n.global.t('run.request.pendingFirst'))
  }
  const intent = { key: createRequestKey(), payload }
  persist(subject, operation, current ? [intent, current] : [intent])
  return intent
}

// Only call for the first send of a key just created by this page. A retrieval may
// follow an earlier in-flight admission, even when the retrieval itself gets 400.
export function forgetRejectedNewIntent(subject: string, operation: string, key: string, error: unknown): boolean {
  if (!(error instanceof ApiError) || error.httpStatus !== 400 || error.code !== 'RUN_006') return false
  const entries = readExecutionIntents(subject, operation)
  if (entries[0]?.key !== key || entries[0].runId) return false
  const confirmed = entries.slice(1)
  if (confirmed.length) persist(subject, operation, confirmed)
  else sessionStorage.removeItem(storageKey(subject, operation))
  return true
}

export function acknowledgeExecutionIntent(subject: string, operation: string, key: string, runId: string): void {
  if (typeof runId !== 'string' || !runId.trim()) throw new Error(i18n.global.t('run.request.missingReceipt'))
  const entries = readExecutionIntents(subject, operation)
  const intent = entries.find(entry => entry.key === key)
  if (!intent) throw new Error(i18n.global.t('run.request.invalidSaved'))
  if (intent.runId && intent.runId !== runId) throw new Error(i18n.global.t('run.request.receiptConflict'))
  intent.runId = runId
  persist(subject, operation, entries[0]?.runId ? [entries[0]] : entries)
}
