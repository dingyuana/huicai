<template>
  <div class="workbench-panel">
    <div class="panel-toolbar">
      <el-radio-group v-model="activeTab" @change="onTabChange">
        <el-radio-button value="RECEIPT">收款单</el-radio-button>
        <el-radio-button value="PAYMENT">付款单</el-radio-button>
      </el-radio-group>
      <el-space>
        <el-button type="primary" @click="onAutoFifo" :disabled="!list.length" :loading="fifoLoading">
          <el-icon><Refresh /></el-icon> 自动核销
        </el-button>
      </el-space>
    </div>

    <el-alert title="P1: 核销需提报后由人工审批，审批通过方可生效" type="info" show-icon :closable="false" style="margin-bottom:12px" />

    <el-form :model="query" inline class="filter-form">
      <el-form-item label="对方名称">
        <el-input v-model="query.keyword" placeholder="搜索客户/供应商" clearable style="width:200px" />
      </el-form-item>
      <el-form-item label="期间">
        <el-input v-model="query.period" placeholder="YYYYMM" style="width:100px" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe @row-click="onRowClick" style="cursor:pointer">
      <el-table-column prop="docNo" label="单据号" width="160" />
      <el-table-column prop="docDate" label="日期" width="110" />
      <el-table-column :label="activeTab === 'RECEIPT' ? '客户' : '供应商'" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ activeTab === 'RECEIPT' ? row.customerName : row.supplierName }}
        </template>
      </el-table-column>
      <el-table-column label="金额" width="130" align="right">
        <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="已核销" width="130" align="right">
        <template #default="{ row }">{{ fmtAmount(row.settledAmount) }}</template>
      </el-table-column>
      <el-table-column label="未核销" width="130" align="right">
        <template #default="{ row }">
          <span :style="{ color: (row.unsettledAmount || 0) > 0 ? '#E6A23C' : '#67C23A' }">
            {{ fmtAmount(row.unsettledAmount) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="(row.unsettledAmount || 0) > 0 ? 'warning' : 'success'" size="small">
            {{ (row.unsettledAmount || 0) > 0 ? '未核完' : '已核完' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="(row.unsettledAmount || 0) > 0"
            text size="small" type="primary" @click.stop="onShowRecommend(row)">
            核销推荐
          </el-button>
          <span v-else style="color:#909399;font-size:12px">已结清</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current="query.current"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      style="margin-top:12px;justify-content:flex-end"
      @size-change="fetchData"
      @current-change="fetchData"
    />

    <!-- 核销推荐弹窗 -->
    <el-dialog v-model="recommendDialogVisible" :title="drawerTitle" width="800px" destroy-on-close>
      <template v-if="recommendLoading">
        <div style="text-align:center;padding:40px">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p style="margin-top:12px;color:#909399">正在匹配核销推荐...</p>
        </div>
      </template>

      <template v-else-if="recommendResult">
        <el-alert :title="recommendResult.message || `共 ${(recommendResult.items && recommendResult.items.length) || 0} 项匹配`"
          :type="recommendResult.items && recommendResult.items.length ? 'success' : 'info'" :closable="false" style="margin-bottom:16px" />

        <el-table v-if="recommendResult.items && recommendResult.items.length" :data="recommendResult.items" border stripe size="small">
          <el-table-column label="目标类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="tagTypeForTarget(row.targetDocType)" size="small">
                {{ TARGET_DOC_TYPE_LABELS[row.targetDocType] || row.targetDocType }}
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
          <el-table-column label="操作" width="160" align="center" fixed="right">
            <template #default="{ row }">
              <el-button text size="small" type="primary" @click="onPreCheck(row as any)">预检查</el-button>
              <el-button text size="small" type="primary" @click="onExecuteRecon(row as any)">执行核销</el-button>
            </template>
          </el-table-column>
        </el-table>

        <p v-else style="text-align:center;color:#909399;padding:20px">暂无匹配的核销项</p>
      </template>
      <template #footer>
        <div style="display:flex;justify-content:space-between;align-items:center;width:100%">
          <span v-if="recommendResult" style="color:#909399;font-size:12px">
            共 {{ (recommendResult.items && recommendResult.items.length) || 0 }} 项，可自动匹配 {{ countAutoMatches() }} 项 (L1-L4)
          </span>
          <div>
            <el-button @click="recommendDialogVisible = false">关闭</el-button>
            <el-button
              type="success"
              :disabled="countAutoMatches() === 0 || batchReconciling"
              :loading="batchReconciling"
              @click="onBatchReconcile">
              自动核销可匹配 ({{ countAutoMatches() }})
            </el-button>
            <el-button
              type="primary"
              :disabled="!(recommendResult && recommendResult.items && recommendResult.items.length) || batchReconcilingAll"
              :loading="batchReconcilingAll"
              @click="onBatchReconcileAll">
              一键核销全部 ({{ (recommendResult && recommendResult.items && recommendResult.items.length) || 0 }})
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
              <span>{{ checkLabel(row.checkName) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'" size="small">{{ row.passed ? '通过' : '不通过' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="160">
            <template #default="{ row }">{{ row.message }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- FIFO 自动核销预览弹窗（dry-run 结果，人工确认后批量执行） -->
    <el-dialog v-model="fifoDialogVisible" title="自动核销匹配结果（预览）" width="600px" destroy-on-close>
      <template v-if="fifoResult && fifoResult.length">
        <el-alert title="共 {{ fifoResult.length }} 项匹配，请人工确认后执行" type="success" :closable="false" style="margin-bottom:16px" />
        <el-table :data="fifoResult" border stripe size="small">
          <el-table-column label="目标单据" prop="targetDocNo" width="160" />
          <el-table-column label="核销金额" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template v-else>
        <el-alert title="暂无匹配的核销项" type="info" :closable="false" />
      </template>
      <template #footer>
        <el-button @click="fifoDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="fifoExecuting" :disabled="!fifoResult || !fifoResult.length" @click="onConfirmFifoAll">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getBusinessDocPage, type BusinessDocVO, type BusinessDocQuery } from '@/api/modules/businessDoc'
import {
  getReceiptRecommend, getPaymentRecommend, executeReconciliation, preCheckReconciliation,
  autoFifoReconciliation, batchExecuteReconciliation,
  type PreCheckResult, type ReconciliationFifoPreview,
} from '@/api/modules/reconciliation'

const activeTab = ref('RECEIPT')
const loading = ref(false)
const list = ref<BusinessDocVO[]>([])
const total = ref(0)

const query = ref<BusinessDocQuery>({ current: 1, size: 20 })

const MATCH_LEVEL_LABELS: Record<string, string> = {
  L1: 'L1 引用号匹配', L2: 'L2 发票号匹配', L3: 'L3 金额+日期匹配',
  L4: 'L4 金额精确匹配', L5: 'L5 容差匹配', L6: 'L6 同客商其他',
}
const CHECK_LABELS: Record<string, string> = {
  sourceDocValid: '来源单据', invoiceValid: '目标单据', partyMatch: '客商一致',
  amountValid: '金额充足', periodValid: '期间正常',
}
function checkLabel(name: string) { return CHECK_LABELS[name as keyof typeof CHECK_LABELS] || name }

function matchLevelType(level: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  switch (level) {
    case 'L1': return 'success'; case 'L2': return 'primary'; case 'L3': return 'primary'
    case 'L4': return 'warning'; case 'L5': return 'danger'; case 'L6': return 'info'
    default: return 'info'
  }
}

function countAutoMatches(): number {
  if (!recommendResult.value || !recommendResult.value.items) return 0
  return recommendResult.value.items.filter((it: any) =>
    it.matchLevel === 'L1' || it.matchLevel === 'L2' || it.matchLevel === 'L3' || it.matchLevel === 'L4').length
}

function fmtAmount(v: number | null | undefined) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const onTabChange = () => {
  query.value.current = 1
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    const params: BusinessDocQuery = {
      current: query.value.current,
      size: query.value.size,
    }
    if (activeTab.value === 'RECEIPT') {
      params.docTypes = ['RECEIPT']
    } else {
      params.docTypes = ['PAYMENT']
    }
    if (query.value.keyword) params.keyword = query.value.keyword
    if (query.value.period) params.period = query.value.period
    const res: any = await getBusinessDocPage(params)
    list.value = (res.records || []).filter((r: BusinessDocVO) => (r.unsettledAmount || 0) > 0)
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(() => { fetchData() })
function onSearch() { query.value.current = 1; fetchData() }
function onReset() { query.value = { current: 1, size: 20 }; fetchData() }

defineExpose({ fetchData })

// ====== 核销推荐 ======
const recommendDialogVisible = ref(false)
const recommendLoading = ref(false)
const recommendResult = ref<any>(null)
const drawerTitle = ref('')
const currentDoc = ref<BusinessDocVO | null>(null)
const batchReconciling = ref(false)
const batchReconcilingAll = ref(false)

function buildPeriod(doc: BusinessDocVO): string {
  if (doc.period) return doc.period
  const now = new Date()
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`
}

async function onRowClick(row: any) {
  if ((row.unsettledAmount || 0) > 0) {
    await onShowRecommend(row)
  } else {
    ElMessage.info('该单据已全部核销')
  }
}

function tagTypeForTarget(docType: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  switch (docType) {
    case 'INVOICE_OUT': case 'RECEIPT': return 'success'
    case 'INVOICE_IN': case 'PAYMENT': return 'warning'
    default: return 'info'
  }
}

const TARGET_DOC_TYPE_LABELS: Record<string, string> = {
  INVOICE_OUT: '应收单',
  INVOICE_IN: '应付单',
  RECEIPT: '收款单',
  PAYMENT: '付款单',
  EXPENSE: '费用报销',
  OTHER_RECEIVABLE: '其他应收',
  OTHER_PAYABLE: '其他应付',
}

async function onShowRecommend(row: any) {
  currentDoc.value = row
  const partyName = activeTab.value === 'RECEIPT' ? row.customerName : row.supplierName
  drawerTitle.value = `核销推荐 — ${partyName || '未知'} ¥${fmtAmount(row.amount)}`
  recommendDialogVisible.value = true
  recommendLoading.value = true
  recommendResult.value = null
  try {
    const customerId = row.customerId
    const supplierId = row.supplierId
    if (activeTab.value === 'RECEIPT') {
      if (!customerId) {
        ElMessage.warning('单据缺少客户信息')
        recommendDialogVisible.value = false
        return
      }
      recommendResult.value = await getReceiptRecommend(row.id, {
        sourceDocType: row.docType,
        customerId,
        amount: row.unsettledAmount || row.amount,
        summary: row.summary,
        counterpartyName: row.customerName,
      })
    } else if (activeTab.value === 'PAYMENT') {
      const vendorId = row.supplierId
      if (!vendorId) {
        ElMessage.warning('单据缺少供应商信息')
        recommendDialogVisible.value = false
        return
      }
      recommendResult.value = await getPaymentRecommend(row.id, {
        sourceDocType: row.docType,
        vendorId,
        amount: row.unsettledAmount || row.amount,
        summary: row.summary,
        counterpartyName: row.supplierName,
      })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '获取核销推荐失败')
    recommendDialogVisible.value = false
  } finally {
    recommendLoading.value = false
  }
}

async function onExecuteRecon(item: any) {
  if (!currentDoc.value) return
  try {
    await executeReconciliation({
      sourceDocType: activeTab.value === 'RECEIPT' ? 'receipt' : 'payment',
      sourceDocId: currentDoc.value.id,
      targetDocType: item.targetDocType,
      targetDocId: item.targetDocId,
      amount: item.suggestedAmount,
      matchScore: item.matchScore,
      matchMethod: 'AUTO',
      period: buildPeriod(currentDoc.value),
    })
    ElMessage.success('核销已提报，待审批')
    recommendDialogVisible.value = false
    await fetchData()
  } catch (e: any) {
    ElMessage.error(e?.message || '核销执行失败')
  }
}

// 批量核销 - 可自动匹配 (L1-L4)
async function onBatchReconcile() {
  if (!currentDoc.value || !recommendResult.value?.items?.length) return
  const autoMatches = recommendResult.value.items.filter((it: any) =>
    it.matchLevel === 'L1' || it.matchLevel === 'L2' || it.matchLevel === 'L3' || it.matchLevel === 'L4')
  if (autoMatches.length === 0) { ElMessage.warning('没有可自动核销的匹配项'); return }
  batchReconciling.value = true
  let okCount = 0, failCount = 0
  for (const item of autoMatches) {
    try {
      await executeReconciliation({
        sourceDocType: activeTab.value === 'RECEIPT' ? 'receipt' : 'payment',
        sourceDocId: currentDoc.value.id,
        targetDocType: item.targetDocType,
        targetDocId: item.targetDocId,
        amount: item.suggestedAmount,
        matchScore: item.matchScore,
        matchMethod: 'AUTO_BATCH',
        period: buildPeriod(currentDoc.value),
      })
      okCount++
    } catch { failCount++ }
  }
  batchReconciling.value = false
  ElMessage[failCount === 0 ? 'success' : 'warning'](
    failCount === 0 ? `已提报 ${okCount} 项可匹配，待审批` : `提报完成: 成功 ${okCount} 项, 失败 ${failCount} 项`)
  recommendDialogVisible.value = false
  await fetchData()
}

// 批量核销 - 全部
async function onBatchReconcileAll() {
  if (!currentDoc.value || !recommendResult.value?.items?.length) return
  batchReconcilingAll.value = true
  let okCount = 0, failCount = 0
  for (const item of recommendResult.value.items) {
    try {
      await executeReconciliation({
        sourceDocType: activeTab.value === 'RECEIPT' ? 'receipt' : 'payment',
        sourceDocId: currentDoc.value.id,
        targetDocType: item.targetDocType,
        targetDocId: item.targetDocId,
        amount: item.suggestedAmount,
        matchScore: item.matchScore,
        matchMethod: 'MANUAL',
        period: buildPeriod(currentDoc.value),
      })
      okCount++
    } catch { failCount++ }
  }
  batchReconcilingAll.value = false
  ElMessage[failCount === 0 ? 'success' : 'warning'](
    failCount === 0 ? `已核销全部 ${okCount} 项` : `核销完成: 成功 ${okCount} 项, 失败 ${failCount} 项`)
  recommendDialogVisible.value = false
  await fetchData()
}

// ====== 预检查 ======
const preCheckDialogVisible = ref(false)
const preCheckLoading = ref(false)
const preCheckResult = ref<PreCheckResult | null>(null)

async function onPreCheck(item: any) {
  if (!currentDoc.value) return
  preCheckResult.value = null
  preCheckLoading.value = true
  preCheckDialogVisible.value = true
  try {
    preCheckResult.value = await preCheckReconciliation({
      sourceDocType: activeTab.value === 'RECEIPT' ? 'receipt' : 'payment',
      sourceDocId: currentDoc.value.id,
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

// ====== FIFO 自动核销 ======
const fifoDialogVisible = ref(false)
const fifoLoading = ref(false)
const fifoExecuting = ref(false)
const fifoResult = ref<ReconciliationFifoPreview[] | null>(null)

async function onAutoFifo() {
  if (!list.value.length) return
  const row = list.value[0]
  const isReceipt = activeTab.value === 'RECEIPT'
  const partyId = isReceipt ? row.customerId : row.supplierId
  if (!partyId) {
    ElMessage.warning('单据缺少客户/供应商信息，无法自动核销')
    return
  }
  fifoLoading.value = true
  try {
    fifoResult.value = await autoFifoReconciliation({
      partyId,
      targetDocType: isReceipt ? 'INVOICE_OUT' : 'INVOICE_IN',
      amount: row.amount || 0,
      sourceDocType: isReceipt ? 'receipt' : 'payment',
      sourceDocId: row.id,
    })
    fifoDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '自动核销失败')
  } finally {
    fifoLoading.value = false
  }
}

async function onConfirmFifoAll() {
  const previews = fifoResult.value
  if (!previews || !previews.length) return
  const isReceipt = activeTab.value === 'RECEIPT'
  fifoExecuting.value = true
  try {
    const requests = previews.map((p) => ({
      sourceDocType: isReceipt ? 'receipt' : 'payment',
      sourceDocId: p.sourceDocId,
      targetDocType: isReceipt ? 'INVOICE_OUT' : 'INVOICE_IN',
      targetDocId: p.targetDocId,
      amount: p.amount,
      matchScore: 100,
      matchMethod: 'AUTO',
    }))
    await batchExecuteReconciliation(requests)
    ElMessage.success(`已执行 ${requests.length} 笔核销`)
    fifoDialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量核销执行失败')
  } finally {
    fifoExecuting.value = false
  }
}
</script>

<style scoped>
.workbench-panel .panel-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.filter-form { margin-bottom: 12px; }
</style>
