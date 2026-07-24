<template>
  <div class="accountant-detail">
    <el-page-header @back="$router.back()" title="返回仪表盘">
      <template #content>
        <span class="page-title">会计详情</span>
      </template>
    </el-page-header>

    <el-card class="section-card" header="负责企业" v-loading="loading">
      <el-table :data="enterprises" stripe empty-text="暂无分配企业">
        <el-table-column prop="enterpriseName" label="企业名称" min-width="150" />
        <el-table-column prop="taxId" label="税号" min-width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'warning'">
              {{ row.status === 'ACTIVE' ? '活跃' : (row.status || '未知') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="种子数据" width="100">
          <template #default="{ row }">
            <el-tag :type="row.seedDataDone ? 'success' : 'info'">
              {{ row.seedDataDone ? '已初始化' : '未初始化' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="分配时间" width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getAccountantDetail } from '@/api/modules/agency'

const route = useRoute()
const loading = ref(false)
const enterprises = ref<any[]>([])

onMounted(async () => {
  const userId = Number(route.params.userId)
  if (!userId) return

  loading.value = true
  try {
    const res = await getAccountantDetail(userId)
    enterprises.value = res.data?.enterprises || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.accountant-detail { padding: 16px; }
.page-title { font-size: 16px; font-weight: 600; }
.section-card { margin-top: 16px; }
</style>