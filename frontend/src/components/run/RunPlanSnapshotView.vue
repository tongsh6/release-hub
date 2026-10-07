<template>
  <section class="plan-snapshot">
    <el-alert :title="t('runPlan.readOnlyNotice')" type="info" :closable="false" show-icon />
    <dl class="plan-facts">
      <div><dt>{{ t('run.columns.runId') }}</dt><dd>{{ plan.runId }}</dd></div>
      <div><dt>{{ t('runPlan.createdAt') }}</dt><dd>{{ plan.createdAt }}</dd></div>
      <div><dt>{{ t('run.columns.operator') }}</dt><dd>{{ plan.operator }}</dd></div>
      <div><dt>{{ t('runPlan.windowAtCreation') }}</dt><dd>{{ plan.snapshot.windowKey }} · {{ plan.snapshot.windowStatus }}</dd></div>
    </dl>
    <div class="plan-table-wrap">
      <table class="plan-table">
        <thead><tr>
          <th>{{ t('runPlan.order') }}</th><th>{{ t('runPlan.repository') }}</th><th>{{ t('runPlan.iteration') }}</th>
          <th>{{ t('runPlan.featureBranch') }}</th><th>{{ t('runPlan.releaseBranch') }}</th>
          <th>{{ t('runPlan.devVersion') }}</th><th>{{ t('runPlan.targetVersion') }}</th>
          <th>{{ t('runPlan.plannedSteps') }}</th>
        </tr></thead>
        <tbody><tr v-for="item in plan.snapshot.items" :key="item.itemId">
          <td>{{ item.plannedOrder }}</td><td :title="item.repoId">{{ item.repoName }}</td><td>{{ item.iterationKey }}</td>
          <td>{{ item.featureBranch }}</td><td>{{ item.releaseBranch }}</td>
          <td>{{ item.devVersion || '—' }}</td><td>{{ item.targetVersion || '—' }}</td>
          <td>{{ item.steps.map(step => t(`runPlan.steps.${step}`)).join(' → ') }}</td>
        </tr></tbody>
      </table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { RunPlanPreview } from '@/api/runPlanApi'
defineProps<{ plan: RunPlanPreview }>()
const { t } = useI18n()
</script>

<style scoped>
.plan-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 12px; margin: 16px 0; }
.plan-facts dt { color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 4px; }
.plan-facts dd { margin: 0; overflow-wrap: anywhere; }
.plan-table-wrap { overflow-x: auto; }
.plan-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.plan-table th, .plan-table td { text-align: left; padding: 10px; border-bottom: 1px solid var(--el-border-color-lighter); white-space: nowrap; }
.plan-table th { background: var(--el-fill-color-light); color: var(--el-text-color-secondary); }
</style>
