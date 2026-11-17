<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" class="sidebar">
      <div class="sidebar-header">
        <h2>慧财财务</h2>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>工作台</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <!-- 突出显示企业名称：大字号 + 主色，一眼可确认当前账簿归属 -->
          <div class="enterprise-badge" v-if="enterpriseName">
            <el-icon class="badge-icon"><OfficeBuilding /></el-icon>
            <span class="badge-name">{{ enterpriseName }}</span>
          </div>
          <div class="enterprise-badge enterprise-badge--warn" v-else-if="authStore.currentEnterpriseId">
            <el-icon class="badge-icon"><WarningFilled /></el-icon>
            <span class="badge-name">企业 #{{ authStore.currentEnterpriseId }}</span>
          </div>
        </div>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              <el-icon><User /></el-icon>
              {{ authStore.userInfo?.realName || '管理员' }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Odometer, User, OfficeBuilding, WarningFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth.store'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 根据 currentEnterpriseId 从 enterpriseList 里找企业名称
const enterpriseName = computed(() => {
  if (!authStore.currentEnterpriseId) return ''
  const match = authStore.enterpriseList?.find(
    (e: any) => e.id === authStore.currentEnterpriseId
  )
  return match?.enterpriseName || ''
})

function handleLogout() {
  authStore.logout()
}
</script>

<style scoped>
.sidebar {
  background-color: #304156;
  overflow-y: auto;
}
.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.sidebar-header h2 {
  font-size: 18px;
  font-weight: 600;
}
.header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-right {
  display: flex;
  align-items: center;
}
/* 企业名称徽章：主色大字，醒目 */
.enterprise-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 14px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9, #ecf5ff);
  border: 1px solid var(--el-color-primary-light-5, #b3d8ff);
}
.badge-icon {
  font-size: 16px;
  color: var(--el-color-primary);
}
.badge-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2, #1a6fa8);
  letter-spacing: 0.5px;
}
.enterprise-badge--warn {
  background: var(--el-color-warning-light-9, #fdf6ec);
  border-color: var(--el-color-warning-light-5, #f5d7a8);
}
.enterprise-badge--warn .badge-icon {
  color: var(--el-color-warning);
}
.enterprise-badge--warn .badge-name {
  color: var(--el-color-warning-dark-2, #b88230);
}
.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
.main-content {
  background: #f0f2f5;
  padding: 20px;
}
</style>
