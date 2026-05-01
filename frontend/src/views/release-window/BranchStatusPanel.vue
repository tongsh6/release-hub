<template>
  <el-card v-loading="loading" style="margin-top: 16px;">
    <template #header>
      <div class="panel-header">
        <span class="title">分支状态</span>
        <el-button size="small" :icon="RefreshRight" @click="refresh">刷新</el-button>
      </div>
    </template>

    <div v-if="!data || data.repos.length === 0" class="empty-tip">
      暂无分支状态数据（窗口内无关联仓库）
    </div>

    <el-table v-else :data="data.repos" border stripe style="width: 100%">
      <el-table-column prop="repoName" label="仓库" min-width="160" />
      <el-table-column prop="iterationKey" label="迭代" width="150" />
      <el-table-column label="Feature 分支" min-width="200">
        <template #default="{ row }">
          <div class="branch-cell">
            <span class="branch-name">{{ row.featureBranch.branchName }}</span>
            <el-tag
              :type="row.featureBranch.exists ? 'success' : 'info'"
              size="small"
              style="margin-left: 8px;"
            >
              {{ row.featureBranch.exists ? '存在' : '不存在' }}
            </el-tag>
            <span v-if="row.featureBranch.latestCommit" class="commit-sha">
              {{ row.featureBranch.latestCommit.substring(0, 8) }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Release 分支" min-width="200">
        <template #default="{ row }">
          <div class="branch-cell">
            <span class="branch-name">{{ row.releaseBranch.branchName }}</span>
            <el-tag
              :type="row.releaseBranch.exists ? 'success' : 'info'"
              size="small"
              style="margin-left: 8px;"
            >
              {{ row.releaseBranch.exists ? '存在' : '不存在' }}
            </el-tag>
            <span v-if="row.releaseBranch.latestCommit" class="commit-sha">
              {{ row.releaseBranch.latestCommit.substring(0, 8) }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="合并状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="mergeStatusType(row.releaseBranch.mergeStatus)" size="small">
            {{ mergeStatusLabel(row.releaseBranch.mergeStatus) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import { releaseWindowApi, type BranchStatusView } from '@/api/modules/releaseWindow'
import { handleError } from '@/utils/error'

const props = defineProps<{
  windowId: string
}>()

const loading = ref(false)
const data = ref<BranchStatusView | null>(null)

const load = async () => {
  loading.value = true
  try {
    data.value = await releaseWindowApi.getBranchStatus(props.windowId)
  } catch (err) {
    handleError(err)
  } finally {
    loading.value = false
  }
}

const refresh = () => load()

const mergeStatusType = (status: string) => {
  switch (status) {
    case 'MERGED': return 'success'
    case 'CONFLICT': return 'danger'
    default: return 'info'
  }
}

const mergeStatusLabel = (status: string) => {
  switch (status) {
    case 'MERGED': return '已合并'
    case 'CONFLICT': return '冲突'
    default: return '待合并'
  }
}

onMounted(() => load())
</script>

<style scoped>
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header .title {
  font-weight: 600;
}

.empty-tip {
  color: #909399;
  text-align: center;
  padding: 20px 0;
}

.branch-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.branch-name {
  font-family: monospace;
  font-size: 13px;
  color: #303133;
}

.commit-sha {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 0 4px;
  border-radius: 2px;
}
</style>
