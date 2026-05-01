import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'audit/block-board',
    name: 'BlockBoard',
    component: () => import('@/views/audit/BlockBoard.vue'),
    meta: { title: 'Block Board', titleKey: 'menu.blockBoard', requiresAuth: true, order: 80 }
  }
]

export default routes
