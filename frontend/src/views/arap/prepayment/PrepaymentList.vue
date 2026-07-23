<template>
  <div class="prepayment-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">预收/预付管理</span>
        <el-button type="primary" @click="openCreate">新增{{ activeTab === 'vendor' ? '预付' : '预收' }}</el-button>
      </div>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 供应商预付 -->
        <el-tab-pane label="供应商预付" name="vendor">
          <div class="toolbar">
            <el-form :model="query" inline>
              <el-form-item label="关键字">
                <el-input v-model="query.keyword" placeholder="编号/供应商" clearable style="width:200px" />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="query.status" placeholder="全部" style="width:110px" clearable @change="fetchData">
                  <el-option label="全部" value="" />
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="已确认" value="CONFIRMED" />
                  <el-option label="已核销" value="APPLIED" />
                  <el-option label="已反冲" value="REVERSED" />
                </el-select>
              </el-form-item>
              <el-form-item label="期间">
                <el-input v-model="query.period" placeholder="YYYYMM" style="width:100px" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="fetchData">查询</el-button>
              </el-form-item>
            </el-form>
          </div>

          <el-table :data="list" v-loading="loading" border stripe style="width:100%">
            <el-table-column type="index" label="序号" width="50" />
            <el-table-column prop="prepayNo" label="单据编号" width="150" />
            <el-table-column prop="vendorName" label="供应商" min-width="160" />
            <el-table-column label="预付金额" width="130" align="right">
              <template #default="{row}">{{ fmtAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="已核销" width="110" align="right">
              <template #default="{row}">{{ fmtAmount(row.appliedAmount) }}</template>
            </el-table-column>
            <el-table-column label="未核销" width="110" align="right">
              <template #default="{row}">{{ fmtAmount((row.amount || 0) - (row.appliedAmount || 0)) }}</template>
            </el-table-column>
            <el-table-column prop="period" label="期间" width="80" align="center" />
            <el-table-column prop="txDate" label="日期" width="100" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{row}">
                <el-button v-if="row.status==='DRAFT'" text size="small" type="success" @click="onConfirm(row)">确认</el-button>
                <el-button v-else-if="row.status==='CONFIRMED'" text size="small" type="primary" @click="openOffset(row)">核销</el-button>
                <el-button v-if="row.status==='CONFIRMED'" text size="small" type="warning" @click="openReverse(row)">反冲</el-button>
                <el-button v-if="row.status==='APPLIED'" text size="small" type="primary" @click="onDetail(row)">详情</el-button>
                <el-popconfirm v-if="row.status==='DRAFT'" title="确认删除?" @confirm="onDelete(row)">
                  <template #reference><el-button text size="small" type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <div class="page-pagination">
            <el-pagination v-model:current-page="query.current" v-model:page-size="query.size"
              :page-sizes="[10,20,50]" :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchData" @current-change="fetchData" />
          </div>
        </el-tab-pane>

        <!-- 客户预收款 -->
        <el-tab-pane label="客户预收款" name="customer">
          <div class="toolbar">
            <el-form :model="query" inline>
              <el-form-item label="关键字">
                <el-input v-model="query.keyword" placeholder="编号/客户" clearable style="width:200px" />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="query.status" placeholder="全部" style="width:110px" clearable @change="fetchData">
                  <el-option label="全部" value="" />
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="已确认" value="CONFIRMED" />
                  <el-option label="已核销" value="APPLIED" />
                  <el-option label="已反冲" value="REVERSED" />
                </el-select>
              </el-form-item>
              <el-form-item label="期间">
                <el-input v-model="query.period" placeholder="YYYYMM" style="width:100px" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="fetchData">查询</el-button>
              </el-form-item>
            </el-form>
          </div>

          <el-table :data="list" v-loading="loading" border stripe style="width:100%">
            <el-table-column type="index" label="序号" width="50" />
            <el-table-column prop="prepayNo" label="单据编号" width="150" />
            <el-table-column prop="customerName" label="客户" min-width="160" />
            <el-table-column label="预收金额" width="130" align="right">
              <template #default="{row}">{{ fmtAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="已核销" width="110" align="right">
              <template #default="{row}">{{ fmtAmount(row.appliedAmount) }}</template>
            </el-table-column>
            <el-table-column label="未核销" width="110" align="right">
              <template #default="{row}">{{ fmtAmount((row.amount || 0) - (row.appliedAmount || 0)) }}</template>
            </el-table-column>
            <el-table-column prop="period" label="期间" width="80" align="center" />
            <el-table-column prop="txDate" label="日期" width="100" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{row}">
                <el-button v-if="row.status==='DRAFT'" text size="small" type="success" @click="onConfirm(row)">确认</el-button>
                <el-button v-else-if="row.status==='CONFIRMED'" text size="small" type="primary" @click="openOffset(row)">核销</el-button>
                <el-button v-if="row.status==='CONFIRMED'" text size="small" type="warning" @click="openReverse(row)">反冲</el-button>
                <el-button v-if="row.status==='APPLIED'" text size="small" type="primary" @click="onDetail(row)">详情</el-button>
                <el-popconfirm v-if="row.status==='DRAFT'" title="确认删除?" @confirm="onDelete(row)">
                  <template #reference><el-button text size="small" type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <div class="page-pagination">
            <el-pagination v-model:current-page="query.current" v-model:page-size="query.size"
              :page-sizes="[10,20,50]" :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchData" @current-change="fetchData" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增/编辑 dialog -->
    <el-dialog v-model="createVisible" :title="'新增' + (activeTab === 'vendor' ? '供应商预付' : '客户预收款')" width="520px">
      <el-form :model="createForm" label-width="100px" :rules="createRules" ref="createFormRef">
        <el-form-item :label="activeTab==='vendor'?'供应商':'客户'" prop="partyId">
          <el-select v-model="createForm.partyId" filterable remote :remote-method="searchParty"
            :placeholder="'请选择' + (activeTab==='vendor'?'供应商':'客户')" style="width:100%"
            :loading="partyLoading">
            <el-option v-for="p in partyOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="createForm.amount" :precision="2" :min="0.01" style="width:100%" />
        </el-form-item>
        <el-form-item label="期间" prop="period">
          <el-input v-model="createForm.period" placeholder="YYYYMM" style="width:160px" />
        </el-form-item>
        <el-form-item label="日期" prop="txDate">
          <el-date-picker v-model="createForm.txDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="createForm.summary" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible=false">取消</el-button>
        <el-button type="primary" @click="onCreateSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- 核销 dialog -->
    <el-dialog v-model="offsetVisible" title="核销预收/预付" width="520px">
      <el-form :model="offsetForm" label-width="100px" ref="offsetFormRef">
        <el-form-item label="核销方式" prop="targetDocId">
          <el-select v-model="offsetForm.targetDocId" filterable :placeholder="activeTab==='vendor'?'选择应付单':'选择应收单'" style="width:100%">
            <el-option v-for="d in targetDocOptions" :key="d.id" :label="d.docNo + ' - ' + fmtAmount(d.amount - (d.appliedAmount || 0))" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="本次核销">
          <el-input-number v-model="offsetForm.applyAmount" :precision="2" :min="0.01" style="width:100%" />
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="offsetForm.period" placeholder="YYYYMM" style="width:160px" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="offsetForm.summary" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="offsetVisible=false">取消</el-button>
        <el-button type="primary" @click="onOffsetSubmit" :loading="offsetSubmitting">确认核销</el-button>
      </template>
    </el-dialog>

    <!-- 反冲 dialog -->
    <el-dialog v-model="reverseVisible" title="反冲预收/预付" width="420px">
      <el-form :model="reverseForm" label-width="80px" ref="reverseFormRef">
        <el-form-item label="原因" prop="reason">
          <el-input v-model="reverseForm.reason" type="textarea" :rows="3" placeholder="请填写反冲原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reverseVisible=false">取消</el-button>
        <el-button type="warning" @click="onReverseSubmit" :loading="reverseSubmitting">确认反冲</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pagePrepayment, createPrepayment, confirmPrepayment,
  applyToPayable, applyToReceivable, reversePrepayment
} from '@/api/modules/prepayment'
import { pageReceivable } from '@/api/modules/arap'
import { pagePayable } from '@/api/modules/arap'
import { pageVendor, listVendor } from '@/api/modules/arap'
import { pageCustomer, listCustomer } from '@/api/modules/arap'

