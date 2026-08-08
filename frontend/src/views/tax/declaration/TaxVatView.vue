<template>
  <div class="tax-vat">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">增值税计算</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchAll">计算</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="20" v-if="result">
        <el-col :span="8">
          <el-card shadow="never" class="summary-card">
            <div class="card-title">销项税</div>
            <div class="card-value primary">{{ fmtAmount(result.outputTax) }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="summary-card">
            <div class="card-title">进项税(可抵扣)</div>
            <div class="card-value success">{{ fmtAmount(result.inputTax) }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="summary-card">
            <div class="card-title">应交增值税</div>
            <div class="card-value" :style="{ color: Number(result.payableTax) > 0 ? '#f56c6c' : '#67c23a' }">
              {{ fmtAmount(result.payableTax) }}
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="summary-card">
            <div class="card-title">附加税合计</div>
            <div class="card-value warning">{{ fmtAmount(result.surcharge) }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="summary-card">
            <div class="card-title">应交税费合计</div>
            <div class="card-value danger">{{ fmtAmount(result.totalPayable) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-alert v-if="result && result.note" :title="result.note" type="info" show-icon :closable="false" style="margin-top: 16px" />

      <el-divider />

      <el-row :gutter="20">
        <el-col :span="12">
          <h3>进项按税率</h3>
          <el-table :data="byRate.input" border>
            <el-table-column label="税率" align="center">
              <template #default="{ row }">{{ (Number(row.tax_rate) * 100).toFixed(0) }}%</template>
            </el-table-column>
            <el-table-column label="金额" align="right">
              <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="张数" align="center" />
          </el-table>
        </el-col>
        <el-col :span="12">
          <h3>销项按税率</h3>
          <el-table :data="byRate.output" border>
            <el-table-column label="税率" align="center">
              <template #default="{ row }">{{ (Number(row.tax_rate) * 100).toFixed(0) }}%</template>
            </el-table-column>
            <el-table-column label="金额" align="right">
              <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="张数" align="center" />
          </el-table>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { resolveDefaultPeriod } from '@/utils/period'
import { calculateVat, inputInvoiceSummary, outputInvoiceSummary } from '@/api/modules/tax'

const query = reactive({ period: '' })
const result = ref<any>(null)
const byRate = reactive({ input: [] as any[], output: [] as any[] })

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchAll = async () => {
  if (!query.period) return
  result.value = await calculateVat(query.period)
  byRate.input = await inputInvoiceSummary(query.period).then(() => []) // 占位: 接口返回格式调整
  byRate.output = await outputInvoiceSummary(query.period).then(() => [])
  // 实际从 inputByTaxRate / outputByTaxRate 获取
  const { default: request } = await import('@/api/request')
  byRate.input = await request.get('/sme/tax/v1/tax/input-invoices/by-tax-rate', { params: { period: query.period } })
  byRate.output = await request.get('/sme/tax/v1/tax/output-invoices/by-tax-rate', { params: { period: query.period } })
}

onMounted(async () => {
  query.period = await resolveDefaultPeriod()
  fetchAll()
})
</script>

<style scoped>
.summary-card {
  margin-bottom: 16px;
  text-align: center;
}
.card-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.card-value {
  font-size: 24px;
  font-weight: 600;
}
.card-value.primary { color: #409eff; }
.card-value.success { color: #67c23a; }
.card-value.warning { color: #e6a23c; }
.card-value.danger { color: #f56c6c; }
</style>
