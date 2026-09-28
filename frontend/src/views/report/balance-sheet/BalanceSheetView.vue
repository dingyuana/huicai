<template>
  <div class="balance-sheet">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">资产负债表</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <PeriodNavigator v-model="query.period" @change="fetchData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="onExport">导出</el-button>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="hideNoMovement">隐藏无发生额且无余额科目</el-checkbox>
          <el-checkbox v-model="hideStandardBlank">隐藏报表标准空白行</el-checkbox>
          <el-checkbox v-model="reclassify">重分类列报（预付/应收贷方→负债）</el-checkbox>
        </el-form-item>
      </el-form>

      <el-alert v-if="result" :title="result.balanced ? '资产=负债+所有者权益, 平衡 ✓' : '⚠ 资产≠负债+所有者权益, 请检查!'" :type="result.balanced ? 'success' : 'error'" show-icon :closable="false" style="margin-bottom: 16px" />

      <el-alert v-if="reclassify" type="info" show-icon :closable="false"
        title="重分类列报已开启：资产类科目的贷方余额重分类为负债列报（仅改列报，不改账、不出凭证）"
        style="margin-bottom: 16px" />

      <el-alert v-if="result && result.yearStartCheckOk === false" type="warning" show-icon :closable="false"
        :title="`⚠ 年初列资产 ≠ 负债+所有者权益，差异 ${fmtAmount(result.yearStartCheckDiff)}（仅提示，不改数）`"
        style="margin-bottom: 16px" />

      <!-- P89-B：对比列（资产负债表 = 年初数，取年初期间 1 月的期初余额） -->
      <el-alert v-if="yearStartLabel" :title="`对比列：年初数（${yearStartLabel}）`" type="info" show-icon :closable="false" style="margin-bottom: 16px" />

      <el-row :gutter="20" v-if="result">
        <el-col :span="12">
          <h3>资产</h3>
          <el-table :data="visibleAssets" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="120" />
            <el-table-column v-if="yearStartAvailable" label="年初数" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, yearStartValue(row.code))">{{ fmtAmount(yearStartValue(row.code)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="期末余额" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, row.end_balance)">{{ fmtAmount(row.end_balance) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <!-- P92-B: 三分小计行常驻，不受零值行开关影响（不折叠口径） -->
          <div class="subtotal-row">
            <span>流动资产合计</span>
            <span class="sub-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b>{{ fmtAmount(yearStartData.currentAssets) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b>{{ fmtAmount(result.currentAssets) }}</b>
              </span>
            </span>
          </div>
          <div class="subtotal-row">
            <span>非流动资产合计</span>
            <span class="sub-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b>{{ fmtAmount(yearStartData.nonCurrentAssets) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b>{{ fmtAmount(result.nonCurrentAssets) }}</b>
              </span>
            </span>
          </div>
          <div v-if="showOtherAssets" class="subtotal-row">
            <span>其他资产</span>
            <span class="sub-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b>{{ fmtAmount(yearStartData.otherAssets) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b>{{ fmtAmount(result.otherAssets) }}</b>
              </span>
            </span>
          </div>
          <div class="total-row">
            <span>资产合计:</span>
            <span class="total-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b :class="amountClass(false, yearStartData.totalAssets)">{{ fmtAmount(yearStartData.totalAssets) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b :class="amountClass(false, result.totalAssets)">{{ fmtAmount(result.totalAssets) }}</b>
              </span>
            </span>
          </div>
        </el-col>
        <el-col :span="12">
          <h3>负债</h3>
          <el-table :data="visibleLiabilities" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="120" />
            <el-table-column v-if="yearStartAvailable" label="年初数" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, yearStartValue(row.code))">{{ fmtAmount(yearStartValue(row.code)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="期末余额" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, row.end_balance)">{{ fmtAmount(row.end_balance) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <!-- P92-B: 三分小计行常驻，不受零值行开关影响（不折叠口径） -->
          <div class="subtotal-row">
            <span>流动负债合计</span>
            <span class="sub-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b>{{ fmtAmount(yearStartData.currentLiabilities) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b>{{ fmtAmount(result.currentLiabilities) }}</b>
              </span>
            </span>
          </div>
          <div class="subtotal-row">
            <span>非流动负债合计</span>
            <span class="sub-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b>{{ fmtAmount(yearStartData.nonCurrentLiabilities) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b>{{ fmtAmount(result.nonCurrentLiabilities) }}</b>
              </span>
            </span>
          </div>
          <div v-if="showOtherLiabilities" class="subtotal-row">
            <span>其他负债</span>
            <span class="sub-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b>{{ fmtAmount(yearStartData.otherLiabilities) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b>{{ fmtAmount(result.otherLiabilities) }}</b>
              </span>
            </span>
          </div>
          <h3 style="margin-top: 16px">所有者权益</h3>
          <el-table :data="visibleEquity" border>
            <el-table-column prop="code" label="编码" width="100" />
            <el-table-column prop="name" label="科目" min-width="120" />
            <el-table-column v-if="yearStartAvailable" label="年初数" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, yearStartValue(row.code))">{{ fmtAmount(yearStartValue(row.code)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="期末余额" align="right" width="130">
              <template #default="{ row }">
                <span :class="amountClass(false, row.end_balance)">{{ fmtAmount(row.end_balance) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="total-row">
            <span>负债+权益合计:</span>
            <span class="total-amount">
              <span class="total-cell" v-if="yearStartAvailable">
                <em>年初</em>
                <b :class="amountClass(false, yearStartData.totalLiabEquity)">{{ fmtAmount(yearStartData.totalLiabEquity) }}</b>
              </span>
              <span class="total-cell">
                <em>期末</em>
                <b :class="amountClass(false, result.totalLiabEquity)">{{ fmtAmount(result.totalLiabEquity) }}</b>
              </span>
            </span>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { resolveLatestClosedPeriod, yearStartPeriod } from '@/utils/period'
import { ElMessage } from 'element-plus'
import { balanceSheet, balanceSheetReclassified, subjectBalance, exportBalanceSheet } from '@/api/modules/report'
import { amountClass, formatAmount } from '@/utils/format'
import { isRowVisible, guardDanglingSubtotal, isStandardBlankRow } from '@/utils/report/rowVisibility'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'

const query = reactive({ period: '' })
const result = ref<any>(null)
// P89-B：年初期间的科目余额明细（1 月期初余额 = 年初余额）
const yearStartRows = ref<any[]>([])
const yearStartData = ref<any>(null)
const yearStartAvailable = ref(false)
// P94 REQ-091：两个开关职责分离——开关1 管明细行，开关2 管报表标准空白行
const hideNoMovement = ref(true)
const hideStandardBlank = ref(false)
// P97/REQ-098：重分类为全局单开关，默认关（关闭时与现状 diff == 0）
const reclassify = ref(false)

const yearStartLabel = computed(() => {
  const p = yearStartPeriod(query.period)
  return p ? `${p.slice(0, 4)}年1月` : ''
})

// P89-A：统一千分位 + 负数标红
const fmtAmount = (v: any) => formatAmount(v)

/** 年初数取值（按科目编码从年初余额明细映射） */
const yearStartValue = (code: string): number => {
  const row = yearStartRows.value.find((r: any) => String(r.code) === String(code))
  return Number(row?.begin_balance || 0)
}

/** 明细行展示列：年初 + 期末。任一非零即显示（只看期末会误藏"年初有值、期末清零"的科目） */
const displayValues = (r: any) => [yearStartValue(r.code), r.end_balance]

const SUBTOTAL_FIELD: Record<string, string> = {
  CURRENT_ASSET: 'currentAssets',
  NON_CURRENT_ASSET: 'nonCurrentAssets',
  OTHER_ASSET: 'otherAssets',
}

/** 按小计分组过滤 + 悬空保护：小计非零却无任何可见明细时，强制保留金额最大的一行 */
const visibleInGroup = (rows: any[], group: string) => {
  const scoped = rows.filter((r: any) => r.subtotalGroup === group)
  const byRule = scoped.map(
    (r: any) => !hideNoMovement.value || isRowVisible(displayValues(r)),
  )
  const field = SUBTOTAL_FIELD[group]
  const subtotal = Number(result.value?.[field] ?? 0)
  const guarded = guardDanglingSubtotal(scoped, byRule, subtotal !== 0,
    (r: any) => Math.abs(Number(r.end_balance || 0)))
  return scoped.filter((_, i) => guarded[i])
}

const byCode = (a: any, b: any) => String(a.code).localeCompare(String(b.code))

const visibleAssets = computed(() => {
  const rows: any[] = Array.isArray(result.value?.assets) ? result.value.assets : []
  return [
    ...visibleInGroup(rows, 'CURRENT_ASSET'),
    ...visibleInGroup(rows, 'NON_CURRENT_ASSET'),
    ...visibleInGroup(rows, 'OTHER_ASSET'),
  ].sort(byCode)
})

const visibleLiabilities = computed(() => {
  const rows: any[] = Array.isArray(result.value?.liabilities) ? result.value.liabilities : []
  return [
    ...visibleInGroup(rows, 'CURRENT_LIABILITY'),
    ...visibleInGroup(rows, 'NON_CURRENT_LIABILITY'),
    ...visibleInGroup(rows, 'OTHER_LIABILITY'),
  ].sort(byCode)
})

const visibleEquity = computed(() => {
  const rows: any[] = Array.isArray(result.value?.equity) ? result.value.equity : []
  if (!hideNoMovement.value) return rows
  return rows.filter((r: any) => isRowVisible(displayValues(r)))
})

/** 开关2：仅兜底分类的"其他资产/其他负债"全零时隐藏，三分小计不归本开关管 */
const showOtherAssets = computed(() => !hideStandardBlank.value
  || !isStandardBlankRow('其他资产', [yearStartData.value?.otherAssets, result.value?.otherAssets]))
const showOtherLiabilities = computed(() => !hideStandardBlank.value
  || !isStandardBlankRow('其他负债', [yearStartData.value?.otherLiabilities, result.value?.otherLiabilities]))

const fetchData = async () => {
  if (!query.period) return
  // P94 REQ-090：小计/合计的年初值取服务端 yearStart 区块（begin 口径，与明细同源），
  // 前端不得重算——后端口径含未分配利润、成本在库存、未分类项等处理，重算必然算错；
  // 明细行年初值仍按科目编码取 1 月期初余额。
  const ys = yearStartPeriod(query.period)
  const [current, ysRows] = await Promise.all([
    reclassify.value ? balanceSheetReclassified(query.period) : balanceSheet(query.period),
    ys ? subjectBalance(ys).catch(() => []) : Promise.resolve([]),
  ])
  result.value = current
  yearStartData.value = current?.yearStart ?? null
  yearStartRows.value = Array.isArray(ysRows) ? ysRows : []
  yearStartAvailable.value = !!current?.yearStart
}

const onExport = async () => {
  if (!query.period) {
    ElMessage.warning('请先选择期间')
    return
  }
  try {
    await exportBalanceSheet(query.period)
    ElMessage.success('导出成功')
  } catch (e) {
    // request 拦截器已统一弹错，这里不重复
  }
}

onMounted(async () => {
  query.period = await resolveLatestClosedPeriod()
  fetchData()
})
</script>

<style scoped>
/* P92-B: 流动分类小计行。视觉权重介于科目明细行与总计行之间；
   不用 display:none / v-if，保证零值时也显示 0.00（法定报表金额必须可见，不折叠口径） */
.subtotal-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  margin-top: 4px;
  background: #fafafa;
  border-top: 1px dashed #dcdfe6;
  font-weight: 600;
}
.subtotal-row .sub-amount {
  display: flex;
  gap: 16px;
}
.subtotal-row .sub-amount b {
  font-weight: 600;
}
.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  margin-top: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-weight: 600;
}
.total-amount {
  display: flex;
  gap: 20px;
}
/* 年初数 / 期末余额 两列并排，标签在上数字在下，与表格列头对应 */
.total-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.total-cell em {
  font-style: normal;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.total-cell b {
  color: #409eff;
}
</style>
