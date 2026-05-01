import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi, type LoginPayload } from '@/api/auth'
import { userApi, type UserProfile } from '@/api/user'

const TOKEN_KEY = 'RH_TOKEN'

export const useUserStore = defineStore('user', () => {
  // State
  // 仅保存 token，不存表单
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const profile = ref<UserProfile | null>(null)
  const permissions = ref<string[]>([])

  // Actions
  async function login(payload: LoginPayload) {
    const res = await authApi.login(payload)
    token.value = res.token
    localStorage.setItem(TOKEN_KEY, res.token)
  }

  async function fetchMe() {
    try {
      const res = await userApi.me()
      profile.value = {
        id: res.id,
        username: res.username,
        displayName: res.displayName
      }
      permissions.value = res.permissions || []
    } catch (error) {
      // 获取用户信息失败，通常意味着 token 无效
      logout()
      throw error
    }
  }

  function logout() {
    token.value = ''
    profile.value = null
    permissions.value = []
    localStorage.removeItem(TOKEN_KEY)
  }

  function hasPermission(required?: string): boolean {
    if (!required) return true
    return permissions.value.includes(required)
  }

  return {
    token,
    profile,
    permissions,
    login,
    fetchMe,
    logout,
    hasPermission
  }
})
