<template>
  <el-drawer
    v-model="visible"
    :title="t('common.detail')"
    size="50%"
    destroy-on-close
  >
    <div class="drawer-content">
      <el-card shadow="never" class="mb-4">
        <template #header>
          <div class="card-header">
            <span>{{ t('repository.columns.repo') }}: {{ detail?.name }}</span>
            <div>
              <el-button @click="refresh">{{ t('common.refresh') }}</el-button>
              <el-button type="primary" :disabled="!detail?.cloneUrl" @click="openGitLab">{{ t('repository.openGitLab') }}</el-button>
            </div>
          </div>
        </template>
        
        <el-descriptions :column="1" border>
            <el-descriptions-item :label="t('repository.columns.defaultBranch')">{{ detail?.defaultBranch }}</el-descriptions-item>
            <el-descriptions-item :label="t('repository.columns.groupPath')">{{ groupPath || detail?.groupCode || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('repository.columns.versionStatus')">
              <span>{{ initialVersion?.version || '-' }}</span>
              <el-tag class="version-source-tag" :type="versionSourceTagType" size="small">{{ versionSourceLabel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('repository.columns.cloneUrl')">{{ detail?.cloneUrl }}</el-descriptions-item>
            <el-descriptions-item :label="t('repository.columns.repoType')">
              <el-tag :type="detail?.repoType === 'LIBRARY' ? 'warning' : 'primary'" size="small">{{ t(`repository.repoTypes.${detail?.repoType || 'SERVICE'}`) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('repository.columns.monoRepo')">{{ detail?.monoRepo ? t('common.yes') : t('common.no') }}</el-descriptions-item>
        </el-descriptions>

        <div class="mt-4 mb-2">{{ t('repository.gateSummary') }}</div>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('repository.gateSummaryLabels.protectedBranch')">
            <el-tag :type="gateSummary?.protectedBranch ? 'success' : 'danger'">{{ gateSummary?.protectedBranch ? t('common.yes') : t('common.no') }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('repository.gateSummaryLabels.approvalRequired')">
            <el-tag :type="gateSummary?.approvalRequired ? 'success' : 'warning'">{{ gateSummary?.approvalRequired ? t('common.yes') : t('common.no') }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('repository.gateSummaryLabels.pipelineGate')">
            <el-tag :type="gateSummary?.pipelineGate ? 'success' : 'warning'">{{ gateSummary?.pipelineGate ? t('common.yes') : t('common.no') }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('repository.gateSummaryLabels.permissionDenied')">
            <el-tag :type="gateSummary?.permissionDenied ? 'danger' : 'success'">{{ gateSummary?.permissionDenied ? t('common.yes') : t('common.no') }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ t('repository.branchesMrSummary') }}</span>
          </div>
        </template>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-statistic :title="t('repository.branchSummary.totalBranches')" :value="branchSummary?.totalBranches || 0" />
          </el-col>
          <el-col :span="6">
            <el-statistic :title="t('repository.branchSummary.activeMrs')" :value="branchSummary?.activeMrs || 0" />
          </el-col>
          <el-col :span="6">
            <el-statistic :title="t('repository.branchSummary.mergedMrs')" :value="branchSummary?.mergedMrs || 0" />
          </el-col>
          <el-col :span="6">
            <el-statistic :title="t('repository.branchSummary.closedMrs')" :value="branchSummary?.closedMrs || 0" />
          </el-col>
        </el-row>
      </el-card>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { repositoryApi, type Repository, type GateSummary, type BranchSummary, type InitialVersionView } from '@/api/repositoryApi'
import { groupApi } from '@/api/modules/group'
import { resolveGroupPath } from '@/utils/groupPath'

const { t } = useI18n()

const visible = ref(false)
const repoId = ref('')
const detail = ref<Repository>()
const gateSummary = ref<GateSummary>()
const branchSummary = ref<BranchSummary>()
const initialVersion = ref<InitialVersionView>()
const groupPath = ref('')

const versionSourceLabel = computed(() => {
  const source = initialVersion.value?.versionSource
  if (!source) {
    return t('repository.versionSources.NOT_SET')
  }
  return t(`repository.versionSources.${source}`)
})

const versionSourceTagType = computed(() => {
  const source = initialVersion.value?.versionSource
  if (source === 'VERSION_UNRESOLVED') {
    return 'danger'
  }
  return source ? 'success' : 'info'
})

const open = async (id: string) => {
  repoId.value = id
  visible.value = true
  await refresh()
}

const refresh = async () => {
  if (!repoId.value) return
  try {
    const [d, g, b, v, tree] = await Promise.all([
      repositoryApi.get(repoId.value),
      repositoryApi.getGateSummary(repoId.value),
      repositoryApi.getBranchSummary(repoId.value),
      repositoryApi.getInitialVersion(repoId.value),
      groupApi.listTree().catch(() => [])
    ])
    detail.value = d
    gateSummary.value = g
    branchSummary.value = b
    initialVersion.value = v
    groupPath.value = resolveGroupPath(d.groupCode, tree) || d.groupCode || ''
  } catch (e) {
    console.error(e)
  }
}

const openGitLab = () => {
  if (detail.value?.cloneUrl) {
    // Remove .git suffix if present for web view, though usually not strictly necessary
    const url = detail.value.cloneUrl.replace(/\.git$/, '')
    window.open(url, '_blank')
  }
}

defineExpose({
  open
})
</script>

<style scoped>
/* 页面特定样式 - 通用样式已移至 index.css */
.version-source-tag {
  margin-left: 8px;
}
</style>
