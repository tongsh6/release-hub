export const API_BASE = '/v1'

export function toQuery(params: Record<string, any>): Record<string, any> {
  const out: Record<string, any> = {}
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return
    out[k] = v
  })
  return out
}
