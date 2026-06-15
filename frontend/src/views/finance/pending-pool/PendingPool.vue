<template>
  <div class="pending-pool">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">C类待处理流水</span>
        <el-button @click="fetchData">刷新</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="银行账户">
          <el-select v-model="query.accountId" placeholder="选择账户" clearable style="width:240px" @change="fetchData">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName} (${a.accountNo})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe @row-click="onRowClick" style="cursor:pointer">
        <el-table-column type="selection" width="40" />
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
        <el-table-column prop="counterAccount" label="对方户名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="确认状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">待人工</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click.stop="openProcess(row)">处理</el-button>
            <el-button text size="small" type="primary" @click.stop="preview(row)">预览</el-button>
            <el-popconfirm title="确定删除?" @confirm="onDelete(row)">
              <template #reference><el-button text size="small" type="danger" @click.stop>删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 处理弹窗 -->
    <el-dialog v-model="processVisible" title="人工处理" width="500px">
      <template v-if="currentRow">
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="日期">{{ currentRow.txDate }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ fmtAmount(currentRow.amount) }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ currentRow.summary }}</el-descriptions-item>
          <el-descriptions-item label="对方户名" :span="2">{{ currentRow.counterAccount || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="120px">
          <el-form-item label="处理类型" prop="targetType">
            <el-radio-group v-model="form.targetType">
              <el-radio value="A">A类 - 直接生成凭证</el-radio>
              <el-radio value="B">B类 - 生成业务单据后制证</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" :loading="processing" @click="onProcess">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="凭证预览" width="600px">
      <el-table v-if="previewEntries.length" :data="previewEntries" border stripe size="small">
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'debit' ? 'danger' : 'success'" size="small">
              {{ row.direction === 'debit' ? '借' : '贷' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="subjectCode" label="科目编码" width="100" />
        <el-table-column prop="subjectName" label="科目名称" width="140" />
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="150" show-overflow-tooltip />
      </el-table>
      <p v-else style="text-align:center;color:#909399">暂无预览数据</p>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getBankStatementPage, deleteStatement, processManualStatement, previewDraftStatement,
  type BankStatementVO,
} from '@/api/modules/bankStatement'
import { getActiveBankAccounts, type BankAccountVO } from '@/api/modules/bankAccount'

const loading = ref(false)
const list = ref<BankStatementVO[]>([])
const total = ref(0)
const accounts = ref<BankAccountVO[]>([])

const query = ref<{ accountId?: number; current: number; size: number }>({
  current: 1, size: 20,
})

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    // 只查询 manual_pending 状态
    const res = await getBankStatementPage({ ...query.value, reviewStatus: 'manual_pending' } as any)
    list.value = (res as any).records || []
    total.value = (res as any).total || 0
  } finally {
    loading.value = false
  }
}

// Process dialog
const processVisible = ref(false)
const processing = ref(false)
const currentRow = ref<BankStatementVO | null>(null)
const form = ref<{ targetType: string }>({ targetType: 'A' })

function openProcess(row: BankStatementVO) {
  currentRow.value = row
  form.value = { targetType: 'A' }
  processVisible.value = true
}

async function onProcess() {
  if (!currentRow.value) return
  processing.value = true
  try {
    await processManualStatement(currentRow.value.id, form.value.targetType)
    ElMessage.success('处理完成')
    processVisible.value = false
    await fetchData()
  } catch { /* handled */ } finally {
    processing.value = false
  }
}

// Preview dialog
const previewVisible = ref(false)
const previewEntries = ref<any[]>([])

async function preview(row: BankStatementVO) {
  try {
    previewEntries.value = await previewDraftStatement(row.id)
    previewVisible.value = true
  } catch (e: any) {
    ElMessage.warning(e?.message || '预览失败')
  }
}

// Row click opens preview
function onRowClick(row: any, column: any) {
  if (column?.type === 'selection') return
  preview(row)
}

async function onDelete(row: any) {
  try {
    await deleteStatement(row.id)
    ElMessage.success('已删除')
    await fetchData()
  } catch { /* handled */ }
}

onMounted(async () => {
  accounts.value = await getActiveBankAccounts() as any
  fetchData()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
.page-pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
