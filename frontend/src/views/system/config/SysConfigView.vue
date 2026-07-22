<template>
  <div class="sys-config-view">
    <el-tabs v-model="activeTab" tab-position="top">
      <el-tab-pane label="系统参数" name="config">
        <SysConfigList />
      </el-tab-pane>
      <el-tab-pane label="数据维护" name="clear-data">
        <ClearDataView />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import SysConfigList from './SysConfigList.vue'
import ClearDataView from '@/views/system/clear-data/ClearDataView.vue'

const route = useRoute()
const activeTab = ref((route.query.tab as string) || 'config')
</script>

<style scoped>
.sys-config-view {
  max-width: 1200px;
}
</style>