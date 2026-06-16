<template>
  <div class="receivable-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">应收明细</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="客户">
          <el-select v-model="query.customerId" filterable clearable placeholder="全部客户" style="width:200px" @change="fetchData">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" clearable @clear="fetchData" @change="fetchData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border :row-class-name="rowClassName">
        <el-table-column prop="period" label="期间" width="90" align="center" />
        <el-table-column prop="txDate" label="发生日期" width="120" />
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="已核销" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.settledAmount) }}</template>
        </el-table-column>
        <el-table-column label="未核销" width="140" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.unsettledAmount) > 0 ? '#f56c6c' : '#67c23a' }">
              {{ fmtAmount(row.unsettledAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="120" />
        <el-table-column label="客户" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || row.customerId || '-' }}</template>
        </el-table-column>
        <el-table-column label="摘要" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.enrichedSummary || row.summary || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="应收明细" width="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID" :span="2">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.customerName || detail.customerId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ detail.period }}</el-descriptions-item>
          <el-descriptions-item label="发生日期">{{ detail.txDate }}</el-descriptions-item>
          <el-descriptions-item label="到期日">{{ detail.dueDate }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ fmtAmount(detail.amount) }}</el-descriptions-item>
          <el-descriptions-item label="已核销">{{ fmtAmount(detail.settledAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未核销">{{ fmtAmount(detail.unsettledAmount) }}</el-descriptions-item>
          <el-descriptions-item label="单据ID">{{ detail.docId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="凭证ID">{{ detail.voucherId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detail.enrichedSummary || detail.summary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageReceivable, listCustomer } from '@/api/modules/arap'
import request from '@/api/request'

const customers = ref<any[]>([])

const query = reactive({ customerId: undefined as number | undefined, period: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const today = new Date().toISOString().slice(0, 10)

const rowClassName = ({ row }: { row: any }) => {
  if (row.dueDate && row.dueDate < today && Number(row.unsettledAmount) > 0) {
    return 'row-overdue'
  }
  return ''
}

const fetchData = async () => {
  loading.value = true
  try {
    const params: any = { current: query.current, size: query.size }
    if (query.customerId) params.customerId = query.customerId
    if (query.period) params.period = query.period
    const res: any = await pageReceivable(params)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

// 详情
const detailVisible = ref(false)
const detail = ref<any>(null)

const openDetail = async (row: any) => {
  try {
    detail.value = await request.get(`/receivables/${row.id}`)
    detailVisible.value = true
  } catch (e: any) {
    detail.value = null
  }
}

onMounted(async () => {
  customers.value = (await listCustomer()) as any[]
  fetchData()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 16px; font-weight: 600; }
.page-pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>

<style>
.el-table .row-overdue {
  --el-table-tr-bg-color: #fef0f0;
}
.el-table .row-overdue:hover > td {
  background-color: #fde2e2 !important;
}
</style>
