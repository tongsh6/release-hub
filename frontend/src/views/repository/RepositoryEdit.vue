<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'edit' ? t('common.edit') : t('repository.addOrSync')"
    width="520px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      status-icon
    >
      <el-form-item :label="t('repository.columns.repo')" prop="name">
        <el-input v-model="form.name" :placeholder="t('repository.placeholders.name')" />
      </el-form-item>
      <el-form-item :label="t('repository.columns.cloneUrl')" prop="cloneUrl">
        <el-input v-model="form.cloneUrl" :placeholder="t('repository.placeholders.cloneUrl')" />
      </el-form-item>
      <el-form-item :label="t('repository.columns.defaultBranch')" prop="defaultBranch">
        <el-input v-model="form.defaultBranch" :placeholder="t('repository.placeholders.defaultBranch')" />
      </el-form-item>
      <el-form-item :label="t('group.title')" prop="groupCode">
        <GroupTreeSelect
          v-model="form.groupCode"
          :leaf-only="true"
        />
      </el-form-item>
      <el-form-item :label="t('repository.columns.repoType')" prop="repoType">
        <el-radio-group v-model="form.repoType">
          <el-radio value="SERVICE">{{ t('repository.repoTypes.SERVICE') }}</el-radio>
          <el-radio value="LIBRARY">{{ t('repository.repoTypes.LIBRARY') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('repository.columns.initialVersion')" prop="initialVersion">
        <el-input v-model="form.initialVersion" :placeholder="t('repository.placeholders.initialVersion')" />
      </el-form-item>
      <el-form-item :label="t('repository.columns.monoRepo')" prop="monoRepo">
        <el-switch v-model="form.monoRepo" />
      </el-form-item>

      <!-- Git 配置折叠区 -->
      <el-divider content-position="left">Git 配置</el-divider>
      <el-form-item label="Git Provider" prop="gitProvider">
        <el-select v-model="form.gitProvider" placeholder="选择 Git 提供商" style="width: 100%;">
          <el-option label="Mock（测试用）" value="MOCK" />
          <el-option label="GitHub" value="GITHUB" />
          <el-option label="GitLab" value="GITLAB" />
        </el-select>
      </el-form-item>
      <el-form-item label="Git Token" prop="gitToken">
        <el-input
          v-model="form.gitToken"
          type="password"
          show-password
          placeholder="Personal Access Token"
          clearable
        />
        <div v-if="mode === 'edit' && maskedToken" class="token-hint">
          当前 Token: {{ maskedToken }}
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" @click="submit">
          {{ t('common.confirm') }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance } from 'element-plus'
import { repositoryApi, type CreateRepoReq, type GitProvider } from '@/api/repositoryApi'
import GroupTreeSelect from '@/components/common/GroupTreeSelect.vue'
import { handleError } from '@/utils/error'

interface RepoForm extends CreateRepoReq {
  gitProvider: GitProvider
  gitToken: string
}

const emit = defineEmits(['success'])
const { t } = useI18n()

const visible = ref(false)
const loading = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'create' | 'edit'>('create')
const currentId = ref<string | null>(null)
const originalToken = ref<string | null>(null)

const form = reactive<RepoForm>({
  name: '',
  cloneUrl: '',
  defaultBranch: 'main',
  repoType: 'SERVICE',
  monoRepo: false,
  initialVersion: '',
  groupCode: '',
  gitProvider: 'MOCK',
  gitToken: ''
})

const maskedToken = computed(() => {
  if (!originalToken.value) return ''
  if (originalToken.value.length <= 4) return '****'
  return originalToken.value.substring(0, 4) + '****'
})

const rules = {
  name: [
    { required: true, message: t('common.pleaseEnter') + t('repository.columns.repo'), trigger: 'blur' },
    { max: 128, message: t('repository.validation.name'), trigger: 'blur' }
  ],
  cloneUrl: [
    { required: true, message: t('common.pleaseEnter') + t('repository.columns.cloneUrl'), trigger: 'blur' },
    { max: 512, message: t('repository.validation.cloneUrl'), trigger: 'blur' }
  ],
  defaultBranch: [
    { max: 128, message: t('repository.validation.defaultBranch'), trigger: 'blur' }
  ],
  groupCode: [
    { required: true, message: t('group.selectGroup'), trigger: 'change' }
  ],
  initialVersion: [
    { max: 50, message: t('repository.validation.initialVersion'), trigger: 'blur' }
  ]
}

const open = (repo?: any) => {
  visible.value = true
  form.name = repo?.name || ''
  form.cloneUrl = repo?.cloneUrl || ''
  form.defaultBranch = repo?.defaultBranch || 'main'
  form.repoType = repo?.repoType || 'SERVICE'
  form.monoRepo = repo?.monoRepo ?? false
  form.initialVersion = ''
  form.groupCode = repo?.groupCode || ''
  form.gitProvider = repo?.gitProvider || 'MOCK'
  form.gitToken = ''
  originalToken.value = repo?.gitToken || null
  mode.value = repo ? 'edit' : 'create'
  currentId.value = repo?.id || null
  if (repo?.id) {
    const id = String(repo.id)
    repositoryApi.getInitialVersion(id)
      .then((res) => {
        if (currentId.value !== id) return
        form.initialVersion = res.version || ''
      })
      .catch(() => {})
  }
}

const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const payload = {
          name: form.name,
          cloneUrl: form.cloneUrl,
          defaultBranch: form.defaultBranch,
          repoType: form.repoType,
          monoRepo: form.monoRepo,
          initialVersion: form.initialVersion || undefined,
          groupCode: form.groupCode,
          gitProvider: form.gitProvider,
          gitToken: form.gitToken || undefined
        }
        if (mode.value === 'edit' && currentId.value) {
          await repositoryApi.update(currentId.value, payload)
        } else {
          await repositoryApi.create(payload)
        }
        ElMessage.success(t('common.success'))
        visible.value = false
        emit('success')
      } catch (e) {
        handleError(e)
      } finally {
        loading.value = false
      }
    }
  })
}

defineExpose({
  open
})
</script>

<style scoped>
.token-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  font-family: monospace;
}
</style>
