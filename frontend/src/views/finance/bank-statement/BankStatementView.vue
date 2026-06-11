<template>
  <div class="bank-statement">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">银行对账单</span>
        <el-button @click="fetchData">刷新</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="银行账户">
          <el-select v-model="query.accountId" placeholder="选择账户" clearable style="width:240px" @change="onSearch">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName} (${a.accountNo})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:140px" @change="onSearch">
            <el-option v-for="(label, value) in MATCH_STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-space style="margin-bottom: 12px">
        <el-button type="primary" @click="openImport">导入CSV</el-button>
        <el-button :disabled="!query.accountId" @click="onAutoMatch">智能匹配</el-button>
      </el-space>

      <h3 class="section-title">智能匹配建议</h3>
      <el-table :data="suggestions" v-loading="matchLoading" border size="small">
        <el-table-column prop="txDate" label="对账日期" width="120" />
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="counterAccount" label="对方" min-width="160" show-overflow-tooltip />
        <el-table-column label="建议日记账" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.matchedJournalId" type="success">#{{ row.matchedJournalId }}</el-tag>
            <el-tag v-else type="info">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="120" align="center">
          <template #default="{ row }">
            <el-progress :percentage="Math.min(100, row.score * 100)" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.matchedJournalId" text size="small" type="success" @click="onConfirm(row)">确认</el-button>
          </template>
        </el-table-column>
      </el-table>

      <h3 class="section-title">对账单记录</h3>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="txDate" label="日期" width="120" />
        <el-table-column prop="txType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.txType === 'INCOME' ? 'success' : 'warning'" size="small">
              {{ row.txType === 'INCOME' ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="counterAccount" label="对方" min-width="160" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="匹配状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.matchStatus) as 'success' | 'warning' | 'info' | 'primary' | 'danger'" size="small">
              {{ MATCH_STATUS_LABELS[row.matchStatus] || row.matchStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匹配日记账" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.matchedJournalId">#{{ row.matchedJournalId }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.matchStatus === 'UNMATCHED'" text size="small" type="danger" @click="onIgnore(row)">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="importDialogVisible" title="导入CSV对账单" width="640" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom:12px">
        格式: 日期,类型(收/支),金额,对方,摘要 (每行一条,首行可为表头)
      </el-alert>
      <el-input v-model="csvContent" type="textarea" :rows="10" placeholder="2026-06-01,收,1000.00,客户A,货款&#10;2026-06-02,支,500.00,供应商B,采购款" />
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="onImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getBankStatementPage, importStatementCsv, autoMatchStatements,
  confirmStatementMatch, ignoreStatement, MATCH_STATUS_LABELS, type BankStatementVO, type MatchSuggestion,
} from '@/api/modules/bankStatement'
import { getActiveBankAccounts, type BankAccountVO } from '@/api/modules/bankAccount'

const loading = ref(false)
const matchLoading = ref(false)
const importing = ref(false)
const list = ref<BankStatementVO[]>([])
const suggestions = ref<MatchSuggestion[]>([])
const accounts = ref<BankAccountVO[]>([])
const importDialogVisible = ref(false)
const csvContent = ref('')

const query = ref<{ accountId?: number; status?: string; current: number; size: number }>({
  current: 1, size: 20,
})

function statusType(s: string) {
  switch (s) {
    case 'MATCHED': return 'success'
    case 'MANUAL_MATCHED': return 'primary'
    case 'IGNORED': return 'info'
    default: return 'warning'
  }
}

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBankStatementPage(query.value)
    list.value = res.records
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

function openImport() {
  if (!query.value.accountId) {
    ElMessage.warning('请先选择银行账户')
    return
  }
  csvContent.value = ''
  importDialogVisible.value = true
}

async function onImport() {
  if (!csvContent.value.trim()) {
    ElMessage.warning('CSV内容不能为空')
    return
  }
  importing.value = true
  try {
    const n = await importStatementCsv(query.value.accountId!, csvContent.value)
    ElMessage.success(`导入 ${n} 条对账单`)
    importDialogVisible.value = false
    await fetchData()
  } catch {
    // handled
  } finally {
    importing.value = false
  }
}

async function onAutoMatch() {
  if (!query.value.accountId) {
    ElMessage.warning('请先选择银行账户')
    return
  }
  matchLoading.value = true
  try {
    suggestions.value = await autoMatchStatements(query.value.accountId)
    ElMessage.success(`生成 ${suggestions.value.length} 条匹配建议`)
  } catch {
    // handled
  } finally {
    matchLoading.value = false
  }
}

async function onConfirm(row: MatchSuggestion) {
  await confirmStatementMatch(row.statementId, row.matchedJournalId!)
  ElMessage.success('已确认匹配')
  await Promise.all([fetchData(), onAutoMatch()])
}

async function onIgnore(row: BankStatementVO) {
  await ignoreStatement(row.id)
  ElMessage.success('已忽略')
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
.bank-statement .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
.section-title { margin: 16px 0 12px; font-size: 14px; font-weight: 600; }
</style>
