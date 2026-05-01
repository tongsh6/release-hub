<template>
  <div class="not-found-container">
    <el-result
      icon="warning"
      title="404"
      sub-title="Sorry, the page you visited does not exist."
    >
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">Back Home</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.not-found-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
