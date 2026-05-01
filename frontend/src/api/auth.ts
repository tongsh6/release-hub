import { post } from '@/api/http'
import { mockAuthApi } from './mock/auth'

export interface LoginPayload {
  username: string
  password: string
  rememberMe?: boolean
}

export interface LoginResult {
  token: string
}

const useMock = import.meta.env.VITE_USE_MOCK === 'true'

export const authApi = {
  // 登录
  login: (data: LoginPayload) => {
    if (useMock) {
      return mockAuthApi.login(data)
    }
    return post<LoginResult>('/v1/auth/login', data)
  }
}
