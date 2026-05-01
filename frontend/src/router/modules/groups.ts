import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'groups',
    name: 'Groups',
    component: () => import('@/views/group/GroupList.vue'),
    meta: { titleKey: 'menu.groups', requiresAuth: true, permission: 'group:read', order: 25 }
  },
  {
    path: 'groups/:id',
    name: 'GroupDetail',
    component: () => import('@/views/group/GroupDetail.vue'),
    meta: { titleKey: 'group.detail', hidden: true, requiresAuth: true, permission: 'group:read' }
  }
]

export default routes
