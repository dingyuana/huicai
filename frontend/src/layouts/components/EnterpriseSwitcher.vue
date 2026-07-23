<template>
  <div class="enterprise-switcher" v-if="authStore.isAgency">
    <el-select
      :model-value="authStore.currentEnterpriseId"
      placeholder="选择客户企业"
      size="small"
      style="width: 200px"
      @change="handleSwitch"
    >
      <el-option
        v-for="ent in authStore.enterpriseList"
        :key="ent.id"
        :label="ent.enterpriseName"
        :value="ent.id"
      >
        <span>{{ ent.enterpriseName }}</span>
        <el-tag size="small" style="margin-left: 8px" :type="ent.status === 'ACTIVE' ? 'success' : 'warning'">
          {{ ent.status === 'ACTIVE' ? '活跃' : ent.status }}
        </el-tag>
      </el-option>
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth.store'
import { ElMessage } from 'element-plus'

const authStore = useAuthStore()

async function handleSwitch(enterpriseId: number) {
  try {
    await authStore.switchEnterprise(enterpriseId)
    ElMessage.success('已切换客户企业')
    window.location.reload()
  } catch {
    ElMessage.error('切换失败')
  }
}
</script>
