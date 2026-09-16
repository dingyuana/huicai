<template>
  <div class="stat-bar">
    <span v-for="(item, i) in items" :key="i" class="stat-bar__item">
      <span class="stat-bar__label">{{ item.label }}</span>
      <span class="stat-bar__value" :style="item.color ? { color: item.color } : undefined">{{ item.value }}</span>
    </span>
  </div>
</template>

<script setup lang="ts">
export interface StatBarItem {
  label: string
  value: string | number
  color?: string
}
defineProps<{ items: StatBarItem[] }>()
</script>

<style scoped>
.stat-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 32px;
  row-gap: 6px;
  padding: 10px 16px;
  margin-bottom: 12px;
  background: var(--color-bg-soft, #f5f7fa);
  border-radius: 4px;
}
.stat-bar__item {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
}
.stat-bar__label {
  font-size: 13px;
  color: var(--color-text-secondary, #606266);
}
.stat-bar__value {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary, #303133);
  font-variant-numeric: tabular-nums;
}
</style>
