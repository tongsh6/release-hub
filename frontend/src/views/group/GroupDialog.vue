<template>
  <EntityDialog
    ref="entityRef"
    :title="mode === 'edit' ? t('group.edit') : t('group.create')"
    :confirm-text="t('common.confirm')"
    :cancel-text="t('common.cancel')"
    @confirm="submitWithValidation"
    @opened="onOpened"
  >
    <template #default>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item :label="t('group.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('common.pleaseEnter') + t('group.name')" />
        </el-form-item>
        <el-form-item :label="t('group.code')" prop="code">
          <el-input
            v-model="form.code"
            :placeholder="t('group.codePlaceholder')"
            :disabled="mode === 'edit'"
          />
          <div v-if="mode === 'create'" class="form-tip">{{ t('group.codeAutoGenTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('group.parentCode')" prop="parentCode">
          <el-input
            v-model="form.parentCode"
            clearable
            :placeholder="t('common.pleaseEnter') + t('group.parentCode')"
          />
          <div v-if="presetParentName" class="preset-tip">
            <el-tag type="info">{{ presetParentName }}</el-tag>
          </div>
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
import { groupApi, type GroupView } from '@/api/modules/group'
import { useDialogForm } from '@/composables/crud/useDialogForm'
import { handleError } from '@/utils/error'

const { t } = useI18n()
const emit = defineEmits<{ (e: 'success'): void }>()

const entityRef = ref<InstanceType<typeof EntityDialog>>()
const formRef = ref<FormInstance>()
const presetParentName = ref<string | undefined>(undefined)

type GroupForm = Pick<GroupView, 'name' | 'code' | 'parentCode'>

const {
  mode,
  form,
  open,
  submit,
  onSuccess
} = useDialogForm<GroupForm>({
  fetchById: async (id) => {
    const code = String(id)
    const res = await groupApi.get(code)
    return { name: res.name!, code: res.code!, parentCode: res.parentCode }
  },
  create: async (payload: GroupForm) => {
    try {
      await groupApi.create({
        name: payload.name!,
        code: payload.code || undefined,
        parentCode: payload.parentCode
      })
      ElMessage.success(t('group.createSuccess'))
    } catch (error) {
      handleError(error)
      throw error
    }
  },
  update: async (id, payload: GroupForm) => {
    const code = String(id)
    try {
      await groupApi.update(code, {
        name: payload.name!,
        parentCode: payload.parentCode
      })
      ElMessage.success(t('group.updateSuccess'))
    } catch (error) {
      handleError(error)
      throw error
    }
  },
  defaultForm: {
    name: '',
    code: '',
    parentCode: undefined
  }
})

onSuccess(() => emit('success'))

const rules: FormRules = {
  name: [
    { required: true, message: t('group.validation.nameRequired'), trigger: 'blur' },
    { max: 128, message: t('group.validation.nameLength'), trigger: 'blur' }
  ],
  code: [
    { max: 64, message: t('group.validation.codeLength'), trigger: 'blur' }
  ],
  parentCode: [
    { max: 64, message: t('group.validation.parentLength'), trigger: 'blur' }
  ]
}

const openWithPreset = (opts?: { parentCode?: string; parentName?: string }) => {
  presetParentName.value = opts?.parentName
  open({ mode: 'create', preset: { parentCode: opts?.parentCode } })
  entityRef.value?.open()
}

const openEdit = (code: string) => {
  presetParentName.value = undefined
  open({ mode: 'edit', id: code })
  entityRef.value?.open()
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

const onOpened = () => {
  formRef.value?.clearValidate()
}

defineExpose({ open: openWithPreset, openEdit })
</script>

<style scoped>
.preset-tip {
  margin-top: 6px;
}
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
