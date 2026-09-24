<template>
  <div class="balance-sheet">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">资产负债表</span>
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

      <el-alert v-if="result" :title="result.balanced ? '资产=负债+所有者权益, 平衡 ✓' : '⚠ 资产≠负债+所有者权益, 请检查!'" :type="result.balanced ? 'success' : 'error'" show-icon :closable="false" style="margin-bottom: 16px" />

      <!-- P89-B：对比列（资产负债表 = 年初数，取年初期间 1 月的期初余额） -->
      <el-alert v-if="yearStartLabel" :title="`对比列：年初数（${yearStartLabel}）`" type="info" show-icon :closable="false" style="margin-bottom: 16px" />

      <el-row :gutter="20" v-if="result">
        <el-col :span="12">
          <h3>资产</h3>
          <el-table :data="visibleAssets" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="120" />
            <el-table-column v-if="yearStartAvailable" label="年初数" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, yearStartValue(row.code))">{{ fmtAmount(yearStartValue(row.code)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="期末余额" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, row.end_balance)">{{ fmtAmount(row.end_balance) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="total-row">
            <span>资产合计:</span>
            <span class="total-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b :class="amountClass(false, yearStartData.totalAssets)">{{ fmtAmount(yearStartData.totalAssets) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b :class="amountClass(false, result.totalAssets)">{{ fmtAmount(result.totalAssets) }}</b>
              </span>
            </span>
          </div>
        </el-col>
        <el-col :span="12">
          <h3>负债</h3>
          <el-table :data="visibleLiabilities" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="120" />
            <el-table-column v-if="yearStartAvailable" label="年初数" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, yearStartValue(row.code))">{{ fmtAmount(yearStartValue(row.code)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="期末余额" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, row.end_balance)">{{ fmtAmount(row.end_balance) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <h3 style="margin-top: 16px">所有者权益</h3>
          <el-table :data="visibleEquity" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="120" />
            <el-table-column v-if="yearStartAvailable" label="年初数" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, yearStartValue(row.code))">{{ fmtAmount(yearStartValue(row.code)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="期末余额" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, row.end_balance)">{{ fmtAmount(row.end_balance) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="total-row">
            <span>负债+权益合计:</span>
            <span class="total-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b :class="amountClass(false, yearStartData.totalLiabEquity)">{{ fmtAmount(yearStartData.totalLiabEquity) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b :class="amountClass(false, result.totalLiabEquity)">{{ fmtAmount(result.totalLiabEquity) }}</b>
              </span>
            </span>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { resolveLatestClosedPeriod, yearStartPeriod } from '@/utils/period'
import { balanceSheet, subjectBalance } from '@/api/modules/report'
import { amountClass, formatAmount } from '@/utils/format'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const query = reactive({ period: '' })
const result = ref<any>(null)
// P89-B：年初期间的科目余额明细（1 月期初余额 = 年初余额）
const yearStartRows = ref<any[]>([])
const yearStartData = ref<any>(null)
const yearStartAvailable = ref(false)
const hideZeroRows = ref(true)

const yearStartLabel = computed(() => {
  const p = yearStartPeriod(query.period)
  return p ? `${p.slice(0, 4)}年1月` : ''
})

// P89-A：统一千分位 + 负数标红
const fmtAmount = (v: any) => formatAmount(v)

/** 年初数取值（按科目编码从年初余额明细映射） */
const yearStartValue = (code: string): number => {
  const row = yearStartRows.value.find((r: any) => String(r.code) === String(code))
  return Number(row?.begin_balance || 0)
}

const isZeroRow = (r: any) => Number(r.end_balance || 0) === 0

const visibleAssets = computed(() => hideZeroRows.value ? result.value.assets.filter((r: any) => !isZeroRow(r)) : result.value.assets)
const visibleLiabilities = computed(() => hideZeroRows.value ? result.value.liabilities.filter((r: any) => !isZeroRow(r)) : result.value.liabilities)
const visibleEquity = computed(() => hideZeroRows.value ? result.value.equity.filter((r: any) => !isZeroRow(r)) : result.value.equity)

const fetchData = async () => {
  if (!query.period) return
  // P89-B：对比列 = 年初数。
  // 科目行年初值取年初期间(1月)的期初余额；合计取年初期间 balanceSheet() 的返回值，
  // 因为后端口径含本年利润、成本在库存、未分类项等特殊处理，前端重算必然算错。
  const ys = yearStartPeriod(query.period)
  const [current, ysBalance, ysRows] = await Promise.all([
    balanceSheet(query.period),
    ys ? balanceSheet(ys).catch(() => null) : Promise.resolve(null),
    ys ? subjectBalance(ys).catch(() => []) : Promise.resolve([]),
  ])
  result.value = current
  yearStartData.value = ysBalance
  yearStartRows.value = Array.isArray(ysRows) ? ysRows : []
  yearStartAvailable.value = !!ysBalance
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
.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  margin-top: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-weight: 600;
}
.total-amount {
  display: flex;
  gap: 20px;
}
/* 年初数 / 期末余额 两列并排，标签在上数字在下，与表格列头对应 */
.total-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.total-cell em {
  font-style: normal;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.total-cell b {
  color: #409eff;
}
</style>
