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

      <h3>按税率明细</h3>
      <el-row :gutter="20">
        <el-col :span="12">
          <h4>进项按税率</h4>
          <el-table :data="byRate.input" border size="small">
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
          <h4>销项按税率</h4>
          <el-table :data="byRate.output" border size="small">
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

    <el-card shadow="never" style="margin-top: 16px">
      <el-tabs v-model="subTab">
        <el-tab-pane label="附表一（销项）" name="appendix-i">
          <template v-if="appendixI">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-statistic label="合计销售额(不含税)" :value="fmtAmount(appendixI.totalSalesAmount)" />
              </el-col>
              <el-col :span="8">
                <el-statistic label="合计销项税" :value="fmtAmount(appendixI.totalTaxAmount)" />
              </el-col>
              <el-col :span="8">
                <el-statistic label="合计含税金额" :value="fmtAmount(appendixI.totalAmount)" />
              </el-col>
            </el-row>
            <el-table :data="appendixI.rows" border size="small" style="margin-top: 12px">
              <el-table-column prop="customerName" label="客户" min-width="150" />
              <el-table-column label="税率" align="center" width="90">
                <template #default="{ row }">{{ (Number(row.rate) * 100).toFixed(0) }}%</template>
              </el-table-column>
              <el-table-column label="销售额(不含税)" align="right" width="140">
                <template #default="{ row }">{{ fmtAmount(row.salesAmount) }}</template>
              </el-table-column>
              <el-table-column label="销项税" align="right" width="120">
                <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
              </el-table-column>
              <el-table-column label="含税金额" align="right" width="120">
                <template #default="{ row }">{{ fmtAmount(row.totalAmount) }}</template>
              </el-table-column>
            </el-table>
            <div v-if="appendixI.rows.length === 0" style="color:#999;padding:16px;text-align:center">无数据</div>
          </template>
          <div v-else style="color:#999;padding:16px;text-align:center">请先计算</div>
        </el-tab-pane>

        <el-tab-pane label="附表二（进项）" name="appendix-ii">
          <template v-if="appendixII">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-statistic label="合计金额(不含税)" :value="fmtAmount(appendixII.totalAmountExTax)" />
              </el-col>
              <el-col :span="8">
                <el-statistic label="合计进项税" :value="fmtAmount(appendixII.totalTaxAmount)" />
              </el-col>
              <el-col :span="8">
                <el-statistic label="可抵扣税额" :value="fmtAmount(appendixII.deductibleTax)">
                  <template #title><span style="color:#67c23a;font-size:14px">已认证已申报</span></template>
                </el-statistic>
              </el-col>
            </el-row>
            <el-table :data="appendixII.rows" border size="small" style="margin-top: 12px">
              <el-table-column prop="vendorName" label="供应商" min-width="150" />
              <el-table-column label="税率" align="center" width="90">
                <template #default="{ row }">{{ (Number(row.rate) * 100).toFixed(0) }}%</template>
              </el-table-column>
              <el-table-column label="金额(不含税)" align="right" width="140">
                <template #default="{ row }">{{ fmtAmount(row.amountExTax) }}</template>
              </el-table-column>
              <el-table-column label="税额" align="right" width="120">
                <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
              </el-table-column>
              <el-table-column prop="declareStatus" label="申报态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.declareStatus === 'DECLARED' ? 'success' : 'info'" size="small">{{ row.declareStatus }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="appendixII.rows.length === 0" style="color:#999;padding:16px;text-align:center">无数据</div>
          </template>
          <div v-else style="color:#999;padding:16px;text-align:center">请先计算</div>
        </el-tab-pane>

        <el-tab-pane label="税负率分析" name="burden">
          <template v-if="burden">
            <el-row :gutter="20">
              <el-col :span="6">
                <el-card shadow="never" style="text-align:center">
                  <div class="card-title">含税销售收入</div>
                  <div class="card-value primary">{{ fmtAmount(burden.revenue) }}</div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card shadow="never" style="text-align:center">
                  <div class="card-title">销项税</div>
                  <div class="card-value primary">{{ fmtAmount(burden.outputTax) }}</div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card shadow="never" style="text-align:center">
                  <div class="card-title">进项可抵扣</div>
                  <div class="card-value success">{{ fmtAmount(burden.inputDeduction) }}</div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card shadow="never" style="text-align:center">
                  <div class="card-title">应交增值税</div>
                  <div class="card-value" :style="{ color: Number(burden.payableTax) > 0 ? '#f56c6c' : '#67c23a' }">
                    {{ fmtAmount(burden.payableTax) }}
                  </div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card shadow="never" style="text-align:center">
                  <div class="card-title">税负率</div>
                  <div class="card-value warning">{{ burden.taxBurdenRate != null ? (Number(burden.taxBurdenRate) * 100).toFixed(2) + '%' : '—' }}</div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card shadow="never" style="text-align:center">
                  <div class="card-title">同比变动</div>
                  <div class="card-value" :style="{ color: burden.yoyChange != null && Number(burden.yoyChange) > 0 ? '#f56c6c' : '#67c23a' }">
                    {{ burden.yoyChange != null ? (Number(burden.yoyChange) * 100).toFixed(2) + '%' : '—' }}
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </template>
          <div v-else style="color:#999;padding:16px;text-align:center">请先计算</div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { resolveDefaultPeriod } from '@/utils/period'
import { calculateVat } from '@/api/modules/tax'
import request from '@/api/request'

const query = reactive({ period: '' })
const result = ref<any>(null)
const byRate = reactive({ input: [] as any[], output: [] as any[] })
const subTab = ref<string>('appendix-i')

const appendixI = ref<any>(null)
const appendixII = ref<any>(null)
const burden = ref<any>(null)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchAppendices = async () => {
  if (!query.period) return
  appendixI.value = await request.get('/sme/tax/v1/tax/vat/appendix-i', { params: { period: query.period } })
  appendixII.value = await request.get('/sme/tax/v1/tax/vat/appendix-ii', { params: { period: query.period } })
  burden.value = await request.get('/sme/tax/v1/tax/vat/tax-burden', { params: { period: query.period, type: 'YOY' } })
}

const fetchAll = async () => {
  if (!query.period) return
  result.value = await calculateVat(query.period)
  const { default: req } = await import('@/api/request')
  byRate.input = await req.get('/sme/tax/v1/tax/input-invoices/by-tax-rate', { params: { period: query.period } })
  byRate.output = await req.get('/sme/tax/v1/tax/output-invoices/by-tax-rate', { params: { period: query.period } })
  await fetchAppendices()
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
  font-size: 22px;
  font-weight: 600;
}
.card-value.primary { color: #409eff; }
.card-value.success { color: #67c23a; }
.card-value.warning { color: #e6a23c; }
.card-value.danger { color: #f56c6c; }
</style>
