<template>
  <div v-loading="loading" class="page-container">
    <div class="toolbar">
      <el-button type="primary" @click="fetchData">{{ t('common.refresh') }}</el-button>
    </div>
    <el-row :gutter="16">
      <el-col v-for="group in groups" :key="group.reasonKey" :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="title">{{ t(`audit.reasons.${group.reasonKey}`) }}</span>
              <el-tag :type="group.items.length > 0 ? 'danger' : 'info'" effect="dark" round>
                {{ group.items.length }}
              </el-tag>
            </div>
          </template>
          <div class="card-body">
            <el-empty v-if="group.items.length === 0" :description="t('common.noData')" :image-size="60" />
            <el-scrollbar v-else max-height="400px">
              <div v-for="item in group.items" :key="item.id" class="block-item">
                <div class="item-header">
                  <span class="repo-name">{{ item.repo }}</span>
                  <el-tag size="small">{{ item.iterationKey }}</el-tag>
                </div>
                <div class="item-desc">
                  {{ item.message }}
                </div>
                <div class="item-footer">
                  <span class="time">{{ formatTime(item.updatedAt) }}</span>
                  <el-button link type="primary" size="small" @click="viewRun(item.runId)">
                    {{ t('common.detail') }}
                  </el-button>
                </div>
              </div>
            </el-scrollbar>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { runApi } from '@/api/runApi'
import dayjs from 'dayjs'

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)

interface BlockedItem {
  id: string // composite key
  runId: string
  repo: string
  iterationKey: string
  message: string
  updatedAt: string
}

interface BlockGroup {
  reasonKey: string
  items: BlockedItem[]
}

const groups = ref<BlockGroup[]>([
  { reasonKey: 'conflict', items: [] },
  { reasonKey: 'pipelineFailed', items: [] },
  { reasonKey: 'approvalRequired', items: [] }
])

const fetchData = async () => {
  loading.value = true
  try {
    // 1. Fetch recent runs (last 50 for example)
    const res = await runApi.list({ page: 1, pageSize: 50 })
    const runs = res.list
    
    // 2. Fetch details for failed runs (since list only gives summary)
    // Optimization: In real world, backend should provide this aggregation.
    // For now, we fetch details for runs that are FAILED or PARTIAL_SUCCESS
    const targetRuns = runs.filter(r => r.status === 'FAILED' || r.status === 'PARTIAL_SUCCESS')
    
    const allBlockedItems: BlockedItem[] = []
    
    // Parallel fetch details
    const details = await Promise.all(targetRuns.map(r => runApi.getRunById(r.id)))
    
    details.forEach(run => {
      run.items.forEach(item => {
        if (item.finalResult === 'FAILED') {
          // Check last step for reason
          const lastStep = item.steps[item.steps.length - 1]
          const msg = lastStep?.message || 'Unknown error'
          
          allBlockedItems.push({
            id: `${run.id}-${item.repo}-${item.iterationKey}`,
            runId: run.id,
            repo: item.repo,
            iterationKey: item.iterationKey,
            message: msg,
            updatedAt: run.finishedAt || run.startedAt
          })
        }
      })
    })

    // Distribute to groups
    groups.value[0].items = allBlockedItems.filter(i => i.message.toLowerCase().includes('conflict'))
    groups.value[1].items = allBlockedItems.filter(i => !i.message.toLowerCase().includes('conflict') && !i.message.toLowerCase().includes('approval'))
    groups.value[2].items = allBlockedItems.filter(i => i.message.toLowerCase().includes('approval'))

  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const formatTime = (t: string) => dayjs(t).format('MM-DD HH:mm')

const viewRun = (runId: string) => {
  router.push(`/runs/${runId}`)
}

onMounted(fetchData)
</script>

<style scoped>
.page-container { padding: 20px; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-weight: bold; }
.block-item {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 12px 0;
}
.block-item:last-child { border-bottom: none; }
.item-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
.repo-name { font-weight: 500; color: var(--el-text-color-primary); }
.item-desc { color: var(--el-text-color-secondary); font-size: 13px; margin-bottom: 8px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.item-footer { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
