<template>
  <div class="ledger-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="科目余额表" name="balance">
        <div class="filter-bar">
          <el-input v-model="balancePeriod" placeholder="会计期间 YYYYMM" style="width:160px" />
          <el-button type="primary" @click="loadBalance">查询</el-button>
          <el-button @click="onTrialBalance">试算平衡</el-button>
        </div>
        <el-table :data="balanceRows" v-loading="balanceLoading" border stripe show-summary>
          <el-table-column prop="subjectCode" label="科目编码" width="140" />
          <el-table-column prop="subjectName" label="科目名称" min-width="180" />
          <el-table-column label="方向" width="70" align="center">
            <template #default="{ row }">{{ row.direction === 'debit' ? '借' : '贷' }}</template>
          </el-table-column>
          <el-table-column label="期初余额" width="140" align="right">
            <template #default="{ row }">{{ fmt(row.beginBalance) }}</template>
          </el-table-column>
          <el-table-column label="借方发生" width="140" align="right">
            <template #default="{ row }">{{ fmt(row.debitTotal) }}</template>
          </el-table-column>
          <el-table-column label="贷方发生" width="140" align="right">
            <template #default="{ row }">{{ fmt(row.creditTotal) }}</template>
          </el-table-column>
          <el-table-column label="期末余额" width="140" align="right">
            <template #default="{ row }">{{ fmt(row.endBalance) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="总分类账" name="general">
        <div class="filter-bar">
          <el-tree-select
            v-model="glSubjectId"
            :data="leafSubjectOptions"
            :props="{ value: 'id', label: 'name' }"
            check-strictly
            :render-after-expand="false"
            placeholder="选择末级科目"
            style="width:280px"
          />
          <el-input v-model="glPeriod" placeholder="会计期间 YYYYMM" style="width:160px" />
          <el-button type="primary" @click="loadGeneral">查询</el-button>
        </div>
        <el-table :data="glRows" v-loading="glLoading" border stripe>
          <el-table-column label="日期/凭证" width="160">
            <template #default="{ row }">
              <span v-if="row.type === 'OPENING'" class="row-tag">期初</span>
              <span v-else-if="row.type === 'CLOSING'" class="row-tag">合计</span>
              <span v-else>#{{ row.voucherId }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="240" />
          <el-table-column label="借方" width="140" align="right">
            <template #default="{ row }">{{ fmt(row.debit) }}</template>
          </el-table-column>
          <el-table-column label="贷方" width="140" align="right">
            <template #default="{ row }">{{ fmt(row.credit) }}</template>
          </el-table-column>
          <el-table-column label="余额" width="160" align="right">
            <template #default="{ row }">{{ fmt(row.running) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="明细账" name="subsidiary">
        <div class="filter-bar">
          <el-tree-select
            v-model="slSubjectId"
            :data="leafSubjectOptions"
            :props="{ value: 'id', label: 'name' }"
            check-strictly
            :render-after-expand="false"
            placeholder="选择末级科目"
            style="width:280px"
          />
          <el-input v-model="slPeriod" placeholder="会计期间 YYYYMM" style="width:160px" />
          <el-button type="primary" @click="loadSubsidiary">查询</el-button>
        </div>
        <el-table :data="slRows" v-loading="slLoading" border stripe>
          <el-table-column label="凭证ID" prop="voucherId" width="100" />
          <el-table-column label="科目编码" prop="subjectCode" width="140" />
          <el-table-column label="科目名称" prop="subjectName" min-width="180" />
          <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
          <el-table-column label="借方" width="140" align="right">
            <template #default="{ row }">{{ fmt(row.debit) }}</template>
          </el-table-column>
          <el-table-column label="贷方" width="140" align="right">
            <template #default="{ row }">{{ fmt(row.credit) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="trialDialogVisible" title="试算平衡结果" width="520">
      <div v-if="trialResult">
        <p>期间: <b>{{ trialResult.period }}</b></p>
        <p>期初: 借 {{ fmt(trialResult.totalBeginDebit) }} / 贷 {{ fmt(trialResult.totalBeginCredit) }}
          <el-tag :type="trialResult.beginBalanced ? 'success' : 'danger'" size="small" style="margin-left:8px">
            {{ trialResult.beginBalanced ? '平衡' : '不平衡' }}
          </el-tag>
        </p>
        <p>发生: 借 {{ fmt(trialResult.totalDebitTotal) }} / 贷 {{ fmt(trialResult.totalCreditTotal) }}
          <el-tag :type="trialResult.movementBalanced ? 'success' : 'danger'" size="small" style="margin-left:8px">
            {{ trialResult.movementBalanced ? '平衡' : '不平衡' }}
          </el-tag>
        </p>
        <p>期末: 借 {{ fmt(trialResult.totalEndDebit) }} / 贷 {{ fmt(trialResult.totalEndCredit) }}
          <el-tag :type="trialResult.endBalanced ? 'success' : 'danger'" size="small" style="margin-left:8px">
            {{ trialResult.endBalanced ? '平衡' : '不平衡' }}
          </el-tag>
        </p>
        <p>整体: <el-tag :type="trialResult.balanced ? 'success' : 'danger'">{{ trialResult.balanced ? '通过' : '失败' }}</el-tag></p>
      </div>
      <template #footer>
        <el-button @click="trialDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSubjectBalance, getGeneralLedger, getSubsidiaryLedger, getTrialBalance, type SubjectBalanceRow, type LedgerRow, type TrialBalance } from '@/api/modules/ledger'
