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

      <!-- P89-B：对比列（利润表 = 上期金额，取上一期间）。失败/空期间时降级隐藏 -->
      <el-alert
        v-if="prevAvailable && prevPeriodLabel"
        :title="`对比列：上期（${prevPeriodLabel}）`"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-table v-if="result" :data="visibleRows" border>
        <el-table-column prop="label" label="项目" min-width="180" />
        <el-table-column v-if="prevAvailable" label="上期金额" align="right" width="180">
          <template #default="{ row }">
            <span :class="amountClass(row.bold, row.prev)">{{ fmtAmount(row.prev) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本期金额" align="right" width="180">
          <template #default="{ row }">
            <span :class="amountClass(row.bold, row.current)">{{ fmtAmount(row.current) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本年累计" align="right" width="180">
          <template #default="{ row }">
            <span :class="amountClass(row.bold, row.cumulative)">{{ fmtAmount(row.cumulative) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { resolveLatestClosedPeriod, prevPeriod } from '@/utils/period'
import { incomeStatement } from '@/api/modules/report'
import { amountClass, formatAmount } from '@/utils/format'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const query = reactive({ period: '' })
const result = ref<any>(null)
const prevData = ref<any>(null)
const prevAvailable = ref(false)
const hideZeroRows = ref(true)

const prevPeriodLabel = computed(() => {
  const p = prevPeriod(query.period)
  if (!p) return ''
  return `${p.slice(0, 4)}年${Number(p.slice(4, 6))}月`
})

// P89-A：统一千分位 + 负数标红
const fmtAmount = (v: any) => formatAmount(v)

const rows = computed(() => {
  if (!result.value) return []
  const r = result.value
  // P88②：累计列逐行接后端字段，不再硬编码 0（旧版除首尾外全部写 0，累计数自相矛盾）
  return [
    { label: '一、营业收入',    prev: prevData.value?.revenue,         current: r.revenue,         cumulative: r.cumulativeRevenue, bold: true },
    { label: '减:营业成本',    prev: prevData.value?.cost,            current: r.cost,            cumulative: r.cumulativeCost, bold: false },
    { label: '二、毛利',       prev: prevData.value?.grossProfit,     current: r.grossProfit,     cumulative: r.cumulativeGrossProfit, bold: true },
    { label: '减:期间费用',    prev: prevData.value?.expense,         current: r.expense,         cumulative: r.cumulativeExpense, bold: false },
    { label: '三、营业利润',   prev: prevData.value?.operatingProfit, current: r.operatingProfit, cumulative: r.cumulativeOperatingProfit, bold: true },
    { label: '减:其他支出',    prev: prevData.value?.otherExpense,    current: r.otherExpense,    cumulative: r.cumulativeOtherExpense, bold: false },
    { label: '四、利润总额',   prev: prevData.value?.totalProfit,     current: r.totalProfit,     cumulative: r.cumulativeProfit, bold: true },
  ]
})

const visibleRows = computed(() =>
  hideZeroRows.value
    ? rows.value.filter(r =>
        Number(r.current || 0) !== 0 ||
        Number(r.cumulative || 0) !== 0 ||
        Number(r.prev || 0) !== 0
      )
    : rows.value
)

const fetchData = async () => {
  if (!query.period) return
  // P89-B：对比列取上一期间。上期无数据/查询失败时降级为不显示对比列，不影响本期报表。
  const prev = prevPeriod(query.period)
  const [current, previous] = await Promise.all([
    incomeStatement(query.period),
    prev ? incomeStatement(prev).catch(() => null) : Promise.resolve(null),
  ])
  result.value = current
  prevData.value = previous
  prevAvailable.value = !!previous
}

const onExport = () => {
  // 导出功能待实现
}

onMounted(async () => {
  query.period = await resolveLatestClosedPeriod()
  fetchData()
})
</script>
