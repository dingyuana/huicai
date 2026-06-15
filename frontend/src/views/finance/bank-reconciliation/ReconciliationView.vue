<template>
  <div class="reconciliation">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">银行对账 / 余额调节表</span>
        <div>
          <el-button :loading="running" @click="onRunMatching">运行自动匹配</el-button>
          <el-button type="primary" @click="onLoad">加载对账数据</el-button>
        </div>
      </div>

      <el-form :model="query" inline>
        <el-form-item label="银行账户">
          <el-select v-model="query.accountId" placeholder="选择账户" style="width:240px">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName} (${a.accountNo})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:160px" />
        </el-form-item>
      </el-form>

      <h3 class="section-title">对账汇总</h3>
      <el-row :gutter="16" v-if="summary" style="margin-bottom:12px">
        <el-col :span="6">
          <el-statistic title="企业日记账总数" :value="summary.enterpriseTotal" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已对账" :value="summary.enterpriseReconciled" :value-style="{ color: '#67c23a' }" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="未对账" :value="summary.enterpriseUnreconciled" :value-style="{ color: '#e6a23c' }" />
        </el-col>
      </el-row>
      <el-row :gutter="16" v-if="summary">
        <el-col :span="6">
          <el-statistic title="对账单总数" :value="summary.statementTotal" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已匹配" :value="summary.statementMatched" :value-style="{ color: '#67c23a' }" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="待确认" :value="summary.statementPendingConfirm" :value-style="{ color: '#909399' }" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="未匹配" :value="summary.statementUnmatched" :value-style="{ color: '#e6a23c' }" />
        </el-col>
      </el-row>

      <h3 class="section-title">余额调节表</h3>
      <el-descriptions v-if="adjustment" :column="1" border>
        <el-descriptions-item label="账户">{{ adjustment.accountName }} ({{ adjustment.accountNo }})</el-descriptions-item>
        <el-descriptions-item label="期间">{{ adjustment.period }}</el-descriptions-item>
        <el-descriptions-item label="企业日记账余额">{{ fmtAmount(adjustment.enterpriseBalance) }}</el-descriptions-item>
        <el-descriptions-item label="银行对账单余额">{{ fmtAmount(adjustment.bankBalance) }}</el-descriptions-item>
        <el-descriptions-item label="差额">
          <span :class="adjustment.balanced ? 'balanced' : 'unbalanced'" style="font-weight:600">
            {{ fmtAmount(adjustment.diff) }} {{ adjustment.balanced ? '✓ 平衡' : '✗ 不平衡 (有未达账项)' }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">未达账项 (4方向)</h3>
      <el-table :data="unmatched" v-loading="loading" border stripe>
        <el-table-column label="方向" width="180" align="center">
          <template #default="{ row }">
            <el-tag :type="unmatchedTypeTag(row.type)" size="small">
              {{ unmatchedTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="txDate" label="日期" width="120" />
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="counterAccount" label="对方" min-width="160" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-card>

    <!-- 自动匹配结果弹窗 -->
    <el-dialog v-model="matchDialogVisible" title="自动匹配结果" width="640px" destroy-on-close>
      <el-alert :title="'共处理 ' + matchResults.length + ' 笔流水'" :type="'info'" :closable="false" style="margin-bottom:16px" />
      <el-table :data="matchResults" border stripe size="small" max-height="400">
        <el-table-column label="匹配状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.matchStatus === 'MATCHED' ? 'success' : row.matchStatus === 'PENDING_CONFIRM' ? 'warning' : 'info'" size="small">
              {{ ({ MATCHED: '自动匹配', PENDING_CONFIRM: '待确认', UNMATCHED: '未匹配' } as Record<string, string>)[(row as any).matchStatus] || (row as any).matchStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="流水ID" prop="statementId" width="80" align="center" />
        <el-table-column label="日记账ID" width="80" align="center">
          <template #default="{ row }">{{ row.journalId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="分数" width="70" align="center">
          <template #default="{ row }">
            <span :style="{ fontWeight: 600, color: row.score >= 85 ? '#67c23a' : row.score >= 60 ? '#e6a23c' : '#909399' }">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAdjustment, getReconciliationSummary, getUnmatchedItems, runMatching,
  type Adjustment, type ReconciliationSummary, type UnmatchedItem, type MatchResult,
} from '@/api/modules/bankReconciliation'
import { getActiveBankAccounts, type BankAccountVO } from '@/api/modules/bankAccount'

const loading = ref(false)
const running = ref(false)
const accounts = ref<BankAccountVO[]>([])
const query = ref({ accountId: undefined as unknown as number, period: new Date().toISOString().slice(0, 7).replace('-', '') })
const adjustment = ref<Adjustment | null>(null)
const summary = ref<ReconciliationSummary | null>(null)
const unmatched = ref<UnmatchedItem[]>([])
const matchDialogVisible = ref(false)
const matchResults = ref<MatchResult[]>([])

const UNMATCHED_TYPE_LABELS: Record<string, string> = {
  BANK_RECEIPT_ENTERPRISE_NOT: '银行已收企业未记',
  BANK_PAYMENT_ENTERPRISE_NOT: '银行已付企业未记',
  ENTERPRISE_RECEIPT_BANK_NOT: '企业已收银行未记',
  ENTERPRISE_PAYMENT_BANK_NOT: '企业已付银行未记',
}

function unmatchedTypeLabel(type: string) {
  return UNMATCHED_TYPE_LABELS[type] || type
}

function unmatchedTypeTag(type: string) {
  switch (type) {
    case 'BANK_RECEIPT_ENTERPRISE_NOT': return 'success'
    case 'BANK_PAYMENT_ENTERPRISE_NOT': return 'danger'
    case 'ENTERPRISE_RECEIPT_BANK_NOT': return 'primary'
    case 'ENTERPRISE_PAYMENT_BANK_NOT': return 'warning'
    default: return 'info'
  }
}

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function onLoad() {
  if (!query.value.accountId) {
    ElMessage.warning('请选择银行账户')
    return
  }
  loading.value = true
  try {
    const [a, s, u] = await Promise.all([
      getAdjustment(query.value.accountId, query.value.period),
      getReconciliationSummary(query.value.accountId, query.value.period),
      getUnmatchedItems(query.value.accountId, query.value.period),
    ])
    adjustment.value = a
    summary.value = s
    unmatched.value = u
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

async function onRunMatching() {
  if (!query.value.accountId) {
    ElMessage.warning('请先选择银行账户和期间')
    return
  }
  running.value = true
  try {
    const results = await runMatching(query.value.accountId, query.value.period)
    matchResults.value = results
    matchDialogVisible.value = true
    // 刷新汇总和未达账项
    await onLoad()
    ElMessage.success('自动匹配完成, 处理 ' + results.length + ' 笔流水')
  } catch (e: any) {
    ElMessage.error(e?.message || '自动匹配失败')
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  try {
    accounts.value = await getActiveBankAccounts()
  } catch {
    // ignore
  }
})
</script>

<style scoped>
.reconciliation .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.section-title { margin: 20px 0 12px; font-size: 14px; font-weight: 600; }
.balanced { color: #67c23a; }
.unbalanced { color: #f56c6c; }
</style>