const activeTab = ref('vendor')
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = reactive({
  keyword: '',
  status: '',
  period: '',
  current: 1,
  size: 20,
  vendorId: undefined as number | undefined,
  customerId: undefined as number | undefined,
})

// create
const createVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref()
const partyLoading = ref(false)
const partyOptions = ref<any[]>([])
const createForm = reactive({
  partyId: undefined as number | undefined,
  amount: 0,
  period: '',
  txDate: '',
  summary: '',
})
const createRules = {
  partyId: [{ required: true, message: '请选择', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  period: [{ required: true, message: '请输入期间', trigger: 'blur' }],
  txDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

// offset
const offsetVisible = ref(false)
const offsetSubmitting = ref(false)
const offsetFormRef = ref()
const offsetCurrentRow = ref<any>(null)
const targetDocOptions = ref<any[]>([])
const offsetForm = reactive({
  targetDocId: undefined as number | undefined,
  applyAmount: 0,
  period: '',
  summary: '',
})

// reverse
const reverseVisible = ref(false)
const reverseSubmitting = ref(false)
const reverseFormRef = ref()
const reverseCurrentRow = ref<any>(null)
const reverseForm = reactive({
  reason: '',
})

// status helpers
function statusType(s: string): 'success' | 'warning' | 'info' | 'primary' | 'danger' {
  const map: Record<string, 'success' | 'warning' | 'info' | 'primary' | 'danger'> = { DRAFT: 'info', CONFIRMED: 'primary', APPLIED: 'success', REVERSED: 'danger' }
  return map[s] || 'info'
}

function statusLabel(s: string): string {
  const map: Record<string, string> = { DRAFT: '草稿', CONFIRMED: '已确认', APPLIED: '已核销', REVERSED: '已反冲' }
  return map[s] || s
}

function fmtAmount(n: any): string {
  const v = Number(n) || 0
  return '¥' + v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// data fetch
async function fetchData() {
  loading.value = true
  try {
    const p: any = {
      current: query.current,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      period: query.period || undefined,
    }
    if (activeTab.value === 'vendor') {
      p.vendorId = query.vendorId
      delete p.customerId
    } else {
      p.customerId = query.customerId
      delete p.vendorId
    }
    const res = await pagePrepayment(p)
    list.value = res?.records || []
    total.value = res?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.msg || '查询失败')
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  query.current = 1
  query.keyword = ''
  query.status = ''
  query.period = ''
  fetchData()
}

// create
function searchParty(keyword: string) {
  partyLoading.value = true
  const fn = activeTab.value === 'vendor' ? listVendor : listCustomer
  fn().then((res: any) => {
    partyOptions.value = Array.isArray(res) ? res : []
  }).finally(() => { partyLoading.value = false })
}

function openCreate() {
  createForm.partyId = undefined
  createForm.amount = 0
  createForm.period = ''
  createForm.txDate = ''
  createForm.summary = ''
  partyOptions.value = []
  createVisible.value = true
}

async function onCreateSubmit() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data: any = {
      amount: createForm.amount,
      period: createForm.period,
      txDate: createForm.txDate,
      summary: createForm.summary || undefined,
    }
    if (activeTab.value === 'vendor') {
      data.vendorId = createForm.partyId
    } else {
      data.customerId = createForm.partyId
    }
    await createPrepayment(data)
    ElMessage.success('创建成功')
    createVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e?.msg || '创建失败')
  } finally {
    submitting.value = false
  }
}

// confirm
async function onConfirm(row: any) {
  try {
    await ElMessageBox.confirm('确认该笔预收/预付单据？', '确认')
    await confirmPrepayment(row.id)
    ElMessage.success('确认成功')
    fetchData()
  } catch { /* cancel */ }
}

// offset (write-off)
async function openOffset(row: any) {
  offsetCurrentRow.value = row
  offsetForm.targetDocId = undefined
  offsetForm.applyAmount = 0
  offsetForm.period = ''
  offsetForm.summary = ''
  targetDocOptions.value = []

  // load open receivables/payables for this party
  try {
    const partyId = activeTab.value === 'vendor' ? row.vendorId : row.customerId
    if (!partyId) { ElMessage.warning('缺少客户/供应商信息'); return }

    const pageParams: Record<string, any> = { current: 1, size: 200, status: 'CONFIRMED' }
    if (activeTab.value === 'vendor') {
      pageParams['vendorId'] = partyId
      const res = await pagePayable(pageParams)
      targetDocOptions.value = (res?.records || []).filter((d: any) => {
        const remain = (d.amount || 0) - (d.appliedAmount || 0)
        return remain > 0
      })
    } else {
      pageParams['customerId'] = partyId
      const res = await pageReceivable(pageParams)
      targetDocOptions.value = (res?.records || []).filter((d: any) => {
        const remain = (d.amount || 0) - (d.appliedAmount || 0)
        return remain > 0
      })
    }
    if (targetDocOptions.value.length === 0) {
      ElMessage.warning('该' + (activeTab.value === 'vendor' ? '供应商' : '客户') + '暂无待核销单据')
    }
  } catch {
    ElMessage.warning('获取待核销单据失败')
  }

  offsetVisible.value = true
}

async function onOffsetSubmit() {
  if (!offsetForm.targetDocId) { ElMessage.warning('请选择核销单据'); return }
  if (!offsetForm.applyAmount || offsetForm.applyAmount <= 0) { ElMessage.warning('请输入核销金额'); return }

  offsetSubmitting.value = true
  try {
    const prepayId = offsetCurrentRow.value.id
    const params: any = { applyAmount: offsetForm.applyAmount }
    if (offsetForm.period) params.period = offsetForm.period
    if (offsetForm.summary) params.summary = offsetForm.summary

    if (activeTab.value === 'vendor') {
      await applyToPayable(prepayId, offsetForm.targetDocId, params)
    } else {
      await applyToReceivable(prepayId, offsetForm.targetDocId, params)
    }
    ElMessage.success('核销成功')
    offsetVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e?.msg || '核销失败')
  } finally {
    offsetSubmitting.value = false
  }
}

