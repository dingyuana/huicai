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
        <el-form-item label="会计期间" prop="period">
          <el-input v-model="form.period" placeholder="YYYYMM" style="width:140px" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="0.01" style="width:160px" />
        </el-form-item>
        <el-form-item v-if="showCustomer" label="客户">
          <el-select v-model="form.customerId" filterable clearable placeholder="选择客户" style="width:240px">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="showSupplier" label="供应商">
          <el-select v-model="form.supplierId" filterable clearable placeholder="选择供应商" style="width:240px">
            <el-option v-for="v in suppliers" :key="v.id" :label="v.name" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="form.summary" placeholder="单据摘要" style="width:340px" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <el-divider>分录明细</el-divider>
      <el-table :data="form.entries" border style="width:100%">
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column label="费用类别" width="160">
          <template #default="{ row }">
            <el-input v-model="row.expenseType" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="科目" min-width="240">
          <template #default="{ row }">
            <el-tree-select
              v-model="row.subjectId"
              :data="leafSubjectOptions"
              :props="({ value: 'id', label: 'name' } as any)"
              check-strictly
              :render-after-expand="false"
              placeholder="选择末级科目"
              style="width:100%"
            />
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
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getBusinessDoc, createBusinessDoc, updateBusinessDoc, submitBusinessDoc,
  DOC_TYPE_LABELS, CUSTOMER_DOC_TYPES, SUPPLIER_DOC_TYPES,
  type BusinessDocDTO, type BusinessDocEntry,
} from '@/api/modules/businessDoc'
import { getSubjectTree, type SubjectVO } from '@/api/modules/subject'
import { listCustomer, listVendor } from '@/api/modules/arap'

const route = useRoute()
const router = useRouter()

const mode = (route.query.mode as string) || 'create'
const editId = route.query.id ? Number(route.query.id) : null
const isEdit = mode === 'edit' && editId != null

const saving = ref(false)
const formRef = ref<FormInstance>()
const subjectTree = ref<SubjectVO[]>([])
const customers = ref<Array<{id: number; name: string}>>([])
const suppliers = ref<Array<{id: number; name: string}>>([])

const showCustomer = computed(() => CUSTOMER_DOC_TYPES.includes(form.value.docType))
const showSupplier = computed(() => SUPPLIER_DOC_TYPES.includes(form.value.docType))

const today = new Date().toISOString().slice(0, 10)
const currentPeriod = new Date().toISOString().slice(0, 7).replace('-', '')

const form = ref<BusinessDocDTO>({
  docType: 'PAYMENT',
  docDate: today,
  period: currentPeriod,
  amount: 0,
  summary: '',
  entries: [
    { expenseType: '', subjectId: undefined as unknown as number, amount: 0, invoiceNo: '', summary: '' },
  ],
})

const formRules = {
  docType: [{ required: true, message: '请选择单据类型', trigger: 'change' }],
  docDate: [{ required: true, message: '请选择单据日期', trigger: 'change' }],
  period: [{ required: true, message: '请输入会计期间', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
}

const leafSubjectOptions = computed(() => {
  const list: SubjectVO[] = []
  const walk = (nodes: SubjectVO[]) => {
    for (const n of nodes) {
      if (n.isLeaf) list.push(n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(subjectTree.value)
  return list
})

const totalAmount = computed(() =>
  form.value.entries.reduce((s, e) => s + (Number(e.amount) || 0), 0))

function fmtAmount(v: number) {
  return (v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function addEntry() {
  form.value.entries.push({ expenseType: '', subjectId: undefined as unknown as number, amount: 0, invoiceNo: '', summary: '' })
}

function removeEntry(i: number) {
  form.value.entries.splice(i, 1)
}

function goBack() {
  router.push({ name: 'BusinessDocList' })
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
    entries: d.entries || [],
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
      entries: form.value.entries.map((e, i) => ({
        subjectId: e.subjectId,
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
    const [cust, vend] = await Promise.all([listCustomer(), listVendor()])
    customers.value = (cust as any[]).map((c: any) => ({ id: c.id, name: c.name }))
    suppliers.value = (vend as any[]).map((v: any) => ({ id: v.id, name: v.name }))
  } catch { /* ignore */ }
}

watch(() => form.value.docType, () => {
  if (showCustomer.value) form.value.supplierId = undefined
  if (showSupplier.value) form.value.customerId = undefined
})

onMounted(async () => {
  try {
    subjectTree.value = await getSubjectTree()
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
