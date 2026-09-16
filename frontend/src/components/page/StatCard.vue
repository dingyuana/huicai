<template>
  <el-col :span="span">
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-info">
          <span class="stat-label"><slot name="label" /></span>
          <span class="stat-value"><slot /></span>
        </div>
        <div class="stat-icon" :class="iconClass">
          <el-icon><slot name="icon" /></el-icon>
        </div>
      </div>
    </el-card>
  </el-col>
</template>

<script setup lang="ts">
defineProps<{ span?: number; iconClass?: string }>()
</script>

<style scoped>
.stat-card {
  margin-bottom: 0;
  border-radius: 8px;
  transition: all 0.3s ease;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1) !important;
}
.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
}
.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-icon .el-icon {
  font-size: 24px;
  color: #fff;
}
</style>
