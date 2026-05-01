<template>
  <div class="page-container">
    <el-page-header @back="goBack">
      <template #content>
        <span class="text-large font-600 mr-3"> Scan Run Detail </span>
      </template>
    </el-page-header>

    <div class="content-area">
      <el-descriptions title="Run Info" :column="2" border>
        <el-descriptions-item label="Run ID">{{ runId }}</el-descriptions-item>
        <el-descriptions-item label="Status">
          <el-tag type="success">Success</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Branch">main</el-descriptions-item>
        <el-descriptions-item label="Duration">2m 30s</el-descriptions-item>
      </el-descriptions>

      <div class="logs-area">
        <h3>Logs</h3>
        <div class="logs-content">
          <p>[INFO] Starting scan...</p>
          <p>[INFO] Checking dependencies...</p>
          <p>[INFO] No vulnerabilities found.</p>
          <p>[INFO] Scan completed successfully.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const runId = route.params.runId

const goBack = () => {
  router.back()
}
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.content-area {
  margin-top: 20px;
  background-color: #fff;
  padding: 20px;
  border-radius: 4px;
}
.logs-area {
  margin-top: 30px;
}
.logs-content {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  font-family: monospace;
  color: #333;
}
</style>
