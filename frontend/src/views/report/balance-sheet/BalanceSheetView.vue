<template>
  <div class="balance-sheet">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">资产负债表</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="onExport">导出</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="result" :title="result.balanced ? '资产=负债+所有者权益, 平衡 ✓' : '⚠ 资产≠负债+所有者权益, 请检查!'" :type="result.balanced ? 'success' : 'error'" show-icon :closable="false" style="margin-bottom: 16px" />

      <el-row :gutter="20" v-if="result">
        <el-col :span="12">
          <h3>资产</h3>
          <el-table :data="result.assets" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="140" />
            <el-table-column label="余额" align="right" width="140">
              <template #default="{ row }">{{ fmtAmount(row.end_balance) }}</template>
            </el-table-column>
          </el-table>
          <div class="total-row">
            <span>资产合计:</span>
            <span class="total-amount">{{ fmtAmount(result.totalAssets) }}</span>
          </div>
        </el-col>
        <el-col :span="12">
          <h3>负债</h3>
          <el-table :data="result.liabilities" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="140" />
            <el-table-column label="余额" align="right" width="140">
              <template #default="{ row }">{{ fmtAmount(row.end_balance) }}</template>
            </el-table-column>
          </el-table>
          <h3 style="margin-top: 16px">所有者权益</h3>
          <el-table :data="result.equity" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="140" />
            <el-table-column label="余额" align="right" width="140">
              <template #default="{ row }">{{ fmtAmount(row.end_balance) }}</template>
            </el-table-column>
          </el-table>
          <div class="total-row">
            <span>负债+权益合计:</span>
            <span class="total-amount">{{ fmtAmount(result.totalLiabEquity) }}</span>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { balanceSheet } from '@/api/modules/report'

const query = reactive({ period: dayjs().format('YYYYMM') })
const result = ref<any>(null)
const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  if (!query.period) return
  result.value = await balanceSheet(query.period)
}

const onExport = () => {
  // 导出功能待实现
}

onMounted(fetchData)
</script>

<style scoped>
.total-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  margin-top: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-weight: 600;
}
.total-amount {
  color: #409eff;
}
</style>
