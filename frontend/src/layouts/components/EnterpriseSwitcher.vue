<template>
  <div class="enterprise-switcher" v-if="authStore.isAgency || authStore.isSuperAdmin">
    <!-- 角色标签 -->
    <el-tag size="small" :type="roleTagType" class="role-tag">
      {{ roleLabel }}
    </el-tag>

    <el-select
      :model-value="authStore.currentEnterpriseId"
      placeholder="选择客户企业"
      size="small"
      style="width: 200px"
      @change="handleSwitch"
    >
      <el-option
        v-if="filteredEnterpriseList.length === 0"
        :value="0"
        label="暂无分配企业"
        disabled
      />
      <el-option
        v-for="ent in filteredEnterpriseList"
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
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

// 角色标签映射
const roleLabelMap: Record<string, string> = {
  AGENCY_ADMIN: '经理',
  ACCOUNTANT: '会计',
  REVIEWER: '审核员',
  ASSISTANT: '助理',
}
const roleLabel = computed(() => {
  if (authStore.isSuperAdmin) return '管理员'
  return roleLabelMap[authStore.agencyRole] || '用户'
})

// 角色标签颜色
const roleTagTypeMap: Record<string, string> = {
  AGENCY_ADMIN: '',
  ACCOUNTANT: 'success',
  REVIEWER: 'warning',
  ASSISTANT: 'info',
}
const roleTagType = computed(() => (roleTagTypeMap[authStore.agencyRole] || 'info') as 'primary' | 'success' | 'warning' | 'info' | 'danger')

// 按角色过滤企业列表：ACCOUNTANT/ASSISTANT 仅显示分配的企业，AGENCY_ADMIN/REVIEWER 显示全部
const filteredEnterpriseList = computed(() => {
  return authStore.enterpriseList
})

// 当前选中企业名称
const currentEnterpriseName = computed(() => {
  const ent = authStore.enterpriseList.find(e => e.id === authStore.currentEnterpriseId)
  return ent?.enterpriseName || ''
})

async function handleSwitch(enterpriseId: number) {
  try {
    await authStore.switchEnterprise(enterpriseId)
    ElMessage.success('已切换客户企业')
    router.push('/dashboard')
  } catch {
    ElMessage.error('切换失败')
  }
}
</script>

<style scoped lang="scss">
.enterprise-switcher {
  display: flex;
  align-items: center;
  gap: 8px;

  .role-tag {
    flex-shrink: 0;
  }
}
</style>
