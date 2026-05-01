<template>
  <div class="dashboard-container">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card v-loading="loading" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.totalRepositories') }}</span>
            </div>
          </template>
          <div class="card-value">{{ stats?.totalRepositories ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-loading="loading" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.totalIterations') }}</span>
            </div>
          </template>
          <div class="card-value">{{ stats?.totalIterations ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-loading="loading" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.activeWindows') }}</span>
            </div>
          </template>
          <div class="card-value">{{ stats?.activeWindows ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card v-loading="loading" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.recentRuns') }}</span>
            </div>
          </template>
          <div class="card-value">{{ stats?.recentRuns ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.recentActivity') }}</span>
            </div>
          </template>
          <el-empty :description="t('dashboard.noActivity')" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.quickActions') }}</span>
            </div>
          </template>
          <div class="quick-actions">
            <el-button type="primary" plain @click="goToNewRelease">{{ t('dashboard.newRelease') }}</el-button>
            <el-button type="success" plain @click="refresh">{{ t('dashboard.refresh') }}</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { dashboardApi, type DashboardStats } from '@/api/dashboardApi'
import { handleError } from '@/utils/error'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const stats = ref<DashboardStats>()

async function fetchStats() {
  loading.value = true
  try {
    stats.value = await dashboardApi.getStats()
  } catch (e) {
    handleError(e)
  } finally {
    loading.value = false
  }
}

function goToNewRelease() {
  router.push('/release-windows')
}

function refresh() {
  fetchStats()
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-value {
  font-size: 28px;
  font-weight: bold;
  text-align: center;
  padding: 10px 0;
}
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
