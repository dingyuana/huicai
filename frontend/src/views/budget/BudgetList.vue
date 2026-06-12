<template>
  <div class="budget-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">预算管理</span>
        <el-button type="primary" @click="openEdit()">新增预算</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" clearable />
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
        <el-table-column prop="budgetNo" label="预算单号" width="180" />
        <el-table-column prop="period" label="期间" width="100" align="center" />
        <el-table-column prop="budgetType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ TYPE_MAP[row.budgetType] || row.budgetType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG_MAP[row.status] || 'info') as any" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" v-if="row.status === 'DRAFT'" @click="onApprove(row)">审批</el-button>
            <el-button text type="warning" v-if="row.status === 'APPROVED'" @click="onActivate(row)">激活</el-button>
            <el-button text v-if="row.status === 'ACTIVE'" @click="onExecution(row)">执行分析</el-button>
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

    <el-dialog v-model="dialogVisible" title="新增预算" width="640px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="预算单号"><el-input v-model="form.budgetNo" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="期间" prop="period"><el-input v-model="form.period" placeholder="YYYYMM" /></el-form-item>
        <el-form-item label="类型" prop="budgetType">
          <el-select v-model="form.budgetType" style="width:100%">
            <el-option v-for="o in TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="executionVisible" title="执行分析" width="640px">
      <div v-if="execution">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-statistic title="预算总额" :value="Number(execution.totalBudget || 0)" :precision="2" />
          </el-col>
          <el-col :span="12">
            <el-statistic title="已用" :value="Number(execution.totalUsed || 0)" :precision="2" />
          </el-col>
          <el-col :span="12">
            <el-statistic title="执行率" :value="Number(execution.executionRatio || 0)" :precision="2" suffix="%" />
          </el-col>
          <el-col :span="12">
            <el-statistic title="剩余" :value="Number(execution.remaining || 0)" :precision="2" />
          </el-col>
        </el-row>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { pageBudget, createBudget, approveBudget, activateBudget, executionAnalysis } from '@/api/modules/budget'

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVED', label: '已审批' },
  { value: 'ACTIVE', label: '已激活' },
  { value: 'CLOSED', label: '已关闭' },
]
const STATUS_MAP: Record<string, string> = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label]))
const STATUS_TAG_MAP: Record<string, string> = { DRAFT: 'info', APPROVED: 'primary', ACTIVE: 'success', CLOSED: 'warning' }

const TYPE_OPTIONS = [
  { value: 'EXPENSE', label: '费用' },
  { value: 'REVENUE', label: '收入' },
  { value: 'CAPEX', label: '资本性支出' },
]
const TYPE_MAP: Record<string, string> = Object.fromEntries(TYPE_OPTIONS.map((o) => [o.value, o.label]))

const query = reactive({ period: '', status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ budgetType: 'EXPENSE' })
const executionVisible = ref(false)
const execution = ref<any>(null)
const rules = {
  period: [{ required: true, message: '请输入期间', trigger: 'blur' }],
  budgetType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageBudget(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const openEdit = () => {
  Object.assign(form, { id: undefined, budgetNo: '', period: '', budgetType: 'EXPENSE', remark: '', entries: [] })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!form.entries) form.entries = []
    await createBudget(form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchData()
  })
}

const onApprove = async (row: any) => {
  await approveBudget(row.id)
  ElMessage.success('已审批')
  fetchData()
}

const onActivate = async (row: any) => {
  await activateBudget(row.id)
  ElMessage.success('已激活')
  fetchData()
}

const onExecution = async (row: any) => {
  execution.value = await executionAnalysis(row.period)
  executionVisible.value = true
}

onMounted(fetchData)
</script>
