import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { i18n } from '@/i18n'

export function registerGuards(router: Router) {
  router.beforeEach(async (to, from, next) => {
    // 0. 动态标题设置
    const appTitle = import.meta.env.VITE_APP_TITLE || 'ReleaseHub'
    let title = to.meta.title
    
    if (to.meta.titleKey) {
      title = i18n.global.t(to.meta.titleKey as string)
    }
    
    document.title = title ? `${title} - ${appTitle}` : appTitle

    const userStore = useUserStore()
    const token = userStore.token
    
    // 1. 访问登录页，若已有 token 则跳首页
    if (to.path === '/login') {
      if (token) {
        return next({ path: '/' })
      }
      return next()
    }

    // 2. 检查是否公开路由
    const isPublic = to.matched.some(record => record.meta.public)
    if (isPublic) {
      return next()
    }

    // 3. 无 token -> 跳登录
    if (!token) {
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }

    // 4. 有 token 但无 profile -> fetchMe
    if (!userStore.profile) {
      try {
        await userStore.fetchMe()
        // 确保 fetchMe 成功后再放行
        // 如果 fetchMe 期间 token 过期，axios 拦截器或 fetchMe catch 会处理
        return next({ ...to, replace: true }) // 使用 replace 确保路由解析完整
      } catch {
        // fetchMe 失败 (401 会由拦截器处理，但这里可能也会捕获到)
        return next({ path: '/login', query: { redirect: to.fullPath } })
      }
    }

    // 5. 放行
    next()
  })
}
