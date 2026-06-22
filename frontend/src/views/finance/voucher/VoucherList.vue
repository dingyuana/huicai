<template>
  <div class="voucher-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">凭证管理</span>
        <div>
          <el-button type="primary" @click="goCreate">新增凭证</el-button>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" clearable style="width:120px" />
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

      <div class="batch-bar">
        <el-button text :disabled="!canBatchSubmit" @click="onBatchSubmit">批量提交</el-button>
        <el-button text :disabled="!canBatchAudit" @click="onBatchAudit">批量审核</el-button>
        <el-button text :disabled="!canBatchPost" @click="onBatchPost">批量记账</el-button>
      </div>

      <el-table
        :data="list"
        v-loading="loading"
        border
        stripe
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" :selectable="(row: VoucherVO) => row.status === 'DRAFT' || row.status === 'SUBMITTED' || row.status === 'AUDITED'" />
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
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="goDetail(row)">查看</el-button>
            <el-button text size="small" v-if="row.status === 'DRAFT'" @click="goEdit(row)">编辑</el-button>
            <el-button text size="small" v-if="row.status === 'DRAFT'" type="success" @click="onSubmit(row)">提交</el-button>
            <el-button text size="small" v-if="row.status === 'SUBMITTED'" type="primary" @click="onAudit(row)">审核</el-button>
            <el-button text size="small" v-if="row.status === 'SUBMITTED'" type="warning" @click="onReject(row)">驳回</el-button>
            <el-button text size="small" v-if="row.status === 'AUDITED'" type="warning" @click="onPost(row)">记账</el-button>
            <el-button text size="small" v-if="row.status === 'POSTED'" type="warning" @click="onUnpost(row)">反过账</el-button>
            <el-button text size="small" v-if="row.status === 'POSTED' || row.status === 'AUDITED'" type="danger" @click="onReverse(row)">红冲</el-button>
            <el-popconfirm v-if="row.status === 'DRAFT'" title="确认删除此凭证？" @confirm="onDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
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
import { ref, computed, onMounted } from 'vue'
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

const router = useRouter()
const loading = ref(false)
const list = ref<VoucherVO[]>([])
const total = ref(0)
const selectedRows = ref<VoucherVO[]>([])

const query = ref<VoucherQueryDTO>({
  period: '',
  status: '',
  keyword: '',
  current: 1,
  size: 20,
})

const canBatchSubmit = computed(() => selectedRows.value.some((r) => r.status === 'DRAFT'))
const canBatchAudit = computed(() => selectedRows.value.some((r) => r.status === 'SUBMITTED'))
const canBatchPost = computed(() => selectedRows.value.some((r) => r.status === 'AUDITED'))

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

async function fetchData() {
  loading.value = true
  try {
    const res = await getVoucherPage(query.value)
    list.value = res.records
    total.value = res.total
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.value.current = 1
  fetchData()
}

function onReset() {
  query.value = { period: '', status: '', keyword: '', current: 1, size: 20 }
  fetchData()
}

function onSelectionChange(rows: VoucherVO[]) {
  selectedRows.value = rows
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

async function onBatchSubmit() {
  const ids = selectedRows.value.filter((r) => r.status === 'DRAFT').map((r) => r.id)
  if (ids.length === 0) return
  await batchSubmitVouchers({ ids })
  ElMessage.success(`已提交 ${ids.length} 张凭证`)
  await fetchData()
}

async function onBatchAudit() {
  const ids = selectedRows.value.filter((r) => r.status === 'SUBMITTED').map((r) => r.id)
  if (ids.length === 0) return
  await batchAuditVouchers({ ids })
  ElMessage.success(`已审核 ${ids.length} 张凭证`)
  await fetchData()
}

async function onBatchPost() {
  const ids = selectedRows.value.filter((r) => r.status === 'AUDITED').map((r) => r.id)
  if (ids.length === 0) return
  await batchPostVouchers({ ids })
  ElMessage.success(`已记账 ${ids.length} 张凭证`)
  await fetchData()
}

onMounted(fetchData)
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
.batch-bar {
  margin-bottom: 12px;
}
.page-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
