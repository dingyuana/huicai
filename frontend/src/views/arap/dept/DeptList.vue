<template>
  <div class="dept-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">部门档案</span>
        <div>
          <el-button type="primary" @click="showCreate = true">新增部门</el-button>
          <el-button @click="showImport = true">导入部门</el-button>
        </div>
      </div>
      <el-table :data="list" v-loading="loading" border row-key="id" default-expand-all
        :tree-props="{ children: 'children' }">
        <el-table-column prop="code" label="部门编码" min-width="160" />
        <el-table-column prop="name" label="部门名称" min-width="200" />
        <el-table-column prop="manager" label="负责人" min-width="120" />
        <el-table-column prop="phone" label="电话" min-width="140" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive !== false ? 'success' : 'danger'" size="small">
              {{ row.isActive !== false ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const list = ref<any[]>([])
const loading = ref(false)
const showCreate = ref(false)
const showImport = ref(false)

async function fetchData() {
  loading.value = true
  try {
    const res = await fetch('/api/v1/departments/list')
    if (res.ok) list.value = await res.json()
    else list.value = []
  } catch { list.value = [] }
  finally { loading.value = false }
}

onMounted(fetchData)
</script>

<style scoped>
.dept-list { padding: 0; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
</style>