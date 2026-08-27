<template>
  <div class="invoice-reconcile">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">发票勾稽（三流合一）</span>
        <div>
          <el-tag type="success" effect="plain">已闭环</el-tag>
          <el-tag type="warning" effect="plain">票到款未到</el-tag>
          <el-tag type="info" effect="plain">未付款</el-tag>
        </div>
      </div>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="本页为只读视图：票流（认证/申报）+ 资金流（付款核销）勾稽，不触发核销动作。实际核销请在核销工作台处理。" />

      <el-tabs v-model="tabType" @tab-change="onTabChange">
        <el-tab-pane label="进项发票（按供应商）" name="INPUT" />
        <el-tab-pane label="销项发票（按客户）" name="OUTPUT" />
      </el-tabs>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" clearable style="width:120px" />
        </el-form-item>
        <el-form-item :label="tabType === 'INPUT' ? '供应商' : '客户'">
          <el-input v-model="query.partyName" clearable style="width:180px" :placeholder="tabType === 'INPUT' ? '供应商名称' : '客户名称'" />
        </el-form-item>
        <el-form-item label="勾稽状态">
          <el-select v-model="query.reconcileStatus" clearable placeholder="全部" style="width:130px">
            <el-option label="未付款" value="UNPAID" />
            <el-option label="部分付款" value="PARTIAL" />
            <el-option label="已付款" value="PAID" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="invoiceNo" label="发票号" width="180" />
        <el-table-column prop="invoiceDate" label="开票日" width="110" />
        <el-table-column :label="tabType === 'INPUT' ? '供应商' : '客户'" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ tabType === 'INPUT' ? row.vendorName : row.customerName }}</template>
        </el-table-column>
        <el-table-column label="价税合计" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="110" align="right">
          <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column label="认证" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(CERT_TAG[row.certificationStatus] || 'info') as any" size="small">
              {{ CERT_MAP[row.certificationStatus] || row.certificationStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申报" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(DECL_TAG[row.declaredStatus] || 'info') as any" size="small">
              {{ DECL_MAP[row.declaredStatus] || row.declaredStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已付款" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="未付款" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.unpaidAmount) }}</template>
        </el-table-column>
        <el-table-column label="勾稽状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="(RECON_TAG[row.reconcileStatus] || 'info') as any" size="small">
              {{ RECON_MAP[row.reconcileStatus] || row.reconcileStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="红冲" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hasRedFlushed" type="danger" size="small">已红冲</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  queryInputReconcile,
  queryOutputReconcile,
  type InvoiceReconcileVO,
} from '@/api/modules/tax'

const tabType = ref<'INPUT' | 'OUTPUT'>('INPUT')
const loading = ref(false)
const list = ref<InvoiceReconcileVO[]>([])

const query = reactive({
  period: '',
  partyName: '',
  reconcileStatus: '',
})

const CERT_MAP: Record<string, string> = { UNCERTIFIED: '未认证', CERTIFIED: '已认证' }
const CERT_TAG: Record<string, string> = { UNCERTIFIED: 'info', CERTIFIED: 'success' }
const DECL_MAP: Record<string, string> = { UNDECLARED: '未申报', DECLARED: '已申报' }
const DECL_TAG: Record<string, string> = { UNDECLARED: 'warning', DECLARED: 'success' }
const RECON_MAP: Record<string, string> = { UNPAID: '未付款', PARTIAL: '部分付款', PAID: '已付款' }
const RECON_TAG: Record<string, string> = { UNPAID: 'info', PARTIAL: 'warning', PAID: 'success' }

function fmtAmount(v: number | undefined) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    const params: any = {
      period: query.period || undefined,
      reconcileStatus: query.reconcileStatus || undefined,
    }
    if (query.partyName) {
      if (tabType.value === 'INPUT') params.vendorName = query.partyName
      else params.customerName = query.partyName
    }
    // 后端按 vendorId/customerId 过滤；此处按名称前端过滤（后端暂不支持名称模糊）
    const res = tabType.value === 'INPUT'
      ? await queryInputReconcile({ period: params.period })
      : await queryOutputReconcile({ period: params.period })
    let data = res || []
    if (query.partyName) {
      const kw = query.partyName.trim()
      data = data.filter((r: InvoiceReconcileVO) =>
        (tabType.value === 'INPUT' ? r.vendorName : r.customerName)?.includes(kw))
    }
    if (params.reconcileStatus) {
      data = data.filter((r: InvoiceReconcileVO) => r.reconcileStatus === params.reconcileStatus)
    }
    list.value = data
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  list.value = []
  fetchData()
}

function onReset() {
  query.period = ''
  query.partyName = ''
  query.reconcileStatus = ''
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.filter-form {
  margin-bottom: 12px;
}
</style>
