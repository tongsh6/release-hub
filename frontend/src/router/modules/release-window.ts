import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'release-windows',
    name: 'ReleaseWindows',
    component: () => import('@/views/release-window/ReleaseWindowList.vue'),
    meta: { title: 'Release Windows', titleKey: 'menu.releaseWindows', requiresAuth: true, permission: 'release-window:read', order: 20 }
  },
  {
    path: 'release-windows/:id',
    name: 'ReleaseWindowDetail',
    component: () => import('@/views/release-window/ReleaseWindowDetail.vue'),
    meta: { titleKey: 'releaseWindow.details', hidden: true, requiresAuth: true, permission: 'release-window:read' }
  }
]

export default routes
