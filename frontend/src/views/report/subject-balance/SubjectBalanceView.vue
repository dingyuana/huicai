<template>
  <div class="subject-balance">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">科目余额表</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <PeriodNavigator v-model="query.period" @change="fetchData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="onExport">导出</el-button>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="hideZeroRows">隐藏零值行</el-checkbox>
        </el-form-item>
      </el-form>

      <el-table :data="visibleRows" v-loading="loading" border show-summary :summary-method="summaryRow">
        <el-table-column prop="code" label="科目编码" width="120" />
        <el-table-column prop="name" label="科目名称" min-width="180" />
        <el-table-column prop="level" label="层级" width="60" align="center" />
        <el-table-column label="期初余额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.begin_balance) }}</template>
        </el-table-column>
        <el-table-column label="借方" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.debit_total) }}</template>
        </el-table-column>
        <el-table-column label="贷方" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.credit_total) }}</template>
        </el-table-column>
        <el-table-column label="期末余额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.end_balance) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { resolveLatestClosedPeriod } from '@/utils/period'
import { ElMessage } from 'element-plus'
import { subjectBalance } from '@/api/modules/report'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const query = reactive({ period: '' })
const list = ref<any[]>([])
const loading = ref(false)
const hideZeroRows = ref(true)

const isZeroRow = (r: any) =>
  [r.begin_balance, r.debit_total, r.credit_total, r.end_balance].every(v => Number(v || 0) === 0)

const visibleRows = computed(() =>
  hideZeroRows.value ? list.value.filter(r => !isZeroRow(r)) : list.value
)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  if (!query.period) return
  loading.value = true
  try {
    list.value = await subjectBalance(query.period)
  } finally {
    loading.value = false
  }
}

const summaryRow = ({ columns, data }: any) => {
  const sums: any = {}
  columns.forEach((col: any) => {
    const prop = col.property
    if (['begin_balance', 'debit_total', 'credit_total', 'end_balance'].includes(prop)) {
      sums[prop] = data.reduce((s: number, r: any) => s + Number(r[prop] || 0), 0).toFixed(2)
    }
  })
  sums['name'] = '合计'
  return sums
}

const onExport = () => {
  ElMessage.info('导出功能: 复制表格内容到 Excel')
}

onMounted(async () => {
  query.period = await resolveLatestClosedPeriod()
  fetchData()
})
</script>