// reverse
function openReverse(row: any) {
  reverseCurrentRow.value = row
  reverseForm.reason = ''
  reverseVisible.value = true
}

async function onReverseSubmit() {
  if (!reverseForm.reason) { ElMessage.warning('请填写反冲原因'); return }
  reverseSubmitting.value = true
  try {
    await reversePrepayment(reverseCurrentRow.value.id, { reason: reverseForm.reason })
    ElMessage.success('反冲成功')
    reverseVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e?.msg || '反冲失败')
  } finally {
    reverseSubmitting.value = false
  }
}

// delete
async function onDelete(row: any) {
  // The backend delete uses reverse endpoint with reason, but for draft we can call reverse
  try {
    await reversePrepayment(row.id, { reason: '删除草稿' })
    ElMessage.success('删除成功')
    fetchData()
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

// detail
function onDetail(row: any) {
  ElMessageBox.alert(
    `<div><b>单据编号：</b>${row.prepayNo}</div>
     <div><b>${activeTab.value === 'vendor' ? '供应商' : '客户'}：</b>${row.vendorName || row.customerName}</div>
     <div><b>金额：</b>${fmtAmount(row.amount)}</div>
     <div><b>已核销：</b>${fmtAmount(row.appliedAmount)}</div>
     <div><b>未核销：</b>${fmtAmount((row.amount || 0) - (row.appliedAmount || 0))}</div>
     <div><b>期间：</b>${row.period}</div>
     <div><b>日期：</b>${row.txDate}</div>
     <div><b>摘要：</b>${row.summary || '-'}</div>
     <div><b>状态：</b>${statusLabel(row.status)}</div>`,
    '单据详情',
    { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }
  )
}

// init
fetchData()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.page-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
