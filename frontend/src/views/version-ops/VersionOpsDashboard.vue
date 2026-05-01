<template>
  <div class="page-container">
    <div class="scan-config-area">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>{{ t('versionOps.scanConfig') }}</span>
            <el-button v-perm.disable="'version-ops:write'" type="primary" @click="handleRunScan">{{ t('versionOps.runScan') }}</el-button>
          </div>
        </template>
        <el-form :inline="true" :model="scanForm">
          <el-form-item :label="t('versionOps.targetBranch')">
            <el-select v-model="scanForm.branch" :placeholder="t('versionOps.selectBranch')">
              <el-option label="main" value="main" />
              <el-option label="develop" value="develop" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('versionOps.scanType')">
            <el-checkbox-group v-model="scanForm.types">
              <el-checkbox :label="t('versionOps.dependencyCheck')" value="dependency" />
              <el-checkbox :label="t('versionOps.codeQuality')" value="quality" />
            </el-checkbox-group>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <div class="scan-results-area">
      <h3>{{ t('versionOps.scanHistory') }}</h3>
      <el-table :data="tableData" style="width: 100%" border>
        <el-table-column prop="id" :label="t('versionOps.runId')" width="100" />
        <el-table-column prop="date" :label="t('versionOps.date')" width="180" />
        <el-table-column prop="branch" :label="t('versionOps.branch')" width="120" />
        <el-table-column prop="status" :label="t('versionOps.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : row.status === 'failed' ? 'danger' : 'warning'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('releaseWindow.actions')" width="150">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewDetail(row)">{{ t('versionOps.viewDetails') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { hasPerm } from '@/utils/perm'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const router = useRouter()

const scanForm = reactive({
  branch: 'main',
  types: ['dependency']
})

const tableData = ref([
  {
    id: '1001',
    date: '2024-03-20 10:00:00',
    branch: 'main',
    status: 'success'
  },
  {
    id: '1002',
    date: '2024-03-19 15:30:00',
    branch: 'develop',
    status: 'failed'
  }
])

const handleRunScan = () => {
  if (!hasPerm('version-ops:write')) {
    ElMessage.warning(t('common.permissionDenied'))
    return
  }
  console.log('Run scan', scanForm)
}

const handleViewDetail = (row: any) => {
  router.push(`/version-ops/runs/${row.id}`)
}
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.scan-config-area {
  margin-bottom: 20px;
}
.scan-results-area {
  background-color: #fff;
  padding: 20px;
  border-radius: 4px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
