<template>
  <el-dialog
    v-model="visible"
    :title="t('releaseWindow.versionUpdate.title')"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
    >
      <el-form-item
        v-if="repositories.length > 1"
        :label="t('releaseWindow.versionUpdate.scope')"
      >
        <el-radio-group v-model="updateScope">
          <el-radio label="SINGLE">{{ t('releaseWindow.versionUpdate.scopeSingle') }}</el-radio>
          <el-radio label="BATCH">{{ t('releaseWindow.versionUpdate.scopeBatch') }}</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item :label="t('releaseWindow.versionUpdate.repoId')" prop="repoId">
        <el-select
          v-if="updateScope === 'SINGLE'"
          v-model="form.repoId"
          :placeholder="t('releaseWindow.versionUpdate.selectRepo')"
          filterable
          style="width: 100%"
          @change="handleRepoChange"
        >
          <el-option
            v-for="repo in repositories"
            :key="repo.id"
            :label="repo.name"
            :value="repo.id"
          />
        </el-select>
        <el-select
          v-else
          v-model="selectedRepoIds"
          :placeholder="t('releaseWindow.versionUpdate.selectRepos')"
          filterable
          multiple
          style="width: 100%"
        >
          <el-option
            v-for="repo in repositories"
            :key="repo.id"
            :label="repo.name"
            :value="repo.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('releaseWindow.versionUpdate.buildTool')" prop="buildTool">
        <el-radio-group v-model="form.buildTool">
          <el-radio label="MAVEN">Maven</el-radio>
          <el-radio label="GRADLE">Gradle</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item :label="t('releaseWindow.versionUpdate.policy')">
        <el-select
          v-model="selectedPolicyId"
          :placeholder="t('releaseWindow.versionUpdate.selectPolicy')"
          :loading="policyLoading"
          filterable
          style="width: 100%"
          @change="handlePolicyChange"
        >
          <el-option
            v-for="policy in versionPolicies"
            :key="policy.id"
            :label="policyLabel(policy)"
            :value="policy.id"
          />
        </el-select>
        <div class="form-item-tip">
          {{ policyTip }}
        </div>
      </el-form-item>

      <el-form-item :label="t('releaseWindow.versionUpdate.targetVersion')" prop="targetVersion">
        <el-input
          v-model="form.targetVersion"
          :placeholder="t('releaseWindow.versionUpdate.targetVersionPlaceholder')"
        />
        <template #error="{ error }">
          <div class="el-form-item__error">{{ error }}</div>
        </template>
        <div class="form-item-tip">
          {{ t('releaseWindow.versionUpdate.targetVersionTip') }}
        </div>
      </el-form-item>

      <el-form-item :label="t('releaseWindow.versionUpdate.repoPath')" prop="repoPath">
        <el-input
          v-model="form.repoPath"
          :placeholder="t('releaseWindow.versionUpdate.repoPathPlaceholder')"
        />
        <div class="form-item-tip">
          {{ t('releaseWindow.versionUpdate.repoPathTip') }}
        </div>
      </el-form-item>

      <el-form-item
        v-if="form.buildTool === 'MAVEN'"
        :label="t('releaseWindow.versionUpdate.pomPath')"
        prop="pomPath"
      >
        <el-input
          v-model="form.pomPath"
          :placeholder="t('releaseWindow.versionUpdate.pomPathPlaceholder')"
        />
      </el-form-item>

      <el-form-item
        v-if="form.buildTool === 'GRADLE'"
        :label="t('releaseWindow.versionUpdate.gradlePropertiesPath')"
        prop="gradlePropertiesPath"
      >
        <el-input
          v-model="form.gradlePropertiesPath"
          :placeholder="t('releaseWindow.versionUpdate.gradlePropertiesPathPlaceholder')"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="conflictBlocked"
        :title="conflictBlocked ? t('conflict.resolveBeforeExecute') : ''"
        @click="handleSubmit"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { releaseWindowApi, getConflicts, type VersionUpdateRequest } from '@/api/modules/releaseWindow'
import { repositoryApi, type Repository } from '@/api/repositoryApi'
import { versionPolicyApi, type VersionPolicyDisplay } from '@/api/versionPolicyApi'
import { handleError } from '@/utils/error'
import type { BuildTool } from '@/types/dto'

const { t } = useI18n()

const emit = defineEmits<{
  success: []
}>()

