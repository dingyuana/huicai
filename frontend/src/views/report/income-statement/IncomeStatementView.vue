<template>
  <div class="income-statement">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">利润表</span>
      </div>

      <DiagnosticAlert :period="query.period" />

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <PeriodNavigator v-model="query.period" @change="fetchData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="onExport">导出</el-button>
        </el-form-item>
        <!-- 利润表不提供"隐藏零值行"：它是法定报表，7 行标准模板行全部固定呈现。
             隐藏任何一行（如营业收入为 0 时）都会破坏「营业收入 − 营业成本 = 毛利」的法定勾稽关系，
             与外部报送口径不符。科目余额表/现金流量表仍保留该选项（科目明细可按需折叠）。 -->
        <el-form-item>
          <el-tooltip content="利润表为法定报表，结构行固定完整呈现，不参与零值隐藏">
            <span class="statutory-hint">法定格式 · 结构固定</span>
          </el-tooltip>
        </el-form-item>
      </el-form>

      <!-- P89-B：对比列（利润表 = 上期金额，取上一期间）。失败/空期间时降级隐藏 -->
      <el-alert
        v-if="prevAvailable && prevPeriodLabel"
        :title="`对比列：上期（${prevPeriodLabel}）`"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-table v-if="result" :data="visibleRows" border>
        <el-table-column prop="label" label="项目" min-width="180" />
        <el-table-column v-if="prevAvailable" label="上期金额" align="right" width="180">
          <template #default="{ row }">
            <span :class="amountClass(row.bold, row.prev)">{{ fmtAmount(row.prev) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本期金额" align="right" width="180">
          <template #default="{ row }">
            <span :class="amountClass(row.bold, row.current)">{{ fmtAmount(row.current) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="本年累计" align="right" width="180">
          <template #default="{ row }">
            <span :class="amountClass(row.bold, row.cumulative)">{{ fmtAmount(row.cumulative) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { resolveLatestClosedPeriod, prevPeriod } from '@/utils/period'
import { ElMessage } from 'element-plus'
import { incomeStatement, exportIncomeStatement } from '@/api/modules/report'
import { amountClass, formatAmount } from '@/utils/format'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'
import DiagnosticAlert from '@/components/report/DiagnosticAlert.vue'

const query = reactive({ period: '' })
const result = ref<any>(null)
const prevData = ref<any>(null)
const prevAvailable = ref(false)
// 注意：利润表不提供"隐藏零值行"开关——法定报表结构行必须完整呈现（见 template 注释）

const prevPeriodLabel = computed(() => {
  const p = prevPeriod(query.period)
  if (!p) return ''
  return `${p.slice(0, 4)}年${Number(p.slice(4, 6))}月`
})

// P89-A：统一千分位 + 负数标红
const fmtAmount = (v: any) => formatAmount(v)

const rows = computed(() => {
  if (!result.value) return []
  const r = result.value
  // P88②：累计列逐行接后端字段，不再硬编码 0（旧版除首尾外全部写 0，累计数自相矛盾）
  // 法定报表骨架——利润表每一行都是法定格式行，隐藏任何一行都会破坏勾稽关系，
  // 故统一 fixed=true，零值行也不隐藏（法定报表结构必须完整呈现）。
  // P97/REQ-099：行序按企业会计准则展开（营收/成本/税金/四费/收益类/减值/营业利润/营业外/所得税/净利润），
  // 取值全部来自后端段位字段——后端已按段位显式取数，前端不做任何加减重算。
  // 累计列键默认由段位名推导，但「利润总额」是历史例外：后端键为 cumulativeProfit（不是
  // cumulativeTotalProfit），P88 存量断言也依赖该名，故在此显式覆盖，不改后端键以免破坏兼容。
  const CUMULATIVE_KEY_ALIAS: Record<string, string> = { totalProfit: 'cumulativeProfit' }
  const line = (label: string, name: string, bold: boolean) => ({
    label,
    prev: prevData.value?.[name],
    current: r[name],
    cumulative: r[CUMULATIVE_KEY_ALIAS[name]
      ?? 'cumulative' + name.charAt(0).toUpperCase() + name.slice(1)],
    bold,
    fixed: true,
  })
  return [
    line('一、营业收入', 'revenue', true),
    line('减:营业成本', 'cost', false),
    line('减:税金及附加', 'taxAndSurcharge', false),
    line('减:销售费用', 'sellingExpense', false),
    line('减:管理费用', 'adminExpense', false),
    line('减:研发费用', 'rdExpense', false),
    line('减:财务费用', 'financialExpense', false),
    line('加:其他收益', 'otherIncome', false),
    line('加:投资收益', 'investmentIncome', false),
    line('加:公允价值变动收益', 'fairValueIncome', false),
    line('加:资产处置收益', 'assetDisposalIncome', false),
    line('减:资产减值损失', 'assetImpairmentLoss', false),
    line('三、营业利润', 'operatingProfit', true),
    line('加:营业外收入', 'nonOperatingIncome', false),
    line('减:营业外支出', 'nonOperatingExpense', false),
    line('四、利润总额', 'totalProfit', true),
    line('减:所得税费用', 'incomeTax', false),
    line('五、净利润', 'netProfit', true),
  ]
})

// 现金流量表同款 fixed 保护：骨架行不受 hideZeroRows 影响
const visibleRows = computed(() =>
  rows.value.filter(r => r.fixed || Number(r.current || 0) !== 0 || Number(r.cumulative || 0) !== 0 || Number(r.prev || 0) !== 0)
)

const fetchData = async () => {
  if (!query.period) return
  // P89-B：对比列取上一期间。上期无数据/查询失败时降级为不显示对比列，不影响本期报表。
  const prev = prevPeriod(query.period)
  const [current, previous] = await Promise.all([
    incomeStatement(query.period),
    prev ? incomeStatement(prev).catch(() => null) : Promise.resolve(null),
  ])
  result.value = current
  prevData.value = previous
  prevAvailable.value = !!previous
}

const onExport = async () => {
  if (!query.period) {
    ElMessage.warning('请先选择期间')
    return
  }
  try {
    await exportIncomeStatement(query.period)
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
.statutory-hint {
  color: #909399;
  font-size: 13px;
  cursor: help;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 4px 10px;
  line-height: 1.4;
}
</style>
