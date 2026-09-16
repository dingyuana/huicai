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

      <el-table :data="list" v-loading="loading" border stripe @row-click="onRowClick">
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
import { ref, onMounted } from 'vue'
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
const scope = ref<'pending' | 'completed'>('pending')

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

function reconcileTagType(row: BusinessDocVO) {
  const amt = Number(row.amount) || 0
  const settled = Number(row.settledAmount) || 0
  if (amt === 0) return 'none'
  if (settled === 0) return 'danger'
  if (Math.abs(settled - amt) < 0.001) return 'success'
  return 'warning'
}

async function fetchCounts() {
  try {
    const all = await getBusinessDocPage({ current: 1, size: 1, scope: scope.value }) as any
    totalCount.value = all.total || 0
    const counts: Record<string, number> = {}
    for (const key of Object.keys(DOC_TYPE_LABELS)) {
      const res = await getBusinessDocPage({ docType: key, current: 1, size: 1, scope: scope.value }) as any
      counts[key] = res.total || 0
    }
    docTypeCounts.value = counts
  } catch { /* ignore */ }
}

async function fetchData() {
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
  fetchCounts()
  fetchData()
}

function onSearch() {
  query.value.current = 1
  query.value.startDate = dateRange.value?.[0] || undefined
  query.value.endDate = dateRange.value?.[1] || undefined
  fetchData()
}
function onReset() {
  query.value = { current: 1, size: 20 }
  scope.value = 'pending'
  dateRange.value = null
  fetchData()
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
