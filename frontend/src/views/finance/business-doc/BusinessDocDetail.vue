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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getBusinessDoc, submitBusinessDoc, approveBusinessDoc, rejectBusinessDoc,
  generateVoucherFromDoc, DOC_TYPE_LABELS, DOC_STATUS_LABELS, type BusinessDocVO,
} from '@/api/modules/businessDoc'

const route = useRoute()
const router = useRouter()
const doc = ref<BusinessDocVO | null>(null)
const id = Number(route.query.id)

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
