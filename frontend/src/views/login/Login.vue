<template>
  <div class="login-container">
    <div class="login-card">
      <h2 class="login-title">慧财财务</h2>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <el-divider />
      <div class="health-check">
        <el-tag v-if="backendStatus === 'ok'" type="success">后端已连接</el-tag>
        <el-tag v-else-if="backendStatus === 'error'" type="danger">后端未连接</el-tag>
        <el-button text size="small" @click="checkHealth">检测连接</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { health } from '@/api/modules/system'

const formRef = ref<FormInstance>()
const loading = ref(false)
const backendStatus = ref('')

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function checkHealth() {
  try {
    const res: any = await health()
    backendStatus.value = 'ok'
  } catch {
    backendStatus.value = 'error'
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  // 登录逻辑将在后续 Phase 2 完善
  backendStatus.value = 'ok'
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
}
.login-title {
  text-align: center;
  margin-bottom: 30px;
  color: #409eff;
  font-size: 24px;
}
.health-check {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
}
</style>