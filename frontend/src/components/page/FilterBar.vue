<template>
  <el-form :model="model" inline class="filter-form">
    <slot />
  </el-form>
</template>

<script setup lang="ts">
defineProps<{ model: Record<string, any> }>()
</script>

<style scoped>
.filter-form {
  margin-bottom: var(--space-md);
}
</style>
