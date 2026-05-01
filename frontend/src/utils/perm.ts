import { useUserStore } from '@/stores/user'

export function hasPerm(required?: string): boolean {
  if (!required) return true
  try {
    const mode = (import.meta.env.VITE_PERM_MODE || 'soft') as 'off' | 'soft' | 'strict'
    if (mode === 'off') return true
    const userStore = useUserStore()
    // soft: 当权限列表为空或未加载时放行
    const perms = userStore.permissions || []
    if (mode === 'soft' && perms.length === 0) return true
    return userStore.hasPermission(required)
  } catch {
    // Pinia/Router 尚未初始化时的安全降级
    const mode = (import.meta.env.VITE_PERM_MODE || 'soft') as 'off' | 'soft' | 'strict'
    return mode !== 'strict'
  }
}
