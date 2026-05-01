<template>
  <el-dialog
    v-model="visible"
    :title="t('releaseWindow.create')"
    width="600px"
    :before-close="handleBeforeClose"
    append-to-body
  >
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="120px"
    >
      <el-form-item :label="t('releaseWindow.name')" prop="name">
        <el-input
          v-model="form.name"
          :placeholder="t('releaseWindow.placeholder.enterName')"
          :maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <el-form-item :label="t('group.title')" prop="groupCode">
        <GroupTreeSelect
          v-model="form.groupCode"
          :leaf-only="true"
        />
      </el-form-item>
      
      <el-form-item :label="t('releaseWindow.plannedReleaseAt')" prop="plannedReleaseAt">
        <el-date-picker
          v-model="form.plannedReleaseAt"
          type="datetime"
          :placeholder="t('common.selectDateTime')"
          style="width: 100%"
          value-format="YYYY-MM-DDTHH:mm:ss.SSSZ"
        />
      </el-form-item>
      
      <el-form-item :label="t('releaseWindow.description')" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          :maxlength="2000"
          show-word-limit
          :placeholder="t('releaseWindow.placeholder.enterDescription')"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="close">{{ t('common.cancel') }}</el-button>
        <el-button 
          type="primary" 
          :loading="saving" 
          @click="handleSubmit"
        >
          {{ t('common.confirm') }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDialogForm } from '@/composables/crud/useDialogForm'
import { releaseWindowApi } from '@/api/modules/releaseWindow'
import GroupTreeSelect from '@/components/common/GroupTreeSelect.vue'
import type { FormInstance, FormRules } from 'element-plus'

const { t } = useI18n()

const emit = defineEmits<{
  (e: 'success', payload: any): void
}>()

const formRef = ref<FormInstance>()

interface DialogFormState {
  name: string
  description: string
  plannedReleaseAt: string
  groupCode: string
}

const { visible, loading, saving, form, open, close, submit, onSuccess } = useDialogForm<DialogFormState>({
  create: async (data) => {
    return releaseWindowApi.create({
      name: data.name,
      description: data.description || undefined,
      plannedReleaseAt: data.plannedReleaseAt || undefined,
      groupCode: data.groupCode
    })
  },
  defaultForm: {
    name: '',
    description: '',
    plannedReleaseAt: '',
    groupCode: ''
  }
})

onSuccess((payload) => {
  emit('success', payload)
})

const rules: FormRules = {
  name: [
    { required: true, message: t('releaseWindow.validation.required'), trigger: 'blur' },
    { max: 200, message: t('common.maxLength', { max: 200 }), trigger: 'blur' }
  ],
  groupCode: [
    { required: true, message: t('group.selectGroup'), trigger: 'change' }
  ],
  description: [
    { max: 2000, message: t('common.maxLength', { max: 2000 }), trigger: 'blur' }
  ]
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      await submit()
    }
  })
}

const handleBeforeClose = (done: () => void) => {
  if (saving.value) return
  done()
}

defineExpose({
  open,
  close
})
</script>
