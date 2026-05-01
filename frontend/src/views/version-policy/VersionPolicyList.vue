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
      <el-table-column :label="t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleView(row)">
            {{ t('common.detail') }}
          </el-button>
        </template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useListPage } from '@/composables/crud/useListPage'
import SearchForm from '@/components/crud/SearchForm.vue'
import DataTable from '@/components/crud/DataTable.vue'
import { versionPolicyApi, type VersionPolicyDisplay } from '@/api/versionPolicyApi'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const { query, loading, list, total, search, reset, onPageChange, onPageSizeChange } = useListPage({
  fetcher: versionPolicyApi.list,
  defaultQuery: {
    name: ''
  }
})

const handleView = (row: VersionPolicyDisplay) => {
  ElMessage.info(`${t('versionPolicy.name')}: ${row.name}\n${t('versionPolicy.scheme')}: ${row.scheme}\n${t('versionPolicy.bumpRule')}: ${row.bumpRule}`)
}
</script>

<style scoped>
.strategy-text {
  color: #606266;
}
</style>
