import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'data-quality/review',
    name: 'DataQualityReview',
    component: () => import('@/views/data-quality/DataQualityReviewQueue.vue'),
    meta: { title: 'Data Quality Review', titleKey: 'menu.dataQualityReview', requiresAuth: true, permission: 'data-quality:review', order: 65 }
  }
]

export default routes
