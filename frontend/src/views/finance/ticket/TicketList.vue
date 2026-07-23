<template>
  <div class="ticket-page">
    <el-card shadow="never">
      <div class="page-header"><span class="page-title">票据管理</span></div>
      <div class="toolbar">
        <el-form :model="query" inline>
          <el-form-item label="类型"><el-select v-model="query.ticketType" placeholder="全部" style="width:130px" @change="fetchData">
            <el-option label="全部" value="" /><el-option label="支票" value="CHECK" /><el-option label="汇票" value="DRAFT" />
            <el-option label="本票" value="CASHIER_CHECK" /><el-option label="银行承兑" value="BANK_ACCEPTANCE" />
          </el-select></el-form-item>
          <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" style="width:130px" @change="fetchData">
            <el-option label="全部" value="" /><el-option label="在库" value="IN_STOCK" /><el-option label="已领用" value="ISSUED" />
            <el-option label="已兑现" value="CASHED" /><el-option label="已作废" value="VOIDED" />
          </el-select></el-form-item>
          <el-form-item><el-button type="primary" @click="fetchData">查询</el-button></el-form-item>
        </el-form>
        <el-button type="primary" @click="openCreate">新增票据</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe style="width:100%">
        <el-table-column prop="ticketNo" label="票据编号" width="140" />
        <el-table-column prop="ticketType" label="类型" width="100">
          <template #default="{row}">{{ ({CHECK:'支票',DRAFT:'汇票',CASHIER_CHECK:'本票',BANK_ACCEPTANCE:'银行承兑'} as any)[row.ticketType]||row.ticketType }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="130" />
        <el-table-column prop="issueDate" label="开票日期" width="100" />
        <el-table-column prop="expireDate" label="到期日" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="({IN_STOCK:'primary' as const,ISSUED:'warning' as const,ENDORSED:'info' as const,CASHED:'success' as const,VOIDED:'danger' as const} as any)[row.status]||'info'" size="small">
              {{ ({IN_STOCK:'在库',ISSUED:'已领用',ENDORSED:'已背书',CASHED:'已兑现',VOIDED:'已作废'} as any)[row.status]||row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payee" label="收款人" width="120" />
        <el-table-column prop="drawer" label="出票人" width="120" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button text size="small" @click="showTransactions(row)">流水</el-button>
            <el-button v-if="row.status==='IN_STOCK'" text size="small" @click="doAction(row.id,'issue')">领用</el-button>
            <el-button v-if="row.status==='ISSUED'||row.status==='ENDORSED'" text size="small" @click="doAction(row.id,'cash')">兑现</el-button>
            <el-button v-if="row.status!=='CASHED'&&row.status!=='VOIDED'" text type="danger" size="small" @click="doAction(row.id,'void')">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size"
        :total="total" layout="total,prev,pager,next" @current-change="fetchData" /></div>
    </el-card>

    <el-dialog v-model="txDialog" title="交易流水" width="500">
      <el-timeline>
        <el-timeline-item v-for="tx in transactions" :key="tx.id" :timestamp="tx.transDate" placement="top">
          <p>{{ ({ISSUE:'领用',ENDORSE:'背书',CASH:'兑现',VOID:'作废',RETURN:'退回'} as Record<string, string>)[(tx as any).transType]||(tx as any).transType }}</p>
          <p v-if="tx.remark" style="color:#909399">{{ tx.remark }}</p>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>

    <el-dialog v-model="dialogVisible" title="新增票据" width="500">
      <el-form :model="form" label-width="100">
        <el-form-item label="票据编号"><el-input v-model="form.ticketNo" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.ticketType" style="width:100%">
          <el-option label="支票" value="CHECK" /><el-option label="汇票" value="DRAFT" />
          <el-option label="本票" value="CASHIER_CHECK" /><el-option label="银行承兑" value="BANK_ACCEPTANCE" />
        </el-select></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="form.amount" :precision="2" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="开票日期"><el-date-picker v-model="form.issueDate" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="到期日"><el-date-picker v-model="form.expireDate" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="收款人"><el-input v-model="form.payee" /></el-form-item>
        <el-form-item label="出票人"><el-input v-model="form.drawer" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import request from '@/api/request'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false), saving = ref(false), dialogVisible = ref(false), txDialog = ref(false)
const list = ref<any[]>([]), total = ref(0), transactions = ref<any[]>([])
const query = ref({ ticketType: '', status: '', current: 1, size: 20 })
const form = ref({ ticketNo: '', ticketType: 'CHECK', amount: 0, issueDate: null, expireDate: null, payee: '', drawer: '', remark: '' })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await request.get('/sme/cash/v1/tickets/page', { params: query.value })
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

function openCreate() {
  form.value = { ticketNo: '', ticketType: 'CHECK', amount: 0, issueDate: null, expireDate: null, payee: '', drawer: '', remark: '' }
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    await request.post('/sme/cash/v1/tickets', form.value)
    ElMessage.success('新增成功'); dialogVisible.value = false; fetchData()
  } finally { saving.value = false }
}

async function doAction(id: number, action: string) {
  await request.post(`/tickets/${id}/${action}`)
  ElMessage.success(`${ {issue:'已领用',cash:'已兑现',void:'已作废'}[action] || '操作成功' }`)
  fetchData()
}

async function showTransactions(row: any) {
  transactions.value = await request.get(`/sme/cash/v1/tickets/${row.id}/transactions`)
  txDialog.value = true
}

fetchData()
</script>
<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.toolbar { display:flex; justify-content:space-between; margin-bottom:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>