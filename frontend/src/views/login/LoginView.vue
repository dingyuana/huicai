<template>
  <div class="login-container">
    <div class="login-card">
      <h1 class="login-title">慧财智能财务平台</h1>
      <p class="login-subtitle">企业财务核算全生命周期管理</p>
      <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" size="large">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
      <p v-if="errorMsg" class="login-error">{{ errorMsg }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMsg.value = ''

  try {
    await authStore.login({ username: form.username, password: form.password })

    const redirect = route.query.redirect
    const targetPath = typeof redirect === 'string' ? redirect : '/dashboard'
    await router.push(targetPath)
    ElMessage.success('登录成功')
  } catch (e: any) {
    errorMsg.value = e?.msg || e?.message || '登录失败，请检查账号密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}
.login-title {
  font-size: 24px;
  text-align: center;
  margin-bottom: 4px;
  color: #333;
}
.login-subtitle {
  text-align: center;
  color: #999;
  margin-bottom: 32px;
  font-size: 13px;
}
.login-form .login-btn {
  width: 100%;
}
.login-error {
  color: #ff4d4f;
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
}
</style>