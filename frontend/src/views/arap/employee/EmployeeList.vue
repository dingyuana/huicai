<template>
  <div class="employee-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">员工档案</span>
        <div>
          <el-button type="primary" @click="showCreate = true">新增员工</el-button>
          <el-button @click="showImport = true">导入员工</el-button>
        </div>
      </div>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="工号" min-width="140" />
        <el-table-column prop="name" label="姓名" min-width="140" />
        <el-table-column prop="deptName" label="部门" min-width="140" />
        <el-table-column prop="phone" label="电话" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="position" label="职位" min-width="140" />
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
import { listEmployee } from '@/api/modules/employee'

const list = ref<any[]>([])
const loading = ref(false)
const showCreate = ref(false)
const showImport = ref(false)

async function fetchData() {
  loading.value = true
  try {
    list.value = await listEmployee()
  } catch { list.value = [] }
  finally { loading.value = false }
}

onMounted(fetchData)
</script>

<style scoped>
.employee-list { padding: 0; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
</style>