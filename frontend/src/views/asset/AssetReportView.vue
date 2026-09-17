<template>
  <div class="asset-report">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">折旧与资产统计</span>
      </div>

      <el-tabs v-model="activeTab" class="asset-tabs">
        <!-- ── 报表A：资产分类汇总（家底快照） ── -->
        <el-tab-pane label="资产分类汇总" name="category">
          <el-form :model="catQuery" inline class="filter-form">
            <el-form-item label="期间">
              <el-button :icon="ArrowLeft" circle title="上一期间" :disabled="loading" @click="shiftCatPeriod(-1)" />
              <el-date-picker
                v-model="catQuery.period"
                type="month"
                value-format="YYYYMM"
                :clearable="false"
                :disabled="loading"
                placeholder="选择期间"
                style="width:140px; margin:0 8px;"
              />
              <el-button :icon="ArrowRight" circle title="下一期间" :disabled="loading" @click="shiftCatPeriod(1)" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :disabled="loading" @click="fetchCategory">查询</el-button>
              <el-button :disabled="loading || catSummary?.rows?.length === 0" @click="exportCategory">导出</el-button>
            </el-form-item>
          </el-form>

          <el-row :gutter="16" class="summary-cards">
            <el-col :span="8">
              <div class="summary-card">
                <div class="card-label">有效资产数</div>
                <div class="card-value">{{ catSummary?.totalQty ?? 0 }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="summary-card">
                <div class="card-label">原值合计</div>
                <div class="card-value">{{ fmtAmount(catSummary?.totalOriginalValue) }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="summary-card">
                <div class="card-label">净值合计</div>
                <div class="card-value">{{ fmtAmount(catSummary?.totalNetValue) }}</div>
              </div>
            </el-col>
          </el-row>

          <el-table :data="catSummary?.rows || []" v-loading="loading" border stripe>
            <el-table-column prop="categoryName" label="类别" min-width="140" />
            <el-table-column prop="qty" label="数量" width="80" align="right" />
            <el-table-column prop="originalValue" label="原值" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.originalValue) }}</template>
            </el-table-column>
            <el-table-column prop="accumulatedDepreciation" label="累计折旧" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.accumulatedDepreciation) }}</template>
            </el-table-column>
            <el-table-column prop="netValue" label="净值" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.netValue) }}</template>
            </el-table-column>
            <el-table-column prop="currentDepreciation" label="本期已提" width="120" align="right">
              <template #default="{ row }">{{ fmtAmount(row.currentDepreciation) }}</template>
            </el-table-column>
            <el-table-column prop="netRatio" label="净值率" width="100" align="right">
              <template #default="{ row }">{{ row.netRatio == null ? '-' : (row.netRatio * 100).toFixed(2) + '%' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ── 报表B：折旧计提汇总 ── -->
        <el-tab-pane label="折旧计提汇总" name="depreciation">
          <el-form :model="depQuery" inline class="filter-form">
            <el-form-item label="区间">
              <el-date-picker v-model="depQuery.periodFrom" type="month" value-format="YYYYMM" :clearable="false" placeholder="起" style="width:130px" />
              <span class="range-sep">~</span>
              <el-date-picker v-model="depQuery.periodTo" type="month" value-format="YYYYMM" :clearable="false" placeholder="止" style="width:130px" />
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="depQuery.groupBy" style="width:160px">
                <el-option label="按类别" value="CATEGORY" />
                <el-option label="按部门" value="DEPT" />
                <el-option label="部门+类别" value="DEPT_CATEGORY" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :disabled="loading" @click="fetchDepreciation">查询</el-button>
              <el-button :disabled="loading || depSummary?.rows?.length === 0" @click="exportDepreciation">导出</el-button>
            </el-form-item>
          </el-form>

          <el-alert
            v-if="depSummary && !depSummary.consistent"
            title="恒等式校验未通过：存在「期初累计 + 区间计提 ≠ 期末累计」的行，建议检查折旧流水与卡片累计是否一致"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom:12px"
          />

          <el-table :data="depSummary?.rows || []" v-loading="loading" border stripe>
            <el-table-column v-if="depQuery.groupBy !== 'DEPT'" prop="categoryName" label="类别" min-width="140" />
            <el-table-column v-if="depQuery.groupBy !== 'CATEGORY'" prop="deptName" label="部门" min-width="140" />
            <el-table-column prop="assetCount" label="资产数" width="90" align="right" />
            <el-table-column prop="depreciated" label="区间计提" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.depreciated) }}</template>
            </el-table-column>
            <el-table-column prop="openingAccumulated" label="期初累计" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.openingAccumulated) }}</template>
            </el-table-column>
            <el-table-column prop="closingAccumulated" label="期末累计" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.closingAccumulated) }}</template>
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
import {
  getAssetCategorySummary, getAssetDepreciationSummary,
  exportAssetCategorySummary, exportAssetDepreciationSummary,
  type AssetCategorySummaryVO, type AssetDepreciationSummaryVO,
  type AssetReportGroupBy,
} from '@/api/modules/asset'

