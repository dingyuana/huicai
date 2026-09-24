<template>
  <div class="doc-list">
    <el-card shadow="never">
    <PageHeader title="业务单据">
      <template #actions>
        <el-button type="primary" @click="goCreate">新增单据</el-button>
        <el-button @click="fetchData">刷新</el-button>
      </template>
    </PageHeader>
      <el-tabs v-model="scope" class="scope-root-tabs" @tab-change="onScopeChange">
        <el-tab-pane label="待处理" name="pending" />
        <el-tab-pane label="已完成" name="completed" />
      </el-tabs>
    <FilterBar :model="query">
        <template v-if="scope === 'completed'">
        <el-form-item label="快捷时段">
          <el-radio-group v-model="quickDate" size="small" @change="onQuickDate">
            <el-radio-button value="thisMonth">本月</el-radio-button>
            <el-radio-button value="last3">近3个月</el-radio-button>
            <el-radio-button value="last6">近6个月</el-radio-button>
            <el-radio-button value="last12">近12个月</el-radio-button>
          </el-radio-group>
        </el-form-item>
        </template>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:130px">
            <el-option v-for="(label, value) in DOC_STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            start-placeholder="起"
            end-placeholder="止"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width:240px"
            @change="onDateRangeChange"
          />
        </el-form-item>
        <el-form-item label="金额">
          <div style="display:flex;align-items:center;gap:6px">
            <el-input-number v-model="query.amountMin" :min="0" :precision="2" controls-position="right" style="width:130px" placeholder="最小" />
            <span>~</span>
            <el-input-number v-model="query.amountMax" :min="0" :precision="2" controls-position="right" style="width:130px" placeholder="最大" />
          </div>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="单据号/摘要" clearable style="width:180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </FilterBar>

      <el-radio-group v-model="query.docType" class="doc-type-tabs" @change="onSearch">
        <el-radio-button :value="''">全部 ({{ totalCount }})</el-radio-button>
        <el-radio-button
          v-for="([docType, typeLabel]) in Object.entries(DOC_TYPE_LABELS)"
          :key="docType"
          :value="docType">
          {{ typeLabel }} ({{ docTypeCounts[docType] || 0 }})
        </el-radio-button>
      </el-radio-group>

      <el-table :data="list" v-loading="loading" border stripe show-summary :summary-method="summaryMethod" @row-click="onRowClick">
        <template #empty>
          <el-empty v-if="scope === 'completed' && !dateRange" description="请先选择日期范围（快捷时段或自定义）查询已完成单据" />
        </template>
        <el-table-column label="单据号" width="160">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="goDetail(row as BusinessDocVO)">{{ row.docNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">{{ DOC_TYPE_LABELS[row.docType] || row.docType }}</template>
        </el-table-column>
        <el-table-column prop="docDate" label="单据日期" width="120" />
        <el-table-column prop="period" label="期间" width="80" align="center" />
        <el-table-column label="摘要" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.enrichedSummary || row.summary || '-' }}</template>
        </el-table-column>
        <el-table-column label="客户/供应商" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.customerName || row.supplierName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="已核销" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="reconcileTagType(row as BusinessDocVO) === 'success'" type="success" size="small">{{ fmtAmount(row.settledAmount) }}</el-tag>
            <el-tag v-else-if="reconcileTagType(row as BusinessDocVO) === 'warning'" type="warning" size="small">{{ fmtAmount(row.settledAmount) }}</el-tag>
            <el-tag v-else-if="reconcileTagType(row as BusinessDocVO) === 'danger'" type="danger" size="small">未核销</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="未核销" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.unsettledAmount) > 0 ? '#f56c6c' : '#67c23a', fontWeight: Number(row.unsettledAmount) > 0 ? '600' : 'normal' }">
              {{ fmtAmount(row.unsettledAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="源单号" width="150" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="row.sourceDocNo" type="primary" :underline="false" @click="goDetailByNo(row.sourceDocNo)">
              {{ row.sourceDocNo }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="到期日" width="120" align="center" prop="dueDate" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status) as 'success' | 'warning' | 'info' | 'primary' | 'danger'" size="small">
              {{ DOC_STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭证号" width="140" align="center">
          <template #default="{ row }">
            <span v-if="row.voucherNo">{{ row.voucherNo }}</span>
            <span v-else-if="row.voucherId">#{{ row.voucherId }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next, jumper"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getBusinessDocPage, DOC_TYPE_LABELS, DOC_STATUS_LABELS,
  type BusinessDocVO, type BusinessDocQuery,
} from '@/api/modules/businessDoc'
import PageHeader from '@/components/page/PageHeader.vue'
import FilterBar from '@/components/page/FilterBar.vue'
const router = useRouter()
const loading = ref(false)
const list = ref<BusinessDocVO[]>([])
const total = ref(0)
const totalCount = ref(0)
const docTypeCounts = ref<Record<string, number>>({})
const query = ref<BusinessDocQuery>({ current: 1, size: 20 })
const dateRange = ref<[string, string] | null>(null)
const quickDate = ref('')
const scope = ref<'pending' | 'completed'>('pending')

function fmtDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function onDateRangeChange(val: [string, string] | null) {
  query.value.startDate = val?.[0]
  query.value.endDate = val?.[1]
  quickDate.value = ''
  onSearch()
}

// 快捷时段：本月 / 近3月 / 近6月 / 近12月
function onQuickDate(val: string | number | boolean | undefined) {
  if (typeof val !== 'string') return
  const today = new Date()
  const end = fmtDate(today)
  let start: Date
  if (val === 'thisMonth') start = new Date(today.getFullYear(), today.getMonth(), 1)
  else if (val === 'last3') start = new Date(today.getFullYear(), today.getMonth() - 3, today.getDate())
  else if (val === 'last6') start = new Date(today.getFullYear(), today.getMonth() - 6, today.getDate())
  else start = new Date(today.getFullYear() - 1, today.getMonth(), today.getDate())
  dateRange.value = [fmtDate(start), end]
  query.value.startDate = fmtDate(start)
  query.value.endDate = end
  onSearch()
}

function statusType(s: string) {
  switch (s) {
    case 'DRAFT': return 'info'
    case 'SUBMITTED': return 'primary'
    case 'APPROVED': return 'warning'
    case 'VOUCHERED': return 'success'
    case 'PARTIALLY_RECONCILED': return 'warning'
    case 'FULLY_RECONCILED': return 'success'
    case 'REVERSED': return 'danger'
    case 'REJECTED': return 'danger'
    case 'CLOSED': return 'info'
    default: return 'info'
  }
}

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 列索引: 0单据号 6金额 7已核销 8未核销（模板列无 prop，按 index 取）
function summaryMethod({ columns, data }: { columns: unknown[]; data: BusinessDocVO[] }) {
  const sumOf = (key: 'amount' | 'settledAmount' | 'unsettledAmount') =>
    data.reduce((s, r) => s + (Number(r[key]) || 0), 0)
  return columns.map((_, index) => {
    if (index === 0) return data.length < total.value ? '本页合计' : '合计'
    if (index === 6) return fmtAmount(sumOf('amount'))
    if (index === 7) return fmtAmount(sumOf('settledAmount'))
    if (index === 8) return fmtAmount(sumOf('unsettledAmount'))
    return ''
  })
}

function reconcileTagType(row: BusinessDocVO) {
  const amt = Number(row.amount) || 0
  const settled = Number(row.settledAmount) || 0
  if (amt === 0) return 'none'
  if (settled === 0) return 'danger'
  if (Math.abs(settled - amt) < 0.001) return 'success'
  return 'warning'
}

async function fetchCounts() {
  // R1/R5: 已完成视图必须有日期条件，避免全量统计（网络阻塞）
  if (scope.value === 'completed' && (!query.value.startDate || !query.value.endDate)) {
    totalCount.value = 0
    docTypeCounts.value = {}
    return
  }
  try {
    const all = await getBusinessDocPage({ current: 1, size: 1, scope: scope.value, startDate: query.value.startDate, endDate: query.value.endDate }) as any
    totalCount.value = all.total || 0
    const counts: Record<string, number> = {}
    for (const key of Object.keys(DOC_TYPE_LABELS)) {
      const res = await getBusinessDocPage({ docType: key, current: 1, size: 1, scope: scope.value, startDate: query.value.startDate, endDate: query.value.endDate }) as any
      counts[key] = res.total || 0
    }
    docTypeCounts.value = counts
  } catch { /* ignore */ }
}

async function fetchData() {
  // R1: 已完成视图必须有日期条件，否则不发起请求（展示空态提示）
  if (scope.value === 'completed' && (!query.value.startDate || !query.value.endDate)) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const res = await getBusinessDocPage({ ...query.value, scope: scope.value })
    list.value = res.records
    total.value = res.total
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function onScopeChange() {
  query.value.current = 1
  query.value.startDate = dateRange.value?.[0] || undefined
  query.value.endDate = dateRange.value?.[1] || undefined
  fetchCounts()
  fetchData()
}

function onSearch() {
  query.value.current = 1
  query.value.startDate = dateRange.value?.[0] || undefined
  query.value.endDate = dateRange.value?.[1] || undefined
  fetchData()
  fetchCounts()
}
function onReset() {
  query.value = { current: 1, size: 20 }
  scope.value = 'pending'
  dateRange.value = null
  quickDate.value = ''
  fetchData()
  fetchCounts()
}

function goCreate() {
  router.push({ name: 'BusinessDocEdit', query: { mode: 'create' } })
}
function goDetail(row: BusinessDocVO) {
  router.push({ name: 'BusinessDocDetail', query: { id: String(row.id) } })
}

function onRowClick(row: BusinessDocVO, column: unknown, event: Event) {
  // 排除单据号链接、源单号链接等可交互元素，避免误触
  const target = event.target as HTMLElement
  if (target.closest('.el-button, .el-link, .el-popconfirm')) return
  goDetail(row)
}

async function goDetailByNo(docNo: string) {
  try {
    const res = await getBusinessDocPage({ keyword: docNo, current: 1, size: 1 })
    if (res.records && res.records.length > 0) {
      router.push({ name: 'BusinessDocDetail', query: { id: String(res.records[0].id) } })
    } else {
      ElMessage.warning('未找到关联单据')
    }
  } catch { /* ignore */ }
}

onMounted(async () => {
  await fetchCounts()
  await fetchData()
})

// keep-alive 从详情/编辑返回时只刷数据，不重置 query/dateRange/scope 筛选态
onActivated(async () => {
  await fetchCounts()
  await fetchData()
})
</script>

<style scoped>
.page-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.doc-type-tabs {
  margin-bottom: 12px;
}
.scope-root-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
.scope-root-tabs :deep(.el-tabs__item) { font-size: 15px; font-weight: 600; padding: 0 24px; height: 42px; line-height: 42px; }
</style>
