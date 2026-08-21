<template>
  <div class="doc-edit">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑单据' : '新增单据' }}</span>
        <div>
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="saving" @click="onSave(false)">保存草稿</el-button>
          <el-button type="success" :loading="saving" @click="onSave(true)">保存并提交</el-button>
        </div>
      </div>

      <el-form :model="form" :rules="formRules" ref="formRef" label-width="100" inline>
        <el-form-item label="单据类型" prop="docType">
          <el-select v-model="form.docType" placeholder="选择类型" style="width:160px">
            <el-option v-for="(label, value) in DOC_TYPE_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="单据日期" prop="docDate">
          <el-date-picker v-model="form.docDate" type="date" value-format="YYYY-MM-DD" style="width:160px" />
        </el-form-item>
        <el-form-item label="会计期间">
          <el-input :value="autoPeriod" placeholder="YYYYMM" style="width:140px" disabled />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="0.01" style="width:160px" />
        </el-form-item>
        <el-form-item v-if="showCustomer" label="客户">
          <el-autocomplete
            v-model="customerQuery"
            :fetch-suggestions="(val, cb) => searchCustomer(val, cb)"
            placeholder="选择或输入客户"
            clearable
            style="width:240px"
            @select="(item: any) => selectCustomer(item)"
          />
        </el-form-item>
        <el-form-item v-if="showSupplier" label="供应商">
          <el-autocomplete
            v-model="supplierQuery"
            :fetch-suggestions="(val, cb) => searchVendor(val, cb)"
            placeholder="选择或输入供应商"
            clearable
            style="width:240px"
            @select="(item: any) => selectSupplier(item)"
          />
        </el-form-item>
        <el-form-item v-if="showSettlementAccount" label="结算账户">
          <el-select v-model="form.settlementAccountId" filterable clearable placeholder="选择结算账户" style="width:240px">
            <el-option v-for="ba in bankAccounts" :key="ba.id" :label="`${ba.accountName} ${ba.accountNo}`" :value="ba.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="form.summary" placeholder="单据摘要" style="width:340px" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <el-divider>分录明细</el-divider>
      <el-table :data="form.entries" border style="width:100%">
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column label="方向/费用类别" width="160">
          <template #default="{ row }">
            <template v-if="isTransfer">
              <el-select v-model="row.expenseType" placeholder="选择方向" style="width:100%">
                <el-option value="debit" label="借方（转入）" />
                <el-option value="credit" label="贷方（转出）" />
              </el-select>
            </template>
            <template v-else>
              <el-input v-model="row.expenseType" placeholder="可选" />
            </template>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" :min="0" :precision="2" :step="0.01" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="发票号" width="180">
          <template #default="{ row }">
            <el-input v-model="row.invoiceNo" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="分录摘要" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.summary" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button text type="danger" size="small" @click="removeEntry($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px">
        <el-button @click="addEntry">添加分录</el-button>
      </div>

      <div class="balance-summary">
        <span>分录合计: <b class="num">{{ fmtAmount(totalAmount) }}</b></span>
        <span>单据金额: <b class="num">{{ fmtAmount(Number(form.amount) || 0) }}</b></span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getBusinessDoc, createBusinessDoc, updateBusinessDoc, submitBusinessDoc,
  DOC_TYPE_LABELS, CUSTOMER_DOC_TYPES, SUPPLIER_DOC_TYPES,
  type BusinessDocDTO, type BusinessDocEntry,
} from '@/api/modules/businessDoc'
import { listCustomer, listVendor, createCustomer, createVendor } from '@/api/modules/arap'
import { getActiveBankAccounts, type BankAccountVO } from '@/api/modules/bankAccount'

const route = useRoute()
const router = useRouter()

const mode = (route.query.mode as string) || 'create'
const editId = route.query.id ? Number(route.query.id) : null
const isEdit = mode === 'edit' && editId != null

const saving = ref(false)
const formRef = ref<FormInstance>()
const customers = ref<Array<{id: number; name: string}>>([])
const suppliers = ref<Array<{id: number; name: string}>>([])
const customerQuery = ref('')
const supplierQuery = ref('')
const bankAccounts = ref<BankAccountVO[]>([])

// 单据类型显示控制
const isTransfer = computed(() => form.value.docType === 'TRANSFER')
const showCustomer = computed(() => CUSTOMER_DOC_TYPES.includes(form.value.docType))
const showSupplier = computed(() => SUPPLIER_DOC_TYPES.includes(form.value.docType))
// 结算账户: 涉及资金流水的单据需要选账户
const SHOW_SETTLEMENT = ['RECEIPT', 'PAYMENT', 'EXPENSE', 'INVOICE_IN', 'INVOICE_OUT', 'SALARY']
const showSettlementAccount = computed(() => SHOW_SETTLEMENT.includes(form.value.docType))

const today = new Date().toISOString().slice(0, 10)
const currentPeriod = new Date().toISOString().slice(0, 7).replace('-', '')

const form = ref<BusinessDocDTO>({
  docType: 'PAYMENT',
  docDate: today,
  period: currentPeriod,
  amount: 0,
  summary: '',
  entries: [
    { expenseType: '', amount: 0, invoiceNo: '', summary: '' },
  ],
})