import { getSubjectTree, type SubjectVO } from '@/api/modules/subject'

const activeTab = ref('balance')
const currentPeriod = new Date().toISOString().slice(0, 7).replace('-', '')

const balancePeriod = ref(currentPeriod)
const balanceLoading = ref(false)
const balanceRows = ref<SubjectBalanceRow[]>([])

const glPeriod = ref(currentPeriod)
const glSubjectId = ref<number | undefined>(undefined)
const glLoading = ref(false)
const glRows = ref<LedgerRow[]>([])

const slPeriod = ref(currentPeriod)
const slSubjectId = ref<number | undefined>(undefined)
const slLoading = ref(false)
const slRows = ref<LedgerRow[]>([])

const trialDialogVisible = ref(false)
const trialResult = ref<TrialBalance | null>(null)

const subjectTree = ref<SubjectVO[]>([])
const leafSubjectOptions = computed(() => {
  const list: SubjectVO[] = []
  const walk = (nodes: SubjectVO[]) => {
    for (const n of nodes) {
      if (n.isLeaf) list.push(n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(subjectTree.value)
  return list
})

function fmt(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadBalance() {
  if (!balancePeriod.value) {
    ElMessage.warning('请输入会计期间')
    return
  }
  balanceLoading.value = true
  try {
    balanceRows.value = await getSubjectBalance(balancePeriod.value)
  } catch {
    // handled
  } finally {
    balanceLoading.value = false
  }
}

async function loadGeneral() {
  if (!glSubjectId.value || !glPeriod.value) {
    ElMessage.warning('请选择科目并输入期间')
    return
  }
  glLoading.value = true
  try {
    glRows.value = await getGeneralLedger(glSubjectId.value, glPeriod.value)
  } catch {
    // handled
  } finally {
    glLoading.value = false
  }
}

async function loadSubsidiary() {
  if (!slSubjectId.value || !slPeriod.value) {
    ElMessage.warning('请选择科目并输入期间')
    return
  }
  slLoading.value = true
  try {
    slRows.value = await getSubsidiaryLedger(slSubjectId.value, slPeriod.value)
  } catch {
    // handled
  } finally {
    slLoading.value = false
  }
}

async function onTrialBalance() {
  if (!balancePeriod.value) {
    ElMessage.warning('请输入会计期间')
    return
  }
  trialResult.value = await getTrialBalance(balancePeriod.value)
  trialDialogVisible.value = true
}

onMounted(async () => {
  try {
    subjectTree.value = await getSubjectTree()
  } catch {
    // ignore
  }
  await loadBalance()
})
</script>

<style scoped>
.ledger-page {
  padding: 0;
}
.filter-bar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.row-tag {
  display: inline-block;
  padding: 0 8px;
  background: #f0f9ff;
  color: #1890ff;
  border-radius: 2px;
  font-size: 12px;
}
</style>
