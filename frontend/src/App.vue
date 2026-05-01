<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { i18n } from '@/i18n'
import { getElementLocale } from '@/i18n/element'

const route = useRoute()
const elementLocale = computed(() => getElementLocale(i18n.global.locale.value as any))

watch(
  () => i18n.global.locale.value,
  () => {
    const appTitle = import.meta.env.VITE_APP_TITLE || 'ReleaseHub'
    let title = route.meta.title as string | undefined
    
    if (route.meta.titleKey) {
      title = i18n.global.t(route.meta.titleKey as string)
    }
    
    document.title = title ? `${title} - ${appTitle}` : appTitle
  }
)
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <RouterView />
  </el-config-provider>
</template>

<style>
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
}
</style>
