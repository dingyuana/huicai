<template>
  <div class="subject-balance">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">科目余额表</span>
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
        <el-form-item>
          <el-checkbox v-model="hideNoMovement">隐藏无发生额且无余额科目</el-checkbox>
          <el-checkbox v-model="hideStandardBlank" disabled>隐藏报表标准空白行（本科目表无空白骨架行）</el-checkbox>
        </el-form-item>
      </el-form>

      <!-- P97/REQ-097：树形展示。行数据为组树后的结果，父级缺失的科目已提升为根（不丢行） -->
      <el-table :data="visibleRows" v-loading="loading" border show-summary
                :summary-method="summaryRow" row-key="subjectId" :tree-props="TREE_PROPS"
                default-expand-all>
        <el-table-column prop="code" label="科目编码" width="120">
          <template #default="{ row }">
            <!-- P89-C 数字穿透：点击科目编码 → 凭证列表（按该科目过滤，2 跳内达凭证明细） -->
            <el-link type="primary" :underline="false" @click="drillToVouchers(row)">
              {{ row.code }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="科目名称" min-width="180" />
        <!-- P89-A 余额方向列：科目记账方向（借=借方记增加，贷=贷方记增加） -->
        <el-table-column label="余额方向" width="90" align="center">
          <template #default="{ row }">
            <span class="direction-tag" :class="row.direction === 'credit' ? 'dir-credit' : 'dir-debit'">
              {{ directionLabel(row.direction) }}
            </span>
          </template>
        </el-table-column>
        <!-- P97：level 此前因后端不回填而恒空，现由 SubjectBalanceVO.level 提供 -->
      <el-table-column prop="level" label="层级" width="60" align="center" />
      <!-- P97/REQ-097 阶段C-2：辅助核算明细。原样展示 assist_json 的键值对，
           不假设 vendorName/customerId 等具体键名（该 schema 全链路透传、无处定义） -->
      <el-table-column type="expand" width="40">
        <template #default="{ row }">
          <div class="aux-detail">
            <template v-if="(auxBySubject.get(row.code) || []).length">
              <div v-for="(item, i) in auxBySubject.get(row.code)" :key="i" class="aux-item">{{ item }}</div>
            </template>
            <span v-else class="aux-empty">该科目本期无辅助核算明细</span>
          </div>
        </template>
      </el-table-column>
        <el-table-column label="期初余额" width="140" align="right">
          <template #default="{ row }">
            <span :class="amountClass(false, row.begin_balance)">{{ fmtAmount(row.begin_balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="借方" width="140" align="right">
          <template #default="{ row }">
            <span :class="amountClass(false, row.debit_total)">{{ fmtAmount(row.debit_total) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="贷方" width="140" align="right">
          <template #default="{ row }">
            <span :class="amountClass(false, row.credit_total)">{{ fmtAmount(row.credit_total) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="期末余额" width="140" align="right">
          <template #default="{ row }">
            <span :class="amountClass(false, row.end_balance)">{{ fmtAmount(row.end_balance) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { resolveLatestClosedPeriod } from '@/utils/period'
import { ElMessage } from 'element-plus'
import { subjectBalance, subjectBalanceAuxiliary, exportSubjectBalance } from '@/api/modules/report'
import { amountClass, formatAmount } from '@/utils/format'
import { isRowVisible } from '@/utils/report/rowVisibility'
import { buildSubjectTree, TREE_PROPS } from '@/utils/report/subjectTree'
import { groupAuxBySubject } from '@/utils/report/auxFormat'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'
import DiagnosticAlert from '@/components/report/DiagnosticAlert.vue'

const query = reactive({ period: '' })
const router = useRouter()
const list = ref<any[]>([])
const loading = ref(false)
// P94 REQ-091：明细行判定本就按四列（期初/借/贷/期末）执行，已合规；
// 开关2 保留位但本页无标准空白骨架行，置 disabled。
const hideNoMovement = ref(true)
const hideStandardBlank = ref(false)

const isZeroRow = (r: any) =>
  !isRowVisible([r.begin_balance, r.debit_total, r.credit_total, r.end_balance])

const auxBySubject = ref(new Map<string, string[]>())

// P97/REQ-097 阶段C-2：辅助核算明细独立取数，失败不阻断余额表主体
const loadAux = async (period: string) => {
  try {
    const rows = await subjectBalanceAuxiliary(period)
    auxBySubject.value = groupAuxBySubject(Array.isArray(rows) ? rows : [])
  } catch {
    auxBySubject.value = new Map()
  }
}

// P97/REQ-097：先按零值规则过滤可见行，再对可见行组树——顺序不能反，
// 否则被隐藏的父级会带着可见子级一起消失。
const visibleRows = computed(() => {
  const visible = hideNoMovement.value ? list.value.filter(r => !isZeroRow(r)) : list.value
  return buildSubjectTree(visible as any)
})

const fmtAmount = (v: any) => formatAmount(v)

// 方向标签：借/贷；空值显示"—"（direction 缺失时不能默认判成借方）
const directionLabel = (d: any) => (d === 'credit' ? '贷' : d === 'debit' ? '借' : '—')

const fetchData = async () => {
  if (!query.period) return
  loading.value = true
  try {
    // 辅助核算明细与主体并行取，互不阻塞：明细失败仅少一列展示，不影响余额表
    loadAux(query.period)
    list.value = await subjectBalance(query.period)
  } finally {
    loading.value = false
  }
}

const summaryRow = ({ columns, data }: any) => {
  const sums: any = {}
  columns.forEach((col: any) => {
    const prop = col.property
    if (['begin_balance', 'debit_total', 'credit_total', 'end_balance'].includes(prop)) {
      sums[prop] = data.reduce((s: number, r: any) => s + Number(r[prop] || 0), 0).toFixed(2)
    }
  })
  sums['name'] = '合计'
  return sums
}

const onExport = async () => {
  if (!query.period) {
    ElMessage.warning('请先选择期间')
    return
  }
  try {
    await exportSubjectBalance(query.period)
    ElMessage.success('导出成功')
  } catch (e) {
    // request 拦截器已统一弹错，这里不重复
  }
}

// P89-C 数字穿透：科目余额表 → 凭证列表（按科目过滤）→ 凭证明细，共 2 跳。
// 报表科目余额按期间取数，跳转时同步带 period，避免穿透后期间对不上。
// 注：mapper 返回 Map，key 为下划线（subject_id），不会被 Jackson 转 camelCase，故取两者。
const drillToVouchers = (row: any) => {
  const subjectId = row.subject_id ?? row.subjectId
  if (!subjectId) return
  router.push({
    name: 'VoucherList',
    query: {
      period: query.period,
      subjectId: String(subjectId),
      subjectName: row.name || '',
    },
  })
}

onMounted(async () => {
  query.period = await resolveLatestClosedPeriod()
  fetchData()
})
</script>

<style scoped>
/* P89-A 余额方向标签 */
.direction-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 3px;
  font-size: 12px;
  line-height: 1.4;
}
.dir-debit {
  color: #c0392b;
  background: rgba(192, 57, 43, 0.1);
}
.dir-credit {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
}
.aux-detail {
  padding: 8px 16px;
  font-size: 13px;
}
.aux-item {
  line-height: 1.8;
}
.aux-empty {
  color: #909399;
}
</style>
