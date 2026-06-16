<template>
  <div class="doc-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">业务单据</span>
        <div>
          <el-button type="primary" @click="goCreate">新增单据</el-button>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="单据类型">
          <el-select v-model="query.docType" placeholder="全部" clearable style="width:140px">
            <el-option v-for="(label, value) in DOC_TYPE_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:130px">
            <el-option v-for="(label, value) in DOC_STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" clearable style="width:120px" />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="单据号/摘要" clearable style="width:180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="docNo" label="单据号" width="160" />
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
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status) as 'success' | 'warning' | 'info' | 'primary' | 'danger'" size="small">
              {{ DOC_STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭证ID" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.voucherId">#{{ row.voucherId }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="goDetail(row)">查看</el-button>
            <el-button text size="small" v-if="row.status === 'DRAFT'" @click="goEdit(row)">编辑</el-button>
            <el-button text size="small" v-if="row.status === 'DRAFT'" type="success" @click="onSubmit(row)">提交</el-button>
            <el-button text size="small" v-if="row.status === 'SUBMITTED'" type="primary" @click="onApprove(row)">审批</el-button>
            <el-button text size="small" v-if="row.status === 'SUBMITTED'" type="danger" @click="onReject(row)">驳回</el-button>
            <el-button text size="small" v-if="row.status === 'APPROVED' && !row.voucherId" type="warning" @click="onGenerateVoucher(row)">生成凭证</el-button>
            <el-button text size="small" v-if="(row.status === 'APPROVED' || row.status === 'VOUCHERED') && !row.voucherId" type="danger" @click="onReverse(row)">红冲</el-button>
            <el-popconfirm v-if="row.status === 'DRAFT'" title="确认删除？" @confirm="onDelete(row)">
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getBusinessDocPage, deleteBusinessDoc, submitBusinessDoc,
  approveBusinessDoc, rejectBusinessDoc, generateVoucherFromDoc,
  reverseBusinessDoc, DOC_TYPE_LABELS, DOC_STATUS_LABELS,
  type BusinessDocVO, type BusinessDocQuery,
} from '@/api/modules/businessDoc'

const router = useRouter()
const loading = ref(false)
const list = ref<BusinessDocVO[]>([])
const total = ref(0)
const query = ref<BusinessDocQuery>({ current: 1, size: 20 })

function statusType(s: string) {
  switch (s) {
    case 'DRAFT': return 'info'
    case 'SUBMITTED': return 'primary'
    case 'APPROVED': return 'warning'
    case 'VOUCHERED': return 'success'
    case 'REJECTED': return 'danger'
    case 'CLOSED': return 'info'
    default: return 'info'
  }
}

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBusinessDocPage(query.value)
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
  query.value = { current: 1, size: 20 }
  fetchData()
}

function goCreate() {
  router.push({ name: 'BusinessDocEdit', query: { mode: 'create' } })
}
function goEdit(row: BusinessDocVO) {
  router.push({ name: 'BusinessDocEdit', query: { mode: 'edit', id: String(row.id) } })
}
function goDetail(row: BusinessDocVO) {
  router.push({ name: 'BusinessDocDetail', query: { id: String(row.id) } })
}

async function onDelete(row: BusinessDocVO) {
  await deleteBusinessDoc(row.id)
  ElMessage.success('删除成功')
  await fetchData()
}
async function onSubmit(row: BusinessDocVO) {
  await submitBusinessDoc(row.id)
  ElMessage.success('提交成功')
  await fetchData()
}
async function onApprove(row: BusinessDocVO) {
  await approveBusinessDoc(row.id)
  ElMessage.success('审批成功')
  await fetchData()
}
async function onReject(row: BusinessDocVO) {
  await rejectBusinessDoc(row.id)
  ElMessage.success('已驳回')
  await fetchData()
}
async function onGenerateVoucher(row: BusinessDocVO) {
  await generateVoucherFromDoc(row.id)
  ElMessage.success('凭证已生成, 请前往凭证管理提交记账')
  await fetchData()
}
async function onReverse(row: BusinessDocVO) {
  await reverseBusinessDoc(row.id)
  ElMessage.success('红冲成功, 新单据为草稿状态')
  await fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.doc-list .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
.page-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
