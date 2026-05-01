import { apiGet } from '@/api/http'
import { API_BASE } from './_shared'

export const getVersionPolicies = (params?: any) => {
  return apiGet(`${API_BASE}/version-policies`, { params })
}
