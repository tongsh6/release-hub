/**
 * 日期格式化工具
 * 统一日期显示格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd
 */

/**
 * 格式化日期时间：yyyy-MM-dd HH:mm:ss
 * @param date 日期字符串或 Date 对象
 * @param fallback 当日期为空时的显示值，默认 '-'
 */
export function formatDateTime(date: string | Date | null | undefined, fallback = '-'): string {
  if (!date) return fallback
  const d = typeof date === 'string' ? new Date(date) : date
  if (isNaN(d.getTime())) return fallback
  
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

/**
 * 格式化日期：yyyy-MM-dd
 * @param date 日期字符串或 Date 对象
 * @param fallback 当日期为空时的显示值，默认 '-'
 */
export function formatDate(date: string | Date | null | undefined, fallback = '-'): string {
  if (!date) return fallback
  const d = typeof date === 'string' ? new Date(date) : date
  if (isNaN(d.getTime())) return fallback
  
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  
  return `${year}-${month}-${day}`
}
