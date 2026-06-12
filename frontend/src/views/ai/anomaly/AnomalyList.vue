<template>
  <div class="anomaly">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">AI 异常标记</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="业务类型">
          <el-input v-model="query.bizType" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="已解决">
          <el-select v-model="query.resolved" clearable placeholder="全部" style="width:120px">
            <el-option label="已解决" :value="true" />
            <el-option label="未解决" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="bizType" label="业务类型" width="120" align="center" />
        <el-table-column prop="bizId" label="业务ID" width="100" align="center" />
        <el-table-column prop="anomalyType" label="异常类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.anomalyType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="严重程度" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="(SEVERITY_TAG_MAP[row.severity] || 'info') as any" size="small">
              {{ SEVERITY_MAP[row.severity] || row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(row.resolved ? 'success' : 'warning') as any" size="small">
              {{ row.resolved ? '已解决' : '未解决' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" v-if="!row.resolved">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listAnomalies } from '@/api/modules/ai'

const SEVERITY_MAP: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' }
const SEVERITY_TAG_MAP: Record<string, string> = { LOW: 'info', MEDIUM: 'primary', HIGH: 'warning', CRITICAL: 'danger' }

const query = reactive({ bizType: '', resolved: undefined as boolean | undefined })
const list = ref<any[]>([])
const loading = ref(false)

const fetchData = async () => {
  loading.value = true
  try {
    list.value = await listAnomalies(query.bizType, query.resolved)
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>
