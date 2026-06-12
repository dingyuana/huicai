<template>
  <div class="receivable-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">应收明细</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
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
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageReceivable } from '@/api/modules/arap'

const query = reactive({ period: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageReceivable(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>
