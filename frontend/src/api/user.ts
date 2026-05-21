import { http } from '@/api/http'
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

export const userApi = {
  // 获取当前用户信息
  me: async () => {
    const res = await http.get<ApiResponse<UserMeResult>>('/v1/me')
    return res.data.data
  }
}
