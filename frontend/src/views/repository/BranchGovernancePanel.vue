<template>
  <div class="branch-governance-panel">
    <el-divider />
    <el-alert
      class="branch-governance-boundary"
      type="warning"
      show-icon
      :closable="false"
      :title="t('repository.branchGovernance.boundary')"
    />
    <el-table
      v-if="branches.length"
      class="noncompliant-branch-table"
      :data="branches"
      style="margin-top: 12px;"
    >
      <el-table-column prop="branchName" :label="t('repository.branchGovernance.branchName')" min-width="180" />
      <el-table-column prop="scopeProjectId" :label="t('branchRule.scopeProjectId')" width="140" />
      <el-table-column prop="scopeSubProjectId" :label="t('branchRule.scopeSubProjectId')" width="180" />
      <el-table-column :label="t('repository.branchGovernance.guidanceLabel')" min-width="260">
        <template #default>
          {{ t('repository.branchGovernance.guidance') }}
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-else
      class="branch-governance-empty"
      :description="t('repository.branchGovernance.empty')"
    />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { NonCompliantBranch } from '@/api/repositoryApi'

defineProps<{
  branches: NonCompliantBranch[]
}>()

const { t } = useI18n()
</script>
