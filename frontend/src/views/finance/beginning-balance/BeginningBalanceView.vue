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

      <el-dialog v-model="dialogVisible" title="录入期初余额" width="500">
        <el-form :model="entryForm" label-width="100">
          <el-form-item label="科目">
            <el-tree-select v-model="entryForm.subjectId" :data="subjectTree" :props="({label:'name',value:'id',children:'children'} as any)"
              check-strictly style="width:100%" placeholder="选择末级科目" />
          </el-form-item>
          <el-form-item label="期初余额">
            <el-input-number v-model="entryForm.amount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible=false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveBalance">保存</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
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

const entryForm = ref({ subjectId: null as number | null, amount: 0 })

async function fetchPeriods() {
  const { default: request } = await import('@/api/request')
  const d: any[] = await request.get('/v1/periods/all')
  periods.value = d.map((p: any) => p.period)
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
  if (!entryForm.value.subjectId) { ElMessage.warning('请选择科目'); return }
  saving.value = true
  try {
    const balances: Record<number, number> = {}
    balances[entryForm.value.subjectId] = entryForm.value.amount
    await initOpeningBalances(queryPeriod.value, balances)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchBalances()
  } finally { saving.value = false }
}

function openEntryDialog() {
  entryForm.value = { subjectId: null, amount: 0 }
  dialogVisible.value = true
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
</style>