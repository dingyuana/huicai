<template>
  <div class="custom-report">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">自定义报表</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <PeriodNavigator v-model="query.period" @change="fetchData" />
        </el-form-item>
        <el-form-item label="科目级次">
          <el-select v-model="query.level" clearable placeholder="全部" style="width:120px">
            <el-option label="1级" :value="1" />
            <el-option label="2级" :value="2" />
            <el-option label="3级" :value="3" />
            <el-option label="4级" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目编码前缀">
          <el-input v-model="query.codePrefix" placeholder="如 1001 或 6" clearable style="width:140px" />
        </el-form-item>
        <el-form-item label="仅显示有余额">
          <el-switch v-model="query.onlyWithBalance" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button type="success" @click="onExport" :disabled="!list.length">导出 Excel</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border height="600">
        <el-table-column prop="code" label="科目编码" width="120" />
        <el-table-column prop="name" label="科目名称" min-width="180" />
        <el-table-column prop="level" label="级次" width="70" align="center" />
        <el-table-column label="余额方向" width="90" align="center">
          <template #default="{ row }">{{ directionLabel(row.direction) }}</template>
        </el-table-column>
        <el-table-column label="期初余额" width="140" align="right">
          <template #default="{ row }">{{ fmt(row.begin_balance) }}</template>
        </el-table-column>
        <el-table-column label="本期借方" width="140" align="right">
          <template #default="{ row }">{{ fmt(row.debit_total) }}</template>
        </el-table-column>
        <el-table-column label="本期贷方" width="140" align="right">
          <template #default="{ row }">{{ fmt(row.credit_total) }}</template>
        </el-table-column>
        <el-table-column label="期末余额" width="140" align="right">
          <template #default="{ row }">{{ fmt(row.end_balance) }}</template>
        </el-table-column>
      </el-table>

      <div class="total-row">共 {{ list.length }} 条记录</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/api/request'
import { ElMessage } from 'element-plus'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const query = reactive({ period: '', level: null as number | null, codePrefix: '', onlyWithBalance: false })
const list = ref<any[]>([])
const loading = ref(false)

const directionLabel = (d: string) => (d === 'debit' ? '借' : d === 'credit' ? '贷' : '—')
const fmt = (v: any) => Number(v || 0).toFixed(2)

onMounted(() => {
  fetchData()
})

const fetchData = async () => {
  if (!query.period) return
  loading.value = true
  try {
    const params: any = { period: query.period, onlyWithBalance: query.onlyWithBalance }
    if (query.level) params.level = query.level
    if (query.codePrefix) params.codePrefix = query.codePrefix
    const res: any = await request.get('/api/base/report/v1/reports/custom/subject-balance', { params })
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

const onExport = () => {
  if (!query.period) { ElMessage.warning('请先选择期间'); return }
  const params = new URLSearchParams()
  params.set('period', query.period)
  if (query.level) params.set('level', String(query.level))
  if (query.codePrefix) params.set('codePrefix', query.codePrefix)
  params.set('onlyWithBalance', String(query.onlyWithBalance))
  window.open(`/api/base/report/v1/reports/custom/subject-balance/export?${params.toString()}`, '_blank')
}
</script>

<style scoped>
.page-header { margin-bottom: 16px; }
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 16px; }
.total-row { margin-top: 12px; text-align: right; color: #909399; font-size: 13px; }
</style>
