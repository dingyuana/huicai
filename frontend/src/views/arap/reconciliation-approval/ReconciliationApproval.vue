<template>
  <div class="reconciliation-approval">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">核销审批</span>
        <el-button @click="fetchData">刷新</el-button>
      </div>

      <div class="toolbar">
        <el-form :model="query" inline>
          <el-form-item label="来源类型">
            <el-select v-model="query.sourceDocType" placeholder="全部" style="width:160px" clearable @change="fetchData">
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

      <el-table :data="list" v-loading="loading" border stripe style="width:100%">
        <el-table-column type="index" label="序号" width="50" />
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ sourceLabel(row.sourceDocType) }}</el-tag>
            <span style="margin-left:4px">#{{ row.sourceDocId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="目标单据" width="140">
          <template #default="{ row }">
            <el-tag :type="row.targetDocType==='INVOICE_OUT'?'success':'warning'" size="small">
              {{ row.targetDocType==='INVOICE_OUT'?'应收':'应付' }}
            </el-tag>
            <span style="margin-left:4px">#{{ row.targetDocId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="核销金额" width="130" align="right">
          <template #default="{ row }">{{ fmtAmount(row.allocatedAmount) }}</template>
        </el-table-column>
        <el-table-column label="匹配度" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.matchScore != null" :type="row.matchScore >= 0.95 ? 'success' : 'warning'" size="small">
              {{ (row.matchScore * 100).toFixed(0) }}%
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="匹配方式" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.matchMethod==='AUTO'?'primary':'info'" size="small">
              {{ row.matchMethod==='AUTO'?'自动':'手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="核销时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="success" @click="onApprove(row)">审批通过</el-button>
            <el-button text size="small" type="danger" @click="onReject(row)">驳回</el-button>
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
import { pageReconLogs, approveReconciliation, rejectReconciliation } from '@/api/modules/arapSettlement'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = ref({ sourceDocType: '', current: 1, size: 20 })

function fmtAmount(v: number | null | undefined) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function sourceLabel(s: string) {
  const map: Record<string, string> = { bank_txn: '银行流水', receipt: '收款单', payment: '付款单' }
  return map[s] || s
}

async function fetchData() {
  loading.value = true
  try {
    const res: any = await pageReconLogs(query.value)
    // 只显示 CONFIRMED 状态的待审批记录
    const all = res.records || []
    list.value = all.filter((r: any) => r.status === 'CONFIRMED')
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function onApprove(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认审批通过该核销？\n金额：¥${fmtAmount(row.allocatedAmount)}`,
      '审批确认',
      { confirmButtonText: '通过', cancelButtonText: '取消', type: 'warning' }
    )
    await approveReconciliation(row.id)
    ElMessage.success('审批通过')
    await fetchData()
  } catch { /* cancel */ }
}

async function onReject(row: any) {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      '请输入驳回原因',
      '驳回核销',
      {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '驳回原因（必填）',
        inputValidator: (v: string) => (v && v.trim() ? true : '请输入驳回原因'),
      }
    )
    await rejectReconciliation(row.id, reason || '')
    ElMessage.success('已驳回')
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
