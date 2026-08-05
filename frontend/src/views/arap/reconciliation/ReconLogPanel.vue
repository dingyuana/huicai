<template>
  <div class="recon-log-panel">
    <div class="toolbar">
      <el-form :model="logQuery" inline>
        <el-form-item label="来源类型">
          <el-select v-model="logQuery.sourceDocType" placeholder="全部" style="width:160px" clearable @change="fetchData">
            <el-option label="全部" value="" />
            <el-option label="银行流水" value="bank_txn" />
            <el-option label="收款单" value="receipt" />
            <el-option label="付款单" value="payment" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="reconLogList" v-loading="logLoading" border stripe style="width:100%">
      <el-table-column type="index" label="序号" width="50" />
      <el-table-column prop="id" label="日志ID" width="70" />
      <el-table-column label="来源" width="120">
        <template #default="{row}">
          <el-tag size="small">{{ sourceLabel(row.sourceDocType) }}</el-tag>
          <span style="margin-left:4px">#{{ row.sourceDocId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="目标单据" width="140">
        <template #default="{row}">
          <el-tag :type="row.targetDocType==='INVOICE_OUT'?'success':'warning'" size="small">
            {{ row.targetDocType==='INVOICE_OUT'?'应收':'应付' }}
          </el-tag>
          <span style="margin-left:4px">#{{ row.targetDocId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="核销金额" width="130" align="right">
        <template #default="{row}">{{ fmtAmount(row.allocatedAmount) }}</template>
      </el-table-column>
      <el-table-column label="匹配度" width="90" align="center">
        <template #default="{row}">
          <el-tag v-if="row.matchScore != null" :type="row.matchScore >= 0.95 ? 'success' : 'warning'" size="small">
            {{ (row.matchScore * 100).toFixed(0) }}%
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="匹配方式" width="90" align="center">
        <template #default="{row}">
          <el-tag :type="row.matchMethod==='AUTO'?'primary':'info'" size="small">
            {{ row.matchMethod==='AUTO'?'自动':'手动' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{row}">
          <el-tag :type="row.status==='CONFIRMED'?'success':'danger'" size="small">
            {{ row.status==='CONFIRMED'?'已核销':'已取消' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{row}">
          <el-button v-if="row.status==='CONFIRMED'" text size="small" type="warning" @click="onReverseRecon(row as ReconciliationLog)">反核销</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination v-model:current-page="logQuery.current" v-model:page-size="logQuery.size"
        :total="logTotal" layout="total,prev,pager,next" @current-change="fetchData" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageReconLogs, reverseRecon, type ReconciliationLog } from '@/api/modules/arapSettlement'

function fmtAmount(v: number | null | undefined) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function sourceLabel(s: string) {
  const map: Record<string, string> = { bank_txn: '银行流水', receipt: '收款单', payment: '付款单' }
  return map[s] || s
}

const logLoading = ref(false)
const reconLogList = ref<ReconciliationLog[]>([])
const logTotal = ref(0)
const logQuery = ref({ sourceDocType: '', current: 1, size: 20 })

async function fetchData() {
  logLoading.value = true
  try {
    const res: any = await pageReconLogs(logQuery.value)
    reconLogList.value = res.records || []
    logTotal.value = res.total || 0
  } finally { logLoading.value = false }
}

onMounted(() => { fetchData() })

defineExpose({ fetchData })

async function onReverseRecon(row: ReconciliationLog) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入反核销原因', '反核销确认', {
      confirmButtonText: '确认反核销',
      cancelButtonText: '取消',
      inputPlaceholder: '反核销原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '请输入反核销原因'),
    })
    await reverseRecon(row.id, reason || '')
    ElMessage.success('反核销成功')
    await fetchData()
  } catch { /* cancel */ }
}
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; margin-bottom:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
