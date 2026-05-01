import { createI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/en'
import zhCN from './messages/zh-CN'
import enUS from './messages/en-US'

export type AppLocale = 'zh-CN' | 'en-US'

const LOCALE_KEY = 'RH_LOCALE'

export function getSavedLocale(): AppLocale {
  const v = localStorage.getItem(LOCALE_KEY)
  return (v === 'en-US' || v === 'zh-CN') ? v : 'zh-CN'
}

function setDayjsLocale(locale: AppLocale) {
  dayjs.locale(locale === 'zh-CN' ? 'zh-cn' : 'en')
}

export function saveLocale(locale: AppLocale) {
  localStorage.setItem(LOCALE_KEY, locale)
  document.documentElement.lang = locale
  setDayjsLocale(locale)
}

// Initial setup
const initialLocale = getSavedLocale()
document.documentElement.lang = initialLocale
setDayjsLocale(initialLocale)

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export function setI18nLocale(locale: AppLocale) {
  // 中文注释：切换语言并持久化
  i18n.global.locale.value = locale
  saveLocale(locale)
}
