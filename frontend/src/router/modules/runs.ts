import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'runs',
    name: 'Runs',
    component: () => import('@/views/run/RunList.vue'),
    meta: { title: 'Runs', titleKey: 'menu.runs', requiresAuth: true, permission: 'run:read', order: 50 }
  },
  {
    path: 'runs/:runId',
    name: 'RunDetail',
    component: () => import('@/views/run/RunDetail.vue'),
    meta: { titleKey: 'run.detail.title', requiresAuth: true, permission: 'run:read', hidden: true }
  },
  {
    path: 'audit/blocks',
    name: 'BlockBoard',
    component: () => import('@/views/audit/BlockBoard.vue'),
    meta: { title: 'Blocks', titleKey: 'menu.blockBoard', requiresAuth: true, permission: 'run:read', order: 60 }
  }
]

export default routes
