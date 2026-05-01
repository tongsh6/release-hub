import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'repositories',
    name: 'Repositories',
    component: () => import('@/views/repository/RepositoryList.vue'),
    meta: { title: 'Repositories', titleKey: 'menu.repositories', requiresAuth: true, permission: 'repository:read', order: 40 }
  },
  {
    path: 'repositories/:repo',
    name: 'RepositoryDetail',
    component: () => import('@/views/repository/RepositoryDetail.vue'),
    meta: { titleKey: 'menu.repositories', requiresAuth: true, permission: 'repository:read', hidden: true }
  }
]

export default routes
