<template>
  <div class="period-close">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">期末结账工作台</span>
        <el-button @click="onReopen" v-if="periodStatus === 'closed'">反结账</el-button>
      </div>

      <el-form inline>
        <el-form-item label="会计期间">
          <PeriodNavigator v-model="period" @change="onPeriodChange" />
        </el-form-item>
        <el-form-item>
          <el-tag v-if="periodStatus" :type="statusTagType" size="small">{{ statusLabel }}</el-tag>
        </el-form-item>
      </el-form>

      <el-divider />

      <el-steps :active="step - 1" finish-status="success" align-center class="wizard-steps">
        <el-step title="结账前检查" />
        <el-step title="生成结转凭证" />
        <el-step title="核对凭证" />
        <el-step title="完成结账" />
      </el-steps>

      <!-- Step 1: 结账前检查 -->
      <div v-show="step === 1" class="step-body">
        <div class="step-action">
          <el-button type="primary" :loading="checking" @click="onCheck">执行结账前检查</el-button>
        </div>

        <div v-if="checkResult">
          <el-alert
            :type="checkResult.passed ? 'success' : 'warning'"
            :title="checkResult.passed ? '结账检查通过, 可继续生成结转凭证' : '存在待处理事项, 结转仍可继续但结账前须解决'"
            :closable="false"
          />
          <ul v-if="!checkResult.passed" class="issue-list">
            <li v-for="(issue, idx) in checkResult.issues" :key="idx">{{ issue }}</li>
          </ul>
          <p v-else class="ok-text">未发现阻碍结转的问题。</p>

          <h3>试算平衡</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="期初">借 {{ fmt(checkResult.trialBalance.totalBeginDebit) }} / 贷 {{ fmt(checkResult.trialBalance.totalBeginCredit) }}</el-descriptions-item>
            <el-descriptions-item label="期初平衡">
              <el-tag :type="checkResult.trialBalance.beginBalanced ? 'success' : 'danger'" size="small">
                {{ checkResult.trialBalance.beginBalanced ? '平衡' : '不平衡' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="本期发生">借 {{ fmt(checkResult.trialBalance.totalDebitTotal) }} / 贷 {{ fmt(checkResult.trialBalance.totalCreditTotal) }}</el-descriptions-item>
            <el-descriptions-item label="发生平衡">
              <el-tag :type="checkResult.trialBalance.movementBalanced ? 'success' : 'danger'" size="small">
                {{ checkResult.trialBalance.movementBalanced ? '平衡' : '不平衡' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="期末余额">借 {{ fmt(checkResult.trialBalance.totalEndDebit) }} / 贷 {{ fmt(checkResult.trialBalance.totalEndCredit) }}</el-descriptions-item>
            <el-descriptions-item label="期末平衡">
              <el-tag :type="checkResult.trialBalance.endBalanced ? 'success' : 'danger'" size="small">
                {{ checkResult.trialBalance.endBalanced ? '平衡' : '不平衡' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <el-empty v-else description="请先执行结账前检查" />
      </div>

      <!-- Step 2: 生成结转凭证 -->
      <div v-show="step === 2" class="step-body">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="将按 折旧(DEPR) → 损益结转(CLOSE) → 利润分配(DISTRIB) 顺序生成草稿凭证。单步无数据时自动跳过并继续, 不会中断整个序列。"
          class="step-tip"
        />
        <div class="step-action">
          <el-button type="warning" :loading="generating" @click="onGenerate">生成结转凭证序列</el-button>
        </div>

        <div v-if="sequence.length" class="seq-summary">
          <el-table :data="sequence" border size="small">
            <el-table-column prop="step" label="步骤" width="100" />
            <el-table-column prop="stepName" label="凭证" width="140" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="stepTagType(row.status)" size="small">{{ stepLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="凭证号" width="150">
              <template #default="{ row }">
                <span v-if="row.vouchers[0]">{{ row.vouchers[0].voucherNo }}</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="180">
              <template #default="{ row }">
                <span v-if="row.reason" class="reason">{{ row.reason }}</span>
                <span v-else-if="row.vouchers[0]" class="muted">已生成草稿, 待审核记账</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- Step 3: 核对凭证 + Drawer -->
      <div v-show="step === 3" class="step-body">
        <div class="step-action">
          <el-button
            type="primary"
            :disabled="!reviewableIds.length"
            :loading="reviewing"
            @click="onBatchReviewPost"
          >
            一键审核记账（{{ reviewableIds.length }} 张草稿）
          </el-button>
          <el-button
            :disabled="!reviewableIds.length"
            @click="drawerVisible = true"
          >
            查看凭证卡片
          </el-button>
        </div>

        <p class="step-tip">
          凭证清单将在右侧抽屉展示。一键审核记账将同事务完成
          <b>提交 → 审核 → 记账</b>，全部由您主动触发。
        </p>

        <el-table v-if="allVouchers.length" :data="allVouchers" border size="small" class="voucher-mini">
          <el-table-column prop="voucherNo" label="凭证号" width="150" />
          <el-table-column prop="voucherTypeName" label="类型" width="120">
            <template #default="{ row }">{{ row.voucherTypeName || '—' }}</template>
          </el-table-column>
          <el-table-column label="借方合计" width="130">
            <template #default="{ row }">{{ fmt(row.totalDebit) }}</template>
          </el-table-column>
          <el-table-column label="贷方合计" width="130">
            <template #default="{ row }">{{ fmt(row.totalCredit) }}</template>
          </el-table-column>
          <el-table-column prop="entryCount" label="分录数" width="80" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="voucherTagType(row.status)" size="small">{{ statusMap[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Step 4: 完成结账 -->
      <div v-show="step === 4" class="step-body">
        <el-result
          v-if="closed"
          icon="success"
          title="结账完成"
          :sub-title="`期间 ${period} 已锁定`"
        />
        <template v-else>
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            :title="canClose ? '全部结转凭证已记账, 可执行结账' : '仍有未记账凭证或检查未通过, 结账将被拦截'"
            class="step-tip"
          />
          <div class="step-action">
            <el-button type="success" :loading="closing" @click="onClose">执行结账</el-button>
          </div>
        </template>
      </div>

      <div class="step-nav">
        <el-button v-if="step > 1" @click="step--">上一步</el-button>
        <el-button
          v-if="step === 1"
          type="primary"
          :disabled="!checkResult"
          @click="step = 2"
        >下一步</el-button>
        <el-button
          v-if="step === 2"
          type="primary"
          :disabled="!sequence.length"
          @click="step = 3"
        >下一步</el-button>
        <el-button
          v-if="step === 3"
          type="primary"
          @click="step = 4"
        >下一步</el-button>
      </div>
    </el-card>

    <!-- 凭证卡片 Drawer -->
    <el-drawer v-model="drawerVisible" title="结转凭证清单" size="46%" direction="rtl">
      <el-empty v-if="!allVouchers.length" description="暂无已生成的结转凭证" />
      <div v-else class="voucher-cards">
        <el-card v-for="v in allVouchers" :key="v.voucherId" shadow="never" class="voucher-card">
          <div class="card-head">
            <span class="card-no">{{ v.voucherNo }}</span>
            <el-tag :type="voucherTagType(v.status)" size="small">{{ statusMap[v.status] || v.status }}</el-tag>
          </div>
          <el-descriptions :column="1" size="small">
            <el-descriptions-item label="凭证类型">{{ v.voucherTypeName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="借方合计">{{ fmt(v.totalDebit) }}</el-descriptions-item>
            <el-descriptions-item label="贷方合计">{{ fmt(v.totalCredit) }}</el-descriptions-item>
            <el-descriptions-item label="分录行数">{{ v.entryCount }}</el-descriptions-item>
          </el-descriptions>
          <div class="card-foot">
            <el-button
              size="small"
              :disabled="v.status !== 'DRAFT'"
              :loading="reviewingSingle === v.voucherId"
              @click="onReviewSingle(v.voucherId)"
            >
              审核记账
            </el-button>
          </div>
        </el-card>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveEarliestUnclosedPeriod } from '@/utils/period'
import PeriodNavigator from '@/components/finance/PeriodNavigator.vue'
import {
  checkClose, closePeriod, reopenPeriod,
  generateSequence, batchReviewPost,
  type CloseCheckResult, type CarryoverStepResult, type SequenceVoucher,
} from '@/api/modules/periodClose'

const period = ref('')
const periodStatus = ref('')

const step = ref(1)
const checkResult = ref<CloseCheckResult | null>(null)
const checking = ref(false)

const sequence = ref<CarryoverStepResult[]>([])
const generating = ref(false)

const allVouchers = ref<SequenceVoucher[]>([])
const reviewing = ref(false)
const reviewingSingle = ref<number | null>(null)
const drawerVisible = ref(false)

const closing = ref(false)
const closed = ref(false)

const statusMap: Record<string, string> = {
  DRAFT: '草稿', SUBMITTED: '已提交', AUDITED: '已审核', POSTED: '已记账', CLOSED: '已结账',
}

onMounted(async () => {
  period.value = await resolveEarliestUnclosedPeriod()
})

function onPeriodChange() {
  // 切换期间需从第 1 步重来: 检查/序列/凭证均与期间绑定
  step.value = 1
  resetAll()
}

function resetAll() {
  checkResult.value = null
  sequence.value = []
  allVouchers.value = []
  closed.value = false
}

// ===== 派生状态 =====
/** 当前可一键审核的草稿凭证（仅 DRAFT 可走一键流程） */
const reviewableIds = computed(() =>
  allVouchers.value.filter(v => v.status === 'DRAFT').map(v => v.voucherId),
)

/** 全部结转凭证已记账, 且结账检查通过 */
const canClose = computed(() =>
  checkResult.value?.passed === true &&
  allVouchers.value.length > 0 &&
  allVouchers.value.every(v => v.status === 'POSTED'),
)

const statusTagType = computed(() =>
  periodStatus.value === 'closed' ? 'danger' : 'info',
)
const statusLabel = computed(() =>
  periodStatus.value === 'closed' ? '已结账' : '未结账',
)

function fmt(v: number | null | undefined) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function stepTagType(s: string) {
  return s === 'GENERATED' ? 'success' : s === 'SKIPPED' ? 'info' : 'danger'
}
function stepLabel(s: string) {
  return s === 'GENERATED' ? '已生成' : s === 'SKIPPED' ? '已跳过' : '失败'
}
function voucherTagType(s: string) {
  return s === 'POSTED' ? 'success' : s === 'DRAFT' ? 'info' : 'warning'
}

// ===== Step 1: 结账前检查 =====
async function onCheck() {
  if (!period.value) {
    ElMessage.warning('请先选择会计期间')
    return
  }
  checking.value = true
  try {
    checkResult.value = await checkClose(period.value)
  } finally {
    checking.value = false
  }
}

// ===== Step 2: 生成结转序列 =====
async function onGenerate() {
  if (!period.value) return
  await ElMessageBox.confirm(
    `将基于期间 ${period.value} 已记账分录依次生成 折旧 / 损益结转 / 利润分配 三张草稿凭证, 是否继续?`,
    '生成结转凭证',
    { type: 'warning', confirmButtonText: '开始生成', cancelButtonText: '取消' },
  )
  generating.value = true
  try {
    const seq = await generateSequence(period.value)
    sequence.value = seq
    // 汇总生成的草稿凭证, 供第 3 步核对与一键审核
    allVouchers.value = seq.flatMap(r => r.vouchers)
    const ok = seq.filter(r => r.status === 'GENERATED').length
    const skipped = seq.filter(r => r.status === 'SKIPPED').length
    const failed = seq.filter(r => r.status === 'FAILED').length
    if (ok) ElMessage.success(`已生成 ${ok} 张草稿凭证${skipped ? `, ${skipped} 步无数据跳过` : ''}${failed ? `, ${failed} 步失败` : ''}`)
    else if (failed) ElMessage.warning('序列生成失败, 请查看明细')
    else ElMessage.info('本期无可生成的结转凭证')
  } finally {
    generating.value = false
  }
}

// ===== Step 3: 一键人工审核记账 =====
async function onBatchReviewPost() {
  const ids = reviewableIds.value
  if (!ids.length) {
    ElMessage.info('没有待审核的草稿凭证')
    return
  }
  await ElMessageBox.confirm(
    `将对选中的 ${ids.length} 张凭证依次执行「提交 → 审核 → 记账」, 三步在同一事务内完成, 任一步失败全部回滚。确认继续?`,
    '一键审核记账',
    { type: 'warning', confirmButtonText: '确认审核记账', cancelButtonText: '取消' },
  )
  await doBatchReview(ids)
}

/** 单张凭证审核记账（Drawer 卡片内按钮） */
async function onReviewSingle(id: number) {
  await ElMessageBox.confirm('确认对该凭证执行「提交 → 审核 → 记账」?', '审核记账', {
    type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消',
  })
  reviewingSingle.value = id
  try {
    await doBatchReview([id])
  } finally {
    reviewingSingle.value = null
  }
}

/** 执行一键审核记账并刷新凭证状态 */
async function doBatchReview(ids: number[]) {
  reviewing.value = true
  try {
    await batchReviewPost(ids)
    ElMessage.success(`已审核并记账 ${ids.length} 张凭证`)
    // 抽屉/列表内即时刷新状态（不跳转页面, 满足"全程不离开结账页"）
    allVouchers.value = allVouchers.value.map(v =>
      ids.includes(v.voucherId) ? { ...v, status: 'POSTED' } : v,
    )
    if (step.value === 3) step.value = 4
  } finally {
    reviewing.value = false
  }
}

// ===== Step 4: 执行结账 =====
async function onClose() {
  if (!canClose.value) {
    ElMessage.warning('仍有未记账凭证或结账检查未通过, 无法结账')
    return
  }
  await ElMessageBox.confirm(
    `确认对期间 ${period.value} 执行结账? 结账后期间将锁定, 不可操作凭证.`,
    '高危操作',
    { type: 'warning', confirmButtonText: '确认结账', cancelButtonText: '取消' },
  )
  closing.value = true
  try {
    await closePeriod(period.value)
    closed.value = true
    ElMessage.success('结账成功')
  } finally {
    closing.value = false
  }
}

async function onReopen() {
  await ElMessageBox.confirm(
    `确认对期间 ${period.value} 执行反结账? 需最高权限, 操作将记入日志.`,
    '高危操作',
    { type: 'error', confirmButtonText: '确认反结账', cancelButtonText: '取消' },
  )
  await reopenPeriod(period.value)
  resetAll()
  step.value = 1
  ElMessage.success('反结账成功')
}
</script>

<style scoped>
.period-close .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.wizard-steps {
  margin: 8px 0 20px;
}
.step-body {
  min-height: 160px;
}
.step-action {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.step-tip {
  margin-bottom: 16px;
}
.step-nav {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.issue-list {
  margin: 12px 0 0;
  padding-left: 24px;
  color: #f56c6c;
}
.ok-text {
  color: #67c23a;
  margin: 12px 0;
}
.muted {
  color: #909399;
}
.reason {
  color: #e6a23c;
  font-size: 13px;
}
.seq-summary,
.voucher-mini {
  margin-bottom: 8px;
}
.voucher-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.voucher-card .card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.voucher-card .card-no {
  font-weight: 600;
  font-size: 14px;
}
.voucher-card .card-foot {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
h3 {
  margin: 16px 0 12px;
  font-size: 14px;
  font-weight: 600;
}
</style>
