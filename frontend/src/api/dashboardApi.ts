import { apiGet } from '@/api/http'

export interface DashboardStats {
  totalRepositories: number
  totalIterations: number
  activeWindows: number
  totalRuns: number
  recentRuns: number
}

export const dashboardApi = {
  async getStats(): Promise<DashboardStats> {
    return await apiGet<DashboardStats>('/v1/dashboard/stats')
  }
}
