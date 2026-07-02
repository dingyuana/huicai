<template>
  <div class="reconciliation-exception">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">核销异常池</span>
        <el-button @click="fetchData">刷新</el-button>
      </div>

      <div class="toolbar">
        <el-form :model="query" inline>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" style="width:120px" clearable @change="fetchData">
              <el-option label="全部" value="" />
              <el-option label="待处理" value="OPEN" />
              <el-option label="已解决" value="RESOLVED" />
              <el-option label="已忽略" value="IGNORED" />
            </el-select>
          </el-form-item>
          <el-form-item label="异常类型">
            <el-select v-model="query.exceptionType" placeholder="全部" style="width:160px" clearable @change="fetchData">
              <el-option label="全部" value="" />
              <el-option label="客商不匹配" value="PARTY_MISMATCH" />
              <el-option label="金额不匹配" value="AMOUNT_MISMATCH" />
              <el-option label="找不到可核销发票" value="INVOICE_NOT_FOUND" />
              <el-option label="匹配失败" value="MATCH_FAILED" />
              <el-option label="需人工审批" value="APPROVAL_REQUIRED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="fetchData">查询</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table :data="list" v-loading="loading" border stripe style="width:100%">
        <el-table-column type="index" label="序号" width="50" />
        <el-table-column label="异常类型" width="130">
          <template #default="{ row }">
            <el-tag :type="exceptionTypeTag(row.exceptionType)" size="small">
              {{ exceptionTypeLabel(row.exceptionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="exceptionReason" label="异常原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ sourceLabel(row.sourceDocType) }}</el-tag>
            <span style="margin-left:4px">#{{ row.sourceDocId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重试" width="60" align="center">
          <template #default="{ row }">{{ row.retryCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'OPEN'">
              <el-button text size="small" type="primary" @click="onResolve(row)">解决</el-button>
              <el-button text size="small" type="warning" @click="onIgnore(row)">忽略</el-button>
              <el-button text size="small" type="success" @click="onRetry(row)">重试</el-button>
            </template>
            <span v-else style="color:#909399;font-size:12px">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-if="total > 0"
          v-model:current="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageReconciliationExceptions,
  resolveException,
  ignoreException,
  retryException,
} from '@/api/modules/arapSettlement'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = ref({ status: 'OPEN', exceptionType: '', current: 1, size: 20 })

function fmtAmount(v: number | null | undefined) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function sourceLabel(s: string) {
  const map: Record<string, string> = { bank_txn: '银行流水', receipt: '收款单', payment: '付款单' }
  return map[s] || s
}

function exceptionTypeLabel(t: string) {
  const map: Record<string, string> = {
    PARTY_MISMATCH: '客商不匹配', AMOUNT_MISMATCH: '金额不匹配',
    INVOICE_NOT_FOUND: '找不到发票', MATCH_FAILED: '匹配失败',
    APPROVAL_REQUIRED: '需人工审批',
  }
  return map[t] || t
}

function exceptionTypeTag(t: string) {
  switch (t) {
    case 'PARTY_MISMATCH': return 'danger'
    case 'AMOUNT_MISMATCH': return 'warning'
    case 'INVOICE_NOT_FOUND': return 'info'
    case 'MATCH_FAILED': return 'warning'
    case 'APPROVAL_REQUIRED': return 'primary'
    default: return 'info'
  }
}

function statusTag(s: string) {
  if (s === 'OPEN') return 'danger'
  if (s === 'RESOLVED') return 'success'
  return 'info'
}

function statusLabel(s: string) {
  const map: Record<string, string> = { OPEN: '待处理', RESOLVED: '已解决', IGNORED: '已忽略' }
  return map[s] || s
}

async function fetchData() {
  loading.value = true
  try {
    const params: any = { current: query.value.current, size: query.value.size }
    if (query.value.status) params.status = query.value.status
    if (query.value.exceptionType) params.exceptionType = query.value.exceptionType
    const res: any = await pageReconciliationExceptions(params)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function onResolve(row: any) {
  try {
    const { value: remark } = await ElMessageBox.prompt(
      '确认标记为已解决？',
      '解决异常',
      { confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '备注（可选）' }
    )
    await resolveException(row.id, remark || '')
    ElMessage.success('已解决')
    await fetchData()
  } catch { /* cancel */ }
}

async function onIgnore(row: any) {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      '请输入忽略原因',
      '忽略异常',
      {
        confirmButtonText: '确认忽略',
        cancelButtonText: '取消',
        inputPlaceholder: '忽略原因（必填）',
        inputValidator: (v: string) => (v && v.trim() ? true : '请输入忽略原因'),
      }
    )
    await ignoreException(row.id, reason || '')
    ElMessage.success('已忽略')
    await fetchData()
  } catch { /* cancel */ }
}

async function onRetry(row: any) {
  try {
    await ElMessageBox.confirm('确认重试该异常核销？', '重试确认', { confirmButtonText: '重试', cancelButtonText: '取消' })
    await retryException(row.id)
    ElMessage.success('重试完成')
    await fetchData()
  } catch { /* cancel */ }
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.toolbar { margin-bottom:12px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
