<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <span class="locale-switch-trigger">
      <el-icon class="icon"><Connection /></el-icon>
      <span class="label">{{ currentLangLabel }}</span>
      <el-icon class="el-icon--right"><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item 
          command="zh-CN" 
          :disabled="currentLocale === 'zh-CN'"
        >
          {{ t('lang.zh') }}
        </el-dropdown-item>
        <el-dropdown-item 
          command="en-US" 
          :disabled="currentLocale === 'en-US'"
        >
          {{ t('lang.en') }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDown, Connection } from '@element-plus/icons-vue'
import { setI18nLocale, type AppLocale } from '@/i18n'

// 使用 useI18n 获取 t 函数和当前 locale
const { t, locale } = useI18n()

// 计算当前语言，用于高亮或禁用当前选项
const currentLocale = computed(() => locale.value as AppLocale)

const currentLangLabel = computed(() => {
  return currentLocale.value === 'zh-CN' ? t('lang.zh') : t('lang.en')
})

// 处理下拉菜单点击
const handleCommand = (command: AppLocale) => {
  setI18nLocale(command)
}
</script>

<style scoped>
.locale-switch-trigger {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.locale-switch-trigger:hover {
  color: var(--el-color-primary);
}

.icon {
  margin-right: 4px;
  font-size: 16px;
}

.label {
  margin-right: 2px;
}
</style>
