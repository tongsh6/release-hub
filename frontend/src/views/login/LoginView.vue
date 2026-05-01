<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">{{ t('login.title') }}</h2>
      </template>
      
      <el-form 
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item :label="t('login.username')" prop="username">
          <el-input v-model="form.username" :placeholder="t('login.placeholder.username')" />
        </el-form-item>
        
        <el-form-item :label="t('login.password')" prop="password">
          <el-input 
            v-model="form.password" 
            type="password" 
            :placeholder="t('login.placeholder.password')" 
            show-password 
          />
        </el-form-item>
        
        <div class="flex items-center justify-between mb-4">
          <el-checkbox v-model="form.rememberMe">{{ t('login.rememberMe') }}</el-checkbox>
        </div>

        <el-button 
          type="primary" 
          class="w-full" 
          :loading="loading"
          @click="handleLogin"
        >
          {{ t('login.signIn') }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const REMEMBER_ME_KEY = 'RH_REMEMBER_ME'
const SAVED_USERNAME_KEY = 'RH_SAVED_USERNAME'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('login.validation.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.validation.passwordRequired'), trigger: 'blur' }]
}))

// Load saved username on mount
onMounted(() => {
  const savedRememberMe = localStorage.getItem(REMEMBER_ME_KEY)
  if (savedRememberMe === 'true') {
    form.rememberMe = true
    const savedUsername = localStorage.getItem(SAVED_USERNAME_KEY)
    if (savedUsername) {
      form.username = savedUsername
    }
  }
})

// Save or clear username based on rememberMe
const handleRememberMe = () => {
  if (form.rememberMe) {
    localStorage.setItem(REMEMBER_ME_KEY, 'true')
    localStorage.setItem(SAVED_USERNAME_KEY, form.username)
  } else {
    localStorage.removeItem(REMEMBER_ME_KEY)
    localStorage.removeItem(SAVED_USERNAME_KEY)
  }
}

const handleLogin = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // 调用 store action，store 内会调用 api 并持久化 token
        await userStore.login(form)
        
        // Save or clear username based on rememberMe
        handleRememberMe()
        
        ElMessage.success(t('login.message.success'))
        
        // 跳转回来源页或首页
        const redirect = route.query.redirect as string || '/'
        router.push(redirect)
      } catch (error: any) {
        console.error(error)
        if (error.code === 'AUTH_FAILED') {
          ElMessage.error(t('login.message.authFailed'))
        } else {
          ElMessage.error(error.message || t('login.message.failed'))
        }
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f0f2f5;
}

.login-card {
  width: 400px;
}

.login-title {
  text-align: center;
  margin: 0;
}

.w-full {
  width: 100%;
}
</style>
