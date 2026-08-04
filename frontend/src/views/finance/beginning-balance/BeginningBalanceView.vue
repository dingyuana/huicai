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
            <el-button type="primary" @click="openEntryDialog">录入期初</el-button>
            <el-button @click="fetchBalances">刷新</el-button>
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
          <el-alert title='期初数据录入并试算平衡后，需点击"启用"按钮锁定期初数据。锁定后不可修改。' type="warning" show-icon />
          <div style="margin-top:16px">
            <el-button v-if="!isLocked" type="primary" @click="confirmLock">启用并锁定期初</el-button>
            <el-tag v-else type="success" size="large">期初已锁定 ✅</el-tag>
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
import { ElMessage } from 'element-plus'
import { getSubjectTree } from '@/api/modules/subject'
import { initOpeningBalances, getSubjectBalances, checkTrialBalance } from '@/api/modules/subject'

const activeTab = ref('entry')
const loading = ref(false)
const checking = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const queryPeriod = ref('')
const balanceList = ref<any[]>([])
const periods = ref<string[]>([])
const trialResult = ref<any>(null)
const isLocked = ref(false)
const subjectTree = ref<any[]>([])

const entryRows = ref<Array<{ subjectId: number | null; amount: number }>>([])

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

function confirmLock() {
  isLocked.value = true
  ElMessage.success('期初数据已锁定')
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