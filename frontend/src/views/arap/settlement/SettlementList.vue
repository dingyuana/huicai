<template>
  <div class="settlement-page">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">往来核销</span>
        <el-space>
          <el-button type="primary" @click="goToWorkbench">核销工作台</el-button>
          <el-button @click="refreshActive">刷新</el-button>
        </el-space>
      </div>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- Tab 1: 核销单 -->
        <el-tab-pane label="核销单" name="settlement">
          <div class="toolbar">
            <el-form :model="settlementQuery" inline>
              <el-form-item label="类型">
                <el-select v-model="settlementQuery.settlementType" placeholder="全部" style="width:130px" @change="fetchSettlements">
                  <el-option label="全部" value="" />
                  <el-option label="应收核销" value="RECEIVABLE" />
                  <el-option label="应付核销" value="PAYABLE" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="settlementQuery.status" placeholder="全部" style="width:120px" clearable @change="fetchSettlements">
                  <el-option label="全部" value="" />
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="已确认" value="CONFIRMED" />
                  <el-option label="已记账" value="VOUCHERED" />
                  <el-option label="已冲销" value="REVERSED" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="fetchSettlements">查询</el-button>
              </el-form-item>
            </el-form>
            <el-button type="primary" @click="openCreate">新增核销</el-button>
          </div>

          <el-table :data="settlementList" v-loading="settlementLoading" border stripe style="width:100%">
            <el-table-column type="index" label="序号" width="50" />
            <el-table-column prop="settlementNo" label="核销编号" width="140" />
            <el-table-column label="类型" width="100" align="center">
                        <template #default="{ row }">
                          <el-tag :type="['RECEIVE','RECEIVABLE'].includes(row.settlementType)?'success':'warning'" size="small">
                            {{ settlementTypeLabel(row.settlementType) }}
                          </el-tag>
                        </template>
                      </el-table-column>
            <el-table-column prop="settlementDate" label="核销日期" width="100" />
            <el-table-column label="客户/供应商" min-width="150">
              <template #default="{row}">{{ row.customerName || row.vendorName || '-' }}</template>
            </el-table-column>
            <el-table-column label="核销金额" width="130" align="right">
              <template #default="{row}">{{ fmtAmount(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column prop="period" label="期间" width="80" align="center" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{row}">
                <el-button v-if="row.status==='DRAFT'" text size="small" type="success" @click="onConfirmSettlement(row as ArapSettlement)">确认</el-button>
                <el-button text size="small" type="primary" @click="onViewSettlement(row as ArapSettlement)">详情</el-button>
                <el-popconfirm v-if="row.status==='DRAFT'" title="确认删除?" @confirm="onDeleteSettlement(row as ArapSettlement)">
                  <template #reference><el-button text type="danger" size="small">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination v-model:current-page="settlementQuery.current" v-model:page-size="settlementQuery.size"
              :total="settlementTotal" layout="total,prev,pager,next" @current-change="fetchSettlements" />
          </div>
        </el-tab-pane>

        <!-- Tab 2: 核销日志 -->
        <el-tab-pane label="核销日志" name="reconLog">
          <div class="toolbar">
            <el-form :model="logQuery" inline>
              <el-form-item label="来源类型">
                <el-select v-model="logQuery.sourceDocType" placeholder="全部" style="width:160px" clearable @change="fetchReconLogs">
                  <el-option label="全部" value="" />
                  <el-option label="银行流水" value="bank_txn" />
                  <el-option label="收款单" value="receipt" />
                  <el-option label="付款单" value="payment" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="fetchReconLogs">查询</el-button>
              </el-form-item>
            </el-form>
          </div>

          <el-table :data="reconLogList" v-loading="logLoading" border stripe style="width:100%">
            <el-table-column type="index" label="序号" width="50" />
            <el-table-column prop="id" label="日志ID" width="70" />
            <el-table-column label="来源" width="120">
              <template #default="{row}">
                <el-tag size="small">{{ sourceLabel(row.sourceDocType) }}</el-tag>
                <span style="margin-left:4px">#{{ row.sourceDocId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="目标单据" width="140">
              <template #default="{row}">
                <el-tag :type="row.targetDocType==='INVOICE_OUT'?'success':'warning'" size="small">
                  {{ row.targetDocType==='INVOICE_OUT'?'应收':'应付' }}
                </el-tag>
                <span style="margin-left:4px">#{{ row.targetDocId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="核销金额" width="130" align="right">
              <template #default="{row}">{{ fmtAmount(row.allocatedAmount) }}</template>
            </el-table-column>
            <el-table-column label="匹配度" width="90" align="center">
              <template #default="{row}">
                <el-tag v-if="row.matchScore != null" :type="row.matchScore >= 0.95 ? 'success' : 'warning'" size="small">
                  {{ (row.matchScore * 100).toFixed(0) }}%
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="匹配方式" width="90" align="center">
              <template #default="{row}">
                <el-tag :type="row.matchMethod==='AUTO'?'primary':'info'" size="small">
                  {{ row.matchMethod==='AUTO'?'自动':'手动' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}">
                <el-tag :type="row.status==='CONFIRMED'?'success':'danger'" size="small">
                  {{ row.status==='CONFIRMED'?'已核销':'已取消' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{row}">
                <el-button v-if="row.status==='CONFIRMED'" text size="small" type="warning" @click="onReverseRecon(row as ReconciliationLog)">反核销</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination v-model:current-page="logQuery.current" v-model:page-size="logQuery.size"
              :total="logTotal" layout="total,prev,pager,next" @current-change="fetchReconLogs" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增核销对话框 -->
    <el-dialog v-model="createDialogVisible" title="新增核销" width="600">
      <el-form :model="createForm" label-width="100">
        <el-form-item label="类型">
          <el-select v-model="createForm.settlementType" style="width:100%">
            <el-option label="应收核销" value="RECEIVABLE" />
            <el-option label="应付核销" value="PAYABLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户/供应商">
          <el-select v-model="createForm.partyId" filterable style="width:100%" placeholder="搜索">
            <el-option v-for="p in parties" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="createForm.totalAmount" :precision="2" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="onCreateSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 核销单详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="核销单详情" width="750">
      <template v-if="settlementDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="核销编号" :span="2">{{ settlementDetail.settlementNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="['RECEIVE','RECEIVABLE'].includes(settlementDetail.settlementType)?'success':'warning'" size="small">
              {{ settlementTypeLabel(settlementDetail.settlementType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(settlementDetail.status)" size="small">{{ statusLabel(settlementDetail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="核销日期">{{ settlementDetail.settlementDate }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ settlementDetail.period }}</el-descriptions-item>
          <el-descriptions-item label="客户/供应商" :span="2">{{ settlementDetail.customerName || settlementDetail.vendorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="核销金额">{{ fmtAmount(settlementDetail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="折扣金额">{{ fmtAmount(settlementDetail.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ settlementDetail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

                <!-- 核销依据：核销明细 -->
                <div v-if="settlementEntries.length" style="margin-top:16px">
                  <h4 style="margin-bottom:8px">核销依据</h4>
                  <el-table :data="settlementEntries" border stripe size="small">
                    <el-table-column label="来源单据" prop="sourceDocNo" min-width="140" />
                    <el-table-column label="目标单据" prop="targetDocNo" min-width="140" />
                    <el-table-column label="核销金额" width="120" align="right">
                      <template #default="{ row }">{{ fmtAmount(row.settledAmount) }}</template>
                    </el-table-column>
                    <el-table-column label="核销前余额" width="120" align="right">
                      <template #default="{ row }">{{ fmtAmount(row.beforeBalance) }}</template>
                    </el-table-column>
                    <el-table-column label="核销后余额" width="120" align="right">
                      <template #default="{ row }">{{ fmtAmount(row.afterBalance) }}</template>
                    </el-table-column>
                  </el-table>
                </div>

                <ReconciliationTimeline v-if="settlementDetail.id" :settlementId="settlementDetail.id" @jump="onTimelineJump" />
        <div style="margin-top:16px">
          <el-space>
            <el-button text type="primary" @click="showUpstreamDrawer = true">
              <el-icon><ArrowUp /></el-icon> 上游来源
            </el-button>
            <el-button text type="primary" @click="showDownstreamDrawer = true">
              <el-icon><ArrowDown /></el-icon> 下游去向
            </el-button>
            <el-button text type="primary" @click="showTimeline = !showTimeline">
              全链路时间轴 {{ showTimeline ? '▲' : '▼' }}
            </el-button>
          </el-space>
        </div>

        <!-- 时间轴 -->
        <div v-show="showTimeline" style="margin-top:12px">
          <ReconciliationTimeline v-if="settlementDetail.id" :settlementId="settlementDetail.id" @jump="onTimelineJump" />
        </div>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible=false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 上游来源抽屉 -->
    <el-drawer v-model="showUpstreamDrawer" title="上游来源" size="400">
      <template v-if="traceData?.upstream">
        <el-card v-if="traceData.upstream.bankTransaction" shadow="never" style="margin-bottom:12px">
          <template #header><span>银行流水</span></template>
          <div>流水号: {{ traceData.upstream.bankTransaction.transactionNo }}</div>
          <div>金额: ¥{{ fmtAmount(traceData.upstream.bankTransaction.amount) }}</div>
          <div>对方账户: {{ traceData.upstream.bankTransaction.counterAccount }}</div>
        </el-card>
        <el-card v-if="traceData.upstream.receipt" shadow="never">
          <template #header><span>收款单</span></template>
          <div>单据号: {{ traceData.upstream.receipt.docNo }}</div>
          <div>金额: ¥{{ fmtAmount(traceData.upstream.receipt.amount) }}</div>
          <div>状态: {{ traceData.upstream.receipt.status }}</div>
        </el-card>
      </template>
      <div v-else style="text-align:center;color:#909399;padding:40px">暂无上游数据</div>
    </el-drawer>

    <!-- 下游去向抽屉 -->
    <el-drawer v-model="showDownstreamDrawer" title="下游去向" size="400">
      <template v-if="traceData?.downstream">
        <div v-for="doc in traceData.downstream.businessDocs" :key="doc.id">
          <el-card shadow="never" style="margin-bottom:12px">
            <template #header>
              <span>{{ doc.docType === 'INVOICE_OUT' ? '应收单' : '应付单' }} #{{ doc.docNo }}</span>
            </template>
            <div>金额: ¥{{ fmtAmount(doc.amount) }}</div>
            <div>已核销: ¥{{ fmtAmount(doc.settledAmount) }}</div>
            <div>未核销: ¥{{ fmtAmount(doc.unsettledAmount) }}</div>
          </el-card>
        </div>
        <div v-for="inv in traceData.downstream.invoices" :key="inv.id">
          <el-card shadow="never" style="margin-bottom:12px">
            <template #header><span>发票 #{{ inv.invoiceNo }}</span></template>
            <div>金额: ¥{{ fmtAmount(inv.amount) }}</div>
            <div>状态: {{ inv.status }}</div>
          </el-card>
        </div>
      </template>
      <div v-else style="text-align:center;color:#909399;padding:40px">暂无下游数据</div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  pageSettlements, getSettlementDetail, getSettlementEntries, createSettlement,
  confirmSettlement, deleteSettlement,
  getReconRecords, pageReconLogs, reverseRecon,
  type ArapSettlement, type ReconciliationLog,
} from '@/api/modules/arapSettlement'
import { getReconciliationTrace, type ReconciliationTraceVO } from '@/api/modules/reconciliation'
import ReconciliationTimeline from '@/views/arap/reconciliation-workbench/ReconciliationTimeline.vue'

const router = useRouter()

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusType(s: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  if (s === 'CONFIRMED' || s === 'VOUCHERED') return 'success'
  if (s === 'REVERSED') return 'danger'
  return 'info'
}

function statusLabel(s: string) {
  const map: Record<string, string> = { DRAFT: '草稿', CONFIRMED: '已确认', VOUCHERED: '已记账', REVERSED: '已冲销', CANCELLED: '已取消', REJECTED: '已驳回' }
  return map[s] || s
}

function settlementTypeLabel(s: string | null) {
  if (!s) return '应收核销'
  if (s === 'RECEIVE' || s === 'RECEIVABLE') return '应收核销'
  if (s === 'PAY' || s === 'PAYABLE') return '应付核销'
  return s
}

function sourceLabel(s: string) {
  const map: Record<string, string> = { bank_txn: '银行流水', receipt: '收款单', payment: '付款单' }
  return map[s] || s
}

// Tab
const activeTab = ref('settlement')

// Settlement tab
const settlementLoading = ref(false)
const settlementList = ref<ArapSettlement[]>([])
const settlementTotal = ref(0)
const settlementQuery = ref({ settlementType: '', status: '', current: 1, size: 20 })

async function fetchSettlements() {
  settlementLoading.value = true
  try {
    const res: any = await pageSettlements(settlementQuery.value)
    settlementList.value = res.records || []
    settlementTotal.value = res.total || 0
  } finally { settlementLoading.value = false }
}

// Recon log tab
const logLoading = ref(false)
const reconLogList = ref<ReconciliationLog[]>([])
const logTotal = ref(0)
const logQuery = ref({ sourceDocType: '', current: 1, size: 20 })

async function fetchReconLogs() {
  logLoading.value = true
  try {
    const res: any = await pageReconLogs(logQuery.value)
    reconLogList.value = res.records || []
    logTotal.value = res.total || 0
  } finally { logLoading.value = false }
}

function onTabChange() {
  if (activeTab.value === 'settlement') fetchSettlements()
  else fetchReconLogs()
}

// Create settlement
const createDialogVisible = ref(false)
const createSaving = ref(false)
const parties = ref<Array<{id: number; name: string}>>([])
const createForm = ref({ settlementType: 'RECEIVABLE', partyId: undefined as number | undefined, totalAmount: 0, remark: '' })

async function openCreate() {
  createForm.value = { settlementType: 'RECEIVABLE', partyId: undefined, totalAmount: 0, remark: '' }
  try {
    const { listCustomer, listVendor } = await import('@/api/modules/arap')
    const [cust, vend] = await Promise.all([listCustomer(), listVendor()])
    parties.value = [
      ...(cust as any[]).map((c: any) => ({ id: c.id, name: `[客户] ${c.name}` })),
      ...(vend as any[]).map((v: any) => ({ id: v.id, name: `[供应商] ${v.name}` })),
    ]
  } catch { parties.value = [] }
  createDialogVisible.value = true
}

async function onCreateSave() {
  if (!createForm.value.partyId) { ElMessage.warning('请选择客户/供应商'); return }
  if (createForm.value.totalAmount <= 0) { ElMessage.warning('金额必须大于0'); return }
  createSaving.value = true
  try {
    await createSettlement(createForm.value as any)
    ElMessage.success('新增成功')
    createDialogVisible.value = false
    await fetchSettlements()
  } finally { createSaving.value = false }
}

// Settlement actions
async function onConfirmSettlement(row: ArapSettlement) {
  try {
    await confirmSettlement(row.id)
    ElMessage.success('核销已确认')
    await fetchSettlements()
  } catch (e: any) { ElMessage.error(e?.message || '确认失败') }
}

async function onDeleteSettlement(row: ArapSettlement) {
  try {
    await deleteSettlement(row.id)
    ElMessage.success('删除成功')
    await fetchSettlements()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// Detail
const detailDialogVisible = ref(false)
const settlementDetail = ref<ArapSettlement | null>(null)
const settlementEntries = ref<any[]>([])

// 上下游穿透 + 时间轴
const showUpstreamDrawer = ref(false)
const showDownstreamDrawer = ref(false)
const showTimeline = ref(false)
const traceData = ref<ReconciliationTraceVO | null>(null)

async function onViewSettlement(row: ArapSettlement) {
  try {
    settlementDetail.value = await getSettlementDetail(row.id)
    settlementEntries.value = await getSettlementEntries(row.id)
    detailDialogVisible.value = true
    // 加载上下游数据（后端同时支持 settlementId 和 logId）
    loadTrace(row.id)
  } catch (e: any) { ElMessage.error(e?.message || '查询详情失败') }
}

async function loadTrace(id: number) {
  try {
    traceData.value = await getReconciliationTrace(id)
  } catch {
    traceData.value = null
  }
}

function onTimelineJump(path: string) {
  if (path) router.push(path)
}

// Reverse reconciliation
async function onReverseRecon(row: ReconciliationLog) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入反核销原因', '反核销确认', {
      confirmButtonText: '确认反核销',
      cancelButtonText: '取消',
      inputPlaceholder: '反核销原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '请输入反核销原因'),
    })
    await reverseRecon(row.id, reason || '')
    ElMessage.success('反核销成功')
    await fetchReconLogs()
  } catch { /* cancel */ }
}

// Navigate to workbench
function goToWorkbench() {
  router.push('/arap/reconciliation-workbench')
}

// Refresh
async function refreshActive() {
  if (activeTab.value === 'settlement') await fetchSettlements()
  else await fetchReconLogs()
}

onMounted(() => {
  fetchSettlements()
  fetchReconLogs()
})
</script>

<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.toolbar { display:flex; justify-content:space-between; margin-bottom:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
