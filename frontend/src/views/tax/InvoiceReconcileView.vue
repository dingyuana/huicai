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

      <el-tabs v-model="scope" class="scope-root-tabs" @tab-change="onScopeChange">
        <el-tab-pane label="待处理（未付款）" name="pending" />
        <el-tab-pane label="已完成（已付款/部分）" name="completed" />
      </el-tabs>

      <el-form :model="query" inline class="filter-form">
        <template v-if="scope === 'completed'">
          <el-form-item label="快捷时段">
            <el-radio-group v-model="quickDate" size="small" @change="onQuickDate">
              <el-radio-button value="thisMonth">本月</el-radio-button>
              <el-radio-button value="last3">近3个月</el-radio-button>
              <el-radio-button value="last6">近6个月</el-radio-button>
              <el-radio-button value="last12">近12个月</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="开票日期">
            <el-date-picker v-model="dateRange" type="daterange" start-placeholder="起" end-placeholder="止"
              format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:240px" @change="onDateRangeChange" />
          </el-form-item>
        </template>
        <el-form-item label="期间">
          <PeriodNavigator v-model="query.period" @change="fetchData" />
        </el-form-item>
        <el-form-item :label="tabType === 'INPUT' ? '供应商' : '客户'">
          <el-input v-model="query.partyName" clearable style="width:180px" :placeholder="tabType === 'INPUT' ? '供应商名称' : '客户名称'" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <template #empty>
          <el-empty v-if="scope === 'completed' && !dateRange" description="请先选择日期范围（快捷时段或自定义开票日期）查询已完成勾稽" />
        </template>
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
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const tabType = ref<'INPUT' | 'OUTPUT'>('INPUT')
const scope = ref<'pending' | 'completed'>('pending')
const quickDate = ref('')
const dateRange = ref<[string, string] | null>(null)
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
  // R1：已完成视图必须带开票日期范围才查询
  if (scope.value === 'completed' && !dateRange.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const scoped = scope.value === 'completed' ? ['PAID', 'PARTIAL'] : ['UNPAID']
    const params: any = { period: query.period || undefined }
    if (query.partyName) {
      if (tabType.value === 'INPUT') params.vendorName = query.partyName
      else params.customerName = query.partyName
    }
    const res = tabType.value === 'INPUT'
      ? await queryInputReconcile({ period: params.period })
      : await queryOutputReconcile({ period: params.period })
    let data = res || []
    data = data.filter((r: InvoiceReconcileVO) => r.reconcileStatus && scoped.includes(r.reconcileStatus))
    if (data.length) {
      if (query.partyName) {
        const kw = query.partyName.trim()
        data = data.filter((r: InvoiceReconcileVO) =>
          (tabType.value === 'INPUT' ? r.vendorName : r.customerName)?.includes(kw))
      }
      if (dateRange.value) {
        const [s, e] = dateRange.value
        data = data.filter((r: InvoiceReconcileVO) =>
          r.invoiceDate && r.invoiceDate >= s && r.invoiceDate <= e)
      }
    }
    list.value = data
  } finally {
    loading.value = false
  }
}

function onScopeChange() {
  quickDate.value = ''
  dateRange.value = null
  fetchData()
}
function onQuickDate() {
  const now = new Date()
  let start: Date
  switch (quickDate.value) {
    case 'thisMonth': start = new Date(now.getFullYear(), now.getMonth(), 1); break
    case 'last3': start = new Date(now.getFullYear(), now.getMonth() - 3, 1); break
    case 'last6': start = new Date(now.getFullYear(), now.getMonth() - 6, 1); break
    case 'last12': start = new Date(now.getFullYear(), now.getMonth() - 12, 1); break
    default: return
  }
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  dateRange.value = [start.toISOString().slice(0, 10), end.toISOString().slice(0, 10)]
  fetchData()
}
function onDateRangeChange() {
  quickDate.value = ''
  fetchData()
}

function onTabChange() {
  list.value = []
  fetchData()
}

function onReset() {
  query.period = ''
  query.partyName = ''
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
