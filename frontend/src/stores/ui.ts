import { defineStore } from 'pinia'
import { ref } from 'vue'

export type Theme = 'light' | 'dark'

export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(false)
  const theme = ref<Theme>('light')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setTheme(newTheme: Theme) {
    theme.value = newTheme
    // Here you could also apply the theme to the document root
    if (newTheme === 'dark') {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  return {
    sidebarCollapsed,
    theme,
    toggleSidebar,
    setTheme
  }
})
