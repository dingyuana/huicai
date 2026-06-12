<template>
  <div class="settlement-page">
    <el-card shadow="never">
      <div class="page-header"><span class="page-title">往来核销</span></div>
      <div class="toolbar">
        <el-form :model="query" inline>
          <el-form-item label="类型"><el-select v-model="query.settlementType" placeholder="全部" style="width:130px" @change="fetchData">
            <el-option label="全部" value="" /><el-option label="应收核销" value="RECEIVABLE" /><el-option label="应付核销" value="PAYABLE" />
          </el-select></el-form-item>
          <el-form-item><el-button type="primary" @click="fetchData">查询</el-button></el-form-item>
        </el-form>
        <el-button type="primary" @click="openCreate">新增核销</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe style="width:100%">
        <el-table-column type="index" label="序号" width="50" />
        <el-table-column prop="settlementNo" label="核销编号" width="140" />
        <el-table-column label="类型" width="100">
          <template #default="{row}">{{ row.settlementType==='RECEIVABLE'?'应收核销':'应付核销' }}</template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户/供应商" min-width="150" />
        <el-table-column prop="totalAmount" label="核销金额" width="130">{{ (row:any) => row.totalAmount?.toFixed(2) }}</el-table-column>
        <el-table-column prop="settlementDate" label="核销日期" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{row}"><el-tag :type="row.status==='CONFIRMED'?'success':'info'" size="small">{{ row.status==='CONFIRMED'?'已确认':'草稿' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button v-if="row.status==='DRAFT'" text size="small" @click="confirmSettlement(row)">确认</el-button>
            <el-button text size="small" @click="viewDetail(row)">详情</el-button>
            <el-popconfirm v-if="row.status==='DRAFT'" title="确认删除?" @confirm="handleDelete(row)">
              <template #reference><el-button text type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size"
        :total="total" layout="total,prev,pager,next" @current-change="fetchData" /></div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增核销" width="600">
      <el-form :model="form" label-width="100">
        <el-form-item label="类型"><el-select v-model="form.settlementType" style="width:100%">
          <el-option label="应收核销" value="RECEIVABLE" /><el-option label="应付核销" value="PAYABLE" />
        </el-select></el-form-item>
        <el-form-item label="客户/供应商"><el-select v-model="form.partyId" filterable style="width:100%" placeholder="搜索">
          <el-option v-for="p in parties" :key="p.id" :label="p.name" :value="p.id" />
        </el-select></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="form.totalAmount" :precision="2" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailDialog" title="核销详情" width="500">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="核销编号">{{ detail?.settlementNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail?.settlementType==='RECEIVABLE'?'应收核销':'应付核销' }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ detail?.totalAmount?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail?.status==='CONFIRMED'?'已确认':'草稿' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail?.remark }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import request from '@/api/request'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false), saving = ref(false), dialogVisible = ref(false), detailDialog = ref(false)
const list = ref<any[]>([]), total = ref(0), detail = ref<any>(null), parties = ref<any[]>([])
const query = ref({ settlementType: '', current: 1, size: 20 })
const form = ref({ settlementType: 'RECEIVABLE', partyId: null, totalAmount: 0, remark: '' })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await request.get('/arap-settlements/page', { params: query.value })
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

async function openCreate() {
  form.value = { settlementType: 'RECEIVABLE', partyId: null, totalAmount: 0, remark: '' }
  const cust: any[] = await request.get('/customers/list')
  const vend: any[] = await request.get('/vendors/list')
  parties.value = [...cust.map((c:any)=>({id:c.id,name:c.name})), ...vend.map((v:any)=>({id:v.id,name:v.name}))]
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    await request.post('/arap-settlements', form.value)
    ElMessage.success('新增成功'); dialogVisible.value = false; fetchData()
  } finally { saving.value = false }
}

async function confirmSettlement(row: any) {
  await request.post(`/arap-settlements/${row.id}/confirm`)
  ElMessage.success('核销已确认'); fetchData()
}

async function handleDelete(row: any) {
  await request.delete(`/arap-settlements/${row.id}`)
  ElMessage.success('删除成功'); fetchData()
}

async function viewDetail(row: any) {
  detail.value = await request.get(`/arap-settlements/${row.id}`)
  detailDialog.value = true
}

onMounted(fetchData)
</script>
<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.toolbar { display:flex; justify-content:space-between; margin-bottom:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>