const visible = ref(false)
const submitting = ref(false)
const conflictBlocked = ref(false)
const formRef = ref<FormInstance>()
const repositories = ref<Repository[]>([])
const scopedRepositories = ref(false)
const updateScope = ref<'SINGLE' | 'BATCH'>('SINGLE')
const selectedRepoIds = ref<string[]>([])
const versionPolicies = ref<VersionPolicyDisplay[]>([])
const selectedPolicyId = ref('')
const currentVersion = ref('')
const policyLoading = ref(false)

const form = reactive<VersionUpdateRequest & { buildTool: BuildTool }>({
  repoId: '',
  targetVersion: '',
  buildTool: 'MAVEN',
  repoPath: '',
  pomPath: '',
  gradlePropertiesPath: ''
})

const rules: FormRules = {
  repoId: [{ required: true, message: t('releaseWindow.versionUpdate.repoIdRequired'), trigger: 'change' }],
  targetVersion: [{ required: true, message: t('releaseWindow.versionUpdate.targetVersionRequired'), trigger: 'blur' }],
  buildTool: [{ required: true, message: t('releaseWindow.versionUpdate.buildToolRequired'), trigger: 'change' }],
  repoPath: [{ required: true, message: t('releaseWindow.versionUpdate.repoPathRequired'), trigger: 'blur' }]
}

let windowId = ''

const resetForm = () => {
  formRef.value?.resetFields()
  form.repoId = ''
  form.targetVersion = ''
  form.buildTool = 'MAVEN'
  form.repoPath = ''
  form.pomPath = ''
  form.gradlePropertiesPath = ''
  versionPolicies.value = []
  selectedPolicyId.value = ''
  currentVersion.value = ''
  updateScope.value = 'SINGLE'
  selectedRepoIds.value = []
}

const loadRepositories = async () => {
  if (scopedRepositories.value) return

  try {
    const result = await repositoryApi.list({ page: 1, pageSize: 100 })
    repositories.value = result.list
  } catch (err) {
    handleError(err)
  }
}

const selectedPolicy = computed(() => {
  return versionPolicies.value.find(policy => policy.id === selectedPolicyId.value)
})

const policyTip = computed(() => {
  if (!form.repoId) return t('releaseWindow.versionUpdate.policySelectRepoTip')
  if (!selectedPolicy.value) return t('releaseWindow.versionUpdate.policyNotFound')
  const version = currentVersion.value || '-'
  return t('releaseWindow.versionUpdate.policyTip', {
    policy: selectedPolicy.value.name,
    scope: policyScopeLabel(selectedPolicy.value),
    currentVersion: version
  })
})

const handleRepoChange = async (repoId: string) => {
  const repo = repositories.value.find(r => r.id === repoId)
  if (repo) {
    // 根据仓库信息自动填充路径
    if (!form.repoPath) {
      form.repoPath = deriveRepoPath(repo)
    }
    await loadApplicablePolicies(repo)
  } else {
    versionPolicies.value = []
    selectedPolicyId.value = ''
    currentVersion.value = ''
  }
}

const handlePolicyChange = async () => {
  await deriveTargetVersion()
}

watch(updateScope, (scope) => {
  if (scope === 'BATCH') {
    if (selectedRepoIds.value.length === 0) {
      selectedRepoIds.value = form.repoId ? [form.repoId] : repositories.value.map(repo => repo.id)
    }
  } else if (!form.repoId && selectedRepoIds.value.length > 0) {
    form.repoId = selectedRepoIds.value[0]
    void handleRepoChange(form.repoId)
  }
})

watch(selectedRepoIds, (repoIds) => {
  if (updateScope.value !== 'BATCH') return
  const firstRepoId = repoIds[0] || ''
  if (firstRepoId && firstRepoId !== form.repoId) {
    form.repoId = firstRepoId
    void handleRepoChange(firstRepoId)
  }
})

