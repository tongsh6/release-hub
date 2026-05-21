<template>
  <div class="version-policy-list-page list-page">
    <SearchForm :loading="loading" @search="search" @reset="reset">
      <el-form-item :label="t('versionPolicy.name')">
        <el-input v-model="query.name" :placeholder="t('versionPolicy.name')" clearable />
      </el-form-item>
    </SearchForm>

    <el-alert 
      type="info" 
      :title="t('versionPolicy.builtInNote')" 
      :closable="false" 
      show-icon 
      class="mb-4"
    />

    <DataTable
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :loading="loading"
      :data="list"
      :total="total"
      @page-change="onPageChange"
      @page-size-change="onPageSizeChange"
    >
      <template #actions>
        <el-button type="primary" @click="handleAdd">{{ t('versionPolicy.create') }}</el-button>
      </template>
      <el-table-column prop="id" label="ID" width="100" />
      <el-table-column prop="name" :label="t('versionPolicy.name')" min-width="180" />
      <el-table-column prop="scheme" :label="t('versionPolicy.scheme')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.scheme === 'SEMVER' ? 'primary' : 'success'" size="small">
            {{ row.scheme }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bumpRule" :label="t('versionPolicy.bumpRule')" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.bumpRule !== 'NONE'" type="info" size="small">
            {{ row.bumpRule }}
          </el-tag>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="strategy" :label="t('versionPolicy.strategy')" min-width="180">
        <template #default="{ row }">
          <span class="strategy-text">{{ row.strategy }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('versionPolicy.scope')" min-width="160">
        <template #default="{ row }">
          <el-tag size="small" :type="row.scope?.level === 'GLOBAL' ? 'info' : ''">
            {{ scopeLabel(row.scope?.level) }}
          </el-tag>
          <span v-if="row.scope?.projectId" class="scope-id">{{ row.scope.projectId }}</span>
          <span v-if="row.scope?.subProjectId" class="scope-id">/ {{ row.scope.subProjectId }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleView(row)">
            {{ t('common.detail') }}
          </el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">
            {{ t('common.edit') }}
          </el-button>
          <el-popconfirm :title="t('common.deleteConfirm')" @confirm="handleDelete(row)">
            <template #reference>
              <el-button link type="danger" size="small">{{ t('common.delete') }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </DataTable>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('common.edit') : t('versionPolicy.create')"
      width="520px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item :label="t('versionPolicy.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('versionPolicy.scheme')" prop="scheme">
          <el-select v-model="form.scheme">
            <el-option label="SEMVER" value="SEMVER" />
            <el-option label="DATE" value="DATE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('versionPolicy.bumpRule')" prop="bumpRule">
          <el-select v-model="form.bumpRule">
            <el-option label="MAJOR" value="MAJOR" />
            <el-option label="MINOR" value="MINOR" />
            <el-option label="PATCH" value="PATCH" />
            <el-option label="NONE" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('versionPolicy.scope')" prop="scopeLevel">
          <el-radio-group v-model="form.scopeLevel" @change="onScopeLevelChange">
            <el-radio value="GLOBAL">{{ t('versionPolicy.scopeGlobal') }}</el-radio>
            <el-radio value="PROJECT">{{ t('versionPolicy.scopeProject') }}</el-radio>
            <el-radio value="SUB_PROJECT">{{ t('versionPolicy.scopeSubProject') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeLevel !== 'GLOBAL'" :label="t('versionPolicy.scopeProjectId')" prop="scopeProjectId">
          <el-input v-model="form.scopeProjectId" />
        </el-form-item>
        <el-form-item v-if="form.scopeLevel === 'SUB_PROJECT'" :label="t('versionPolicy.scopeSubProjectId')" prop="scopeSubProjectId">
          <el-input v-model="form.scopeSubProjectId" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useListPage } from '@/composables/crud/useListPage'
import SearchForm from '@/components/crud/SearchForm.vue'
import DataTable from '@/components/crud/DataTable.vue'
import { versionPolicyApi, type VersionPolicyDisplay } from '@/api/versionPolicyApi'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { handleError } from '@/utils/error'

const { t } = useI18n()

const { query, loading, list, total, search, reset, onPageChange, onPageSizeChange, fetch } = useListPage({
  fetcher: versionPolicyApi.list,
  defaultQuery: {
    name: ''
  }
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editId = ref('')
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  scheme: 'SEMVER',
  bumpRule: 'PATCH',
  scopeLevel: 'GLOBAL' as 'GLOBAL' | 'PROJECT' | 'SUB_PROJECT',
  scopeProjectId: '',
  scopeSubProjectId: ''
})

const validateProjectScope = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (form.scopeLevel !== 'GLOBAL' && !value?.trim()) {
    callback(new Error(t('versionPolicy.scopeProjectRequired')))
    return
  }
  callback()
}

const validateSubProjectScope = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (form.scopeLevel === 'SUB_PROJECT' && !value?.trim()) {
    callback(new Error(t('versionPolicy.scopeSubProjectRequired')))
    return
  }
  callback()
}

const rules: FormRules = {
  name: [{ required: true, message: t('versionPolicy.nameRequired'), trigger: 'blur' }],
  scopeProjectId: [{ validator: validateProjectScope, trigger: 'blur' }],
  scopeSubProjectId: [{ validator: validateSubProjectScope, trigger: 'blur' }]
}

const scopeLabel = (level?: string) => {
  if (level === 'PROJECT') return t('versionPolicy.scopeProject')
  if (level === 'SUB_PROJECT') return t('versionPolicy.scopeSubProject')
  return t('versionPolicy.scopeGlobal')
}

const handleAdd = () => {
  isEdit.value = false
  editId.value = ''
  dialogVisible.value = true
}

const handleEdit = (row: VersionPolicyDisplay) => {
  isEdit.value = true
  editId.value = row.id
  form.name = row.name
  form.scheme = row.scheme
  form.bumpRule = row.bumpRule
  form.scopeLevel = row.scope?.level || 'GLOBAL'
  form.scopeProjectId = row.scope?.projectId || ''
  form.scopeSubProjectId = row.scope?.subProjectId || ''
  dialogVisible.value = true
}

const onScopeLevelChange = (level: string) => {
  if (level === 'GLOBAL') {
    form.scopeProjectId = ''
    form.scopeSubProjectId = ''
  }
  if (level === 'PROJECT') {
    form.scopeSubProjectId = ''
  }
  formRef.value?.clearValidate(['scopeProjectId', 'scopeSubProjectId'])
}

const handleView = (row: VersionPolicyDisplay) => {
  ElMessage.info(`${t('versionPolicy.name')}: ${row.name}\n${t('versionPolicy.scheme')}: ${row.scheme}\n${t('versionPolicy.bumpRule')}: ${row.bumpRule}\n${t('versionPolicy.scope')}: ${scopeLabel(row.scope?.level)}`)
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const req = {
      name: form.name,
      scheme: form.scheme,
      bumpRule: form.bumpRule,
      scopeLevel: form.scopeLevel,
      scopeProjectId: form.scopeLevel !== 'GLOBAL' ? form.scopeProjectId || undefined : undefined,
      scopeSubProjectId: form.scopeLevel === 'SUB_PROJECT' ? form.scopeSubProjectId || undefined : undefined
    }
    if (isEdit.value) {
      await versionPolicyApi.update(editId.value, req)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await versionPolicyApi.create(req)
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    fetch()
  } catch (error) {
    handleError(error)
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: VersionPolicyDisplay) => {
  try {
    await versionPolicyApi.remove(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    fetch()
  } catch (error) {
    handleError(error)
  }
}

const resetForm = () => {
  isEdit.value = false
  editId.value = ''
  form.name = ''
  form.scheme = 'SEMVER'
  form.bumpRule = 'PATCH'
  form.scopeLevel = 'GLOBAL'
  form.scopeProjectId = ''
  form.scopeSubProjectId = ''
  formRef.value?.clearValidate()
}
</script>

<style scoped>
.strategy-text {
  color: #606266;
}

.scope-id {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
