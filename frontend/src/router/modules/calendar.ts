import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: 'calendar',
    name: 'Calendar',
    component: () => import('@/views/calendar/CalendarView.vue'),
    meta: { title: 'Calendar', titleKey: 'menu.calendar', requiresAuth: true, permission: 'release-window:read', order: 15 }
  }
]

export default routes
