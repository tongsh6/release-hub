import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'release-governance/candidate-review',
    name: 'ReleaseCandidateReview',
    component: () => import('@/views/release-governance/ReleaseCandidateReview.vue'),
    meta: { title: 'Release Candidate Review', titleKey: 'menu.releaseCandidateReview', requiresAuth: true, permission: 'release-governance:review', order: 66 }
  }
]

export default routes
