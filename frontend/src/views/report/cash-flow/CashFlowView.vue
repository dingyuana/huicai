<template>
  <div class="cash-flow">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">现金流量表</span>
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
        <el-table-column prop="label" label="项目" min-width="280" />
        <el-table-column label="本期金额" align="right" width="180">
          <template #default="{ row }">
            <span :class="amountClass(row.bold, row.amount, row.warn)">{{ fmtAmount(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本年累计金额" align="right" width="180">
          <template #default="{ row }">
            <span :class="amountClass(row.bold, row.amountYtd, row.warn)">{{ fmtAmount(row.amountYtd) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { resolveLatestClosedPeriod } from '@/utils/period'
import { ElMessage } from 'element-plus'
import { cashFlowStatement, exportCashFlow } from '@/api/modules/report'
import { amountClass, formatAmount } from '@/utils/format'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const query = reactive({ period: '' })
const result = ref<any>(null)
const hideZeroRows = ref(true)

const fmtAmount = (v: any) => formatAmount(v)

const rows = computed(() => {
  if (!result.value) return []
  const r = result.value
  // P92-A：每行同时携带本期金额(amount)与本年累计金额(amountYtd)
  const list = [
    { label: '一、经营活动现金流量',   amount: '', amountYtd: '', bold: true },
    { label: '  现金流入',           amount: r.operatingIn,   amountYtd: r.operatingInYtd,   bold: false },
    { label: '  现金流出',           amount: r.operatingOut,  amountYtd: r.operatingOutYtd,  bold: false },
    { label: '  经营活动净流量',     amount: r.operatingNet,  amountYtd: r.operatingNetYtd,  bold: true },
    { label: '二、投资活动现金流量',   amount: '', amountYtd: '', bold: true },
    { label: '  现金流入',           amount: r.investingIn,   amountYtd: r.investingInYtd,   bold: false },
    { label: '  现金流出',           amount: r.investingOut,  amountYtd: r.investingOutYtd,  bold: false },
    { label: '  投资活动净流量',     amount: r.investingNet,  amountYtd: r.investingNetYtd,  bold: true },
    { label: '三、筹资活动现金流量',   amount: '', amountYtd: '', bold: true },
    { label: '  现金流入',           amount: r.financingIn,   amountYtd: r.financingInYtd,   bold: false },
    { label: '  现金流出',           amount: r.financingOut,  amountYtd: r.financingOutYtd,  bold: false },
    { label: '  筹资活动净流量',     amount: r.financingNet,  amountYtd: r.financingNetYtd,  bold: true },
    { label: '四、现金及现金等价物净增加额', amount: r.totalNet, amountYtd: r.totalNetYtd, bold: true },
    // P88③：期初/期末现金闭环（fixed=报表骨架，零值不隐藏）
    { label: '加：期初现金及现金等价物余额', amount: r.openingCash, amountYtd: r.openingCashYtd, bold: false, fixed: true },
    { label: '五、期末现金及现金等价物余额', amount: r.closingCash, amountYtd: r.closingCashYtd, bold: true, fixed: true },
  ]
  // P88③：勾稽提示行（差异才显示）
  if (r.cashCheckOk === false) {
    list.push({ label: '⚠ 勾稽差异（期末现金 vs 科目余额）', amount: r.cashCheckDiff, amountYtd: r.cashCheckDiffYtd, bold: false, warn: true, fixed: true })
  }
  return list
})

const visibleRows = computed(() => {
  if (!hideZeroRows.value) return rows.value
  // P92-A：本期或本年累计任一非零则显示该行（原先只看本期，累计有数会被误藏）
  return rows.value.filter(r =>
    r.fixed ||
    r.amount === '' ||
    (Number(r.amount) !== 0 || Number(r.amountYtd) !== 0))
})

const fetchData = async () => {
  if (!query.period) return
  result.value = await cashFlowStatement(query.period)
}

const onExport = async () => {
  if (!query.period) {
    ElMessage.warning('请先选择期间')
    return
  }
  try {
    await exportCashFlow(query.period)
    ElMessage.success('导出成功')
  } catch (e) {
    // request 拦截器已统一弹错，这里不重复
  }
}

onMounted(async () => {
  query.period = await resolveLatestClosedPeriod()
  fetchData()
})
</script>

<style scoped>
.amount-bold {
  font-weight: 600;
  color: #409eff;
}
.amount-warn {
  color: #f56c6c;
  font-weight: 600;
}
</style>
