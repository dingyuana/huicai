<template>
  <div class="expense-edit">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑报销单' : '新增报销单' }}</span>
      </div>
      <el-form :model="form" label-width="100px" style="max-width:600px" ref="formRef" :rules="rules">
        <el-form-item label="员工" prop="employeeId">
          <el-select v-model="form.employeeId" filterable placeholder="按工号/姓名搜索" style="width:100%">
            <el-option v-for="e in employees" :key="e.id" :value="e.id as number"
              :label="`${e.code} ${e.name}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用类型" prop="expenseType">
          <el-select v-model="form.expenseType" style="width:100%">
            <el-option label="差旅费" value="TRAVEL" />
            <el-option label="办公费" value="OFFICE" />
            <el-option label="招待费" value="ENTERTAINMENT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :precision="2" :min="0.01" style="width:100%" />
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="报销事由说明" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存草稿</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { createExpense, updateExpense, getExpense } from '@/api/modules/expense'
import { listEmployee, type Employee } from '@/api/modules/employee'

const route = useRoute()
const router = useRouter()
const isEdit = ref(false)
const saving = ref(false)
const employees = ref<Employee[]>([])
const formRef = ref<FormInstance>()
const form = ref({ employeeId: undefined as unknown as number, expenseType: 'OTHER', amount: 0, summary: '', remark: '' })
const rules = {
  employeeId: [{ required: true, message: '请输入员工ID', trigger: 'blur' }],
  expenseType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入摘要', trigger: 'blur' }],
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await updateExpense(Number(route.query.id), form.value)
        ElMessage.success('更新成功')
      } else {
        await createExpense(form.value)
        ElMessage.success('创建成功')
      }
      router.push('/arap/expense')
    } finally {
      saving.value = false
    }
  })
}

onMounted(async () => {
  try {
    employees.value = await listEmployee()
  } catch {
    /* 员工列表加载失败不阻塞编辑 */
  }
  const id = route.query.id
  if (id) {
    isEdit.value = true
    const data: any = await getExpense(Number(id))
    form.value = { employeeId: data.employeeId, expenseType: data.expenseType, amount: data.amount, summary: data.summary, remark: data.remark }
  }
})
</script>
<style scoped>
.page-header { margin-bottom:24px; }
.page-title { font-size:16px; font-weight:600; }
</style>