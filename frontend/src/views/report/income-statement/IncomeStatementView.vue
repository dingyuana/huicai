<template>
  <div class="income-statement">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">利润表</span>
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

      <el-table v-if="result" :data="visibleRows" border>
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
import { resolveLatestClosedPeriod } from '@/utils/period'
import { incomeStatement } from '@/api/modules/report'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const query = reactive({ period: '' })
const result = ref<any>(null)
const hideZeroRows = ref(true)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const rows = computed(() => {
  if (!result.value) return []
  const r = result.value
  // P88②：累计列逐行接后端字段，不再硬编码 0（旧版除首尾外全部写 0，累计数自相矛盾）
  return [
    { label: '一、营业收入',    current: r.revenue,         cumulative: r.cumulativeRevenue, bold: true },
    { label: '减:营业成本',    current: r.cost,            cumulative: r.cumulativeCost, bold: false },
    { label: '二、毛利',       current: r.grossProfit,     cumulative: r.cumulativeGrossProfit, bold: true },
    { label: '减:期间费用',    current: r.expense,         cumulative: r.cumulativeExpense, bold: false },
    { label: '三、营业利润',   current: r.operatingProfit, cumulative: r.cumulativeOperatingProfit, bold: true },
    { label: '减:其他支出',    current: r.otherExpense,    cumulative: r.cumulativeOtherExpense, bold: false },
    { label: '四、利润总额',   current: r.totalProfit,     cumulative: r.cumulativeProfit, bold: true },
  ]
})

const visibleRows = computed(() =>
  hideZeroRows.value ? rows.value.filter(r => Number(r.current || 0) !== 0 || Number(r.cumulative || 0) !== 0) : rows.value
)

const fetchData = async () => {
  if (!query.period) return
  result.value = await incomeStatement(query.period)
}

const onExport = () => {
  // 导出功能待实现
}

onMounted(async () => {
  query.period = await resolveLatestClosedPeriod()
  fetchData()
})
</script>

<style scoped>
.amount-bold {
  font-weight: 600;
}
</style>