const formRules = {
  docType: [{ required: true, message: '请选择单据类型', trigger: 'change' }],
  docDate: [{ required: true, message: '请选择单据日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
}

// 会计期间: 从 docDate 自动计算，防止用户输错
const autoPeriod = computed(() => {
  if (!form.value.docDate) return ''
  const d = form.value.docDate
  return d.slice(0, 4) + d.slice(5, 7)
})

const totalAmount = computed(() =>
  form.value.entries.reduce((s, e) => s + (Number(e.amount) || 0), 0))

function fmtAmount(v: number) {
  return (v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function addEntry() {
  form.value.entries.push({ expenseType: '', amount: 0, invoiceNo: '', summary: '' })
}

function removeEntry(i: number) {
  form.value.entries.splice(i, 1)
}

function goBack() {
  router.push({ name: 'BusinessDocList' })
}

// ─── 客户/供应商即输即建 ───

async function searchCustomer(val: string, cb: any) {
  if (!val) { cb([]); return }
  try {
    const list = customers.value
    const results = list.filter(c => c.name.includes(val))
    // 无匹配则显示"+ 新增"提示
    cb(results.length === 0 && val.trim() ? [{ value: `+ 新增客户：${val}`, name: val, isNew: true }] : results)
  } catch { cb([]) }
}

async function searchVendor(val: string, cb: any) {
  if (!val) { cb([]); return }
  try {
    const list = suppliers.value
    const results = list.filter(v => v.name.includes(val))
    cb(results.length === 0 && val.trim() ? [{ value: `+ 新增供应商：${val}`, name: val, isNew: true }] : results)
  } catch { cb([]) }
}

async function selectCustomer(item: any) {
  if (item.isNew) {
    try {
      await ElMessageBox.confirm(`是否创建新客户「${item.name}」？`, '即输即建')
      const created = await createCustomer({
        code: 'AUTO-' + Date.now(),
        name: item.name,
        isActive: true,
      } as any)
      customers.value.push({ id: created.id, name: created.name })
      form.value.customerId = created.id
      customerQuery.value = created.name
      ElMessage.success(`客户「${created.name}」已创建`)
    } catch { /* cancelled */ }
  } else {
    form.value.customerId = item.id
    customerQuery.value = item.name
  }
}

async function selectSupplier(item: any) {
  if (item.isNew) {
    try {
      await ElMessageBox.confirm(`是否创建新供应商「${item.name}」？`, '即输即建')
      const created = await createVendor({
        code: 'AUTO-' + Date.now(),
        name: item.name,
        isActive: true,
      } as any)
      suppliers.value.push({ id: created.id, name: created.name })
      form.value.supplierId = created.id
      supplierQuery.value = created.name
      ElMessage.success(`供应商「${created.name}」已创建`)
    } catch { /* cancelled */ }
  } else {
    form.value.supplierId = item.id
    supplierQuery.value = item.name
  }
}

async function loadDoc() {
  if (!editId) return
  const d = await getBusinessDoc(editId)
  form.value = {
    id: d.id,
    docType: d.docType,
    docDate: d.docDate,
    period: d.period,
    amount: d.amount,
    customerId: d.customerId,
    supplierId: d.supplierId,
    applicantId: d.applicantId,
    deptId: d.deptId,
    summary: d.summary || '',
    attachmentIds: d.attachmentIds,
    settlementAccountId: d.settlementAccountId,
    entries: d.entries || [],
  }
  // 回填客户/供应商名称
  if (d.customerId && d.customerName) {
    customerQuery.value = d.customerName
    if (!customers.value.find(c => c.id === d.customerId)) {
      customers.value.push({ id: d.customerId!, name: d.customerName! })
    }
  }
  if (d.supplierId && d.supplierName) {
    supplierQuery.value = d.supplierName
    if (!suppliers.value.find(v => v.id === d.supplierId)) {
      suppliers.value.push({ id: d.supplierId!, name: d.supplierName! })
    }
  }
}

async function onSave(submitAfter: boolean) {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!form.value.entries.length) {
    ElMessage.error('至少添加一条分录')
    return
  }
  saving.value = true
  try {
    const dto: BusinessDocDTO = {
      ...form.value,
      period: autoPeriod.value,
      entries: form.value.entries.map((e, i) => ({
        amount: e.amount,
        expenseType: e.expenseType,
        invoiceNo: e.invoiceNo,
        summary: e.summary,
        sortOrder: e.sortOrder ?? i + 1,
      })),
    }
    let id: number
    if (isEdit) {
      const r = await updateBusinessDoc(editId!, dto)
      id = r.id
    } else {
      const r = await createBusinessDoc(dto)
      id = r.id
    }
    if (submitAfter) {
      await submitBusinessDoc(id)
      ElMessage.success('已保存并提交')
    } else {
      ElMessage.success('保存成功')
    }
    router.push({ name: 'BusinessDocList' })
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function loadPartyOptions() {
  try {
    const [cust, vend, bks] = await Promise.all([listCustomer(), listVendor(), getActiveBankAccounts()])
    customers.value = (cust as any[]).map((c: any) => ({ id: c.id, name: c.name }))
    suppliers.value = (vend as any[]).map((v: any) => ({ id: v.id, name: v.name }))
    bankAccounts.value = (bks as BankAccountVO[]) || []
  } catch { /* ignore */ }
}

watch(() => form.value.docType, () => {
  if (showCustomer.value) form.value.supplierId = undefined
  if (showSupplier.value) form.value.customerId = undefined
  // 切换到不需要结算账户的类型时清空
  if (!showSettlementAccount.value) form.value.settlementAccountId = undefined
})

onMounted(async () => {
  try {
    await loadPartyOptions()
  } catch {
    // ignore
  }
  if (isEdit) await loadDoc()
})
</script>

<style scoped>
.doc-edit .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.balance-summary {
  margin-top: 12px;
  padding: 10px 16px;
  background: #f5f7fa;
  display: flex;
  gap: 32px;
  font-size: 14px;
}
.balance-summary .num { font-weight: 600; margin-left: 4px; }
</style>
