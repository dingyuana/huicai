<template>
  <div class="page-header">
    <span class="page-title"><slot name="title">{{ title }}</slot></span>
    <el-space wrap>
      <slot name="actions" />
    </el-space>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-lg);
}
.page-title {
  font-size: var(--font-title);
  font-weight: var(--font-weight-title);
  color: var(--font-color-title);
}
</style>
