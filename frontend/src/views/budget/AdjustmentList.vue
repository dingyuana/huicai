<template>
  <div class="adjustment-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">预算调整</span>
        <el-button type="primary" @click="dialogVisible = true">新增调整</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="id" label="编号" width="80" />
        <el-table-column prop="budgetId" label="预算单ID" width="100" />
        <el-table-column label="调整金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.adjustAmount) }}</template>
        </el-table-column>
        <el-table-column label="调整后金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.newAmount) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="调整原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'warning'" size="small">
              {{ row.status === 'DRAFT' ? '草稿' : row.status === 'SUBMITTED' ? '已提交' : row.status === 'APPROVED' ? '已审批' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" v-if="row.status === 'DRAFT'" @click="onSubmit(row)">提交</el-button>
            <el-button text type="primary" v-if="row.status === 'SUBMITTED'" @click="onApprove(row)">审批</el-button>
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

    <el-dialog v-model="dialogVisible" title="新增调整" width="500px">
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="预算单ID" prop="budgetId">
          <el-input-number v-model="form.budgetId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="调整金额" prop="adjustAmount">
          <el-input-number v-model="form.adjustAmount" :precision="2" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="调整后金额" prop="newAmount">
          <el-input-number v-model="form.newAmount" :precision="2" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import request from '@/api/request'

const query = reactive({ status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ budgetId: null, adjustAmount: 0, newAmount: 0, reason: '', status: 'DRAFT' })
const rules = {
  budgetId: [{ required: true, message: '请输入预算单ID', trigger: 'blur' }],
  adjustAmount: [{ required: true, message: '请输入调整金额', trigger: 'blur' }],
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/sme/budget/v1/budgets/adjustments/page', { params: query })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const onCreate = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await request.post('/sme/budget/v1/budgets/adjustments', form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchData()
  })
}

const onSubmit = async (row: any) => {
  // 提交调整需要调用 submit 端点，但后端无此端点，先直接改状态
  ElMessage.warning('提交功能待完善')
}

const onApprove = async (row: any) => {
  await request.post(`/budgets/adjustments/${row.id}/approve`)
  ElMessage.success('已审批')
  fetchData()
}

onMounted(fetchData)
</script>
<style scoped>
.page-header { display:flex; justify-content:space-between; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.page-pagination { margin-top:16px; text-align:right; }
</style>