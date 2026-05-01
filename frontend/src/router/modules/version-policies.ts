import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'version-policies',
    name: 'VersionPolicies',
    component: () => import('@/views/version-policy/VersionPolicyList.vue'),
    meta: { title: 'Version Policies', titleKey: 'menu.versionPolicies', requiresAuth: true, permission: 'version-policy:read', hidden: true, order: 130 }
  }
]

export default routes
