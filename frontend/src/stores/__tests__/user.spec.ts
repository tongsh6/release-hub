import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock API modules before store import
vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn()
  }
}))
vi.mock('@/api/user', () => ({
  userApi: {
    me: vi.fn()
  }
}))

import { useUserStore } from '../user'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('should start with empty token and null profile', () => {
    const store = useUserStore()
    expect(store.token).toBe('')
    expect(store.profile).toBeNull()
    expect(store.permissions).toEqual([])
  })

  it('should login and store token', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'jwt-token-123' })
    const store = useUserStore()

    await store.login({ username: 'admin', password: 'admin' })

    expect(store.token).toBe('jwt-token-123')
    expect(localStorage.getItem('RH_TOKEN')).toBe('jwt-token-123')
  })

  it('should clear state on logout', () => {
    const store = useUserStore()
    store.token = 'some-token'
    store.permissions = ['ADMIN']

    store.logout()

    expect(store.token).toBe('')
    expect(store.profile).toBeNull()
    expect(store.permissions).toEqual([])
    expect(localStorage.getItem('RH_TOKEN')).toBeNull()
  })

  it('should fetch user profile', async () => {
    vi.mocked(userApi.me).mockResolvedValue({
      id: 1, username: 'admin', displayName: 'Admin', permissions: ['ADMIN']
    })
    const store = useUserStore()

    await store.fetchMe()

    expect(store.profile?.username).toBe('admin')
    expect(store.permissions).toContain('ADMIN')
  })

  it('should logout on fetchMe failure', async () => {
    vi.mocked(userApi.me).mockRejectedValue(new Error('401'))
    const store = useUserStore()
    store.token = 'expired-token'

    await expect(store.fetchMe()).rejects.toThrow()
    expect(store.token).toBe('')
  })

  it('should check permissions correctly', () => {
    const store = useUserStore()
    store.permissions = ['ADMIN', 'RELEASE_MANAGER']

    expect(store.hasPermission('ADMIN')).toBe(true)
    expect(store.hasPermission('DEVELOPER')).toBe(false)
    expect(store.hasPermission()).toBe(true)   // no required perm
    expect(store.hasPermission('')).toBe(true) // empty string
  })
})
