import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import type { AppLocale } from './index'

// 中文注释：Element Plus 语言包映射
export function getElementLocale(locale: AppLocale) {
  return locale === 'en-US' ? en : zhCn
}
