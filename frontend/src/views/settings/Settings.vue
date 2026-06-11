<template>
  <div class="settings-page">
    <div class="page-header">
      <h2>{{ t('menu.settings') }}</h2>
    </div>

    <section class="settings-section">
      <h3>{{ t('settings.group.external') }}</h3>
      <el-card shadow="never" class="setting-card">
        <template #header>
          <span class="card-title">GitLab</span>
        </template>
        <el-form :model="gitlabForm" label-width="120px">
          <el-form-item :label="t('settings.labels.baseUrl')">
            <el-input v-model="gitlabForm.baseUrl" placeholder="https://gitlab.com" />
          </el-form-item>
          <el-form-item :label="t('settings.labels.token')">
            <el-input v-model="gitlabForm.token" type="password" show-password placeholder="Access Token" />
          </el-form-item>
          <el-form-item>
            <el-button :loading="testingGitlab" @click="testGitLab">{{ t('settings.buttons.testConnection') }}</el-button>
            <el-button type="primary" :loading="savingGitlab" @click="saveGitLab">{{ t('common.save') }}</el-button>
          </el-form-item>
          <el-alert
            v-if="gitlabTestError"
            class="gitlab-diagnostic"
            type="error"
            show-icon
            :closable="false"
            :title="gitlabTestError"
          />
        </el-form>
      </el-card>
    </section>

    <section class="settings-section">
      <h3>{{ t('settings.group.rules') }}</h3>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-card shadow="never" class="setting-card link-card" @click="goTo('/branch-rules')">
            <template #header>
              <span class="card-title">{{ t('menu.branchRules') }}</span>
            </template>
            <p class="card-desc">{{ t('settings.desc.branchRules') }}</p>
            <el-button size="small" class="card-action">{{ t('settings.buttons.enter') }}</el-button>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-card shadow="never" class="setting-card link-card" @click="goTo('/version-policies')">
            <template #header>
              <span class="card-title">{{ t('menu.versionPolicies') }}</span>
            </template>
            <p class="card-desc">{{ t('settings.desc.versionPolicies') }}</p>
            <el-button size="small" class="card-action">{{ t('settings.buttons.enter') }}</el-button>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="setting-card">
        <template #header>
          <span class="card-title">{{ t('settings.tabs.blockPolicy') }}</span>
        </template>
        <el-radio-group v-model="blockingForm.defaultPolicy">
          <el-radio value="FAIL_FAST">{{ t('settings.policy.failFast') }}</el-radio>
          <el-radio value="CONTINUE_ON_BLOCK">{{ t('settings.policy.continueOnBlock') }}</el-radio>
        </el-radio-group>
        <div class="form-actions">
          <el-button type="primary" :loading="savingBlocking" @click="saveBlocking">{{ t('common.save') }}</el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="setting-card">
        <template #header>
          <span class="card-title">{{ t('settings.tabs.refs') }}</span>
        </template>
        <el-empty :description="t('settings.messages.refsNotConfigurable')" />
      </el-card>
    </section>

    <section class="settings-section">
      <h3>{{ t('settings.group.general') }}</h3>
      <el-card shadow="never" class="setting-card">
        <template #header>
          <span class="card-title">{{ t('settings.tabs.naming') }}</span>
        </template>
        <el-form :model="namingForm" label-width="160px">
          <el-form-item :label="t('settings.labels.featureTemplate')">
            <el-input v-model="namingForm.featureTemplate" placeholder="feature/{code}-{desc}" />
          </el-form-item>
          <el-form-item :label="t('settings.labels.releaseTemplate')">
            <el-input v-model="namingForm.releaseTemplate" placeholder="release/{version}" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingNaming" @click="saveNaming">{{ t('common.save') }}</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="setting-card">
        <template #header>
          <span class="card-title">{{ t('settings.tabs.display') }}</span>
        </template>
        <el-empty :description="t('common.todo')" />
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { settingsApi, type GitLabSettings, type NamingSettings, type BlockingSettings } from '@/api/settingsApi'
import { handleError } from '@/utils/error'
import { ApiError } from '@/api/http'

const { t } = useI18n()
const router = useRouter()

const savingGitlab = ref(false)
const savingNaming = ref(false)
const savingBlocking = ref(false)
const testingGitlab = ref(false)
const gitlabTestError = ref('')

const gitlabForm = ref<GitLabSettings>({ baseUrl: '', token: '' })
const namingForm = ref<NamingSettings>({ featureTemplate: '', releaseTemplate: '' })
const blockingForm = ref<BlockingSettings>({ defaultPolicy: 'FAIL_FAST' })

const goTo = (path: string) => {
  router.push(path)
}

const loadGitLab = async () => {
  try {
    const data = await settingsApi.getGitLab()
    if (data) gitlabForm.value = data
  } catch {
    // ignore error on load
  }
}

const saveGitLab = async () => {
  savingGitlab.value = true
  try {
    await settingsApi.saveGitLab(gitlabForm.value)
    ElMessage.success(t('common.saveSuccess'))
  } catch (error) {
    handleError(error)
  } finally {
    savingGitlab.value = false
  }
}

const testGitLab = async () => {
  testingGitlab.value = true
  gitlabTestError.value = ''
  try {
    await settingsApi.testGitLab()
    ElMessage.success(t('settings.messages.connectionSuccess'))
  } catch (error) {
    if (error instanceof ApiError && error.code.startsWith('GITLAB_')) {
      gitlabTestError.value = error.message
    }
    handleError(error)
  } finally {
    testingGitlab.value = false
  }
}

const loadNaming = async () => {
  try {
    const data = await settingsApi.getNaming()
    if (data) namingForm.value = data
  } catch {
    // ignore error on load
  }
}

const saveNaming = async () => {
  savingNaming.value = true
  try {
    await settingsApi.saveNaming(namingForm.value)
    ElMessage.success(t('common.saveSuccess'))
  } catch (error) {
    handleError(error)
  } finally {
    savingNaming.value = false
  }
}

const loadBlocking = async () => {
  try {
    const data = await settingsApi.getBlocking()
    if (data) blockingForm.value = data
  } catch {
    // ignore error on load
  }
}

const saveBlocking = async () => {
  savingBlocking.value = true
  try {
    await settingsApi.saveBlocking(blockingForm.value)
    ElMessage.success(t('common.saveSuccess'))
  } catch (error) {
    handleError(error)
  } finally {
    savingBlocking.value = false
  }
}

onMounted(() => {
  loadGitLab()
  loadNaming()
  loadBlocking()
})
</script>

<style scoped>
.settings-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 18px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.settings-section {
  margin-bottom: 24px;
}

.settings-section h3 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.setting-card {
  margin-bottom: 16px;
  border-radius: 6px;
}

.card-title {
  font-weight: 600;
}

.link-card {
  min-height: 140px;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.link-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: var(--el-box-shadow-light);
}

.card-desc {
  min-height: 40px;
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.card-action {
  pointer-events: none;
}

.form-actions {
  margin-top: 16px;
}

.gitlab-diagnostic {
  margin-top: 12px;
}
</style>
