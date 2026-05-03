import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUiStore } from '../ui'

describe('useUiStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should start with sidebar expanded and light theme', () => {
    const store = useUiStore()
    expect(store.sidebarCollapsed).toBe(false)
    expect(store.theme).toBe('light')
  })

  it('should toggle sidebar', () => {
    const store = useUiStore()
    store.toggleSidebar()
    expect(store.sidebarCollapsed).toBe(true)
    store.toggleSidebar()
    expect(store.sidebarCollapsed).toBe(false)
  })

  it('should set theme to dark', () => {
    const store = useUiStore()
    store.setTheme('dark')
    expect(store.theme).toBe('dark')
  })

  it('should set theme to light', () => {
    const store = useUiStore()
    store.setTheme('dark')
    store.setTheme('light')
    expect(store.theme).toBe('light')
  })
})
