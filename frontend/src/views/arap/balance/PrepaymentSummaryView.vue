<template>
  <div class="prepayment-summary">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">预收预付余额汇总</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-button :icon="ArrowLeft" circle title="上一期间" :disabled="loading" @click="shiftPeriod(-1)" />
          <el-date-picker
            v-model="query.period"
            type="month"
            value-format="YYYYMM"
            :clearable="false"
            :disabled="loading"
            placeholder="选择期间"
            style="width:140px; margin:0 8px;"
            @change="fetchSummary"
          />
          <el-button :icon="ArrowRight" circle title="下一期间" :disabled="loading" @click="shiftPeriod(1)" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="loading" @click="fetchSummary">查询</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="16" class="summary-cards">
        <el-col :span="6">
          <div class="summary-card">
            <div class="card-label">预收期末未结清</div>
            <div class="card-value">{{ fmtAmount(summary?.preReceiptTotal) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="card-label">预付期末未结清</div>
            <div class="card-value">{{ fmtAmount(summary?.prePaymentTotal) }}</div>
          </div>
        </el-col>
        <el-col :span="12">
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

      <el-tabs v-model="activeTab" class="prepay-tabs">
        <el-tab-pane label="预收余额（按客户）" name="preReceipt">
          <el-table :data="summary?.preReceipts || []" v-loading="loading" border stripe>
            <el-table-column prop="partyName" label="客户" min-width="160" />
            <el-table-column prop="openingUnsettled" label="期初未结清" width="140" align="right">
              <template #default="{ row }">{{ fmtAmount(row.openingUnsettled) }}</template>
            </el-table-column>
            <el-table-column prop="currentCreated" label="本期新增" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.currentCreated) }}</template>
            </el-table-column>
            <el-table-column prop="currentApplied" label="本期抵扣" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.currentApplied) }}</template>
            </el-table-column>
            <el-table-column prop="currentReversed" label="本期冲销" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.currentReversed) }}</template>
            </el-table-column>
            <el-table-column prop="closingUnsettled" label="期末未结清" width="140" align="right">
              <template #default="{ row }">{{ fmtAmount(row.closingUnsettled) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="预付余额（按供应商）" name="prePayment">
          <el-table :data="summary?.prePayments || []" v-loading="loading" border stripe>
            <el-table-column prop="partyName" label="供应商" min-width="160" />
            <el-table-column prop="openingUnsettled" label="期初未结清" width="140" align="right">
              <template #default="{ row }">{{ fmtAmount(row.openingUnsettled) }}</template>
            </el-table-column>
            <el-table-column prop="currentCreated" label="本期新增" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.currentCreated) }}</template>
            </el-table-column>
            <el-table-column prop="currentApplied" label="本期抵扣" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.currentApplied) }}</template>
            </el-table-column>
            <el-table-column prop="currentReversed" label="本期冲销" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.currentReversed) }}</template>
            </el-table-column>
            <el-table-column prop="closingUnsettled" label="期末未结清" width="140" align="right">
              <template #default="{ row }">{{ fmtAmount(row.closingUnsettled) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { resolveDefaultPeriod } from '@/utils/period'
import { getPrepaymentBalanceSummary, type PrepaymentBalanceSummaryVO } from '@/api/modules/prepayment'

type BalanceTab = 'preReceipt' | 'prePayment'

const query = ref({ period: '' })
const loading = ref(false)
const summary = ref<PrepaymentBalanceSummaryVO | null>(null)
const activeTab = ref<BalanceTab>('preReceipt')

/** 校验期间为合法 YYYYMM（月份 01-12），避免 dayjs YYYYMM 歧义解析 */
const isValidPeriod = (p: string): boolean => {
  if (!/^\d{6}$/.test(p)) return false
  const m = Number(p.slice(4, 6))
  return m >= 1 && m <= 12
}

const fetchSummary = async () => {
  if (!isValidPeriod(query.value.period)) {
    ElMessage.warning('期间必填且必须为合法月份（YYYYMM，01-12）')
    return
  }
  loading.value = true
  try {
    summary.value = await getPrepaymentBalanceSummary({ period: query.value.period })
  } catch {
    summary.value = null
  } finally {
    loading.value = false
  }
}

/** 上一期间 / 下一期间：显式年月算术，正确处理 1 月/12 月跨年 */
const shiftPeriod = (delta: number) => {
  const p = query.value.period
  if (!isValidPeriod(p)) return
  const year = Number(p.slice(0, 4))
  const month = Number(p.slice(4, 6))
  const total = year * 12 + (month - 1) + delta
  const newYear = Math.floor(total / 12)
  const newMonth = (total % 12) + 1
  query.value.period = `${newYear}${String(newMonth).padStart(2, '0')}`
  fetchSummary()
}

const fmtAmount = (v: number | null | undefined): string => Number(v ?? 0).toFixed(2)

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
.prepay-tabs {
  margin-top: 4px;
}
</style>
