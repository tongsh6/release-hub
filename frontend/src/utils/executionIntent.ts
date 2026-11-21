export interface PendingExecutionIntent {
  key: string
  payload: string
}

// getRandomValues works on the existing HTTP self-hosted entry point.
export function createRequestKey(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
}

function storageKey(subject: string, operation: string): string {
  if (!subject) throw new Error('An authenticated subject is required')
  return JSON.stringify(['releasehub.execution-intent', subject, operation])
}

export function readExecutionIntent(subject: string, operation: string): PendingExecutionIntent | null {
  const value = sessionStorage.getItem(storageKey(subject, operation))
  if (value === null) return null
  const intent: unknown = JSON.parse(value)
  if (!intent || typeof intent !== 'object'
    || !('key' in intent) || typeof intent.key !== 'string'
    || !('payload' in intent) || typeof intent.payload !== 'string') {
    throw new Error('The saved execution request cannot be read')
  }
  return { key: intent.key, payload: intent.payload }
}

// Call only after the user has confirmed this exact payload and subject.
export function saveExecutionIntent(subject: string, operation: string, payload: string): PendingExecutionIntent {
  const existing = readExecutionIntent(subject, operation)
  if (existing?.payload === payload) return existing
  const intent = { key: createRequestKey(), payload }
  sessionStorage.setItem(storageKey(subject, operation), JSON.stringify(intent))
  return intent
}

export function acknowledgeExecutionIntent(subject: string, operation: string, key: string): void {
  if (readExecutionIntent(subject, operation)?.key === key) {
    sessionStorage.removeItem(storageKey(subject, operation))
  }
}
