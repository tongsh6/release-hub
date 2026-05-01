import { http } from '@/api/http'
import { mockUserApi } from './mock/auth'
import type { ApiResponse } from '@/types/dto'

export interface UserProfile {
  id: string
  username: string
  displayName: string
}

export interface UserMeResult {
  id: string
  username: string
  displayName: string
  permissions: string[]
}

const useMock = import.meta.env.VITE_USE_MOCK === 'true'

export const userApi = {
  // 获取当前用户信息
  me: async () => {
    if (useMock) {
      return mockUserApi.me()
    }
    const res = await http.get<ApiResponse<UserMeResult>>('/v1/me')
    return res.data.data
  }
}
