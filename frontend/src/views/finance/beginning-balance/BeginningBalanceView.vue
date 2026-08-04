<template>
  <div class="beginning-balance">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">期初建账</span>
        <el-select v-model="queryPeriod" placeholder="选择会计期间" style="width:160px" @change="switchPeriod">
          <el-option v-for="p in periods" :key="p" :label="p" :value="p" />
        </el-select>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="期初录入" name="entry">
          <div class="toolbar">
            <el-button type="primary" :disabled="isOpeningLocked" @click="openEntryDialog">录入期初</el-button>
            <el-button :disabled="isOpeningLocked || isOpeningNone" @click="onClearOpening">清空重录</el-button>
            <el-button @click="fetchBalances">刷新</el-button>
            <el-tag v-if="openingStatus" :type="openingStatusTagType" size="large" style="margin-left:8px">
              {{ openingStatusLabel }}
            </el-tag>
          </div>
          <el-table :data="balanceList" v-loading="loading" border stripe style="width:100%">
            <el-table-column prop="subjectCode" label="科目编码" width="120" />
            <el-table-column prop="subjectName" label="科目名称" min-width="180" />
            <el-table-column prop="direction" label="方向" width="60" />
            <el-table-column prop="beginBalance" label="期初余额" width="150">
              <template #default="{ row }">{{ row.beginBalance?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="debitTotal" label="本期借方" width="150">
              <template #default="{ row }">{{ row.debitTotal?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="creditTotal" label="本期贷方" width="150">
              <template #default="{ row }">{{ row.creditTotal?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="endBalance" label="期末余额" width="150">
              <template #default="{ row }">{{ row.endBalance?.toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="试算平衡" name="trial">
          <el-button type="primary" :loading="checking" @click="doTrialBalance">执行试算平衡检查</el-button>
          <div v-if="trialResult" class="trial-result">
            <el-alert :title="trialResult.balanced ? '试算平衡通过' : '试算不平衡'"
              :type="trialResult.balanced ? 'success' : 'error'" show-icon style="margin-top:16px" />
            <el-descriptions :column="2" border style="margin-top:16px">
              <el-descriptions-item label="期初借方合计">{{ trialResult.totalBeginDebit?.toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="期初贷方合计">{{ trialResult.totalBeginCredit?.toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="本期借方合计">{{ trialResult.totalDebitTotal?.toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="本期贷方合计">{{ trialResult.totalCreditTotal?.toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="期末借方合计">{{ trialResult.totalEndDebit?.toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="期末贷方合计">{{ trialResult.totalEndCredit?.toFixed(2) }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>

        <el-tab-pane label="启用/锁定" name="lock">
          <el-alert
            :title="lockingHint"
            :type="isOpeningLocked ? 'success' : 'warning'"
            show-icon />
          <div style="margin-top:16px">
            <el-button v-if="!isOpeningEntered" type="primary" disabled>请先完成期初建账</el-button>
            <template v-else>
              <el-button v-if="!isOpeningLocked" type="primary" :loading="locking" @click="onLockOpening">启用并锁定期初</el-button>
              <el-button v-else type="warning" :loading="locking" @click="onUnlockOpening">解锁期初</el-button>
              <el-tag v-if="isOpeningLocked" type="success" size="large" style="margin-left:8px">期初已锁定 ✅</el-tag>
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-dialog v-model="dialogVisible" title="录入期初余额" width="760">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"
          title="期初建账需借贷平衡：请一次性录入全部科目的期初余额（借方资产 + 贷方权益/负债），借贷合计相等方可保存。" />
        <div class="entry-header">
          <el-button size="small" type="primary" plain @click="addEntryRow">添加科目</el-button>
          <div class="entry-total" :class="debitTotal !== creditTotal ? 'unbalanced' : 'balanced'">
            借方合计：<b>{{ debitTotal.toFixed(2) }}</b>
            ／贷方合计：<b>{{ creditTotal.toFixed(2) }}</b>
            <el-tag :type="debitTotal === creditTotal ? 'success' : 'danger'" size="small" style="margin-left:8px">
              {{ debitTotal === creditTotal ? '试算平衡' : '试算不平衡' }}
            </el-tag>
          </div>
        </div>
        <div v-for="(row, idx) in entryRows" :key="idx" class="entry-row">
          <el-tree-select
            v-model="row.subjectId"
            :data="subjectTree"
            :props="({label:'name',value:'id',children:'children'} as any)"
            check-strictly
            filterable
            style="width:100%"
            placeholder="选择末级科目" />
          <el-input-number v-model="row.amount" :min="0" :precision="2" style="width:160px" />
          <el-button size="small" text type="danger" @click="removeEntryRow(idx)">删除</el-button>
        </div>
        <template #footer>
          <el-button @click="dialogVisible=false">取消</el-button>
          <el-button type="primary" :loading="saving" :disabled="debitTotal !== creditTotal || entryRows.length === 0" @click="saveBalance">
            保存
          </el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSubjectTree } from '@/api/modules/subject'
import {
  initOpeningBalances,
  getSubjectBalances,
  checkTrialBalance,
  lockOpeningBalances,
  unlockOpeningBalances,
  clearOpeningBalances,
} from '@/api/modules/subject'

const activeTab = ref('entry')
const loading = ref(false)
const checking = ref(false)
const saving = ref(false)
const locking = ref(false)
const dialogVisible = ref(false)
const queryPeriod = ref('')
const balanceList = ref<any[]>([])
const periods = ref<string[]>([])
const periodStatusMap = ref<Record<string, string>>({})
const trialResult = ref<any>(null)
const subjectTree = ref<any[]>([])

const entryRows = ref<Array<{ subjectId: number | null; amount: number }>>([])

const openingStatus = computed(() => periodStatusMap.value[queryPeriod.value] || 'none')
const isOpeningNone = computed(() => openingStatus.value === 'none')
const isOpeningEntered = computed(() => openingStatus.value === 'entered')
const isOpeningLocked = computed(() => openingStatus.value === 'locked')

const openingStatusLabel = computed(() => {
  switch (openingStatus.value) {
    case 'none': return '未建账'
    case 'entered': return '已录入（未锁定）'
    case 'locked': return '已锁定'
    default: return openingStatus.value
  }
})
const openingStatusTagType = computed(() => {
  switch (openingStatus.value) {
    case 'none': return 'info'
    case 'entered': return 'warning'
    case 'locked': return 'success'
    default: return 'info'
  }
})
const lockingHint = computed(() => {
  if (isOpeningLocked.value) return '期初已锁定, 凭证业务可正常过账; 如需修改期初请先解锁（要求期间无已过账凭证）'
  if (isOpeningEntered.value) return '期初数据已录入, 试算平衡通过后请点击"启用"按钮锁定期初数据'
  return '尚未完成期初建账, 请先在"期初录入"页签录入并保存期初余额, 再试算平衡后启用'
})

/** 科目ID -> 方向 快速查找 */
const subjectDirectionMap = new Map<number, string>()
function indexSubjectDirections(nodes: any[]) {
  for (const n of nodes) {
    if (n.id && n.direction) subjectDirectionMap.set(n.id, n.direction)
    if (n.children?.length) indexSubjectDirections(n.children)
  }
}

/** 借方合计(科目方向为 debit) */
const debitTotal = computed(() =>
  entryRows.value.reduce((sum, r) => r.subjectId && subjectDirectionMap.get(r.subjectId) === 'debit' ? sum + (r.amount || 0) : sum, 0))
/** 贷方合计(科目方向为 credit) */
const creditTotal = computed(() =>
  entryRows.value.reduce((sum, r) => r.subjectId && subjectDirectionMap.get(r.subjectId) === 'credit' ? sum + (r.amount || 0) : sum, 0))

async function fetchPeriods() {
  const { default: request } = await import('@/api/request')
  const d: any[] = await request.get('/v1/periods/all')
  periods.value = d.map((p: any) => p.periodCode).filter(Boolean)
  const map: Record<string, string> = {}
  for (const p of d) {
    if (p.periodCode) map[p.periodCode] = p.openingStatus || 'none'
  }
  periodStatusMap.value = map
  if (periods.value.length > 0 && !queryPeriod.value) {
    queryPeriod.value = periods.value[0]
  }
}

async function fetchBalances() {
  if (!queryPeriod.value) return
  loading.value = true
  try {
    balanceList.value = await getSubjectBalances(queryPeriod.value)
  } finally { loading.value = false }
}

async function fetchSubjectTree() {
  subjectTree.value = await getSubjectTree()
  indexSubjectDirections(subjectTree.value)
}

async function switchPeriod() {
  fetchBalances()
}

async function doTrialBalance() {
  if (!queryPeriod.value) return
  checking.value = true
  try {
    trialResult.value = await checkTrialBalance(queryPeriod.value)
  } finally { checking.value = false }
}

async function saveBalance() {
  const rows = entryRows.value.filter(r => r.subjectId && (r.amount || 0) > 0)
  if (rows.length === 0) { ElMessage.warning('请至少录入一个科目'); return }
  if (debitTotal.value !== creditTotal.value) {
    ElMessage.warning(`试算不平衡：借方 ${debitTotal.value.toFixed(2)}，贷方 ${creditTotal.value.toFixed(2)}`)
    return
  }
  saving.value = true
  try {
    const balances: Record<number, number> = {}
    rows.forEach(r => { balances[r.subjectId!] = r.amount || 0 })
    await initOpeningBalances(queryPeriod.value, balances)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    entryRows.value = []
    periodStatusMap.value = { ...periodStatusMap.value, [queryPeriod.value]: 'entered' }
    fetchBalances()
  } finally { saving.value = false }
}

function openEntryDialog() {
  entryRows.value = [{ subjectId: null, amount: 0 }]
  dialogVisible.value = true
}

function addEntryRow() {
  entryRows.value.push({ subjectId: null, amount: 0 })
}

function removeEntryRow(idx: number) {
  entryRows.value.splice(idx, 1)
}

async function onLockOpening() {
  if (!queryPeriod.value) return
  try {
    await ElMessageBox.confirm(
      `锁定期间 ${queryPeriod.value} 的期初余额, 锁定后需先解锁(无已过账凭证时)才能修改。是否继续？`,
      '锁定期初', { type: 'warning' })
  } catch { return }
  locking.value = true
  try {
    await lockOpeningBalances(queryPeriod.value)
    periodStatusMap.value = { ...periodStatusMap.value, [queryPeriod.value]: 'locked' }
    ElMessage.success('期初已锁定')
  } finally { locking.value = false }
}

async function onUnlockOpening() {
  if (!queryPeriod.value) return
  try {
    await ElMessageBox.confirm(
      `解锁期间 ${queryPeriod.value} 的期初数据, 仅在期间无已过账凭证时允许。是否继续？`,
      '解锁期初', { type: 'warning' })
  } catch { return }
  locking.value = true
  try {
    await unlockOpeningBalances(queryPeriod.value)
    periodStatusMap.value = { ...periodStatusMap.value, [queryPeriod.value]: 'entered' }
    ElMessage.success('期初已解锁')
  } finally { locking.value = false }
}

async function onClearOpening() {
  if (!queryPeriod.value) return
  try {
    await ElMessageBox.confirm(
      `确认清空期间 ${queryPeriod.value} 的期初余额？期间不能有已过账凭证, 且清空后需重新录入才能锁定。`,
      '清空期初', { type: 'warning', confirmButtonText: '确认清空' })
  } catch { return }
  try {
    await clearOpeningBalances(queryPeriod.value)
    periodStatusMap.value = { ...periodStatusMap.value, [queryPeriod.value]: 'none' }
    balanceList.value = []
    ElMessage.success('期初已清空')
  } catch (e: any) {
    ElMessage.error(e?.message || '清空失败')
  }
}

onMounted(() => {
  fetchPeriods()
  fetchSubjectTree()
})
</script>

<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.toolbar { margin-bottom:16px; display:flex; gap:8px; }
.trial-result { margin-top:16px; }

.entry-header {
  display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;
}
.entry-total {
  font-size:13px; padding:4px 10px; border-radius:6px; background:#f4f4f5;
}
.entry-total.balanced { background:#f0f9eb; color:#67c23a; }
.entry-total.unbalanced { background:#fef0f0; color:#f56c6c; }
.entry-row {
  display:flex; align-items:center; gap:8px; margin-bottom:8px;
}
</style>