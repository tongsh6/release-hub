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
      <el-table-column prop="name" :label="t('branchRule.name')" min-width="150" />
      <el-table-column prop="pattern" :label="t('branchRule.pattern')" min-width="150" />
      <el-table-column prop="type" :label="t('branchRule.type')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.type === 'ALLOW' ? 'success' : 'danger'">
            {{ row.type === 'ALLOW' ? t('branchRule.typeAllow') : t('branchRule.typeBlock') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="150" fixed="right">
        <template #default="{ row }">
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
      width="500px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="t('branchRule.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('branchRule.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('branchRule.pattern')" prop="pattern">
          <el-input v-model="form.pattern" :placeholder="t('branchRule.patternPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('branchRule.type')" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio value="ALLOW">{{ t('branchRule.typeAllow') }}</el-radio>
            <el-radio value="BLOCK">{{ t('branchRule.typeBlock') }}</el-radio>
          </el-radio-group>
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
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useListPage } from '@/composables/crud/useListPage'
import SearchForm from '@/components/crud/SearchForm.vue'
import DataTable from '@/components/crud/DataTable.vue'
import { branchRuleApi, type BranchRule } from '@/api/branchRuleApi'
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
  type: 'ALLOW' as 'ALLOW' | 'BLOCK'
})

const rules: FormRules = {
  name: [{ required: true, message: t('branchRule.nameRequired'), trigger: 'blur' }],
  pattern: [{ required: true, message: t('branchRule.patternRequired'), trigger: 'blur' }],
  type: [{ required: true, message: t('branchRule.typeRequired'), trigger: 'change' }]
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

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  saving.value = true
  try {
    if (isEdit.value) {
      await branchRuleApi.update(editId.value, form)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await branchRuleApi.create(form)
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
  form.type = 'ALLOW'
  formRef.value?.resetFields()
}
</script>

<style scoped>
/* 页面特定样式 - 通用样式已移至 index.css */
</style>
