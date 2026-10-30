<template>
  <div class="voucher-detail">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">凭证详情</span>
        <div class="header-actions">
          <!-- 凭证浏览导航：id 顺序取自进入页时的列表上下文（从列表进入=同筛选集；直达=全部凭证） -->
          <el-button :disabled="disFirst || navLoading" @click="goVoucherFirst">第一条</el-button>
          <el-button :disabled="disPrev || navLoading" @click="goVoucherPrev">上一条</el-button>
          <el-button :disabled="disNext || navLoading" @click="goVoucherNext">下一条</el-button>
          <el-button :disabled="disLast || navLoading" @click="goVoucherLast">末一条</el-button>
          <span v-if="hasNav" class="nav-pos">{{ navIndex + 1 }} / {{ navIds.length }}</span>
          <el-button @click="goBack">返回</el-button>
          <el-button v-if="voucher?.status === 'DRAFT'" type="primary" @click="goEdit">编辑</el-button>
          <el-button
            v-if="voucher?.status === 'DRAFT'"
            type="success"
            :aria-disabled="locked('voucher:submit')"
            :class="{ 'is-permission-locked': locked('voucher:submit') }"
            @click="onSubmit"
          >提交</el-button>
          <el-button
            v-if="voucher?.status === 'SUBMITTED'"
            type="primary"
            :aria-disabled="locked('voucher:audit')"
            :class="{ 'is-permission-locked': locked('voucher:audit') }"
            @click="onAudit"
          >审核</el-button>
          <el-button
            v-if="voucher?.status === 'AUDITED'"
            type="warning"
            :aria-disabled="locked('voucher:post')"
            :class="{ 'is-permission-locked': locked('voucher:post') }"
            @click="onPost"
          >记账</el-button>
          <el-button
            v-if="voucher?.status === 'POSTED'"
            type="danger"
            :aria-disabled="locked('voucher:unpost')"
            :class="{ 'is-permission-locked': locked('voucher:unpost') }"
            @click="onUnpost"
          >反记账</el-button>
        </div>
      </div>

      <el-descriptions v-if="voucher" :column="3" border>
        <el-descriptions-item label="凭证号">{{ voucher.voucherNo }}</el-descriptions-item>
        <el-descriptions-item label="会计期间">{{ voucher.period }}</el-descriptions-item>
        <el-descriptions-item label="凭证类型">
          <el-tag size="small">{{ voucher.voucherTypeName || voucher.voucherTypeId }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态" :span="3">
          <el-tag :type="statusType(voucher.status) as 'success' | 'warning' | 'info' | 'primary'">
            {{ VOUCHER_STATUS_MAP[voucher.status] || voucher.status }}
          </el-tag>
          <span v-if="voucher.reversedFrom" class="reverse-tag">（红冲自 #{{ voucher.reversedFrom }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="摘要" :span="3">{{ voucher.summary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ voucher.createdByName || voucher.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="制单时间">{{ fmtTime(voucher.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ sourceLabel(voucher.source) }}</el-descriptions-item>
        <el-descriptions-item v-if="voucher.submittedBy" label="提交人">{{ voucher.submittedByName || voucher.submittedBy }}</el-descriptions-item>
        <el-descriptions-item v-if="voucher.submittedAt" label="提交时间">{{ fmtTime(voucher.submittedAt) }}</el-descriptions-item>
        <el-descriptions-item></el-descriptions-item>
        <el-descriptions-item v-if="voucher.auditedBy" label="审核人">{{ voucher.auditedByName || voucher.auditedBy }}</el-descriptions-item>
        <el-descriptions-item v-if="voucher.auditedAt" label="审核时间">{{ fmtTime(voucher.auditedAt) }}</el-descriptions-item>
        <el-descriptions-item></el-descriptions-item>
        <el-descriptions-item v-if="voucher.postedBy" label="记账人">{{ voucher.postedByName || voucher.postedBy }}</el-descriptions-item>
        <el-descriptions-item v-if="voucher.postedAt" label="记账时间">{{ fmtTime(voucher.postedAt) }}</el-descriptions-item>
        <el-descriptions-item></el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">分录明细</h3>
      <el-table :data="voucher?.entries || []" border stripe>
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column label="科目编码" prop="subjectCode" width="140" />
        <el-table-column label="科目名称" prop="subjectName" min-width="200" />
        <el-table-column label="摘要" prop="summary" min-width="200" show-overflow-tooltip />
        <el-table-column label="借方金额" width="160" align="right">
          <template #default="{ row }">{{ fmtAmount(row.debit) }}</template>
        </el-table-column>
        <el-table-column label="贷方金额" width="160" align="right">
          <template #default="{ row }">{{ fmtAmount(row.credit) }}</template>
        </el-table-column>
      </el-table>
      <div class="total-row">
        <span>合计:</span>
        <span class="num">借方 {{ fmtAmount(voucher?.totalDebit) }}</span>
        <span class="num">贷方 {{ fmtAmount(voucher?.totalCredit) }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getVoucher,
  getVoucherPage,
  submitVoucher,
  auditVoucher,
  postVoucher,
  unpostVoucher,
  VOUCHER_STATUS_MAP,
  type VoucherVO,
} from '@/api/modules/voucher'
import { usePermissionGate } from '@/composables/usePermissionGate'

const { locked, ensure } = usePermissionGate()

const route = useRoute()
const router = useRouter()
const voucher = ref<VoucherVO | null>(null)
const currentId = ref<number>(Number(route.query.id) || 0)

/** 导航 id 序列：与进入页时的列表上下文同序（period DESC, voucher_no DESC） */
const navIds = ref<number[]>([])
const navLoading = ref(false)
const navIndex = computed(() => navIds.value.indexOf(currentId.value))
const hasNav = computed(() => navIndex.value >= 0 && navIds.value.length > 0)
const disFirst = computed(() => !hasNav.value || navIndex.value <= 0)
const disPrev = computed(() => !hasNav.value || navIndex.value <= 0)
const disNext = computed(() => !hasNav.value || navIndex.value >= navIds.value.length - 1)
const disLast = computed(() => !hasNav.value || navIndex.value >= navIds.value.length - 1)

function statusType(s: string): '' | 'success' | 'warning' | 'info' | 'primary' {
  switch (s) {
    case 'DRAFT': return 'info'
    case 'SUBMITTED': return 'primary'
    case 'AUDITED': return 'warning'
    case 'POSTED': return 'success'
    default: return 'info'
  }
}

function sourceLabel(s?: string) {
  return s === 'MANUAL' ? '手工录入'
    : s === 'TEMPLATE' ? '模板生成'
    : s === 'GENERATED' ? '单据生成'
    : s === 'REVERSAL' ? '红冲凭证' : '-'
}

function fmtAmount(v?: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtTime(v?: string) {
  if (!v) return ''
  // "2026-06-15T11:22:22.550228" → "2026-06-15 11:22"
  const d = new Date(v)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function goBack() {
  router.push({ name: 'VoucherList' })
}

function goEdit() {
  router.push({ name: 'VoucherEdit', query: { mode: 'edit', id: String(currentId.value) } })
}

/** 加载导航上下文：从列表进入时读取其筛选（VoucherList#goDetail 写入 sessionStorage），直达时为空=全部凭证 */
async function loadNavContext() {
  let ctx: Record<string, unknown> = { current: 1, size: 9999 }
  try {
    const saved = sessionStorage.getItem('voucherListFilter')
    if (saved) {
      const s = JSON.parse(saved)
      if (s && typeof s === 'object') ctx = { ...s, current: 1, size: 9999 }
    }
  } catch {
    ctx = { current: 1, size: 9999 }
  }
  try {
    const res = await getVoucherPage(ctx as any)
    navIds.value = (res.records || []).map((r: VoucherVO) => r.id)
  } catch {
    // 加载失败不阻断详情展示，仅导航禁用
    navIds.value = []
  }
}

async function goNav(target: number) {
  if (!target || navLoading.value) return
  navLoading.value = true
  try {
    currentId.value = target
    await router.replace({ query: { ...route.query, id: String(target) } })
    await fetchData()
  } finally {
    navLoading.value = false
  }
}

async function goVoucherFirst() {
  if (hasNav.value) await goNav(navIds.value[0])
}
async function goVoucherPrev() {
  if (hasNav.value && navIndex.value > 0) await goNav(navIds.value[navIndex.value - 1])
}
async function goVoucherNext() {
  if (hasNav.value && navIndex.value < navIds.value.length - 1) await goNav(navIds.value[navIndex.value + 1])
}
async function goVoucherLast() {
  if (hasNav.value) await goNav(navIds.value[navIds.value.length - 1])
}

async function fetchData() {
  voucher.value = await getVoucher(currentId.value)
}

async function onSubmit() {
  if (!(await ensure('voucher:submit', '提交'))) return
  await submitVoucher(currentId.value)
  ElMessage.success('提交成功')
  await fetchData()
}

async function onAudit() {
  if (!(await ensure('voucher:audit', '审核'))) return
  await auditVoucher(currentId.value)
  ElMessage.success('审核成功')
  await fetchData()
}

async function onPost() {
  if (!(await ensure('voucher:post', '记账'))) return
  await postVoucher(currentId.value)
  ElMessage.success('记账成功')
  await fetchData()
}

/** 反记账：POSTED → AUDITED，后端对称回减科目余额（P142 补全） */
async function onUnpost() {
  if (!(await ensure('voucher:unpost', '反记账'))) return
  try {
    await ElMessageBox.confirm(
      '反记账将凭证退回「已审核」状态，并回减该凭证计入的科目余额。确定执行？',
      '反记账确认',
      { type: 'warning', confirmButtonText: '确定反记账', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await unpostVoucher(currentId.value)
  ElMessage.success('反记账成功，凭证已退回已审核状态')
  await fetchData()
}

onMounted(async () => {
  await Promise.all([fetchData(), loadNavContext()])
})
</script>

<style scoped>
.voucher-detail .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.nav-pos {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 0 4px;
  white-space: nowrap;
}
.section-title {
  margin: 20px 0 12px;
  font-size: 14px;
  font-weight: 600;
}
.total-row {
  margin-top: 12px;
  padding: 10px 16px;
  background: #f5f7fa;
  display: flex;
  gap: 32px;
  justify-content: flex-end;
  font-size: 14px;
}
.total-row .num {
  font-weight: 600;
}
.reverse-tag {
  margin-left: 8px;
  color: #e6a23c;
  font-size: 12px;
}

/* SPEC-P136：无权限的按钮视觉上禁用，但保留可点击以给出解释 */
.is-permission-locked {
  opacity: 0.5;
}
</style>