type ReportTab = 'category' | 'depreciation'

const activeTab = ref<ReportTab>('category')
const loading = ref(false)
const catSummary = ref<AssetCategorySummaryVO | null>(null)
const depSummary = ref<AssetDepreciationSummaryVO | null>(null)

const catQuery = ref({ period: '' })
const depQuery = ref({ periodFrom: '', periodTo: '', groupBy: 'CATEGORY' as AssetReportGroupBy })

const isValidPeriod = (p: string): boolean => {
  if (!/^\d{6}$/.test(p)) return false
  const m = Number(p.slice(4, 6))
  return m >= 1 && m <= 12
}

const fetchCategory = async () => {
  if (!isValidPeriod(catQuery.value.period)) {
    ElMessage.warning('期间必填且必须为合法月份（YYYYMM，01-12）')
    return
  }
  loading.value = true
  try {
    catSummary.value = await getAssetCategorySummary({ period: catQuery.value.period })
  } catch {
    catSummary.value = null
  } finally {
    loading.value = false
  }
}

const shiftCatPeriod = (delta: number) => {
  const p = catQuery.value.period
  if (!isValidPeriod(p)) return
  const total = Number(p.slice(0, 4)) * 12 + (Number(p.slice(4, 6)) - 1) + delta
  catQuery.value.period = `${Math.floor(total / 12)}${String((total % 12) + 1).padStart(2, '0')}`
  fetchCategory()
}

const exportCategory = async () => {
  try {
    await exportAssetCategorySummary({ period: catQuery.value.period })
  } catch {
    ElMessage.error('导出失败')
  }
}

const fetchDepreciation = async () => {
  if (!isValidPeriod(depQuery.value.periodFrom) || !isValidPeriod(depQuery.value.periodTo)) {
    ElMessage.warning('区间起止均必填且必须为合法月份（YYYYMM，01-12）')
    return
  }
  if (depQuery.value.periodFrom > depQuery.value.periodTo) {
    ElMessage.warning('起期不能晚于止期')
    return
  }
  loading.value = true
  try {
    depSummary.value = await getAssetDepreciationSummary({
      periodFrom: depQuery.value.periodFrom,
      periodTo: depQuery.value.periodTo,
      groupBy: depQuery.value.groupBy,
    })
    if (depSummary.value && !depSummary.value.consistent) {
      ElMessage.warning('恒等式校验未通过，部分行 期初+计提 ≠ 期末')
    }
  } catch {
    depSummary.value = null
  } finally {
    loading.value = false
  }
}

const exportDepreciation = async () => {
  try {
    await exportAssetDepreciationSummary({
      periodFrom: depQuery.value.periodFrom,
      periodTo: depQuery.value.periodTo,
      groupBy: depQuery.value.groupBy,
    })
  } catch {
    ElMessage.error('导出失败')
  }
}

const fmtAmount = (v: number | null | undefined): string => Number(v ?? 0).toFixed(2)

onMounted(async () => {
  const defaultPeriod = await resolveDefaultPeriod()
  catQuery.value.period = defaultPeriod
  depQuery.value.periodFrom = defaultPeriod.slice(0, 4) + '01'
  depQuery.value.periodTo = defaultPeriod
  fetchCategory()
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
.range-sep {
  margin: 0 8px;
  color: #909399;
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
.asset-tabs {
  margin-top: 4px;
}
</style>
