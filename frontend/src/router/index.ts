import { createRouter, createWebHistory } from 'vue-router'
import { constantRoutes } from './routes'
import { registerGuards } from './guard'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes
})

// Register global guards
registerGuards(router)

export default router
