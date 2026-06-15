<template>
  <div class="aging-analysis">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">账龄分析</span>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="应收账龄" name="receivable">
          <el-form inline class="filter-form">
            <el-form-item label="客户">
              <el-select v-model="customerId" filterable clearable placeholder="选择客户" style="width:240px" @change="fetchReceivableAging">
                <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :disabled="!customerId" @click="fetchReceivableAging">查询</el-button>
            </el-form-item>
          </el-form>

          <template v-if="receivableData">
            <el-alert title="客户账龄" :description="receivableSummary" type="info" show-icon :closable="false" style="margin-bottom:16px" />
            <el-table :data="receivableData.rows" border>
              <el-table-column label="账龄区间" prop="label" width="160" />
              <el-table-column label="金额" prop="amount" width="200" align="right">
                <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="笔数" prop="count" width="100" align="center" />
              <el-table-column label="占比" width="160" align="right">
                <template #default="{ row }">
                  {{ receivableData.total > 0 ? (row.amount / receivableData.total * 100).toFixed(1) : 0 }}%
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="请选择客户查看账龄分析" />
        </el-tab-pane>

        <el-tab-pane label="应付账龄" name="payable">
          <el-form inline class="filter-form">
            <el-form-item label="供应商">
              <el-select v-model="vendorId" filterable clearable placeholder="选择供应商" style="width:240px" @change="fetchPayableAging">
                <el-option v-for="v in vendors" :key="v.id" :label="v.name" :value="v.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :disabled="!vendorId" @click="fetchPayableAging">查询</el-button>
            </el-form-item>
          </el-form>

          <template v-if="payableData">
            <el-alert title="供应商账龄" :description="payableSummary" type="info" show-icon :closable="false" style="margin-bottom:16px" />
            <el-table :data="payableData.rows" border>
              <el-table-column label="账龄区间" prop="label" width="160" />
              <el-table-column label="金额" prop="amount" width="200" align="right">
                <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="笔数" prop="count" width="100" align="center" />
              <el-table-column label="占比" width="160" align="right">
                <template #default="{ row }">
                  {{ payableData.total > 0 ? (row.amount / payableData.total * 100).toFixed(1) : 0 }}%
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="请选择供应商查看账龄分析" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listCustomer, listVendor, receivableAging, payableAging } from '@/api/modules/arap'

const activeTab = ref('receivable')

const customers = ref<any[]>([])
const vendors = ref<any[]>([])

const customerId = ref<number | undefined>()
const vendorId = ref<number | undefined>()

interface AgingRow {
  label: string
  amount: number
  count: number
}
interface AgingData {
  rows: AgingRow[]
  total: number
}

const receivableData = ref<AgingData | null>(null)
const payableData = ref<AgingData | null>(null)

const receivableSummary = ref('')
const payableSummary = ref('')

const BUCKET_LABELS: Record<string, string> = {
  current: '未到期',
  days_0_30: '0-30天',
  days_31_60: '31-60天',
  days_61_90: '61-90天',
  days_91_180: '91-180天',
  days_181_365: '181-365天',
  over_365: '365天以上',
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const buildAgingRows = (apiResult: any): AgingData => {
  const buckets: string[] = apiResult.buckets || []
  const amounts: number[] = apiResult.amounts || []
  const counts: number[] = apiResult.counts || []
  const total = Number(apiResult.total || 0)
  const rows: AgingRow[] = buckets.map((key: string, i: number) => ({
    label: BUCKET_LABELS[key] || key,
    amount: Number(amounts[i] || 0),
    count: counts[i] || 0,
  }))
  return { rows, total }
}

const fetchReceivableAging = async () => {
  if (!customerId.value) { receivableData.value = null; return }
  const res = await receivableAging(customerId.value)
  receivableData.value = buildAgingRows(res)
  const cust = customers.value.find(c => c.id === customerId.value)
  receivableSummary.value = `客户: ${cust?.name || '未知'} | 未清余额合计: ¥${fmtAmount(receivableData.value.total)}`
}

const fetchPayableAging = async () => {
  if (!vendorId.value) { payableData.value = null; return }
  const res = await payableAging(vendorId.value)
  payableData.value = buildAgingRows(res)
  const v = vendors.value.find(v => v.id === vendorId.value)
  payableSummary.value = `供应商: ${v?.name || '未知'} | 未清余额合计: ¥${fmtAmount(payableData.value.total)}`
}

onMounted(async () => {
  const [custList, vendList] = await Promise.all([listCustomer(), listVendor()])
  customers.value = custList as any[]
  vendors.value = vendList as any[]
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 16px; }
</style>
