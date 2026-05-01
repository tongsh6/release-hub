<template>
  <div class="diff-viewer">
    <el-collapse v-model="activeNames">
      <el-collapse-item name="diff" :title="t('run.diff.title')">
        <div class="diff-content">
          <div v-if="diff" class="diff-text">
            <div
              v-for="(line, index) in diffLines"
              :key="index"
              :class="getLineClass(line)"
            >
              {{ line }}
            </div>
          </div>
          <el-empty v-else :description="t('run.diff.noDiff')" />
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  diff?: string
}>()

const { t } = useI18n()
const activeNames = ref<string[]>(['diff'])

const diffLines = computed(() => {
  if (!props.diff) return []
  return props.diff.split('\n')
})

function getLineClass(line: string): string {
  if (line.startsWith('@@')) return 'diff-line diff-hunk'
  if (line.startsWith('+')) return 'diff-line diff-add'
  if (line.startsWith('-')) return 'diff-line diff-remove'
  return 'diff-line'
}
</script>

<style scoped>
.diff-viewer {
  margin-top: 16px;
}

.diff-content {
  max-height: 500px;
  overflow: auto;
}

.diff-text {
  margin: 0;
  padding: 12px;
  background-color: #fafafa;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace;
  font-size: 12px;
  line-height: 1.6;
  border: 1px solid #e4e7ed;
}

.diff-line {
  padding: 2px 8px;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.diff-add {
  background-color: #e6ffec;
  color: #1f883d;
}

.diff-remove {
  background-color: #ffebe9;
  color: #cf222e;
}

.diff-hunk {
  background-color: #ddf4ff;
  color: #0969da;
  font-weight: 500;
  margin: 8px 0 4px 0;
  padding: 4px 8px;
  border-radius: 2px;
}

:deep(.el-collapse-item__header) {
  font-weight: 500;
}
</style>
