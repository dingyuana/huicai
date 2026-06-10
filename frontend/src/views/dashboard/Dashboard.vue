<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span class="dashboard-title">慧财财务系统</span>
          </template>
          <div class="dashboard-content">
            <el-empty description="欢迎使用慧财财务系统" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.dashboard {
  padding: 20px;
}
.dashboard-title {
  font-size: 18px;
  font-weight: 600;
}
.dashboard-content {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}
</style>