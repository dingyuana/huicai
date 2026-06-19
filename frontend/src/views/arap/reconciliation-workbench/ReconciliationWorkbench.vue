<template>
  <div class="reconciliation-workbench">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">核销工作台</span>
        <el-button @click="onRefresh">刷新</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="银行账户">
          <el-select v-model="query.accountId" placeholder="选择账户" clearable style="width:240px" @change="onAccountChange">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName} (${a.accountNo})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对方名称">
          <el-input v-model="query.counterparty" placeholder="搜索对方名称" clearable style="width:180px" @clear="onSearch" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe @row-click="onRowClick" style="cursor:pointer">
        <el-table-column prop="txDate" label="日期" width="110" />
        <el-table-column label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.txType === 'INCOME' ? 'success' : 'warning'" size="small">
              {{ row.txType === 'INCOME' ? '收' : '支' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="counterAccount" label="对方名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="业务分类" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.classification === 'business_receipt' ? 'success' : 'primary'" size="small">
              {{ CLASSIFICATION_LABELS[row.classification] || row.classification }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="canShowRecommend(row) ? 'success' : 'warning'" size="small">
              {{ canShowRecommend(row) ? '已确认' : '待确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核销操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canShowRecommend(row)"
              text size="small" type="primary" @click.stop="onShowRecommend(row)">
              核销推荐
            </el-button>
            <el-tag v-else size="small" type="info">请先确认分类</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end"
        @change="fetchData"
      />
    </el-card>

    <!-- 核销推荐弹窗 -->
    <el-dialog v-model="recommendDialogVisible" :title="drawerTitle" width="650px" destroy-on-close>
      <template v-if="recommendLoading">
        <div style="text-align:center;padding:40px">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p style="margin-top:12px;color:#909399">正在匹配核销推荐...</p>
        </div>
      </template>

      <template v-else-if="recommendResult">
        <el-alert :title="recommendResult.message" :type="recommendResult.items?.length ? 'success' : 'info'" :closable="false" style="margin-bottom:16px" />

        <el-table v-if="recommendResult.items?.length" :data="recommendResult.items" border stripe size="small">
          <el-table-column label="发票类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.targetDocType === 'INVOICE_OUT' ? 'success' : 'warning'" size="small">
                {{ row.targetDocType === 'INVOICE_OUT' ? '销售发票' : '采购发票' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetDocNo" label="单据号" width="140" />
          <el-table-column label="原始金额" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.originalAmount) }}</template>
          </el-table-column>
          <el-table-column label="未核销金额" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.unsettledAmount) }}</template>
          </el-table-column>
          <el-table-column label="匹配级别" width="150" align="center">
            <template #default="{ row }">
              <el-tag :type="matchLevelType(row.matchLevel)" size="small" effect="plain">
                {{ MATCH_LEVEL_LABELS[row.matchLevel] || row.matchLevel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="建议核销" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.suggestedAmount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center">
            <template #default="{ row }">
              <el-button text size="small" type="primary" @click="onPreCheck(row)">
                预检查
              </el-button>
              <el-button text size="small" type="primary" @click="onExecuteRecon(row)">
                执行核销
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <p v-else style="text-align:center;color:#909399;padding:20px">暂无匹配的核销项</p>
      </template>
      <template #footer>
        <div style="display:flex;justify-content:space-between;align-items:center;width:100%">
          <span v-if="recommendResult" style="color:#909399;font-size:12px">
            共 {{ recommendResult.items?.length || 0 }} 项，其中精确匹配
            {{ countExactMatches() }} 项 (L1/L2/L3)
          </span>
          <div>
            <el-button @click="recommendDialogVisible = false">关闭</el-button>
            <el-button
              type="success"
              :disabled="countExactMatches() === 0 || batchReconciling"
              :loading="batchReconciling"
              @click="onBatchReconcile">
              自动核销精确匹配 ({{ countExactMatches() }})
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 核销预检查对话框 -->
    <el-dialog v-model="preCheckDialogVisible" title="核销预检查" width="480px" destroy-on-close>
      <template v-if="preCheckLoading">
        <div style="text-align:center;padding:20px">
          <el-icon class="is-loading" :size="28"><Loading /></el-icon>
          <p style="margin-top:8px;color:#909399">正在执行预检查...</p>
        </div>
      </template>
      <template v-else-if="preCheckResult">
        <el-alert :title="preCheckResult.allPassed ? '全部检查通过' : '存在未通过检查项'"
          :type="preCheckResult.allPassed ? 'success' : 'warning'" :closable="false" style="margin-bottom:16px" />
        <el-table :data="preCheckResult.checks" border stripe size="small">
          <el-table-column label="检查项" min-width="120">
            <template #default="{ row }">
              <span>{{ ({ sourceDocValid: '来源单据', invoiceValid: '目标单据', partyMatch: '客商一致', amountValid: '金额充足', periodValid: '期间正常' } as Record<string, string>)[(row as any).checkName as string] || (row as any).checkName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                {{ row.passed ? '通过' : '不通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="160">
            <template #default="{ row }">{{ row.message }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getBankStatementPage } from '@/api/modules/bankStatement'
import { getActiveBankAccounts, type BankAccountVO } from '@/api/modules/bankAccount'
import {
  getReconciliationRecommend, executeReconciliation, preCheckReconciliation,
  type RecommendItem, type ReconciliationRecommendResult, type PreCheckResult,
} from '@/api/modules/reconciliation'

const CLASSIFICATION_LABELS: Record<string, string> = {
  business_receipt: '业务收款',
  business_payment: '业务付款',
}

const MATCH_LEVEL_LABELS: Record<string, string> = {
  L1: 'L1 引用号匹配',
  L2: 'L2 发票号匹配',
  L3: 'L3 金额+日期匹配',
  L4: 'L4 金额精确匹配',
  L5: 'L5 容差匹配',
}

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const accounts = ref<BankAccountVO[]>([])

const query = ref<{ accountId?: number; counterparty?: string; current: number; size: number }>({
  current: 1, size: 20,
})

const recommendDialogVisible = ref(false)
const recommendLoading = ref(false)
const recommendResult = ref<ReconciliationRecommendResult | null>(null)
const drawerTitle = ref('')
const currentStatement = ref<any>(null)

const preCheckDialogVisible = ref(false)
const preCheckLoading = ref(false)
const preCheckResult = ref<PreCheckResult | null>(null)
const preCheckTarget = ref<RecommendItem | null>(null)

const batchReconciling = ref(false)

function matchLevelType(level: string) {
  switch (level) {
    case 'L1': return 'success'
    case 'L2': return 'primary'
    case 'L3': return 'primary'
    case 'L4': return 'warning'
    case 'L5': return 'danger'
    default: return 'info'
  }
}

function isConfirmed(status: string): boolean {
  return ['classified', 'voucher_generated', 'payment_created', 'approved', 'CONFIRMED'].includes(status || '')
}

function countExactMatches(): number {
  if (!recommendResult.value?.items) return 0
  return recommendResult.value.items.filter(
    (it) => it.matchLevel === 'L1' || it.matchLevel === 'L2' || it.matchLevel === 'L3'
  ).length
}

function buildPeriod(): string {
  const d = currentStatement.value?.txDate
  if (!d) {
    const now = new Date()
    return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`
  }
  // txDate 可能是 "2024-07-29" 或 ISO 格式
  const s = String(d)
  if (s.length >= 7) return s.substring(0, 4) + s.substring(5, 7)
  return `${new Date().getFullYear()}${String(new Date().getMonth() + 1).padStart(2, '0')}`
}

async function onBatchReconcile() {
  if (!currentStatement.value || !recommendResult.value?.items) return
  const exacts = recommendResult.value.items.filter(
    (it) => it.matchLevel === 'L1' || it.matchLevel === 'L2' || it.matchLevel === 'L3'
  )
  if (exacts.length === 0) {
    ElMessage.warning('没有可自动核销的精确匹配项')
    return
  }
  batchReconciling.value = true
  const period = buildPeriod()
  let okCount = 0
  let failCount = 0
  for (const item of exacts) {
    try {
      await executeReconciliation({
        sourceDocType: 'bank_txn',
        sourceDocId: currentStatement.value.id,
        targetDocType: item.targetDocType,
        targetDocId: item.targetDocId,
        amount: item.suggestedAmount,
        matchScore: item.matchScore,
        matchMethod: 'AUTO_BATCH',
        period,
      } as any)
      okCount++
    } catch (e: any) {
      failCount++
      console.warn(`自动核销失败: targetId=${item.targetDocId}`, e)
    }
  }
  batchReconciling.value = false
  if (failCount === 0) {
    ElMessage.success(`已自动核销 ${okCount} 项精确匹配`)
  } else {
    ElMessage.warning(`核销完成: 成功 ${okCount} 项, 失败 ${failCount} 项`)
  }
  recommendDialogVisible.value = false
  await fetchData()
}

function canShowRecommend(row: any): boolean {
  if (isConfirmed(row.reviewStatus)) return true
  if (row.generatedDocId || row.generatedVoucherId) return true
  return false
}

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    const params: any = {
      accountId: query.value.accountId,
      status: 'UNMATCHED',
      reviewStatus: 'classified,voucher_generated,payment_created,approved',
      current: query.value.current,
      size: query.value.size,
    }
    const res: any = await getBankStatementPage(params)
    list.value = (res.records || []).filter(
      (r: any) => r.classification === 'business_receipt' || r.classification === 'business_payment'
    )
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function onAccountChange() {
  query.value.current = 1
  await fetchData()
}

function onSearch() {
  query.value.current = 1
  fetchData()
}

function onReset() {
  query.value = { current: 1, size: 20 }
  fetchData()
}

async function onRefresh() {
  await fetchData()
}

async function onRowClick(row: any, column: any) {
  if (column?.type === 'selection') return
  if (canShowRecommend(row)) {
    await onShowRecommend(row)
  } else {
    ElMessage.info('请先在银行对账单页面确认该流水分类')
  }
}

async function onShowRecommend(row: any) {
  currentStatement.value = row
  drawerTitle.value = `核销推荐 — ${row.counterAccount || '未知对方'} ¥${fmtAmount(row.amount)}`
  recommendDialogVisible.value = true
  recommendLoading.value = true
  recommendResult.value = null
  try {
    recommendResult.value = await getReconciliationRecommend(row.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '获取核销推荐失败')
    recommendDialogVisible.value = false
  } finally {
    recommendLoading.value = false
  }
}

async function onExecuteRecon(item: RecommendItem) {
  if (!currentStatement.value) return
  try {
    await executeReconciliation({
      sourceDocType: 'bank_txn',
      sourceDocId: currentStatement.value.id,
      targetDocType: item.targetDocType,
      targetDocId: item.targetDocId,
      amount: item.suggestedAmount,
      matchScore: item.matchScore,
      matchMethod: 'AUTO',
      period: buildPeriod(),
    })
    ElMessage.success('核销执行成功')
    recommendDialogVisible.value = false
    await fetchData()
  } catch (e: any) {
    ElMessage.error(e?.message || '核销执行失败')
  }
}

async function onPreCheck(item: RecommendItem) {
  if (!currentStatement.value) return
  preCheckTarget.value = item
  preCheckResult.value = null
  preCheckLoading.value = true
  preCheckDialogVisible.value = true
  try {
    preCheckResult.value = await preCheckReconciliation({
      sourceDocType: 'bank_txn',
      sourceDocId: currentStatement.value.id,
      targetDocType: item.targetDocType,
      targetDocId: item.targetDocId,
      amount: item.suggestedAmount,
    })
  } catch (e: any) {
    ElMessage.error(e?.message || '预检查失败')
    preCheckDialogVisible.value = false
  } finally {
    preCheckLoading.value = false
  }
}

onMounted(async () => {
  try {
    accounts.value = await getActiveBankAccounts()
    if (accounts.value.length === 1) {
      query.value.accountId = accounts.value[0].id
    }
  } catch { /* ignore */ }
  await fetchData()
})
</script>

<style scoped>
.reconciliation-workbench .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
</style>
