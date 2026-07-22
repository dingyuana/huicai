<template>
  <div class="account-and-summary">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="会计科目" name="subjects">
        <SubjectList />
      </el-tab-pane>
      <el-tab-pane label="常用摘要" name="summaries">
        <SummaryLibList />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import SubjectList from '@/views/system/subject/SubjectList.vue'
import SummaryLibList from '@/views/system/summary-lib/SummaryLibList.vue'

const activeTab = ref('subjects')
</script>

<style scoped>
.account-and-summary {
  padding: 0;
}

.el-tabs {
  height: 100%;
}

.el-tab-pane {
  height: calc(100% - 40px);
  overflow-y: auto;
}
</style>
