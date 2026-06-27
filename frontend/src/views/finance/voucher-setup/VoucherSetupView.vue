<template>
  <div class="voucher-setup-page">
    <el-tabs v-model="activeTab" tab-position="top" @tab-change="handleTabChange">
      <el-tab-pane label="凭证类型" name="type">
        <voucher-type-list ref="typeRef" />
      </el-tab-pane>
      <el-tab-pane label="凭证模板" name="template">
        <voucher-template-view ref="templateRef" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VoucherTypeList from '@/views/system/voucher-type/VoucherTypeList.vue'
import VoucherTemplateView from '@/views/finance/voucher-template/VoucherTemplateView.vue'

const route = useRoute()
const router = useRouter()

const activeTab = ref('type')
const typeRef = ref()
const templateRef = ref()

function handleTabChange(tab: string) {
  // Update URL query param without pushing a full navigation
  router.replace({ query: { tab } })
}

onMounted(() => {
  // Sync tab from URL query
  const tab = route.query.tab as string
  if (tab === 'type' || tab === 'template') {
    activeTab.value = tab
  }
})
</script>

<style scoped>
.voucher-setup-page {
  padding: 0;
}
.voucher-setup-page :deep(.el-tabs__content) {
  padding: 0;
}
</style>
