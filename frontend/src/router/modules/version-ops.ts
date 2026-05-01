import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'version-ops',
    name: 'VersionOps',
    component: () => import('@/views/version-ops/VersionOpsDashboard.vue'),
    meta: { title: 'Version Ops', titleKey: 'menu.versionOps', requiresAuth: true, permission: 'version-ops:read', hidden: true, order: 140 }
  },
  {
    path: 'version-ops/runs/:runId',
    name: 'VersionRunDetail',
    component: () => import('@/views/version-ops/VersionRunDetail.vue'),
    meta: { titleKey: 'run.detail.title', hidden: true, requiresAuth: true, permission: 'version-ops:read' }
  }
]

export default routes
