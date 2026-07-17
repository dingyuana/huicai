<template>
  <div class="input-invoice">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">进项发票</span>
        <el-button type="primary" @click="openEdit()">新增发票</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="供应商">
          <el-input v-model="query.vendorName" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" clearable />
        </el-form-item>
        <el-form-item label="认证状态">
          <el-select v-model="query.certStatus" clearable placeholder="全部" style="width:130px">
            <el-option v-for="o in CERT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:130px">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="invoiceNo" label="发票号" width="180" />
        <el-table-column prop="invoiceDate" label="开票日期" width="120" />
        <el-table-column prop="vendorName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="80" align="center">
          <template #default="{ row }">{{ Number(row.taxRate).toFixed(2) }}%</template>
        </el-table-column>
        <el-table-column label="审核状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG_MAP[row.status] || 'info') as any" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="认证状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(CERT_TAG_MAP[row.certificationStatus] || 'info') as any" size="small">
              {{ CERT_MAP[row.certificationStatus] || row.certificationStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_CONFIRM'">
              <el-button link type="primary" size="small" @click="doAction(row, 'submitReview')">提交审核</el-button>
              <el-button link type="danger" size="small" @click="doAction(row, 'void')">作废</el-button>
            </template>
            <template v-else-if="row.status === 'PENDING_REVIEW'">
              <el-button link type="primary" size="small" @click="doAction(row, 'confirm')">通过</el-button>
              <el-button link type="warning" size="small" @click="doAction(row, 'reject')">驳回</el-button>
              <el-button link type="danger" size="small" @click="doAction(row, 'void')">作废</el-button>
            </template>
            <template v-else-if="row.status === 'CONFIRMED'">
              <el-button link type="primary" size="small" @click="doAction(row, 'genVoucher')">生成凭证</el-button>
              <el-button link type="warning" size="small" @click="doAction(row, 'revert')">回退</el-button>
              <el-button link type="danger" size="small" @click="doAction(row, 'void')">作废</el-button>
              <el-button link type="danger" size="small" @click="doAction(row, 'reverse')">红冲</el-button>
            </template>
            <template v-else-if="row.status === 'VOUCHERED' || row.status === 'PARTIALLY_RECONCILED'">
              <el-tag size="small" type="success">已生成凭证</el-tag>
              <el-button link type="danger" size="small" @click="doAction(row, 'reverse')">红冲</el-button>
            </template>
            <template v-else-if="row.status === 'FULLY_RECONCILED'">
              <el-tag size="small" type="success">已核销</el-tag>
            </template>
            <template v-else-if="row.status === 'VOIDED'">
              <el-tag size="small" type="danger">已作废</el-tag>
            </template>
            <el-button v-if="row.certificationStatus === 'UNCERTIFIED' && row.status !== 'VOIDED'"
              link type="primary" size="small" @click="onCertify(row)">认证</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增进项发票" width="640px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="发票号" prop="invoiceNo"><el-input v-model="form.invoiceNo" /></el-form-item>
        <el-form-item label="开票日期" prop="invoiceDate">
          <el-date-picker v-model="form.invoiceDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="供应商" prop="vendorName"><el-input v-model="form.vendorName" /></el-form-item>
        <el-form-item label="金额(不含税)" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" @change="recalcTax" />
        </el-form-item>
        <el-form-item label="税率" prop="taxRate">
          <el-select v-model="form.taxRate" style="width:100%" @change="recalcTax">
            <el-option :value="13" label="13%" />
            <el-option :value="9" label="9%" />
            <el-option :value="6" label="6%" />
            <el-option :value="3" label="3%" />
            <el-option :value="0" label="0%" />
          </el-select>
        </el-form-item>
        <el-form-item label="税额">
          <el-input-number v-model="form.taxAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="发票类型" prop="invoiceType">
          <el-select v-model="form.invoiceType" style="width:100%">
            <el-option label="增值税专用发票" value="SPECIAL" />
            <el-option label="普通发票" value="PLAIN" />
            <el-option label="海关缴款书" value="CUSTOMS" />
            <el-option label="运输发票" value="TRANSPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  pageInputInvoice, createInputInvoice, certifyInputInvoice,
  submitInputReview, confirmInputInvoice, rejectInputInvoice, revertInputInvoice, voidInputInvoice, reverseInputInvoice,
} from '@/api/modules/tax'

const CERT_OPTIONS = [
  { value: 'UNCERTIFIED', label: '未认证' },
  { value: 'CERTIFIED', label: '已认证' },
  { value: 'INVALID', label: '无效' },
  { value: 'CANCELLED', label: '已注销' },
]
const CERT_MAP: Record<string, string> = Object.fromEntries(CERT_OPTIONS.map((o) => [o.value, o.label]))
const CERT_TAG_MAP: Record<string, string> = {
  UNCERTIFIED: 'warning', CERTIFIED: 'success', INVALID: 'danger', CANCELLED: 'info',
}

const STATUS_OPTIONS = [
  { value: 'PENDING_CONFIRM', label: '待确认' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'VOUCHERED', label: '已生成凭证' },
  { value: 'FULLY_RECONCILED', label: '已核销' },
  { value: 'PARTIALLY_RECONCILED', label: '部分核销' },
  { value: 'VOIDED', label: '已作废' },
]
const STATUS_MAP: Record<string, string> = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label]))
const STATUS_TAG_MAP: Record<string, string> = {
  PENDING_CONFIRM: 'warning', PENDING_REVIEW: 'info', CONFIRMED: 'success',
  VOUCHERED: '', FULLY_RECONCILED: 'success', PARTIALLY_RECONCILED: '',
  VOIDED: 'danger',
}

