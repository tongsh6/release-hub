import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'branch-rules',
    name: 'BranchRules',
    component: () => import('@/views/branch-rule/BranchRuleList.vue'),
    meta: { title: 'Branch Rules', titleKey: 'menu.branchRules', requiresAuth: true, permission: 'branch-rule:read', hidden: true, order: 120 }
  }
]

export default routes
