import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    titleKey?: string
    requiresAuth?: boolean
    permission?: string
    icon?: string
    hidden?: boolean
    public?: boolean
  }
}
