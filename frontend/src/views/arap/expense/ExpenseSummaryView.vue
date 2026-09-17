<template>
  <div class="expense-summary">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">费用汇总报表</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="起">
          <el-date-picker
            v-model="query.periodFrom"
            type="month"
            value-format="YYYYMM"
            :clearable="false"
            :disabled="loading"
            placeholder="起始期间"
            style="width:140px;"
            @change="onFromChange"
          />
        </el-form-item>
        <el-form-item label="止">
          <el-date-picker
            v-model="query.periodTo"
            type="month"
            value-format="YYYYMM"
            :clearable="false"
            :disabled="loading"
            placeholder="结束期间"
            style="width:140px;"
            @change="onToChange"
          />
        </el-form-item>
        <el-form-item label="维度">
          <el-select v-model="query.groupBy" :disabled="loading" style="width:130px" @change="fetchSummary">
            <el-option label="部门" value="DEPT" />
            <el-option label="费用类型" value="EXPENSE_TYPE" />
            <el-option label="员工" value="EMPLOYEE" />
          </el-select>
        </el-form-item>
        <el-form-item label="同比">
          <el-switch v-model="query.includeYoy" :disabled="loading" @change="fetchSummary" />
        </el-form-item>
        <el-form-item label="环比">
          <el-switch v-model="query.includeMom" :disabled="loading" @change="fetchSummary" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="loading" @click="fetchSummary">查询</el-button>
          <el-button :disabled="loading" @click="doExport">导出</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="16" class="summary-cards">
        <el-col :span="8">
          <div class="summary-card">
            <div class="card-label">期间单据数</div>
            <div class="card-value">{{ summary?.totalCount ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="summary-card">
            <div class="card-label">期间金额合计</div>
            <div class="card-value">{{ fmtAmount(summary?.totalAmount) }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="summary-card">
            <div class="card-label">当前维度</div>
            <div class="card-value card-value-sm">{{ dimLabel }}</div>
          </div>
        </el-col>
      </el-row>

      <el-table :data="summary?.rows || []" v-loading="loading" border stripe>
        <el-table-column prop="dimName" :label="dimLabel" min-width="160" />
        <el-table-column prop="count" label="单据数" width="100" align="right" />
        <el-table-column prop="amount" label="金额" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column v-if="query.groupBy === 'DEPT'" prop="perCapita" label="人均" width="130" align="right">
          <template #default="{ row }">{{ row.perCapita == null ? '—' : fmtAmount(row.perCapita) }}</template>
        </el-table-column>
        <el-table-column v-if="query.includeYoy" prop="amountYoy" label="同比" width="130" align="right">
          <template #default="{ row }">{{ row.amountYoy == null ? '—' : fmtAmount(row.amountYoy) }}</template>
        </el-table-column>
        <el-table-column v-if="query.includeMom" prop="amountMom" label="环比" width="130" align="right">
          <template #default="{ row }">{{ row.amountMom == null ? '—' : fmtAmount(row.amountMom) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveDefaultPeriod } from '@/utils/period'
import {
  getExpenseSummary, exportExpenseSummary,
  type ExpenseSummaryVO, type ExpenseGroupBy
} from '@/api/modules/expenseReimbursement'

const query = ref({
  periodFrom: '',
  periodTo: '',
  groupBy: 'DEPT' as ExpenseGroupBy,
  includeYoy: true,
  includeMom: true,
})
const loading = ref(false)
const summary = ref<ExpenseSummaryVO | null>(null)

const dimLabel = computed(() => ({ DEPT: '部门', EMPLOYEE: '员工', EXPENSE_TYPE: '费用类型' }[query.value.groupBy]))

const isValidPeriod = (p: string): boolean => {
  if (!/^\d{6}$/.test(p)) return false
  const m = Number(p.slice(4, 6))
  return m >= 1 && m <= 12
}

/** 起期间变化时，若止期间早于起，自动对齐 */
const onFromChange = () => {
  if (query.value.periodFrom && query.value.periodTo &&
      query.value.periodTo < query.value.periodFrom) {
    query.value.periodTo = query.value.periodFrom
  }
  fetchSummary()
}

const onToChange = () => {
  if (query.value.periodFrom && query.value.periodTo &&
      query.value.periodTo < query.value.periodFrom) {
    ElMessage.warning('止期间不得早于起期间')
    query.value.periodTo = query.value.periodFrom
  }
  fetchSummary()
}

const fetchSummary = async () => {
  if (!isValidPeriod(query.value.periodFrom) || !isValidPeriod(query.value.periodTo)) {
    ElMessage.warning('期间必填且必须为合法月份（YYYYMM，01-12）')
    return
  }
  if (query.value.periodFrom > query.value.periodTo) {
    ElMessage.warning('起期间不得晚于止期间')
    return
  }
  loading.value = true
  try {
    summary.value = await getExpenseSummary({
      periodFrom: query.value.periodFrom,
      periodTo: query.value.periodTo,
      groupBy: query.value.groupBy,
      includeYoy: query.value.includeYoy,
      includeMom: query.value.includeMom,
    })
  } catch {
    summary.value = null
  } finally {
    loading.value = false
  }
}

const doExport = async () => {
  if (!isValidPeriod(query.value.periodFrom) || !isValidPeriod(query.value.periodTo)) {
    ElMessage.warning('期间必填且必须为合法月份')
    return
  }
  loading.value = true
  try {
    await exportExpenseSummary({
      periodFrom: query.value.periodFrom,
      periodTo: query.value.periodTo,
      groupBy: query.value.groupBy,
      includeYoy: query.value.includeYoy,
      includeMom: query.value.includeMom,
    })
  } catch {
    ElMessage.error('导出失败')
  } finally {
    loading.value = false
  }
}

const fmtAmount = (v: number | null | undefined): string => Number(v ?? 0).toFixed(2)

onMounted(async () => {
  const p = await resolveDefaultPeriod()
  query.value.periodFrom = p
  query.value.periodTo = p
  fetchSummary()
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.filter-form {
  margin-bottom: 12px;
}
.summary-cards {
  margin-bottom: 20px;
}
.summary-card {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-label {
  font-size: 13px;
  color: #909399;
}
.card-value {
  font-size: 20px;
  font-weight: 600;
}
.card-value-sm {
  font-size: 16px;
}
</style>
