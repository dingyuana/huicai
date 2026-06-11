<template>
  <div class="voucher-detail">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">凭证详情</span>
        <div>
          <el-button @click="goBack">返回</el-button>
          <el-button v-if="voucher?.status === 'DRAFT'" type="primary" @click="goEdit">编辑</el-button>
          <el-button v-if="voucher?.status === 'DRAFT'" type="success" @click="onSubmit">提交</el-button>
          <el-button v-if="voucher?.status === 'SUBMITTED'" type="primary" @click="onAudit">审核</el-button>
          <el-button v-if="voucher?.status === 'AUDITED'" type="warning" @click="onPost">记账</el-button>
        </div>
      </div>

      <el-descriptions v-if="voucher" :column="3" border>
        <el-descriptions-item label="凭证号">{{ voucher.voucherNo }}</el-descriptions-item>
        <el-descriptions-item label="会计期间">{{ voucher.period }}</el-descriptions-item>
        <el-descriptions-item label="凭证类型">
          <el-tag size="small">{{ voucher.voucherTypeName || voucher.voucherTypeId }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态" :span="3">
          <el-tag :type="statusType(voucher.status) as 'success' | 'warning' | 'info' | 'primary'">
            {{ VOUCHER_STATUS_MAP[voucher.status] || voucher.status }}
          </el-tag>
          <span v-if="voucher.reversedFrom" class="reverse-tag">（红冲自 #{{ voucher.reversedFrom }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="摘要" :span="3">{{ voucher.summary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ voucher.createdByName || voucher.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="制单时间">{{ voucher.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ sourceLabel(voucher.source) }}</el-descriptions-item>
        <el-descriptions-item v-if="voucher.submittedBy" label="提交人">{{ voucher.submittedByName || voucher.submittedBy }}</el-descriptions-item>
        <el-descriptions-item v-if="voucher.submittedAt" label="提交时间">{{ voucher.submittedAt }}</el-descriptions-item>
        <el-descriptions-item></el-descriptions-item>
        <el-descriptions-item v-if="voucher.auditedBy" label="审核人">{{ voucher.auditedByName || voucher.auditedBy }}</el-descriptions-item>
        <el-descriptions-item v-if="voucher.auditedAt" label="审核时间">{{ voucher.auditedAt }}</el-descriptions-item>
        <el-descriptions-item></el-descriptions-item>
        <el-descriptions-item v-if="voucher.postedBy" label="记账人">{{ voucher.postedByName || voucher.postedBy }}</el-descriptions-item>
        <el-descriptions-item v-if="voucher.postedAt" label="记账时间">{{ voucher.postedAt }}</el-descriptions-item>
        <el-descriptions-item></el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">分录明细</h3>
      <el-table :data="voucher?.entries || []" border stripe>
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column label="科目编码" prop="subjectCode" width="140" />
        <el-table-column label="科目名称" prop="subjectName" min-width="200" />
        <el-table-column label="摘要" prop="summary" min-width="200" show-overflow-tooltip />
        <el-table-column label="借方金额" width="160" align="right">
          <template #default="{ row }">{{ fmtAmount(row.debit) }}</template>
        </el-table-column>
        <el-table-column label="贷方金额" width="160" align="right">
          <template #default="{ row }">{{ fmtAmount(row.credit) }}</template>
        </el-table-column>
      </el-table>
      <div class="total-row">
        <span>合计:</span>
        <span class="num">借方 {{ fmtAmount(voucher?.totalDebit) }}</span>
        <span class="num">贷方 {{ fmtAmount(voucher?.totalCredit) }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getVoucher,
  submitVoucher,
  auditVoucher,
  postVoucher,
  VOUCHER_STATUS_MAP,
  type VoucherVO,
} from '@/api/modules/voucher'

const route = useRoute()
const router = useRouter()
const voucher = ref<VoucherVO | null>(null)
const id = Number(route.query.id)

function statusType(s: string): '' | 'success' | 'warning' | 'info' | 'primary' {
  switch (s) {
    case 'DRAFT': return 'info'
    case 'SUBMITTED': return 'primary'
    case 'AUDITED': return 'warning'
    case 'POSTED': return 'success'
    default: return 'info'
  }
}

function sourceLabel(s?: string) {
  return s === 'MANUAL' ? '手工录入'
    : s === 'TEMPLATE' ? '模板生成'
    : s === 'GENERATED' ? '单据生成'
    : s === 'REVERSAL' ? '红冲凭证' : '-'
}

function fmtAmount(v?: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function goBack() {
  router.push({ name: 'VoucherList' })
}

function goEdit() {
  router.push({ name: 'VoucherEdit', query: { mode: 'edit', id: String(id) } })
}

async function fetchData() {
  voucher.value = await getVoucher(id)
}

async function onSubmit() {
  await submitVoucher(id)
  ElMessage.success('提交成功')
  await fetchData()
}

async function onAudit() {
  await auditVoucher(id)
  ElMessage.success('审核成功')
  await fetchData()
}

async function onPost() {
  await postVoucher(id)
  ElMessage.success('记账成功')
  await fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.voucher-detail .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.section-title {
  margin: 20px 0 12px;
  font-size: 14px;
  font-weight: 600;
}
.total-row {
  margin-top: 12px;
  padding: 10px 16px;
  background: #f5f7fa;
  display: flex;
  gap: 32px;
  justify-content: flex-end;
  font-size: 14px;
}
.total-row .num {
  font-weight: 600;
}
.reverse-tag {
  margin-left: 8px;
  color: #e6a23c;
  font-size: 12px;
}
</style>
