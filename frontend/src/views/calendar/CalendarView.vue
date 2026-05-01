<template>
  <div class="calendar-page">
    <div class="calendar-header">
      <div class="calendar-title">
        <h2>{{ t('calendar.title') }}</h2>
      </div>
      <div class="calendar-controls">
        <el-button-group class="view-switcher">
          <el-button :type="viewMode === 'month' ? 'primary' : ''" @click="viewMode = 'month'">{{ t('calendar.month') }}</el-button>
          <el-button :type="viewMode === 'week' ? 'primary' : ''" @click="viewMode = 'week'">{{ t('calendar.week') }}</el-button>
        </el-button-group>
        <el-button-group>
          <el-button :icon="ArrowLeft" @click="prevNav" />
          <el-button @click="goToday">{{ t('calendar.today') }}</el-button>
          <el-button :icon="ArrowRight" @click="nextNav" />
        </el-button-group>
        <span class="current-month">{{ currentMonthLabel }}</span>
      </div>
    </div>

    <!-- 月视图 -->
    <div v-if="viewMode === 'month'" v-loading="loading" class="calendar-grid">
      <!-- 星期标题 -->
      <div class="calendar-weekdays">
        <div v-for="day in weekdayHeaders" :key="day" class="weekday">{{ day }}</div>
      </div>

      <!-- 日期格子 -->
      <div class="calendar-days">
        <div
          v-for="(day, index) in calendarDays"
          :key="index"
          class="calendar-day"
          :class="{
            'other-month': !day.isCurrentMonth,
            'today': day.isToday,
            'has-events': day.events.length > 0
          }"
        >
          <div class="day-number">{{ day.date.getDate() }}</div>
          <div class="day-events">
            <div
              v-for="event in day.events.slice(0, 3)"
              :key="event.id"
              class="event-item"
              :class="getEventClass(event)"
              :title="event.name"
              @click="handleEventClick(event)"
            >
              <span v-if="event.frozen" class="frozen-icon">❄️</span>
              <span class="event-name">{{ event.name }}</span>
            </div>
            <div v-if="day.events.length > 3" class="more-events">
              +{{ day.events.length - 3 }} {{ t('calendar.more') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 周视图 -->
    <div v-else v-loading="loading" class="week-grid">
      <div class="week-columns">
        <div
          v-for="day in weekDays"
          :key="day.date.toISOString()"
          class="week-column"
          :class="{ 'today': day.isToday }"
        >
          <div class="week-column-header">
            <div class="week-day-name">{{ day.dayName }}</div>
            <div class="week-day-number" :class="{ 'today-number': day.isToday }">{{ day.date.getDate() }}</div>
          </div>
          <div class="week-column-events">
            <div
              v-for="event in day.events"
              :key="event.id"
              class="event-item event-item-week"
              :class="getEventClass(event)"
              :title="event.name"
              @click="handleEventClick(event)"
            >
              <span v-if="event.frozen" class="frozen-icon">❄️</span>
              <span class="event-time">{{ formatTime(event.plannedReleaseAt) }}</span>
              <span class="event-name">{{ event.name }}</span>
            </div>
            <div v-if="day.events.length === 0" class="no-events">—</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 图例 -->
    <div class="calendar-legend">
      <span class="legend-item">
        <span class="legend-dot status-draft"></span>
        {{ t('releaseWindow.statusText.DRAFT') }}
      </span>
      <span class="legend-item">
        <span class="legend-dot status-published"></span>
        {{ t('releaseWindow.statusText.PUBLISHED') }}
      </span>
      <span class="legend-item">
        <span class="legend-dot status-closed"></span>
        {{ t('releaseWindow.statusText.CLOSED') }}
      </span>
      <span class="legend-item">
        <span class="frozen-icon">❄️</span>
        {{ t('releaseWindow.frozen') }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { list, type ReleaseWindowView } from '@/api/modules/releaseWindow'
import { handleError } from '@/utils/error'

const { t, locale } = useI18n()
const router = useRouter()

// 状态
const loading = ref(false)
const currentDate = ref(new Date())
const releaseWindows = ref<ReleaseWindowView[]>([])
const viewMode = ref<'month' | 'week'>('month')

// 星期标题
const weekdayHeaders = computed(() => {
  if (locale.value === 'zh-CN') {
    return ['日', '一', '二', '三', '四', '五', '六']
  }
  return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
})

// 周视图短名
const weekDayNames = computed(() => {
  if (locale.value === 'zh-CN') {
    return ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  }
  return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
})

// 计算周号（年内第几周，简单实现：从1月1日起，每7天一周）
function getWeekNumber(date: Date): number {
  const start = new Date(date.getFullYear(), 0, 1)
  const diff = date.getTime() - start.getTime()
  return Math.ceil((diff / 86400000 + start.getDay() + 1) / 7)
}

// 当前月份/周次标签
const currentMonthLabel = computed(() => {
  const year = currentDate.value.getFullYear()
  const month = currentDate.value.getMonth()
  
  let baseStr = ''
  if (locale.value === 'zh-CN') {
    baseStr = `${year}年${month + 1}月`
  } else {
    const monthNames = ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December']
    baseStr = `${monthNames[month]} ${year}`
  }

  if (viewMode.value === 'week') {
    const weekNum = getWeekNumber(currentDate.value)
    return `${baseStr} ${t('calendar.weekOf', { n: weekNum })}`
  }
  return baseStr
})

// 月视图格子数据
interface CalendarDay {
  date: Date
  isCurrentMonth: boolean
  isToday: boolean
  events: ReleaseWindowView[]
}

const calendarDays = computed<CalendarDay[]>(() => {
  const year = currentDate.value.getFullYear()
  const month = currentDate.value.getMonth()

  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)

  const startDate = new Date(firstDay)
  startDate.setDate(startDate.getDate() - firstDay.getDay())

  const endDate = new Date(lastDay)
  const remainingDays = 6 - lastDay.getDay()
  endDate.setDate(endDate.getDate() + remainingDays)

  const days: CalendarDay[] = []
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  const current = new Date(startDate)
  while (current <= endDate) {
    const date = new Date(current)
    const isCurrentMonth = date.getMonth() === month
    const isToday = date.getTime() === today.getTime()

    const events = releaseWindows.value.filter(rw => {
      if (!rw.plannedReleaseAt) return false
      const rwDate = new Date(rw.plannedReleaseAt)
      return rwDate.getFullYear() === date.getFullYear() &&
             rwDate.getMonth() === date.getMonth() &&
             rwDate.getDate() === date.getDate()
    })

    days.push({ date, isCurrentMonth, isToday, events })
    current.setDate(current.getDate() + 1)
  }

  return days
})

// 周视图数据
interface WeekDay {
  date: Date
  isToday: boolean
  dayName: string
  events: ReleaseWindowView[]
}

const weekDays = computed<WeekDay[]>(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  // 找到当周周日
  const base = new Date(currentDate.value)
  base.setHours(0, 0, 0, 0)
  const dayOfWeek = base.getDay()
  const sunday = new Date(base)
  sunday.setDate(base.getDate() - dayOfWeek)

  const days: WeekDay[] = []
  for (let i = 0; i < 7; i++) {
    const date = new Date(sunday)
    date.setDate(sunday.getDate() + i)
    const isToday = date.getTime() === today.getTime()

    const events = releaseWindows.value.filter(rw => {
      if (!rw.plannedReleaseAt) return false
      const rwDate = new Date(rw.plannedReleaseAt)
      return rwDate.getFullYear() === date.getFullYear() &&
             rwDate.getMonth() === date.getMonth() &&
             rwDate.getDate() === date.getDate()
    })

    days.push({ date, isToday, dayName: weekDayNames.value[i], events })
  }

  return days
})

// 格式化时间 HH:mm
function formatTime(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  return `${h}:${m}`
}

// 获取事件样式类
function getEventClass(event: ReleaseWindowView) {
  const classes = [`status-${event.status.toLowerCase()}`]
  if (event.frozen) {
    classes.push('frozen')
  }
  return classes
}

// 事件点击处理
function handleEventClick(event: ReleaseWindowView) {
  router.push(`/release-windows/${event.id}`)
}

// 导航方法
function prevMonth() {
  const newDate = new Date(currentDate.value)
  newDate.setMonth(newDate.getMonth() - 1)
  currentDate.value = newDate
}

function nextMonth() {
  const newDate = new Date(currentDate.value)
  newDate.setMonth(newDate.getMonth() + 1)
  currentDate.value = newDate
}

function prevWeek() {
  const newDate = new Date(currentDate.value)
  newDate.setDate(newDate.getDate() - 7)
  currentDate.value = newDate
}

function nextWeek() {
  const newDate = new Date(currentDate.value)
  newDate.setDate(newDate.getDate() + 7)
  currentDate.value = newDate
}

function prevNav() {
  if (viewMode.value === 'week') {
    prevWeek()
  } else {
    prevMonth()
  }
}

function nextNav() {
  if (viewMode.value === 'week') {
    nextWeek()
  } else {
    nextMonth()
  }
}

function goToday() {
  currentDate.value = new Date()
}

// 获取发布窗口数据
async function fetchReleaseWindows() {
  loading.value = true
  try {
    const result = await list({ page: 1, pageSize: 100 })
    releaseWindows.value = result.list
  } catch (e) {
    handleError(e)
  } finally {
    loading.value = false
  }
}

// 监听日期变化重新加载数据
watch(currentDate, () => {
  fetchReleaseWindows()
})

onMounted(() => {
  fetchReleaseWindows()
})
</script>

<style scoped>
.calendar-page {
  padding: 20px;
  background-color: #fff;
  border-radius: 4px;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.calendar-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.calendar-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.view-switcher {
  margin-right: 4px;
}

.current-month {
  font-size: 18px;
  font-weight: 500;
  min-width: 180px;
  text-align: center;
}

/* ===== 月视图 ===== */
.calendar-grid {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background-color: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.weekday {
  padding: 12px 8px;
  text-align: center;
  font-weight: 500;
  color: #606266;
}

.calendar-days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.calendar-day {
  min-height: 100px;
  padding: 8px;
  border-right: 1px solid #e4e7ed;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fff;
}

.calendar-day:nth-child(7n) {
  border-right: none;
}

.calendar-day:nth-last-child(-n+7) {
  border-bottom: none;
}

.calendar-day.other-month {
  background-color: #fafafa;
}

.calendar-day.other-month .day-number {
  color: #c0c4cc;
}

.calendar-day.today {
  background-color: #ecf5ff;
}

.calendar-day.today .day-number {
  background-color: #409eff;
  color: #fff;
  border-radius: 50%;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.day-number {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
}

.day-events {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* ===== 周视图 ===== */
.week-grid {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}

.week-columns {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.week-column {
  border-right: 1px solid #e4e7ed;
  min-height: 300px;
}

.week-column:last-child {
  border-right: none;
}

.week-column.today {
  background-color: #ecf5ff;
}

.week-column-header {
  padding: 12px 8px;
  text-align: center;
  background-color: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.week-day-name {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.week-day-number {
  font-size: 20px;
  font-weight: 600;
  margin-top: 4px;
  color: #303133;
}

.week-day-number.today-number {
  background-color: #409eff;
  color: #fff;
  border-radius: 50%;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 4px auto 0;
}

.week-column-events {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.no-events {
  text-align: center;
  color: #c0c4cc;
  font-size: 18px;
  margin-top: 12px;
}

.event-item-week {
  flex-direction: column;
  align-items: flex-start !important;
  padding: 4px 6px !important;
  gap: 1px !important;
}

.event-time {
  font-size: 11px;
  opacity: 0.85;
  font-weight: 600;
}

/* ===== 共用事件样式 ===== */
.event-item {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 4px;
}

.event-item:hover {
  opacity: 0.8;
}

.event-item.status-draft {
  background-color: #e6a23c;
  color: #fff;
}

.event-item.status-published {
  background-color: #67c23a;
  color: #fff;
}

.event-item.status-closed {
  background-color: #909399;
  color: #fff;
}

.event-item.status-planned {
  background-color: #409eff;
  color: #fff;
}

.event-item.status-active {
  background-color: #f56c6c;
  color: #fff;
}

.event-item.status-frozen {
  background-color: #b3d8ff;
  color: #303133;
}

.event-item.status-cancelled {
  background-color: #e4e7ed;
  color: #909399;
  text-decoration: line-through;
}

.event-item.frozen {
  border: 2px solid #409eff;
}

.frozen-icon {
  font-size: 10px;
}

.event-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.more-events {
  font-size: 11px;
  color: #909399;
  padding: 2px 6px;
}

/* ===== 图例 ===== */
.calendar-legend {
  display: flex;
  gap: 24px;
  margin-top: 16px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.legend-dot.status-draft {
  background-color: #e6a23c;
}

.legend-dot.status-published {
  background-color: #67c23a;
}

.legend-dot.status-closed {
  background-color: #909399;
}
</style>
