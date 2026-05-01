import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'settings',
    name: 'Settings',
    component: () => import('@/views/settings/Settings.vue'),
    meta: { title: 'Settings', titleKey: 'menu.settings', requiresAuth: true, permission: 'settings:read', order: 70 }
  }
]

export default routes
