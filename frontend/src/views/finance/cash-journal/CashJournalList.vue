<template>
  <div class="cash-journal">
    <el-card shadow="never">
      <div class="page-header"><span class="page-title">现金日记账</span></div>
      <div class="toolbar">
        <el-form :model="query" inline>
          <el-form-item label="期间"><el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" /></el-form-item>
          <el-form-item label="开始"><el-date-picker v-model="query.startDate" type="date" style="width:140px" /></el-form-item>
          <el-form-item label="结束"><el-date-picker v-model="query.endDate" type="date" style="width:140px" /></el-form-item>
          <el-form-item><el-button type="primary" @click="fetchData">查询</el-button></el-form-item>
        </el-form>
        <el-button type="primary" @click="openCreate">新增</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe style="width:100%">
        <el-table-column prop="journalNo" label="编号" width="140" />
        <el-table-column prop="journalDate" label="日期" width="100" />
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="debit" label="借方" width="130">
          <template #default="{row}"><span style="color:#409eff">{{ row.debit?.toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column prop="credit" label="贷方" width="130">
          <template #default="{row}"><span style="color:#e6a23c">{{ row.credit?.toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" width="130">
          <template #default="{row}"><strong>{{ row.balance?.toFixed(2) }}</strong></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button text size="small" @click="editRow(row)">编辑</el-button>
            <el-button text size="small" @click="genVoucher(row)" :disabled="row.voucherId">生成凭证</el-button>
            <el-popconfirm title="确认删除?" @confirm="handleDelete(row)">
              <template #reference><el-button text type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size"
        :total="total" layout="total,prev,pager,next" @current-change="fetchData" /></div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑':'新增'" width="500">
      <el-form :model="form" label-width="100">
        <el-form-item label="期间"><el-input v-model="form.period" placeholder="YYYYMM" /></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="form.journalDate" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" type="textarea" /></el-form-item>
        <el-form-item label="借方"><el-input-number v-model="form.debit" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="贷方"><el-input-number v-model="form.credit" :min="0" :precision="2" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
const { default: request } = await import('@/api/request')

const loading = ref(false), saving = ref(false), dialogVisible = ref(false), isEdit = ref(false)
const list = ref<any[]>([]), total = ref(0)
const query = ref({ period: '', startDate: null, endDate: null, current: 1, size: 20 })
const form = ref({ period: '', journalDate: null, summary: '', debit: 0, credit: 0 })
const editId = ref<number | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res: any = await request.get('/cash-journals/page', { params: query.value })
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false; editId.value = null
  form.value = { period: '', journalDate: null, summary: '', debit: 0, credit: 0 }
  dialogVisible.value = true
}

function editRow(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { period: row.period, journalDate: row.journalDate, summary: row.summary, debit: row.debit, credit: row.credit }
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await request.put(`/cash-journals/${editId.value}`, form.value)
      ElMessage.success('修改成功')
    } else {
      await request.post('/cash-journals', form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false; fetchData()
  } finally { saving.value = false }
}

async function handleDelete(row: any) {
  await request.delete(`/cash-journals/${row.id}`)
  ElMessage.success('删除成功'); fetchData()
}

async function genVoucher(row: any) {
  await request.post(`/cash-journals/${row.id}/generate-voucher`)
  ElMessage.success('凭证生成成功'); fetchData()
}

fetchData()
</script>
<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.toolbar { display:flex; justify-content:space-between; margin-bottom:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>