<template>
  <div class="budget-edit">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑预算' : '新增预算' }}</span>
      </div>
      <el-form :model="form" label-width="120" style="max-width:800px">
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item label="预算期间"><el-input v-model="form.period" placeholder="YYYYMM" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="预算类型"><el-select v-model="form.budgetType" style="width:100%">
            <el-option label="费用预算" value="EXPENSE" /><el-option label="收入预算" value="REVENUE" /><el-option label="资本支出" value="CAPEX" />
          </el-select></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-form-item label="预算明细">
          <el-button type="primary" size="small" @click="addEntry">添加行</el-button>
        </el-form-item>
        <el-table :data="form.entries" border stripe style="width:100%">
          <el-table-column label="科目" width="200">
            <template #default="{ row, $index }">
              <el-tree-select v-model="row.subjectId" :data="subjectTree" :props="{label:'name',value:'id'}"
                check-strictly style="width:100%" placeholder="选择科目" @change="calcTotal" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="150">
            <template #default="{ row, $index }">
              <el-input-number v-model="row.amount" :precision="2" :min="0" style="width:100%" @change="calcTotal" />
            </template>
          </el-table-column>
          <el-table-column label="控制方式" width="130">
            <template #default="{ row }">
              <el-select v-model="row.controlType" style="width:100%">
                <el-option label="警告" value="WARN" /><el-option label="审批" value="APPROVE" /><el-option label="禁止" value="BLOCK" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60">
            <template #default="{ $index }">
              <el-button text type="danger" size="small" @click="removeEntry($index)">✕</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top:16px;text-align:right;font-size:16px">
          合计: <strong>{{ totalAmount.toFixed(2) }}</strong>
        </div>
        <div style="margin-top:24px;text-align:center">
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存草稿</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
const { default: request } = await import('@/api/request')

const route = useRoute()
const router = useRouter()
const isEdit = ref(false)
const saving = ref(false)
const subjectTree = ref<any[]>([])
const form = ref({ period: '', budgetType: 'EXPENSE', remark: '', entries: [{ subjectId: null, amount: 0, controlType: 'WARN' }] })
const totalAmount = computed(() => form.value.entries.reduce((s, e) => s + (e.amount || 0), 0))

function addEntry() { form.value.entries.push({ subjectId: null, amount: 0, controlType: 'WARN' }) }
function removeEntry(i: number) { form.value.entries.splice(i, 1) }
function calcTotal() { /* computed handles it */ }

async function handleSave() {
  saving.value = true
  try {
    await request.post('/budgets', form.value)
    ElMessage.success('保存成功')
    router.push('/budget')
  } finally { saving.value = false }
}

onMounted(async () => {
  subjectTree.value = await request.get('/subjects/tree')
  const id = route.query.id
  if (id) {
    isEdit.value = true
    const data: any = await request.get(`/budgets/${id}`)
    form.value = { period: data.period, budgetType: data.budgetType, remark: data.remark, entries: data.entries || [] }
  }
})
</script>
<style scoped>
.page-header { margin-bottom:24px; }
.page-title { font-size:16px; font-weight:600; }
</style>