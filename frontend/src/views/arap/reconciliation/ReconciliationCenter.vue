<template>
  <div class="reconciliation-center">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">核销管理</span>
        <el-button @click="refreshActive">刷新</el-button>
      </div>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane v-if="showWorkbench" label="核销工作台" name="workbench">
          <WorkbenchPanel ref="workbenchRef" />
        </el-tab-pane>
        <el-tab-pane v-if="showSettlement" label="核销单" name="settlement">
          <SettlementPanel ref="settlementRef" />
        </el-tab-pane>
        <el-tab-pane v-if="showSettlement" label="核销日志" name="reconLog">
          <ReconLogPanel ref="reconLogRef" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import WorkbenchPanel from './WorkbenchPanel.vue'
import SettlementPanel from './SettlementPanel.vue'
import ReconLogPanel from './ReconLogPanel.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const showWorkbench = computed(() => authStore.isSuperAdmin || authStore.hasPermission('arap:reconciliation:workbench'))
const showSettlement = computed(() => authStore.isSuperAdmin || authStore.hasPermission('arap:settlement:list'))

const workbenchRef = ref<InstanceType<typeof WorkbenchPanel> | null>(null)
const settlementRef = ref<InstanceType<typeof SettlementPanel> | null>(null)
const reconLogRef = ref<InstanceType<typeof ReconLogPanel> | null>(null)

const activeTab = ref('workbench')

function resolveDefaultTab(): string {
  if (showWorkbench.value) return 'workbench'
  if (showSettlement.value) return 'settlement'
  return 'workbench'
}

function syncTabFromQuery() {
  const q = route.query.tab as string | undefined
  if (q === 'workbench' && showWorkbench.value) activeTab.value = 'workbench'
  else if ((q === 'settlement' || q === 'reconLog') && showSettlement.value) activeTab.value = q
  else activeTab.value = resolveDefaultTab()
}

function onTabChange() {
  router.replace({ query: { ...route.query, tab: activeTab.value } })
}

async function refreshActive() {
  if (activeTab.value === 'workbench') workbenchRef.value?.fetchData()
  else if (activeTab.value === 'settlement') settlementRef.value?.fetchData()
  else reconLogRef.value?.fetchData()
}

onMounted(() => {
  syncTabFromQuery()
})
</script>

<style scoped>
.reconciliation-center .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
</style>
