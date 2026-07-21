<template>
  <div class="party-list">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="客户" name="customer" />
      <el-tab-pane label="供应商" name="vendor" />
      <el-tab-pane label="员工" name="employee" />
      <el-tab-pane label="部门" name="dept" />
    </el-tabs>
    <div class="tab-content">
      <!-- TODO: Phase 2 实现合并的客商档案 CRUD -->
      <el-empty description="功能开发中，请联系管理员" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const activeTab = ref('customer')
</script>

<style scoped>
.party-list { padding: 20px; }
.tab-content { margin-top: 20px; }
</style>
