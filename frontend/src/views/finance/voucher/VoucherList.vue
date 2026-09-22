<template>
  <div class="voucher-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">凭证管理</span>
        <div class="header-actions">
          <BatchActionBar
            :rows="selectedRows"
            :actions="BATCH_ACTIONS"
            :status-matrix="BATCH_STATUS_MATRIX"
            @action="onBatchAction"
            @clear="clearSelection"
          />
          <el-button type="primary" @click="goCreate">新增凭证</el-button>
          <el-button @click="handleExport">导出Excel</el-button>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </div>

      <!-- 统计卡片 -->
      <StatBar :items="[
        { label: '总凭证数', value: fmtNum(stats.totalCount || 0) },
        { label: '借方合计', value: `¥ ${fmtAmount(stats.totalDebit)}` },
        { label: '贷方合计', value: `¥ ${fmtAmount(stats.totalCredit)}` },
        { label: '已记账', value: fmtNum(stats.postedCount || 0) },
        { label: '草稿', value: fmtNum(stats.draftCount || 0) },
      ]" />

      <!-- 分类标签：待处理 / 已完成 -->
      <el-tabs v-model="scope" class="scope-root-tabs" @tab-change="onScopeChange">
        <el-tab-pane label="待处理" name="pending" />
        <el-tab-pane label="已完成" name="completed" />
      </el-tabs>

      <!-- 分类标签（按状态细分） -->
      <el-radio-group v-model="tabType" style="margin-bottom:12px" @change="onTabChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="DRAFT">草稿 ({{ stats.draftCount || 0 }})</el-radio-button>
        <el-radio-button value="SUBMITTED">已提交 ({{ stats.submittedCount || 0 }})</el-radio-button>
        <el-radio-button value="AUDITED">已审核 ({{ stats.auditedCount || 0 }})</el-radio-button>
        <el-radio-button value="POSTED">已记账 ({{ stats.postedCount || 0 }})</el-radio-button>
      </el-radio-group>

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
          <el-form-item label="日期范围">
            <el-date-picker
              v-model="dateRange" type="daterange" start-placeholder="起" end-placeholder="止"
              format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:240px" @change="onDateRangeChange" />
          </el-form-item>
        </template>
        <el-form-item label="期间">
          <PeriodNavigator v-model="query.period" @change="fetchData" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:130px">
            <el-option v-for="o in VOUCHER_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="凭证号/摘要" clearable style="width:180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="tableRef"
        :data="list"
        v-loading="loading"
        border
        stripe
        highlight-current-row
        @selection-change="onSelectionChange"
        @row-click="goDetail"
        style="cursor:pointer"
      >
        <template #empty>
          <el-empty
            v-if="scope === 'completed'"
            description="暂无已完成（已记账）凭证"
          />
          <el-empty
            v-else
            description="本期无待处理凭证。已记账凭证请切换到「已完成」标签查看"
          />
        </template>
        <el-table-column type="selection" width="48" :selectable="isBatchable" />
        <el-table-column prop="voucherNo" label="凭证号" width="160" />
        <el-table-column prop="period" label="期间" width="80" align="center" />
        <el-table-column prop="voucherTypeName" label="凭证类型" width="100" align="center" />
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="借方合计" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.totalDebit) }}</template>
        </el-table-column>
        <el-table-column label="贷方合计" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.totalCredit) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status) as 'success' | 'warning' | 'info' | 'primary'" size="small">
              {{ VOUCHER_STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdByName" label="制单人" width="80" align="center" />
        <el-table-column prop="createdAt" label="制单时间" width="160" />
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

      <BatchResultDialog v-model="resultVisible" :result="result" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getVoucherPage,
  submitVoucher,
  auditVoucher,
  postVoucher,
  deleteVoucher,
  reverseVoucher,
  rejectVoucher,
  unpostVoucher,
  batchSubmitVouchers,
  batchAuditVouchers,
  batchPostVouchers,
  VOUCHER_STATUS_MAP,
  VOUCHER_STATUS_OPTIONS,
  type VoucherVO,
  type VoucherQueryDTO,
} from '@/api/modules/voucher'
import { resolveDefaultPeriod } from '@/utils/period'
import { getAllPeriods } from '@/api/modules/period'
import BatchActionBar from '@/components/batch/BatchActionBar.vue'
import BatchResultDialog from '@/components/batch/BatchResultDialog.vue'
import { useBatchOperation, type BatchActionDef } from '@/composables/useBatchOperation'
import StatBar from '@/components/page/StatBar.vue'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const router = useRouter()
const loading = ref(false)
const list = ref<VoucherVO[]>([])
const total = ref(0)
const tableRef = ref()
const scope = ref<'pending' | 'completed'>('pending')
const quickDate = ref('')
const dateRange = ref<[string, string] | null>(null)
const periodsSet = ref<Set<string>>(new Set())

// P67 统一批量操作：状态矩阵 + every() 启用语义 + 统一结果弹窗
const BATCH_ACTIONS: BatchActionDef[] = [
  { key: 'submit', label: '批量提交' },
  { key: 'audit', label: '批量审核' },
  { key: 'post', label: '批量记账', needConfirm: true, confirmText: '记账后将计入正式账簿（可通过反过账修正）。确认执行批量记账？' },
]
const BATCH_STATUS_MATRIX: Record<string, string[]> = {
  DRAFT: ['submit'],
  SUBMITTED: ['audit'],
  AUDITED: ['post'],
}
const { selectedRows, result, resultVisible, onSelectionChange, clearSelection, run } = useBatchOperation({
  refresh: fetchData,
  clearer: () => tableRef.value?.clearSelection(),
})

function onBatchAction(key: string) {
  const def = BATCH_ACTIONS.find((a) => a.key === key)
  if (!def) return
  run(def, (ids) => {
    if (key === 'submit') return batchSubmitVouchers({ ids })
    if (key === 'audit') return batchAuditVouchers({ ids })
    return batchPostVouchers({ ids })
  })
}

// 分类标签
const tabType = ref('')

// scope 分区控制
const onScopeChange = () => {
  query.value.current = 1
  quickDate.value = ''
  dateRange.value = null
  fetchData()
}
const onQuickDate = () => {
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
const onDateRangeChange = () => {
  quickDate.value = ''
  fetchData()
}

// 汇总统计
const stats = reactive({
  totalCount: 0,
  totalDebit: 0,
  totalCredit: 0,
  draftCount: 0,
  submittedCount: 0,
  auditedCount: 0,
  postedCount: 0,
})

const query = ref<VoucherQueryDTO>({
  period: '',
  status: '',
  keyword: '',
  current: 1,
  size: 20,
})

/** 批量操作是否可选 */
function isBatchable(row: VoucherVO) {
  return row.status === 'DRAFT' || row.status === 'SUBMITTED' || row.status === 'AUDITED'
}

function statusType(s: string): '' | 'success' | 'warning' | 'info' | 'primary' {
  switch (s) {
    case 'DRAFT': return 'info'
    case 'SUBMITTED': return 'primary'
    case 'AUDITED': return 'warning'
    case 'POSTED': return 'success'
    default: return 'info'
  }
}

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtNum(v: number) {
  return v == null ? '0' : Number(v).toLocaleString('zh-CN')
}

async function fetchData() {
  const prevPeriod = query.value.period
  const validPeriod = await validatePeriod(query.value.period)
  if (!validPeriod) {
    query.value.period = prevPeriod
    loading.value = false
    return
  }
  loading.value = true
  try {
    // 分页查询（按当前分类标签过滤）
    const params: any = { ...query.value, scope: scope.value }
    if (tabType.value) params.status = tabType.value
    if (dateRange.value) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getVoucherPage(params)
    list.value = res.records
    total.value = res.total

    // 统计汇总（同受 scope/date 约束，R5）
    const allParams: any = { period: query.value.period, keyword: query.value.keyword, current: 1, size: 9999, scope: scope.value }
    if (dateRange.value) {
      allParams.startDate = dateRange.value[0]
      allParams.endDate = dateRange.value[1]
    }
    const allRes = await getVoucherPage(allParams)
    const allRecords = allRes.records || []
    stats.totalCount = allRes.total || 0
    stats.totalDebit = allRecords.reduce((s: number, r: VoucherVO) => s + Number(r.totalDebit || 0), 0)
    stats.totalCredit = allRecords.reduce((s: number, r: VoucherVO) => s + Number(r.totalCredit || 0), 0)
    stats.draftCount = allRecords.filter((r: VoucherVO) => r.status === 'DRAFT').length
    stats.submittedCount = allRecords.filter((r: VoucherVO) => r.status === 'SUBMITTED').length
    stats.auditedCount = allRecords.filter((r: VoucherVO) => r.status === 'AUDITED').length
    stats.postedCount = allRecords.filter((r: VoucherVO) => r.status === 'POSTED').length
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

async function validatePeriod(period: string): Promise<boolean> {
  if (!period) return true  // 空期间允许查询（由 API 返回空结果）
  if (periodsSet.value.size === 0) {
    try {
      const periods = await getAllPeriods()
      periodsSet.value = new Set(periods.map(p => p.periodCode))
    } catch {
      return true
    }
  }
  if (periodsSet.value.has(period)) return true
  ElMessage.warning(`期间 ${period} 不存在于系统中，请先在「基础数据-会计期间」创建该期间`)
  return false
}

function onSearch() {
  query.value.current = 1
  fetchData()
}

function onReset() {
  query.value = { period: '', status: '', keyword: '', current: 1, size: 20 }
  tabType.value = ''
  scope.value = 'pending'
  quickDate.value = ''
  dateRange.value = null
  fetchData()
}

function onTabChange() {
  query.value.current = 1
  fetchData()
}

function goCreate() {
  router.push({ name: 'VoucherEdit', query: { mode: 'create' } })
}

function goEdit(row: VoucherVO) {
  router.push({ name: 'VoucherEdit', query: { mode: 'edit', id: String(row.id) } })
}

function goDetail(row: VoucherVO) {
  router.push({ name: 'VoucherDetail', query: { id: String(row.id) } })
}

async function onSubmit(row: VoucherVO) {
  await submitVoucher(row.id)
  ElMessage.success('提交成功')
  await fetchData()
}

async function onAudit(row: VoucherVO) {
  await auditVoucher(row.id)
  ElMessage.success('审核成功')
  await fetchData()
}

async function onPost(row: VoucherVO) {
  await postVoucher(row.id)
  ElMessage.success('记账成功')
  await fetchData()
}

async function onDelete(row: VoucherVO) {
  await deleteVoucher(row.id)
  ElMessage.success('删除成功')
  await fetchData()
}

async function onReverse(row: VoucherVO) {
  await reverseVoucher(row.id)
  ElMessage.success('红冲成功，请前往草稿提交')
  await fetchData()
}

async function onReject(row: VoucherVO) {
  const { value: reason } = await (await import('element-plus')).ElMessageBox.prompt(
    '请输入驳回原因', '驳回凭证', { inputType: 'textarea', inputValidator: (v: string) => !!v?.trim(), inputErrorMessage: '原因不能为空' }
  ).catch(() => ({ value: null }))
  if (!reason) return
  await rejectVoucher(row.id, reason)
  ElMessage.success('驳回成功')
  await fetchData()
}

async function onUnpost(row: VoucherVO) {
  await unpostVoucher(row.id)
  ElMessage.success('反过账成功')
  await fetchData()
}

onMounted(async () => {
  query.value.period = await resolveDefaultPeriod()
  scope.value = 'pending'
  fetchData()
})

/** 导出凭证到 Excel */
async function handleExport() {
  try {
    // 构建查询参数（使用当前筛选条件）
    const params = new URLSearchParams()
    if (query.value.period) params.set('period', query.value.period)
    if (query.value.status) params.set('status', query.value.status)
    if (query.value.voucherTypeId) params.set('voucherTypeId', String(query.value.voucherTypeId))
    if (query.value.keyword) params.set('keyword', query.value.keyword)

    const token = localStorage.getItem('token')
    const res = await fetch(`/api/base/voucher/v1/vouchers/export?${params}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!res.ok) throw new Error('导出失败')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const now = new Date()
    const dateStr = `${now.getFullYear()}${String(now.getMonth()+1).padStart(2,'0')}${String(now.getDate()).padStart(2,'0')}`
    a.download = `vouchers_${dateStr}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败: ' + ((e as any)?.message || '未知错误'))
  }
}
</script>

<style scoped>
.voucher-list .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.filter-form {
  margin-bottom: 12px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.page-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>