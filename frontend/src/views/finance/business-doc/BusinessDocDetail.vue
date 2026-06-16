<template>
  <div class="doc-detail">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">单据详情</span>
        <div>
          <el-button @click="goBack">返回</el-button>
          <el-button v-if="doc?.status === 'DRAFT'" type="primary" @click="goEdit">编辑</el-button>
          <el-button v-if="doc?.status === 'DRAFT'" type="success" @click="onSubmit">提交</el-button>
          <el-button v-if="doc?.status === 'SUBMITTED'" type="primary" @click="onApprove">审批</el-button>
          <el-button v-if="doc?.status === 'SUBMITTED'" type="danger" @click="onReject">驳回</el-button>
          <el-button v-if="doc?.status === 'APPROVED' && !doc?.voucherId" type="warning" @click="onGenerateVoucher">生成凭证</el-button>
          <el-button v-if="canReconcile" type="primary" @click="onOpenReconcile">去核销</el-button>
        </div>
      </div>

      <el-descriptions v-if="doc" :column="3" border>
        <el-descriptions-item label="单据号">{{ doc.docNo }}</el-descriptions-item>
        <el-descriptions-item label="单据类型">{{ DOC_TYPE_LABELS[doc.docType] || doc.docType }}</el-descriptions-item>
        <el-descriptions-item label="单据日期">{{ doc.docDate }}</el-descriptions-item>
        <el-descriptions-item label="会计期间">{{ doc.period }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmtAmount(doc.amount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(doc.status) as 'success' | 'warning' | 'info' | 'primary' | 'danger'">
            {{ DOC_STATUS_LABELS[doc.status] || doc.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户" :span="3">{{ doc.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商" :span="3">{{ doc.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="摘要" :span="3">{{ doc.summary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="凭证ID">
          <span v-if="doc.voucherId">#{{ doc.voucherId }}</span>
          <span v-else style="color:#909399">未生成</span>
        </el-descriptions-item>
        <el-descriptions-item label="制单人">{{ doc.createdByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="制单时间">{{ doc.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交人">{{ doc.submittedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ doc.submittedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ doc.approvedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ doc.approvedAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">分录明细</h3>
      <el-table :data="doc?.entries || []" border stripe>
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column prop="expenseType" label="费用类别" width="140" />
        <el-table-column label="科目编码" prop="subjectCode" width="140" />
        <el-table-column label="科目名称" prop="subjectName" min-width="200" />
        <el-table-column prop="invoiceNo" label="发票号" width="160" />
        <el-table-column prop="summary" label="分录摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="金额" width="160" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="reconDrawerVisible" :title="reconDrawerTitle" size="640px" destroy-on-close>
      <template v-if="reconLoading">
        <div style="text-align:center;padding:40px">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p style="margin-top:12px;color:#909399">正在匹配核销推荐...</p>
        </div>
      </template>
      <template v-else-if="reconResult">
        <el-alert
          :title="reconResult.items?.length ? `匹配到 ${reconResult.items.length} 个候选目标` : '暂无匹配目标 — 对方可能尚未开票, 建议走预收/预付路径'"
          :type="reconResult.items?.length ? 'success' : 'info'"
          :closable="false"
          style="margin-bottom:16px"
        />
        <el-table v-if="reconResult.items?.length" :data="reconResult.items" border stripe size="small">
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.targetDocType === 'INVOICE_OUT' ? 'success' : 'warning'" size="small">
                {{ row.targetDocType === 'INVOICE_OUT' ? '应收' : '应付' }}
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
          <el-table-column label="匹配级别" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="matchLevelType(row.matchLevel)" size="small">{{ row.matchLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="建议核销" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.suggestedAmount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button text size="small" type="primary" :loading="row._executing" @click="onExecuteRecon(row)">执行核销</el-button>
            </template>
          </el-table-column>
        </el-table>
        <p v-else style="text-align:center;color:#909399;padding:24px 0">
          若供应商/客户尚未开票, 凭 {{ doc?.voucherId ? '已生成凭证' : '审批后' }} 的单据可直接走"预付/预收"挂账路径
        </p>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  getBusinessDoc, submitBusinessDoc, approveBusinessDoc, rejectBusinessDoc,
  generateVoucherFromDoc, DOC_TYPE_LABELS, DOC_STATUS_LABELS, type BusinessDocVO,
} from '@/api/modules/businessDoc'
import {
  getPaymentRecommend, getReceiptRecommend, executeReconciliation,
  type RecommendItem, type ReconciliationRecommendResult,
} from '@/api/modules/reconciliation'

const route = useRoute()
const router = useRouter()
const doc = ref<BusinessDocVO | null>(null)
const id = Number(route.query.id)

const reconDrawerVisible = ref(false)
const reconLoading = ref(false)
const reconResult = ref<ReconciliationRecommendResult | null>(null)
const reconDrawerTitle = ref('')

const canReconcile = computed(() => {
  if (!doc.value) return false
  if (!['APPROVED', 'VOUCHERED'].includes(doc.value.status)) return false
  if (!['PAYMENT', 'RECEIPT'].includes(doc.value.docType)) return false
  if (doc.value.amount == null || Number(doc.value.amount) <= 0) return false
  if (doc.value.docType === 'PAYMENT' && !doc.value.supplierId) return false
  if (doc.value.docType === 'RECEIPT' && !doc.value.customerId) return false
  return true
})

function statusType(s: string) {
  switch (s) {
    case 'DRAFT': return 'info'
    case 'SUBMITTED': return 'primary'
    case 'APPROVED': return 'warning'
    case 'VOUCHERED': return 'success'
    case 'REJECTED': return 'danger'
    default: return 'info'
  }
}

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

function fmtAmount(v?: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function goBack() { router.push({ name: 'BusinessDocList' }) }
function goEdit() { router.push({ name: 'BusinessDocEdit', query: { mode: 'edit', id: String(id) } }) }

async function fetchData() {
  doc.value = await getBusinessDoc(id)
}

async function onSubmit() {
  await submitBusinessDoc(id)
  ElMessage.success('提交成功')
  await fetchData()
}
async function onApprove() {
  await approveBusinessDoc(id)
  ElMessage.success('审批成功')
  await fetchData()
}
async function onReject() {
  await rejectBusinessDoc(id)
  ElMessage.success('已驳回')
  await fetchData()
}
async function onGenerateVoucher() {
  await generateVoucherFromDoc(id)
  ElMessage.success('凭证已生成, 请前往凭证管理提交记账')
  await fetchData()
}

async function onOpenReconcile() {
  if (!doc.value) return
  const isPayment = doc.value.docType === 'PAYMENT'
  reconDrawerTitle.value = isPayment
    ? `核销推荐 — ${doc.value.supplierName || '供应商'} ¥${fmtAmount(doc.value.amount)}`
    : `核销推荐 — ${doc.value.customerName || '客户'} ¥${fmtAmount(doc.value.amount)}`
  reconDrawerVisible.value = true
  reconLoading.value = true
  reconResult.value = null
  try {
    if (isPayment) {
      reconResult.value = await getPaymentRecommend(doc.value.id, {
        vendorId: doc.value.supplierId!,
        amount: doc.value.amount!,
        summary: doc.value.summary || undefined,
        counterpartyName: doc.value.supplierName || undefined,
      })
    } else {
      reconResult.value = await getReceiptRecommend(doc.value.id, {
        customerId: doc.value.customerId!,
        amount: doc.value.amount!,
        summary: doc.value.summary || undefined,
        counterpartyName: doc.value.customerName || undefined,
      })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '获取核销推荐失败')
    reconDrawerVisible.value = false
  } finally {
    reconLoading.value = false
  }
}

async function onExecuteRecon(item: RecommendItem & { _executing?: boolean }) {
  if (!doc.value) return
  item._executing = true
  try {
    const sourceDocType = doc.value.docType === 'PAYMENT' ? 'payment' : 'receipt'
    await executeReconciliation({
      sourceDocType,
      sourceDocId: doc.value.id,
      targetDocType: item.targetDocType,
      targetDocId: item.targetDocId,
      amount: item.suggestedAmount,
      matchScore: item.matchScore,
      matchMethod: 'MANUAL',
      customerId: doc.value.customerId,
      vendorId: doc.value.supplierId,
      period: doc.value.period,
    })
    ElMessage.success('核销执行成功')
    reconDrawerVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '核销执行失败')
  } finally {
    item._executing = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.doc-detail .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.section-title { margin: 20px 0 12px; font-size: 14px; font-weight: 600; }
</style>
