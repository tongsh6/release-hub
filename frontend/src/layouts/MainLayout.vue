<template>
  <el-container class="layout-container rh-layout">
    <el-aside width="240px" class="rh-sidebar">
      <div class="logo">ReleaseHub</div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          class="el-menu-vertical"
          router
        >
          <template v-for="menuItem in menuRoutes" :key="menuItem.path">
            <el-menu-item v-if="!menuItem.meta?.hidden" :index="'/' + menuItem.path">
              <el-icon><component :is="getIcon(menuItem.name as string)" /></el-icon>
              <span>{{ menuItem.meta?.titleKey ? t(menuItem.meta.titleKey as string) : menuItem.meta?.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>
    <el-container class="rh-main-container">
      <el-header>
        <div class="header-content">
          <span>{{ t('header.title') }}</span>
          <div class="header-right">
            <LocaleSwitch class="mr-4" />
            <el-button type="primary" link @click="logout">{{ t('common.logout') }}</el-button>
          </div>
        </div>
      </el-header>
      <el-main class="rh-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import LocaleSwitch from '@/components/common/LocaleSwitch.vue'
import { constantRoutes } from '@/router/routes'
import {
  Menu as IconMenu,
  List,
  Setting,
  Monitor,
  DataBoard,
  Calendar,
  Collection,
  Folder,
  TrendCharts,
  Warning
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n({ useScope: 'global' })
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const menuRoutes = computed(() => {
  const mainRoute = constantRoutes.find(r => r.path === '/')
  const children = (mainRoute?.children || []).filter(r => !r.meta?.hidden)
  return children.sort((a, b) => {
    const ao = (a.meta?.order as number) ?? 999
    const bo = (b.meta?.order as number) ?? 999
    return ao - bo
  })
})

const getIcon = (name: string) => {
  switch (name) {
    case 'Dashboard': return DataBoard
    case 'ReleaseWindows': return Calendar
    case 'BranchRules': return Setting
    case 'VersionPolicies': return List
    case 'VersionOps': return Monitor
    case 'Iterations': return Collection
    case 'Repositories': return Folder
    case 'Runs': return TrendCharts
    case 'BlockBoard': return Warning
    default: return IconMenu
  }
}

const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
/* 整体高度占满视口 */
.rh-layout {
  height: 100vh;
}

/* 左侧侧边栏固定宽度，不允许被右侧内容挤压或撑宽 */
.rh-sidebar {
  width: 240px;
  flex: 0 0 240px;       /* 不伸缩，宽度锁死（关键） */
  overflow: hidden;      /* 避免内部内容撑破侧栏 */
  border-right: 1px solid var(--el-border-color-light);
  background-color: #fff;
  display: flex;
  flex-direction: column;
}

/* 确保 scrollbar 占满剩余空间 */
.rh-sidebar :deep(.el-scrollbar) {
  flex: 1;
}

.rh-sidebar :deep(.el-scrollbar__view) {
  height: 100%;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: bold;
  color: #409eff;
  border-bottom: 1px solid var(--el-border-color-light);
  /* 确保 logo 不会被 scrollbar 滚动 */
  flex-shrink: 0;
}

.el-menu-vertical {
  border-right: none;
}

.rh-main-container {
  min-width: 0;          /* 防止 flex 子项溢出 */
  display: flex;
  flex-direction: column;
}

.el-header {
  background-color: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
  display: flex;
  align-items: center;
  flex-shrink: 0;        /* 头部不收缩 */
}

.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
}

/* 右侧主区域允许自适应，并防止内容把布局撑坏（关键） */
.rh-main {
  flex: 1 1 auto;
  min-width: 0;          /* 允许内容在容器内裁剪/滚动，而不是撑布局 */
  overflow: auto;        /* 右侧内容超出时滚动 */
  padding: 20px;
}
</style>
