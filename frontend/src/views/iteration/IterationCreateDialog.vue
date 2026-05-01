<template>
  <EntityDialog
    ref="entityRef"
    :title="t('iteration.new')"
    :confirm-text="t('common.confirm')"
    :cancel-text="t('common.cancel')"
    @confirm="submitWithValidation"
    @opened="onOpened"
  >
    <template #default>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item :label="t('iteration.columns.name')" prop="name">
          <el-input
            v-model="form.name"
            :placeholder="t('common.pleaseEnter') + t('iteration.columns.name')"
            :maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item :label="t('group.title')" prop="groupCode">
          <GroupTreeSelect
            v-model="form.groupCode"
            :leaf-only="true"
          />
        </el-form-item>

        <el-form-item :label="t('iteration.columns.expectedReleaseAt')" prop="expectedReleaseAt">
          <el-date-picker
            v-model="form.expectedReleaseAt"
            type="date"
            :placeholder="t('common.pleaseSelect') + t('iteration.columns.expectedReleaseAt')"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item :label="t('iteration.columns.description')" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :maxlength="2000"
            show-word-limit
            :placeholder="t('common.pleaseEnter') + t('iteration.columns.description')"
          />
        </el-form-item>
      </el-form>
    </template>
  </EntityDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import EntityDialog from '@/components/common/EntityDialog.vue'
import GroupTreeSelect from '@/components/common/GroupTreeSelect.vue'
import { useDialogForm } from '@/composables/crud/useDialogForm'
import { iterationApi, type CreateIterationRequest } from '@/api/iterationApi'

const { t } = useI18n()
const emit = defineEmits<{ (e: 'success'): void }>()

const entityRef = ref<InstanceType<typeof EntityDialog>>()
const formRef = ref<FormInstance>()

interface IterationFormState {
  name: string
  description?: string
  expectedReleaseAt?: string | null
  groupCode: string
}

const {
  form,
  open,
  submit,
  onSuccess
} = useDialogForm<IterationFormState>({
  fetchById: async (id) => {
    const key = String(id)
    const res = await iterationApi.get(key)
    return { 
      name: res.name, 
      description: res.description, 
      expectedReleaseAt: res.expectedReleaseAt,
      groupCode: '' // Iteration 目前没有返回 groupCode，后续需要后端支持
    }
  },
  create: async (payload: IterationFormState) => {
    const req: CreateIterationRequest = {
      name: payload.name,
      description: payload.description,
      expectedReleaseAt: payload.expectedReleaseAt,
      groupCode: payload.groupCode
    }
    await iterationApi.create(req)
    ElMessage.success(t('common.success'))
  },
  update: async (id, payload: IterationFormState) => {
    await iterationApi.update(String(id), {
      name: payload.name,
      description: payload.description,
      expectedReleaseAt: payload.expectedReleaseAt,
      groupCode: payload.groupCode
    })
    ElMessage.success(t('common.success'))
  },
  defaultForm: {
    name: '',
    description: '',
    expectedReleaseAt: null,
    groupCode: ''
  }
})

onSuccess(() => {
  entityRef.value?.close()
  emit('success')
})

const rules: FormRules = {
  name: [
    { required: true, message: t('common.pleaseEnter') + t('iteration.columns.name'), trigger: 'blur' },
    { max: 500, message: t('common.maxLength', { max: 500 }), trigger: 'blur' }
  ],
  groupCode: [
    { required: true, message: t('group.selectGroup'), trigger: 'change' }
  ],
  description: [
    { max: 2000, message: t('common.maxLength', { max: 2000 }), trigger: 'blur' }
  ]
}

const submitWithValidation = async () => {
  if (!formRef.value) {
    await submit()
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  await submit()
}

const openCreate = () => {
  open({ mode: 'create' })
  entityRef.value?.open()
}

const onOpened = () => {
  formRef.value?.clearValidate()
}

defineExpose({ open: openCreate })
</script>

<style scoped>
</style>
