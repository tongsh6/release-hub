<template>
  <div class="page-container">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">{{ t('common.back') }}</el-button>
      <span class="page-title">{{ detail?.name || t('repository.columns.repo') }}</span>
      <div class="page-actions">
        <el-button :loading="syncing" @click="handleSync">{{ t('common.sync') }}</el-button>
        <el-button @click="refresh">{{ t('common.refresh') }}</el-button>
        <el-button type="primary" :disabled="!gitlabUrl" @click="openGitLab">{{ t('repository.openGitLab') }}</el-button>
      </div>
    </div>
    <el-card>
      <div class="mb-2">{{ t('repository.gateSummary') }}</div>
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

    <el-card style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span>{{ t('repository.branchesMrSummary') }}</span>
        </div>
      </template>
      <el-row :gutter="20" style="margin-bottom: 20px;">
        <el-col :span="8">
          <el-statistic :title="t('repository.branchSummary.totalBranches')" :value="branchSummary?.totalBranches || 0" />
        </el-col>
        <el-col :span="8">
          <el-statistic :title="t('repository.branchSummary.activeBranches')" :value="branchSummary?.activeBranches || 0" />
        </el-col>
        <el-col :span="8">
          <el-statistic :title="t('repository.branchSummary.nonCompliant')" :value="branchSummary?.nonCompliantBranches || 0" :value-style="branchSummary?.nonCompliantBranches ? { color: 'var(--el-color-danger)' } : {}" />
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-statistic :title="t('repository.branchSummary.activeMrs')" :value="branchSummary?.activeMrs || 0" />
        </el-col>
        <el-col :span="8">
          <el-statistic :title="t('repository.branchSummary.mergedMrs')" :value="branchSummary?.mergedMrs || 0" />
        </el-col>
        <el-col :span="8">
          <el-statistic :title="t('repository.branchSummary.closedMrs')" :value="branchSummary?.closedMrs || 0" />
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import { repositoryApi, type Repository, type GateSummary, type BranchSummary } from '@/api/repositoryApi'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import { handleError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const repoId = route.params.repo as string
const { t } = useI18n()

const goBack = () => {
  router.push({ name: 'Repositories' })
}

const detail = ref<Repository>()
const gateSummary = ref<GateSummary>()
const branchSummary = ref<BranchSummary>()
const syncing = ref(false)

/**
 * 从 cloneUrl 解析出 GitLab Web URL
 * 支持 SSH (git@gitlab.com:group/project.git) 和 HTTPS (https://gitlab.com/group/project.git) 格式
 */
const gitlabUrl = computed(() => {
  const cloneUrl = detail.value?.cloneUrl
  if (!cloneUrl) return ''
  
  // SSH 格式: git@gitlab.example.com:group/project.git
  const sshMatch = cloneUrl.match(/^git@([^:]+):(.+?)(\.git)?$/)
  if (sshMatch) {
    const [, host, path] = sshMatch
    return `https://${host}/${path}`
  }
  
  // HTTPS 格式: https://gitlab.example.com/group/project.git
  const httpsMatch = cloneUrl.match(/^https?:\/\/([^/]+)\/(.+?)(\.git)?$/)
  if (httpsMatch) {
    const [, host, path] = httpsMatch
    return `https://${host}/${path}`
  }
  
  // 如果无法解析，尝试直接打开（去掉 .git 后缀）
  return cloneUrl.replace(/\.git$/, '')
})

/**
 * 在新窗口打开 GitLab 仓库页面
 */
function openGitLab() {
  if (gitlabUrl.value) {
    window.open(gitlabUrl.value, '_blank')
  } else {
    ElMessage.warning(t('repository.gitlabUrlNotAvailable'))
  }
}

async function handleSync() {
  if (!repoId) return
  syncing.value = true
  try {
    await repositoryApi.sync(repoId)
    ElMessage.success(t('common.success'))
    refresh()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'GITLAB_001') {
      ElMessage.warning(t('repository.gitlabMissing'))
    } else {
      handleError(e)
    }
  } finally {
    syncing.value = false
  }
}

async function refresh() {
  if (!repoId) return
  try {
    const [d, g, b] = await Promise.all([
      repositoryApi.get(repoId),
      repositoryApi.getGateSummary(repoId),
      repositoryApi.getBranchSummary(repoId)
    ])
    detail.value = d
    gateSummary.value = g
    branchSummary.value = b
  } catch (e) {
    handleError(e)
  }
}

onMounted(() => {
  refresh()
})
</script>

<style scoped>
/* 页面特定样式 - 通用样式已移至 index.css */
</style>