const handleClose = () => {
  visible.value = false
  scopedRepositories.value = false
  repositories.value = []
  resetForm()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) {
      ElMessage.warning(t('releaseWindow.versionUpdate.validationFailed'))
      return
    }

    if (updateScope.value === 'BATCH' && selectedRepoIds.value.length === 0) {
      ElMessage.warning(t('releaseWindow.versionUpdate.repoIdRequired'))
      return
    }

    // 执行前冲突检测
    const conflictsReport = await getConflicts(windowId)
    if (conflictsReport.hasConflicts) {
      ElMessage.warning(t('conflict.resolveBeforeExecute'))
      return
    }

    submitting.value = true
    try {
      const request: VersionUpdateRequest = {
        repoId: form.repoId,
        targetVersion: form.targetVersion,
        buildTool: form.buildTool,
        repoPath: form.repoPath,
        pomPath: form.pomPath || undefined,
        gradlePropertiesPath: form.gradlePropertiesPath || undefined
      }

      ElMessage.info(t('releaseWindow.versionUpdate.executing'))
      const response = updateScope.value === 'BATCH'
        ? await releaseWindowApi.executeBatchVersionUpdate(windowId, {
          targetVersion: form.targetVersion,
          repositories: selectedRepoIds.value.map(repoId => {
            const repo = repositories.value.find(item => item.id === repoId)
            return {
              repoId,
              buildTool: form.buildTool,
              repoPath: repo ? deriveRepoPath(repo) : form.repoPath,
              pomPath: form.pomPath || undefined,
              gradlePropertiesPath: form.gradlePropertiesPath || undefined
            }
          })
        })
        : await releaseWindowApi.executeVersionUpdate(windowId, request)
      ElMessage.success(t('releaseWindow.versionUpdate.success', { runId: response.runId }))
      emit('success')
      handleClose()
    } catch (err) {
      handleError(err)
    } finally {
      submitting.value = false
    }
  })
}

const open = async (id: string, windowRepositories: Repository[] = []) => {
  windowId = id
  resetForm()
  scopedRepositories.value = windowRepositories.length > 0
  repositories.value = [...windowRepositories]
  if (windowRepositories.length === 1) {
    form.repoId = windowRepositories[0].id
    await handleRepoChange(windowRepositories[0].id)
  } else if (windowRepositories.length > 1) {
    form.repoId = windowRepositories[0].id
    selectedRepoIds.value = windowRepositories.map(repo => repo.id)
    await handleRepoChange(windowRepositories[0].id)
  }
  visible.value = true
  conflictBlocked.value = false

  // 打开时预检冲突
  try {
    const conflictsReport = await getConflicts(id)
    conflictBlocked.value = conflictsReport.hasConflicts
  } catch {
    conflictBlocked.value = false
  }

  await loadRepositories()
}

defineExpose({
  open
})

function deriveRepoPath(repo: Repository): string {
  return repo.cloneUrl.replace(/\.git$/, '').split('/').pop() || ''
}

async function loadApplicablePolicies(repo: Repository) {
  policyLoading.value = true
  try {
    const [policies, initialVersion] = await Promise.all([
      versionPolicyApi.applicable({ projectId: repo.groupCode, subProjectId: repo.id }),
      repositoryApi.getInitialVersion(repo.id)
    ])
    versionPolicies.value = policies
    selectedPolicyId.value = policies[0]?.id || ''
    currentVersion.value = initialVersion.version || ''
    await deriveTargetVersion()
  } catch (err) {
    versionPolicies.value = []
    selectedPolicyId.value = ''
    currentVersion.value = ''
    handleError(err)
  } finally {
    policyLoading.value = false
  }
}

async function deriveTargetVersion() {
  if (!selectedPolicyId.value || !currentVersion.value) return
  try {
    const result = await releaseWindowApi.validateVersion(windowId, {
      policyId: selectedPolicyId.value,
      currentVersion: currentVersion.value
    })
    if (result.valid && result.derivedVersion) {
      form.targetVersion = result.derivedVersion
    }
  } catch (err) {
    handleError(err)
  }
}

function policyLabel(policy: VersionPolicyDisplay): string {
  return `${policy.name} - ${policy.strategy} - ${policyScopeLabel(policy)}`
}

function policyScopeLabel(policy: VersionPolicyDisplay): string {
  const level = policy.scope?.level || 'GLOBAL'
  if (level === 'SUB_PROJECT') {
    return `${t('versionPolicy.scopeSubProject')} ${policy.scope?.projectId || ''}/${policy.scope?.subProjectId || ''}`.trim()
  }
  if (level === 'PROJECT') {
    return `${t('versionPolicy.scopeProject')} ${policy.scope?.projectId || ''}`.trim()
  }
  return t('versionPolicy.scopeGlobal')
}
</script>

<style scoped>
.form-item-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}
</style>
