import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'iterations',
    name: 'Iterations',
    component: () => import('@/views/iteration/IterationList.vue'),
    meta: { title: 'Iterations', titleKey: 'menu.iterations', requiresAuth: true, permission: 'iteration:read', order: 30 }
  },
  {
    path: 'iterations/:iterationKey',
    name: 'IterationDetail',
    component: () => import('@/views/iteration/IterationDetail.vue'),
    meta: { titleKey: 'menu.iterations', requiresAuth: true, permission: 'iteration:read', hidden: true }
  }
]

export default routes
