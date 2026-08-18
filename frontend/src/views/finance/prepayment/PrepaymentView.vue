<template>
  <div class="prepayment-view">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">预收 / 预付管理</span>
        <el-button type="primary" @click="showCreate">新增</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="类型">
          <el-select v-model="query.kind" clearable placeholder="全部" style="width:120px">
            <el-option value="RECEIPT" label="预收" />
            <el-option value="PAYMENT" label="预付" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:130px">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="id" label="编号" width="70" />
        <el-table-column label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.customerId ? 'success' : 'danger'" size="small">
              {{ row.customerId ? '预收' : '预付' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="往来单位" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.customerName || row.vendorName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            {{ fmtAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="已核销" width="100" align="right">
          <template #default="{ row }">
            <span style="color:#67c23a">{{ fmtAmount(row.settledAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未核销" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.unsettledAmount) > 0 ? '#f56c6c' : '#67c23a' }">
              {{ fmtAmount(row.unsettledAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column prop="txDate" label="日期" width="110" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status] || 'info'" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" v-if="row.status === 'DRAFT'" @click="onConfirm(row)">确认</el-button>
            <el-button text size="small" type="danger" v-if="row.status === 'CONFIRMED' || row.status === 'APPLIED'"
              @click="onReverse(row)">反冲</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next, jumper"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isPrepay ? '新增预收款' : '新增预付款'" width="550px">
      <el-form :model="createForm" label-width="80">
        <el-form-item label="类型">
          <el-radio-group v-model="isPrepay" @change="onTypeChange">
            <el-radio :value="true">预收款（客户）</el-radio>
            <el-radio :value="false">预付款（供应商）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="客户" v-if="isPrepay">
          <el-select v-model="createForm.customerId" filterable clearable placeholder="选择客户" style="width:100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" v-if="!isPrepay">
          <el-select v-model="createForm.vendorId" filterable clearable placeholder="选择供应商" style="width:100%">
            <el-option v-for="v in vendors" :key="v.id" :label="v.name" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="createForm.amount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="createForm.txDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="createForm.summary" placeholder="摘要" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pagePrepayment, createPrepayment, confirmPrepayment, reversePrepayment,
} from '@/api/modules/prepayment'
import { listCustomer, listVendor } from '@/api/modules/arap'

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'APPLIED', label: '已核销' },
  { value: 'REVERSED', label: '已反冲' },
]
const STATUS_MAP = Object.fromEntries(STATUS_OPTIONS.map(o => [o.value, o.label]))
const STATUS_TAG: Record<string, any> = { DRAFT: 'info', CONFIRMED: 'warning', APPLIED: 'success', REVERSED: 'danger' }

const query = reactive({ kind: '', status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const customers = ref<Array<{ id: number; name: string }>>([])
const vendors = ref<Array<{ id: number; name: string }>>([])

const dialogVisible = ref(false)
const isPrepay = ref(true)
const createForm = reactive({
  customerId: undefined as number | undefined,
  vendorId: undefined as number | undefined,
  amount: 0,
  txDate: new Date().toISOString().slice(0, 10),
  summary: '',
})

function fmtAmount(v: any) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function showCreate() {
  isPrepay.value = true
  Object.assign(createForm, { customerId: undefined, vendorId: undefined, amount: 0, txDate: new Date().toISOString().slice(0, 10), summary: '' })
  dialogVisible.value = true
}

function onTypeChange() {
  createForm.customerId = undefined
  createForm.vendorId = undefined
}

async function fetchData() {
  loading.value = true
  try {
    const res: any = await pagePrepayment(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function doCreate() {
  const data: any = {
    amount: createForm.amount,
    txDate: createForm.txDate,
    summary: createForm.summary,
  }
  if (isPrepay.value) {
    if (!createForm.customerId) { ElMessage.warning('请选择客户'); return }
    data.customerId = createForm.customerId
  } else {
    if (!createForm.vendorId) { ElMessage.warning('请选择供应商'); return }
    data.vendorId = createForm.vendorId
  }
  await createPrepayment(data)
  ElMessage.success('已创建')
  dialogVisible.value = false
  fetchData()
}

async function onConfirm(row: any) {
  await confirmPrepayment(row.id)
  ElMessage.success('已确认')
  fetchData()
}

async function onReverse(row: any) {
  const { value } = await ElMessageBox.prompt('请输入反冲原因', '反冲', { inputType: 'textarea' })
  await reversePrepayment(row.id, { reason: value || '人工反冲', userId: 0 })
  ElMessage.success('已反冲')
  fetchData()
}

onMounted(async () => {
  try {
    const [c, v] = await Promise.all([listCustomer(), listVendor()])
    customers.value = (c as any[]).map((x: any) => ({ id: x.id, name: x.name }))
    vendors.value = (v as any[]).map((x: any) => ({ id: x.id, name: x.name }))
  } catch { /* ignore */ }
  fetchData()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 16px; }
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 16px; }
.page-pagination { margin-top: 16px; text-align: right; }
</style>