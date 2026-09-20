<template>
  <div class="balance-summary">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">应收应付余额汇总</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-select
            v-model="query.period"
            placeholder="选择期间"
            style="width:160px;"
            :loading="periodLoading"
            @change="onPeriodChange"
          >
            <el-option
              v-for="p in periodOptions"
              :key="p.periodCode"
              :label="p.periodCode"
              :value="p.periodCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="fetchSummary">查询</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="16" class="summary-cards">
        <el-col :span="8">
          <div class="summary-card">
            <div class="card-label">应收期末余额合计</div>
            <div class="card-value receivable">{{ fmtAmount(summary?.receivableTotal) }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="summary-card">
            <div class="card-label">应付期末余额合计</div>
            <div class="card-value payable">{{ fmtAmount(summary?.payableTotal) }}</div>
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

      <VChart :option="chartOption" autoresize class="chart-container" />

      <el-tabs v-model="activeTab" class="balance-tabs">
        <el-tab-pane label="应收余额（按客户）" name="receivable">
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
        </el-tab-pane>
        <el-tab-pane label="应付余额（按供应商）" name="payable">
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
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { getBalanceSummary, type ArapBalanceSummaryVO, type PeriodEntity, listPeriods } from '@/api/modules/arap'

type BalanceTab = 'receivable' | 'payable'

const query = ref({ period: '' })
const loading = ref(false)
const periodLoading = ref(false)
const summary = ref<ArapBalanceSummaryVO | null>(null)
const activeTab = ref<BalanceTab>('receivable')
const periodOptions = ref<PeriodEntity[]>([])

const isValidPeriod = (p: string): boolean => /^\d{6}$/.test(p) && Number(p.slice(4, 6)) >= 1 && Number(p.slice(4, 6)) <= 12

/** 加载期间列表 */
const loadPeriods = async () => {
  periodLoading.value = true
  try {
    const list = await listPeriods()
    periodOptions.value = list
    if (!query.value.period && list.length > 0) {
      query.value.period = list[0].periodCode
    }
  } catch {
    // 忽略，使用默认当前月
  } finally {
    periodLoading.value = false
  }
}

/** ECharts 柱状图配置：应收 vs 应付期末余额 */
const chartOption = computed(() => {
  const receivable = summary.value?.receivableTotal ?? 0
  const payable = summary.value?.payableTotal ?? 0
  return {
    title: { text: '期末余额趋势（本期）', left: 'center', top: 8, textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 4 },
    grid: { left: 60, right: 40, top: 50, bottom: 50 },
    xAxis: { type: 'category', data: ['应收合计', '应付合计'], axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', name: '金额', axisLabel: { formatter: (v: number) => Number(v).toFixed(2) } },
    series: [
      { name: '期末余额', type: 'bar', barWidth: '40%', data: [receivable, payable],
        itemStyle: { color: (params: number) => params === 0 ? '#5470c6' : '#91cc75' } }
    ]
  }
})

const fetchSummary = async () => {
  if (!isValidPeriod(query.value.period)) {
    ElMessage.warning('期间必填且必须为合法月份（YYYYMM，01-12）')
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

const onPeriodChange = () => { fetchSummary() }

const fmtAmount = (v: number | null | undefined): string => Number(v ?? 0).toFixed(2)

onMounted(async () => {
  await loadPeriods()
  await fetchSummary()
})
</script>

<style scoped lang="scss">
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
.summary-cards { margin-bottom: 16px; }
.summary-card { padding: 12px 16px; background: #f5f7fa; border-radius: 6px; display: flex; flex-direction: column; gap: 6px; }
.card-label { font-size: 13px; color: #909399; }
.card-value { font-size: 20px; font-weight: 600; }
.receivable { color: #5470c6; }
.payable { color: #91cc75; }
.chart-container { width: 100%; height: 320px; margin-bottom: 20px; }
.balance-tabs { margin-top: 4px; }
</style>
