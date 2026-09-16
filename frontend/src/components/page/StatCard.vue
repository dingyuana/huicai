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
/* 图标色板 */
.icon-total { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
.icon-blue { background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); }
.icon-red { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); }
.icon-red-count { background: linear-gradient(135deg, #ff9a44 0%, #fc6076 100%); }
.icon-reversed { background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%); }
.icon-voided { background: linear-gradient(135deg, #8e9eab 0%, #eef2f3 100%); }
.icon-total-amount { background: linear-gradient(135deg, #409EFF 0%, #66B1FF 100%); }
.icon-debit { background: linear-gradient(135deg, #67C23A 0%, #409EFF 100%); }
.icon-credit { background: linear-gradient(135deg, #E6A23C 0%, #F56C6C 100%); }
.icon-posted { background: linear-gradient(135deg, #67C23A 0%, #3AAF9B 100%); }
.icon-draft { background: linear-gradient(135deg, #909399 0%, #c0c4cc 100%); }
</style>
