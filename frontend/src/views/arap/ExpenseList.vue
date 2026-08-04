<template>
  <div class="expense-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">费用报销单</span>
        <el-button type="primary" @click="$router.push('/arap/expense/edit')">新增报销</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="员工">
          <el-select v-model="query.employeeId" filterable clearable placeholder="按工号/姓名搜索" style="width:150px">
            <el-option v-for="e in employees" :key="e.id" :value="e.id as number" :label="`${e.code} ${e.name}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:130px">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="id" label="编号" width="70" />
        <el-table-column label="员工" width="140">
          <template #default="{ row }">{{ row.employeeName || employeeLabel(row.employeeId) }}</template>
        </el-table-column>
        <el-table-column prop="expenseType" label="费用类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ EXPENSE_TYPE_MAP[row.expenseType] || row.expenseType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG_MAP[row.status] || 'info'" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" v-if="row.status === 'DRAFT'" @click="$router.push('/arap/expense/edit?id=' + row.id)">编辑</el-button>
            <el-button text type="primary" v-if="row.status === 'DRAFT'" @click="onSubmit(row)">提交</el-button>
            <el-button text type="success" v-if="row.status === 'SUBMITTED'" @click="onApprove(row)">通过</el-button>
            <el-button text type="danger" v-if="row.status === 'SUBMITTED'" @click="onReject(row)">驳回</el-button>
            <el-button text type="warning" v-if="row.status === 'APPROVED'" @click="onAutoVoucher(row)">生成凭证</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageExpense, submitExpense, approveExpense, rejectExpense, autoVoucher } from '@/api/modules/expense'
import { listEmployee, type Employee } from '@/api/modules/employee'

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'SUBMITTED', label: '已提交' },
  { value: 'APPROVED', label: '已审核' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'VOUCHERED', label: '已制证' },
]
const STATUS_MAP: Record<string, string> = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label]))
const STATUS_TAG_MAP: Record<string, 'success' | 'warning' | 'info' | 'primary' | 'danger'> = { DRAFT: 'info', SUBMITTED: 'warning', APPROVED: 'success', REJECTED: 'danger', VOUCHERED: 'primary' }

const EXPENSE_TYPE_MAP: Record<string, string> = {
  TRAVEL: '差旅费', OFFICE: '办公费', ENTERTAINMENT: '招待费',
  TRANSPORT: '交通费', MEAL: '餐饮费', OTHER: '其他',
}

const query = reactive({ employeeId: '', status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const employees = ref<Employee[]>([])

const employeeLabel = (id?: number) => {
  const e = employees.value.find((x) => x.id === id)
  return e ? `${e.code} ${e.name}` : id?.toString() || '-'
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

onMounted(async () => {
  try {
    employees.value = await listEmployee()
  } catch {
    /* 员工列表加载失败不阻塞列表 */
  }
})

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageExpense(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const onSubmit = async (row: any) => {
  await submitExpense(row.id)
  ElMessage.success('已提交')
  fetchData()
}

const onApprove = async (row: any) => {
  await approveExpense(row.id)
  ElMessage.success('已审核通过')
  fetchData()
}

const onReject = async (row: any) => {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', { inputType: 'textarea', inputPlaceholder: '驳回原因' })
  await rejectExpense(row.id, value)
  ElMessage.success('已驳回')
  fetchData()
}

const onAutoVoucher = async (row: any) => {
  await autoVoucher(row.id)
  ElMessage.success('凭证已生成')
  fetchData()
}

onMounted(fetchData)
</script>
<style scoped>
.page-header { display:flex; justify-content:space-between; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.filter-form { margin-bottom:16px; }
.page-pagination { margin-top:16px; text-align:right; }
</style>