const query = reactive({ vendorName: '', period: '', certStatus: '', status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ invoiceType: 'SPECIAL', taxRate: 13 })
const rules = {
  invoiceNo: [{ required: true, message: '请输入发票号', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
  vendorName: [{ required: true, message: '请输入供应商', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  taxRate: [{ required: true, message: '请选择税率', trigger: 'change' }],
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const recalcTax = () => {
  if (form.amount && form.taxRate != null) {
    form.taxAmount = Number((form.amount * form.taxRate / 100).toFixed(2))
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageInputInvoice(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const openEdit = () => {
  Object.assign(form, {
    id: undefined, invoiceNo: '', invoiceDate: '', vendorName: '',
    amount: 0, taxRate: 13, taxAmount: 0, invoiceType: 'SPECIAL', remark: '',
  })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await createInputInvoice(form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchData()
  })
}

const onCertify = async (row: any) => {
  await certifyInputInvoice(row.id)
  ElMessage.success('已认证')
  fetchData()
}

const doAction = async (row: any, action: string) => {
  const id = row?.id
  if (!id) return
  const label = ({ submitReview: '提交审核', confirm: '审核通过', reject: '驳回', revert: '回退', void: '作废', reverse: '红冲', genVoucher: '手工生成凭证' } as any)[action] || action

  if (action === 'reject' || action === 'void' || action === 'reverse') {
    const { value: reason } = await ElMessageBox.prompt(
      `请输入${label}原因`, label, { inputType: 'textarea', inputValidator: (v: string) => !!v?.trim(), inputErrorMessage: '原因不能为空' }
    ).catch(() => ({ value: null }))
    if (!reason) return
    try {
      if (action === 'reject') await rejectInputInvoice(id, reason)
      else if (action === 'reverse') await reverseInputInvoice(id, reason)
      else await voidInputInvoice(id, reason)
      ElMessage.success(`${label}成功`)
      fetchData()
    } catch { /* backend handles error msg */ }
    return
  }

  try {
    if (action === 'submitReview') await submitInputReview(id)
    else if (action === 'confirm') await confirmInputInvoice(id)
    else if (action === 'revert') await revertInputInvoice(id)
    ElMessage.success(`${label}成功`)
    fetchData()
  } catch { /* backend handles error msg */ }
}

onMounted(fetchData)
</script>
