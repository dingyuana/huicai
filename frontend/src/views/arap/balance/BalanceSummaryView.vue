<template>
  <div class="balance-summary">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">应收应付余额汇总</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchSummary">查询</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="16" class="summary-cards">
        <el-col :span="8">
          <div class="summary-card">
            <div class="card-label">应收期末余额合计</div>
            <div class="card-value">{{ fmtAmount(summary?.receivableTotal) }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="summary-card">
            <div class="card-label">应付期末余额合计</div>
            <div class="card-value">{{ fmtAmount(summary?.payableTotal) }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="summary-card">
            <div class="card-label">口径校验</div>
            <div class="card-value">
              <el-tag :type="summary?.consistent ? 'success' : 'danger'" size="small">
                {{ summary?.consistent ? '恒等式成立' : '存在差异' }}
              </el-tag>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="section-title">应收余额（按客户）</div>
      <el-table :data="summary?.receivables || []" v-loading="loading" border stripe>
        <el-table-column prop="partyName" label="客户" min-width="160" />
        <el-table-column prop="openingUnsettled" label="期初未核销" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.openingUnsettled) }}</template>
        </el-table-column>
        <el-table-column prop="currentAmount" label="本期应收" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.currentAmount) }}</template>
        </el-table-column>
        <el-table-column prop="currentSettled" label="本期实收" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.currentSettled) }}</template>
        </el-table-column>
        <el-table-column prop="closingUnsettled" label="期末余额" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.closingUnsettled) }}</template>
        </el-table-column>
      </el-table>

      <div class="section-title">应付余额（按供应商）</div>
      <el-table :data="summary?.payables || []" v-loading="loading" border stripe>
        <el-table-column prop="partyName" label="供应商" min-width="160" />
        <el-table-column prop="openingUnsettled" label="期初未核销" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.openingUnsettled) }}</template>
        </el-table-column>
        <el-table-column prop="currentAmount" label="本期应付" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.currentAmount) }}</template>
        </el-table-column>
        <el-table-column prop="currentSettled" label="本期实付" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.currentSettled) }}</template>
        </el-table-column>
        <el-table-column prop="closingUnsettled" label="期末余额" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.closingUnsettled) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveDefaultPeriod } from '@/utils/period'
import { getBalanceSummary, type ArapBalanceSummaryVO } from '@/api/modules/arap'

const query = ref({ period: '' })
const loading = ref(false)
const summary = ref<ArapBalanceSummaryVO | null>(null)

const fetchSummary = async () => {
  if (!/^\d{6}$/.test(query.value.period)) {
    ElMessage.warning('期间必填且必须为6位数字（YYYYMM）')
    return
  }
  loading.value = true
  try {
    summary.value = await getBalanceSummary({ period: query.value.period })
  } catch {
    summary.value = null
  } finally {
    loading.value = false
  }
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

onMounted(async () => {
  query.value.period = await resolveDefaultPeriod()
  fetchSummary()
})
</script>

<style scoped lang="scss">
.page-header {
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
.summary-cards {
  margin-bottom: 20px;
}
.summary-card {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-label {
  font-size: 13px;
  color: #909399;
}
.card-value {
  font-size: 20px;
  font-weight: 600;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 16px 0 8px;
}
</style>