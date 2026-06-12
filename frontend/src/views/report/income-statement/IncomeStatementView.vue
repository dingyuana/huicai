<template>
  <div class="income-statement">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">利润表</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="onExport">导出</el-button>
        </el-form-item>
      </el-form>

      <el-table v-if="result" :data="rows" border>
        <el-table-column prop="label" label="项目" min-width="180" />
        <el-table-column label="本期金额" align="right" width="180">
          <template #default="{ row }">
            <span :class="{ 'amount-bold': row.bold }">{{ fmtAmount(row.current) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本年累计" align="right" width="180">
          <template #default="{ row }">
            <span :class="{ 'amount-bold': row.bold }">{{ fmtAmount(row.cumulative) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import dayjs from 'dayjs'
import { incomeStatement } from '@/api/modules/report'

const query = reactive({ period: dayjs().format('YYYYMM') })
const result = ref<any>(null)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const rows = computed(() => {
  if (!result.value) return []
  return [
    { label: '一、营业收入',    current: result.value.revenue,         cumulative: result.value.cumulativeRevenue, bold: true },
    { label: '减:营业成本',    current: result.value.cost,            cumulative: 0, bold: false },
    { label: '二、毛利',       current: result.value.grossProfit,     cumulative: 0, bold: true },
    { label: '减:期间费用',    current: result.value.expense,         cumulative: 0, bold: false },
    { label: '三、营业利润',   current: result.value.operatingProfit, cumulative: 0, bold: true },
    { label: '减:其他支出',    current: result.value.otherExpense,    cumulative: 0, bold: false },
    { label: '四、利润总额',   current: result.value.totalProfit,     cumulative: result.value.cumulativeProfit, bold: true },
  ]
})

const fetchData = async () => {
  if (!query.period) return
  result.value = await incomeStatement(query.period)
}

onMounted(fetchData)
</script>

<style scoped>
.amount-bold {
  font-weight: 600;
}
</style>
