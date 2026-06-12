<template>
  <div class="app-layout">
    <AppSidebar />
    <div class="layout-main">
      <AppHeader />
      <main class="layout-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
</script>

<style scoped lang="scss">
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.layout-content {
  flex: 1;
  overflow-y: auto;
  background: #f5f7fa;
}
</style>