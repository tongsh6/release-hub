<template>
  <div class="home-container">
    <h1>Welcome to ReleaseHub</h1>
    <p v-if="userStore.profile">Logged in as: {{ userStore.profile.displayName || userStore.profile.username }}</p>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
</script>

<style scoped>
.home-container {
  padding: 20px;
}
</style>
