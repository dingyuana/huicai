<template>
  <div class="bank-journal">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">银行日记账</span>
        <div>
          <el-button type="primary" @click="openCreate">新增分录</el-button>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="银行账户">
          <el-select v-model="query.accountId" placeholder="全部" clearable style="width:240px">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName} (${a.accountNo})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" clearable style="width:120px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.txType" placeholder="全部" clearable style="width:130px">
            <el-option v-for="(label, value) in TX_TYPE_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="txDate" label="日期" width="120" />
        <el-table-column prop="period" label="期间" width="80" align="center" />
        <el-table-column label="账户" min-width="200">
          <template #default="{ row }">{{ accountName(row.accountId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="txTypeTag(row.txType) as 'success' | 'warning' | 'info' | 'primary'" size="small">
              {{ TX_TYPE_LABELS[row.txType] || row.txType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="对账" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isReconciled ? 'success' : 'info'" size="small">
              {{ row.isReconciled ? '已对账' : '未对账' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭证ID" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.voucherId">#{{ row.voucherId }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" v-if="!row.voucherId" type="warning" @click="onGenerateVoucher(row as BankJournalVO)">生成凭证</el-button>
            <el-button text size="small" v-if="!row.isReconciled && !row.voucherId" @click="openEdit(row as BankJournalVO)">编辑</el-button>
            <el-popconfirm v-if="!row.isReconciled && !row.voucherId" title="确认删除?" @confirm="onDelete(row as BankJournalVO)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑日记账' : '新增日记账'" width="520" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100">
        <el-form-item label="银行账户" prop="accountId">
          <el-select v-model="form.accountId" placeholder="选择账户" style="width:100%">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName} (${a.accountNo})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" prop="txDate">
          <el-date-picker v-model="form.txDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="会计期间" prop="period">
          <el-input v-model="form.period" placeholder="YYYYMM" />
        </el-form-item>
        <el-form-item label="交易类型" prop="txType">
          <el-select v-model="form.txType" placeholder="选择类型" style="width:100%">
            <el-option v-for="(label, value) in TX_TYPE_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对方账户" prop="counterAccount">
          <el-input v-model="form.counterAccount" placeholder="对方名称/账号" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="0.01" style="width:100%" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getBankJournalPage, createBankJournal, updateBankJournal, deleteBankJournal,
  generateVoucherFromJournal, TX_TYPE_LABELS, type BankJournalVO,
} from '@/api/modules/bankJournal'
import { getActiveBankAccounts, type BankAccountVO } from '@/api/modules/bankAccount'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const list = ref<BankJournalVO[]>([])
const total = ref(0)
const accounts = ref<BankAccountVO[]>([])

const today = new Date().toISOString().slice(0, 10)
const currentPeriod = new Date().toISOString().slice(0, 7).replace('-', '')

const query = ref<{ accountId?: number; period?: string; txType?: string; current: number; size: number }>({
  current: 1, size: 20,
})
const form = ref({
  accountId: undefined as unknown as number,
  txDate: today,
  period: currentPeriod,
  txType: 'INCOME',
  counterAccount: '',
  amount: 0,
  summary: '',
})
const formRules = {
  accountId: [{ required: true, message: '请选择账户', trigger: 'change' }],
  txDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  period: [{ required: true, message: '请输入会计期间', trigger: 'blur' }],
  txType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
}

function accountName(id?: number) {
  const a = accounts.value.find((x) => x.id === String(id))
  return a ? `${a.accountName} (${a.accountNo})` : `#${id}`
}

function txTypeTag(t: string): 'success' | 'warning' | 'info' | 'primary' {
  switch (t) {
    case 'INCOME': return 'success'
    case 'EXPENSE': return 'warning'
    case 'TRANSFER_IN': return 'primary'
    case 'TRANSFER_OUT': return 'info'
    default: return 'info'
  }
}

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBankJournalPage(query.value)
    list.value = res.records
    total.value = res.total
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.value.current = 1
  fetchData()
}
function onReset() {
  query.value = { current: 1, size: 20 }
  fetchData()
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = {
    accountId: undefined as unknown as number,
    txDate: today,
    period: currentPeriod,
    txType: 'INCOME',
    counterAccount: '',
    amount: 0,
    summary: '',
  }
  dialogVisible.value = true
}

function openEdit(row: BankJournalVO) {
  isEdit.value = true
  editId.value = row.id
  form.value = {
    accountId: row.accountId,
    txDate: row.txDate,
    period: row.period,
    txType: row.txType,
    counterAccount: row.counterAccount || '',
    amount: row.amount,
    summary: row.summary || '',
  }
  dialogVisible.value = true
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateBankJournal(editId.value, form.value)
      ElMessage.success('修改成功')
    } else {
      await createBankJournal(form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await fetchData()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function onDelete(row: BankJournalVO) {
  await deleteBankJournal(row.id)
  ElMessage.success('删除成功')
  await fetchData()
}

async function onGenerateVoucher(row: BankJournalVO) {
  await generateVoucherFromJournal(row.id)
  ElMessage.success('凭证已生成, 请前往凭证管理提交记账')
  await fetchData()
}

onMounted(async () => {
  try {
    accounts.value = await getActiveBankAccounts()
  } catch {
    // ignore
  }
  await fetchData()
})
</script>

<style scoped>
.bank-journal .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
.page-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
