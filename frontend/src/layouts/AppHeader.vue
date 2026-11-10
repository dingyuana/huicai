<template>
  <header class="app-header">
    <div class="header-left">
      <el-button text @click="appStore.toggleSidebar">
        <el-icon :size="20">
          <component :is="appStore.sidebarCollapsed ? 'Expand' : 'Fold'" />
        </el-icon>
      </el-button>
      <el-breadcrumb class="header-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
      </el-breadcrumb>
      <!-- 企业名称醒目展示：对 ENTERPRISE 用户直接显示；AGENCY/SUPER_ADMIN 放在角色标签旁 -->
      <div class="enterprise-chip" v-if="enterpriseName">
        <el-icon><OfficeBuilding /></el-icon>
        <span class="chip-name">{{ enterpriseName }}</span>
      </div>
      <div class="enterprise-chip enterprise-chip--warn" v-else-if="authStore.currentEnterpriseId">
        <el-icon><WarningFilled /></el-icon>
        <span class="chip-name">企业 #{{ authStore.currentEnterpriseId }}</span>
      </div>
    </div>
    <div class="header-right">
      <EnterpriseSwitcher />
      <el-dropdown trigger="click">
        <span class="header-user">
          <el-avatar :size="28" icon="UserFilled" />
          <span class="user-name">{{ authStore.userInfo?.realName || authStore.userInfo?.username || '用户' }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app.store'
import { useAuthStore } from '@/stores/auth.store'
import { OfficeBuilding, WarningFilled } from '@element-plus/icons-vue'
import EnterpriseSwitcher from './components/EnterpriseSwitcher.vue'

const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()

// Pinia store auto-unwraps ref/computed — 直接用，无需 .value
const enterpriseName = computed(() => {
  if (!authStore.currentEnterpriseId) return ''
  const match = authStore.enterpriseList?.find((e: any) => e.id === authStore.currentEnterpriseId)
  return match?.enterpriseName || ''
})

function handleLogout() {
  authStore.logout()
}
</script>

<style scoped lang="scss">
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .header-breadcrumb {
    font-size: 13px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .header-user {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;

    .user-name {
      font-size: 13px;
      color: #333;
    }
  }
}

/* 企业名称徽章：醒目，与侧边栏 logo 形成视觉锚点 */
.enterprise-chip {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 12px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9, #ecf5ff);
  border: 1px solid var(--el-color-primary-light-5, #b3d8ff);
  flex-shrink: 0;

  .el-icon {
    font-size: 14px;
    color: var(--el-color-primary);
  }
}
.chip-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2, #1a6fa8);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}
.enterprise-chip--warn {
  background: var(--el-color-warning-light-9, #fdf6ec);
  border-color: var(--el-color-warning-light-5, #f5d7a8);

  .el-icon {
    color: var(--el-color-warning);
  }
  .chip-name {
    color: var(--el-color-warning-dark-2, #b88230);
  }
}
</style>