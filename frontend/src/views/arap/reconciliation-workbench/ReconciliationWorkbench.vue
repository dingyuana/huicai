<template>
  <div class="reconciliation-workbench">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">核销工作台</span>
        <el-button @click="onRefresh">刷新</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="银行账户">
          <el-select v-model="query.accountId" placeholder="选择账户" clearable style="width:240px" @change="onAccountChange">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName} (${a.accountNo})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对方名称">
          <el-input v-model="query.counterparty" placeholder="搜索对方名称" clearable style="width:180px" @clear="onSearch" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe @row-click="onRowClick" style="cursor:pointer">
        <el-table-column prop="txDate" label="日期" width="110" />
        <el-table-column label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.txType === 'INCOME' ? 'success' : 'warning'" size="small">
              {{ row.txType === 'INCOME' ? '收' : '支' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="counterAccount" label="对方名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="业务分类" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.classification === 'business_receipt' ? 'success' : 'primary'" size="small">
              {{ CLASSIFICATION_LABELS[row.classification] || row.classification }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reviewStatus === 'CONFIRMED' ? 'success' : 'warning'" size="small">
              {{ row.reviewStatus === 'CONFIRMED' ? '已确认' : '待确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核销操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.reviewStatus === 'CONFIRMED'"
              text size="small" type="primary" @click.stop="onShowRecommend(row)">
              核销推荐
            </el-button>
            <el-tag v-else size="small" type="info">请先确认分类</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end"
        @change="fetchData"
      />
    </el-card>

    <!-- 核销推荐抽屉 -->
    <el-drawer v-model="recommendDrawerVisible" :title="drawerTitle" size="600px" destroy-on-close>
      <template v-if="recommendLoading">
        <div style="text-align:center;padding:40px">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p style="margin-top:12px;color:#909399">正在匹配核销推荐...</p>
        </div>
      </template>

      <template v-else-if="recommendResult">
        <el-alert :title="recommendResult.message" :type="recommendResult.items?.length ? 'success' : 'info'" :closable="false" style="margin-bottom:16px" />

        <el-table v-if="recommendResult.items?.length" :data="recommendResult.items" border stripe size="small">
          <el-table-column label="单据类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.targetDocType === 'INVOICE_OUT' ? 'success' : 'warning'" size="small">
                {{ row.targetDocType === 'INVOICE_OUT' ? '应收' : '应付' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetDocNo" label="单据号" width="140" />
          <el-table-column label="原始金额" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.originalAmount) }}</template>
          </el-table-column>
          <el-table-column label="未核销金额" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.unsettledAmount) }}</template>
          </el-table-column>
          <el-table-column label="匹配度" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.matchLevel === 'GREEN' ? 'success' : 'warning'" size="small">
                {{ (row.matchScore * 100).toFixed(0) }}%
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="建议核销" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.suggestedAmount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <el-button text size="small" type="primary" @click="onExecuteRecon(row)">
                执行核销
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <p v-else style="text-align:center;color:#909399;padding:20px">暂无匹配的核销项</p>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getBankStatementPage } from '@/api/modules/bankStatement'
import { getActiveBankAccounts, type BankAccountVO } from '@/api/modules/bankAccount'
import {
  getReconciliationRecommend, executeReconciliation,
  type RecommendItem, type ReconciliationRecommendResult,
} from '@/api/modules/reconciliation'

const CLASSIFICATION_LABELS: Record<string, string> = {
  business_receipt: '业务收款',
  business_payment: '业务付款',
}

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const accounts = ref<BankAccountVO[]>([])

const query = ref<{ accountId?: number; counterparty?: string; current: number; size: number }>({
  current: 1, size: 20,
})

const recommendDrawerVisible = ref(false)
const recommendLoading = ref(false)
const recommendResult = ref<ReconciliationRecommendResult | null>(null)
const drawerTitle = ref('')
const currentStatement = ref<any>(null)

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    const params: any = {
      accountId: query.value.accountId,
      status: 'UNMATCHED',
      current: query.value.current,
      size: query.value.size,
    }
    const res: any = await getBankStatementPage(params)
    list.value = (res.records || []).filter(
      (r: any) => r.classification === 'business_receipt' || r.classification === 'business_payment'
    )
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function onAccountChange() {
  query.value.current = 1
  await fetchData()
}

function onSearch() {
  query.value.current = 1
  fetchData()
}

function onReset() {
  query.value = { current: 1, size: 20 }
  fetchData()
}

async function onRefresh() {
  await fetchData()
}

async function onRowClick(row: any, column: any) {
  if (column?.type === 'selection') return
  if (row.reviewStatus === 'CONFIRMED') {
    await onShowRecommend(row)
  } else {
    ElMessage.info('请先在银行对账单页面确认该流水分类')
  }
}

async function onShowRecommend(row: any) {
  currentStatement.value = row
  drawerTitle.value = `核销推荐 — ${row.counterAccount || '未知对方'} ¥${fmtAmount(row.amount)}`
  recommendDrawerVisible.value = true
  recommendLoading.value = true
  recommendResult.value = null
  try {
    recommendResult.value = await getReconciliationRecommend(row.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '获取核销推荐失败')
    recommendDrawerVisible.value = false
  } finally {
    recommendLoading.value = false
  }
}

async function onExecuteRecon(item: RecommendItem) {
  if (!currentStatement.value) return
  try {
    await executeReconciliation({
      sourceDocType: 'bank_txn',
      sourceDocId: currentStatement.value.id,
      targetDocType: item.targetDocType,
      targetDocId: item.targetDocId,
      amount: item.suggestedAmount,
      matchScore: item.matchScore,
      matchMethod: 'AUTO',
    })
    ElMessage.success('核销执行成功')
    recommendDrawerVisible.value = false
    await fetchData()
  } catch (e: any) {
    ElMessage.error(e?.message || '核销执行失败')
  }
}

onMounted(async () => {
  try {
    accounts.value = await getActiveBankAccounts()
    if (accounts.value.length === 1) {
      query.value.accountId = accounts.value[0].id
    }
  } catch { /* ignore */ }
  await fetchData()
})
</script>

<style scoped>
.reconciliation-workbench .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
</style>
