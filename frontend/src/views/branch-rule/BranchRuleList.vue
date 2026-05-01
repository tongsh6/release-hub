<template>
  <div class="branch-rule-list-page list-page">
    <SearchForm :loading="loading" @search="search" @reset="reset">
      <el-form-item :label="t('branchRule.name')">
        <el-input v-model="query.name" :placeholder="t('branchRule.name')" clearable />
      </el-form-item>
    </SearchForm>

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
        <el-button type="primary" :icon="Plus" @click="handleAdd">{{ t('branchRule.create') }}</el-button>
      </template>
      <el-table-column prop="id" label="ID" width="280" />
      <el-table-column prop="name" :label="t('branchRule.name')" min-width="140" />
      <el-table-column prop="pattern" :label="t('branchRule.pattern')" min-width="150" />
      <el-table-column prop="type" :label="t('branchRule.type')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.type === 'TEMPLATE' ? '' : 'warning'" size="small">
            {{ row.type === 'TEMPLATE' ? t('branchRule.typeTemplate') : t('branchRule.typeRegex') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('branchRule.scope')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.scope?.level === 'GLOBAL' ? 'info' : ''">
            {{ scopeLabel(row.scope?.level) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('branchRule.status')" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 'ENABLED'"
            size="small"
            @change="(val: boolean) => handleToggle(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleTest(row)">{{ t('branchRule.test') }}</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
          <el-popconfirm
            :title="t('common.deleteConfirm')"
            @confirm="handleDelete(row)"
          >
            <template #reference>
          <el-button link type="danger" size="small">{{ t('common.delete') }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('branchRule.edit') : t('branchRule.create')"
      width="550px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="t('branchRule.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('branchRule.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('branchRule.type')" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio value="TEMPLATE">{{ t('branchRule.typeTemplate') }}</el-radio>
            <el-radio value="REGEX">{{ t('branchRule.typeRegex') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('branchRule.pattern')" prop="pattern">
          <el-input v-model="form.pattern" :placeholder="t('branchRule.patternPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('branchRule.description')" prop="description">
          <el-input v-model="form.description" :placeholder="t('branchRule.descriptionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('branchRule.scopeLevel')" prop="scopeLevel">
          <el-radio-group v-model="form.scopeLevel">
            <el-radio value="GLOBAL">{{ t('branchRule.scopeGlobal') }}</el-radio>
            <el-radio value="PROJECT">{{ t('branchRule.scopeProject') }}</el-radio>
            <el-radio value="SUB_PROJECT">{{ t('branchRule.scopeSubProject') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeLevel !== 'GLOBAL'" :label="t('branchRule.scopeProjectId')" prop="scopeProjectId">
          <el-input v-model="form.scopeProjectId" />
        </el-form-item>
        <el-form-item v-if="form.scopeLevel === 'SUB_PROJECT'" :label="t('branchRule.scopeSubProjectId')" prop="scopeSubProjectId">
          <el-input v-model="form.scopeSubProjectId" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 测试对话框 -->
    <el-dialog
      v-model="testVisible"
      :title="t('branchRule.test')"
      width="500px"
    >
      <el-form label-width="110px">
        <el-form-item :label="t('branchRule.pattern')">
          <el-input :model-value="testForm.pattern" disabled />
        </el-form-item>
        <el-form-item :label="t('branchRule.testBranchName')">
          <el-input v-model="testForm.branchName" :placeholder="t('branchRule.testBranchNamePlaceholder')" />
        </el-form-item>
      </el-form>
      <div v-if="testResult !== null" class="test-result">
        <el-alert
          :type="testResult.ok ? 'success' : 'error'"
          :title="testResult.ok ? t('branchRule.testMatch') : t('branchRule.testNoMatch')"
          :closable="false"
          show-icon
        />
        <div v-if="testResult.errors?.length" class="test-errors">
          <p v-for="(err, i) in testResult.errors" :key="i" class="error-msg">{{ err }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="testVisible = false">{{ t('common.close') }}</el-button>
        <el-button type="primary" :loading="testing" @click="runTest">{{ t('branchRule.testRun') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useListPage } from '@/composables/crud/useListPage'
import SearchForm from '@/components/crud/SearchForm.vue'
import DataTable from '@/components/crud/DataTable.vue'
import { branchRuleApi, type BranchRule, type BranchRuleType, type ScopeLevel, type BranchRuleTestResp } from '@/api/branchRuleApi'
import { handleError } from '@/utils/error'

const { t } = useI18n()

const { query, loading, list, total, search, reset, onPageChange, onPageSizeChange, reload } = useListPage({
  fetcher: branchRuleApi.list,
  defaultQuery: {
    name: ''
  }
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editId = ref<string>('')
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  pattern: '',
  type: 'TEMPLATE' as BranchRuleType,
  description: '',
  scopeLevel: 'GLOBAL' as ScopeLevel,
  scopeProjectId: '',
  scopeSubProjectId: ''
})

const rules: FormRules = {
  name: [{ required: true, message: t('branchRule.nameRequired'), trigger: 'blur' }],
  pattern: [{ required: true, message: t('branchRule.patternRequired'), trigger: 'blur' }],
}

// Test dialog
const testVisible = ref(false)
const testing = ref(false)
const testForm = reactive({ pattern: '', type: 'TEMPLATE' as BranchRuleType, branchName: '' })
const testResult = ref<BranchRuleTestResp | null>(null)

const scopeLabel = (level?: string) => {
  if (level === 'PROJECT') return t('branchRule.scopeProject')
  if (level === 'SUB_PROJECT') return t('branchRule.scopeSubProject')
  return t('branchRule.scopeGlobal')
}

const handleAdd = () => {
  isEdit.value = false
  editId.value = ''
  dialogVisible.value = true
}

const handleEdit = (row: BranchRule) => {
  isEdit.value = true
  editId.value = row.id
  form.name = row.name
  form.pattern = row.pattern
  form.type = row.type
  form.description = row.description || ''
  form.scopeLevel = row.scope?.level || 'GLOBAL'
  form.scopeProjectId = row.scope?.projectId || ''
  form.scopeSubProjectId = row.scope?.subProjectId || ''
  dialogVisible.value = true
}

const handleDelete = async (row: BranchRule) => {
  try {
    await branchRuleApi.remove(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    reload()
  } catch (e) {
    handleError(e)
  }
}

const handleToggle = async (row: BranchRule, enabled: boolean) => {
  try {
    if (enabled) {
      await branchRuleApi.enable(row.id)
      ElMessage.success(t('branchRule.enableSuccess'))
    } else {
      await branchRuleApi.disable(row.id)
      ElMessage.success(t('branchRule.disableSuccess'))
    }
    reload()
  } catch (e) {
    handleError(e)
  }
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  saving.value = true
  try {
    const req = {
      name: form.name,
      pattern: form.pattern,
      type: form.type,
      description: form.description || undefined,
      scopeLevel: form.scopeLevel,
      scopeProjectId: form.scopeLevel !== 'GLOBAL' ? form.scopeProjectId || undefined : undefined,
      scopeSubProjectId: form.scopeLevel === 'SUB_PROJECT' ? form.scopeSubProjectId || undefined : undefined
    }
    if (isEdit.value) {
      await branchRuleApi.update(editId.value, req)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await branchRuleApi.create(req)
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    reload()
  } catch (e) {
    handleError(e)
  } finally {
    saving.value = false
  }
}

const resetForm = () => {
  form.name = ''
  form.pattern = ''
  form.type = 'TEMPLATE'
  form.description = ''
  form.scopeLevel = 'GLOBAL'
  form.scopeProjectId = ''
  form.scopeSubProjectId = ''
  formRef.value?.resetFields()
}

const handleTest = (row: BranchRule) => {
  testForm.pattern = row.pattern
  testForm.type = row.type
  testForm.branchName = ''
  testResult.value = null
  testVisible.value = true
}

const runTest = async () => {
  if (!testForm.branchName.trim()) return
  testing.value = true
  try {
    testResult.value = await branchRuleApi.test({
      pattern: testForm.pattern,
      type: testForm.type,
      branchName: testForm.branchName
    })
  } catch (e) {
    handleError(e)
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.test-result { margin-top: 16px; }
.test-errors { margin-top: 8px; }
.error-msg { color: var(--el-color-danger); font-size: 13px; margin: 2px 0; }
</style>
