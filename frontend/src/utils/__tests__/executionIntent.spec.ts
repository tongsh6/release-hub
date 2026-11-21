import { beforeEach, describe, expect, it } from 'vitest'
import { saveExecutionIntent, readExecutionIntent, acknowledgeExecutionIntent } from '../executionIntent'

describe('durable client execution receipts', () => {
  beforeEach(() => sessionStorage.clear())

  it('keeps the confirmed Run ID and the original payload instead of discarding the receipt', () => {
    const intent = saveExecutionIntent('admin', 'ORCHESTRATE:window-1', '{"repoIds":["repo-1"]}')
    acknowledgeExecutionIntent('admin', 'ORCHESTRATE:window-1', intent.key, 'run-1')
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')).toEqual({ ...intent, runId: 'run-1' })
  })

  it('rejects a different Run ID for the same key without replacing the confirmed receipt', () => {
    const intent = saveExecutionIntent('admin', 'RETRY:source', '{"items":["item-1"]}')
    acknowledgeExecutionIntent('admin', 'RETRY:source', intent.key, 'run-1')
    expect(() => acknowledgeExecutionIntent('admin', 'RETRY:source', intent.key, 'run-2')).toThrow()
    expect(readExecutionIntent('admin', 'RETRY:source')).toMatchObject({ key: intent.key, runId: 'run-1' })
  })

  it('requires the original receipt before accepting a different payload', () => {
    const first = saveExecutionIntent('admin', 'ORCHESTRATE:window-1', '{"repoIds":["repo-1"]}')
    expect(() => saveExecutionIntent('admin', 'ORCHESTRATE:window-1', '{"repoIds":["repo-2"]}')).toThrow()
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')).toEqual(first)
  })

  it('keeps only the latest confirmed receipt alongside one new pending intent', () => {
    const first = saveExecutionIntent('admin', 'ORCHESTRATE:window-1', '{"repoIds":["repo-1"]}')
    acknowledgeExecutionIntent('admin', 'ORCHESTRATE:window-1', first.key, 'first-run')
    const next = saveExecutionIntent('admin', 'ORCHESTRATE:window-1', '{"repoIds":["repo-2"]}')
    const stored = () => JSON.parse(sessionStorage.getItem(sessionStorage.key(0)!)!)
    expect(stored().history).toEqual([{ ...first, runId: 'first-run' }])
    acknowledgeExecutionIntent('admin', 'ORCHESTRATE:window-1', first.key, 'first-run')
    expect(readExecutionIntent('admin', 'ORCHESTRATE:window-1')).toEqual(next)
    acknowledgeExecutionIntent('admin', 'ORCHESTRATE:window-1', next.key, 'next-run')
    expect(stored()).toEqual({ ...next, runId: 'next-run' })
  })

  it('reuses an unconfirmed key and isolates the request from another authenticated subject', () => {
    const first = saveExecutionIntent('admin', 'ORCHESTRATE:window-1', '{"repoIds":["repo-1"]}')
    expect(saveExecutionIntent('admin', 'ORCHESTRATE:window-1', first.payload)).toEqual(first)
    expect(readExecutionIntent('another-user', 'ORCHESTRATE:window-1')).toBeNull()
  })

  it('rejects an empty receipt without acknowledging or changing the original key', () => {
    const intent = saveExecutionIntent('admin', 'RETRY:source', '{"items":["item-1"]}')
    expect(() => acknowledgeExecutionIntent('admin', 'RETRY:source', intent.key, '')).toThrow()
    expect(readExecutionIntent('admin', 'RETRY:source')).toEqual(intent)
  })
})
