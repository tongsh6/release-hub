<template>
  <div class="page-container">
    <el-tabs v-model="active" @tab-change="handleTabChange">
      <!-- GitLab Tab -->
      <el-tab-pane :label="t('settings.tabs.gitlab')" name="gitlab">
        <el-form :model="gitlabForm" label-width="140px" class="pane-card">
          <el-form-item :label="t('settings.labels.baseUrl')">
            <el-input v-model="gitlabForm.baseUrl" placeholder="https://gitlab.com" />
          </el-form-item>
          <el-form-item :label="t('settings.labels.token')">
            <el-input v-model="gitlabForm.token" type="password" show-password placeholder="Access Token" />
          </el-form-item>
          <el-form-item>
            <el-button :loading="testing" @click="testGitLab">{{ t('settings.buttons.testConnection') }}</el-button>
            <el-button type="primary" :loading="saving" @click="saveGitLab">{{ t('common.save') }}</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Naming Tab -->
      <el-tab-pane :label="t('settings.tabs.naming')" name="naming">
        <el-form :model="namingForm" label-width="160px" class="pane-card">
          <el-form-item :label="t('settings.labels.featureTemplate')">
             <el-input v-model="namingForm.featureTemplate" placeholder="feature/{code}-{desc}" />
          </el-form-item>
          <el-form-item :label="t('settings.labels.releaseTemplate')">
             <el-input v-model="namingForm.releaseTemplate" placeholder="release/{version}" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveNaming">{{ t('common.save') }}</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Refs Tab (Empty for now) -->
      <el-tab-pane :label="t('settings.tabs.refs')" name="refs">
        <el-empty :description="t('common.todo')" />
      </el-tab-pane>

      <!-- Block Policy Tab -->
      <el-tab-pane :label="t('settings.tabs.blockPolicy')" name="block-policy">
        <div class="pane-card">
          <el-radio-group v-model="blockingForm.defaultPolicy">
            <el-radio label="FAIL_FAST">{{ t('settings.policy.failFast') }}</el-radio>
            <el-radio label="CONTINUE_ON_BLOCK">{{ t('settings.policy.continueOnBlock') }}</el-radio>
          </el-radio-group>
          <div style="margin-top: 20px;">
            <el-button type="primary" :loading="saving" @click="saveBlocking">{{ t('common.save') }}</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- Display Tab (Empty) -->
      <el-tab-pane :label="t('settings.tabs.display')" name="display">
        <el-empty :description="t('common.todo')" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { settingsApi, type GitLabSettings, type NamingSettings, type BlockingSettings } from '@/api/settingsApi'
import { handleError } from '@/utils/error'

const { t } = useI18n()
const active = ref('gitlab')
const saving = ref(false)
const testing = ref(false)

const gitlabForm = ref<GitLabSettings>({ baseUrl: '', token: '' })
const namingForm = ref<NamingSettings>({ featureTemplate: '', releaseTemplate: '' })
const blockingForm = ref<BlockingSettings>({ defaultPolicy: 'FAIL_FAST' })

const loadGitLab = async () => {
  try {
    const data = await settingsApi.getGitLab()
    if (data) gitlabForm.value = data
  } catch {
    // ignore error on load
  }
}

const saveGitLab = async () => {
  saving.value = true
  try {
    await settingsApi.saveGitLab(gitlabForm.value)
    ElMessage.success(t('common.saveSuccess'))
  } catch (error) {
    handleError(error)
  } finally {
    saving.value = false
  }
}

const testGitLab = async () => {
  testing.value = true
  try {
    await settingsApi.testGitLab()
    ElMessage.success(t('common.success'))
  } catch (error) {
    handleError(error)
  } finally {
    testing.value = false
  }
}

const loadNaming = async () => {
  try {
    const data = await settingsApi.getNaming()
    if (data) namingForm.value = data
  } catch {
    /* ignore */
  }
}

const saveNaming = async () => {
  saving.value = true
  try {
    await settingsApi.saveNaming(namingForm.value)
    ElMessage.success(t('common.saveSuccess'))
  } catch (error) {
    handleError(error)
  } finally {
    saving.value = false
  }
}

const loadBlocking = async () => {
  try {
    const data = await settingsApi.getBlocking()
    if (data) blockingForm.value = data
  } catch {
    /* ignore */
  }
}

const saveBlocking = async () => {
  saving.value = true
  try {
    await settingsApi.saveBlocking(blockingForm.value)
    ElMessage.success(t('common.saveSuccess'))
  } catch (error) {
    handleError(error)
  } finally {
    saving.value = false
  }
}

const handleTabChange = (name: any) => {
  if (name === 'gitlab') loadGitLab()
  if (name === 'naming') loadNaming()
  if (name === 'block-policy') loadBlocking()
}

onMounted(() => {
  loadGitLab()
})
</script>

<style scoped>
.page-container { padding: 20px; }
.pane-card { background: #fff; padding: 20px; border-radius: 4px; }
</style>
