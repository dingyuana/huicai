<template>
  <div class="output-invoice">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">销项发票</span>
        <el-space>
          <el-button @click="openImportDialog">导入发票</el-button>
          <el-button type="primary" @click="openEdit()">新增发票</el-button>
        </el-space>
      </div>

      <!-- 统计栏 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="4">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">总发票数</span>
                <span class="stat-value">{{ stats.totalCount || 0 }}</span>
              </div>
              <div class="stat-icon icon-total">
                <el-icon><Document /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="5">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.1s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">蓝字总金额</span>
                <span class="stat-value">¥ {{ fmtAmount(stats.blueAmount) }}</span>
              </div>
              <div class="stat-icon icon-blue">
                <el-icon><Money /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="5">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.2s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">红字金额</span>
                <span class="stat-value">¥ {{ fmtAmount(stats.redAmount) }}</span>
              </div>
              <div class="stat-icon icon-red">
                <el-icon><Minus /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="3">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.3s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">红字数</span>
                <span class="stat-value">{{ stats.redCount || 0 }}</span>
              </div>
              <div class="stat-icon icon-red-count">
                <el-icon><Warning /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="3">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.4s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">已冲销</span>
                <span class="stat-value">{{ stats.reversedCount || 0 }}</span>
              </div>
              <div class="stat-icon icon-reversed">
                <el-icon><Refresh /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.5s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">已作废</span>
                <span class="stat-value">{{ stats.voidedCount || 0 }}</span>
              </div>
              <div class="stat-icon icon-voided">
                <el-icon><Delete /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-row style="margin-bottom:12px">
        <el-button size="small" @click="onBatchLinkRedFlush" :loading="linkingRedFlush">批量红冲关联</el-button>
      </el-row>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="客户">
          <el-input v-model="query.customerName" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="invoiceNo" label="发票号" width="180" />
        <el-table-column prop="invoiceDate" label="开票日期" width="120" />
        <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="80" align="center">
          <template #default="{ row }">{{ Number(row.taxRate).toFixed(2) }}%</template>
        </el-table-column>
        <el-table-column label="发票类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.amount) < 0" type="danger" size="small">红字发票</el-tag>
            <span v-else>{{ row.invoiceType === 'SPECIAL' ? '专用发票' : row.invoiceType === 'PLAIN' ? '普通发票' : row.invoiceType }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG_MAP[row.status] || 'info') as any" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING_CONFIRM'">
              <el-button link type="primary" size="small" @click="doAction(row, 'submitReview')">提交审核</el-button>
              <el-button link type="danger" size="small" @click="doAction(row, 'void')">作废</el-button>
            </template>
            <template v-else-if="row.status === 'PENDING_REVIEW'">
              <el-button link type="primary" size="small" @click="doAction(row, 'confirm')">通过</el-button>
              <el-button link type="warning" size="small" @click="doAction(row, 'reject')">驳回</el-button>
              <el-button link type="danger" size="small" @click="doAction(row, 'void')">作废</el-button>
            </template>
            <template v-else-if="row.status === 'CONFIRMED'">
              <el-button link type="primary" size="small" @click="doAction(row, 'markVouchered')">生成凭证</el-button>
              <el-button link type="warning" size="small" @click="doAction(row, 'revert')">回退</el-button>
              <el-button link type="danger" size="small" @click="doAction(row, 'void')">作废</el-button>
            </template>
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

    <el-dialog v-model="dialogVisible" title="新增销项发票" width="640px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="发票号" prop="invoiceNo"><el-input v-model="form.invoiceNo" /></el-form-item>
        <el-form-item label="开票日期" prop="invoiceDate">
          <el-date-picker v-model="form.invoiceDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="客户" prop="customerName"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item label="金额(不含税)" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" @change="recalcTax" />
        </el-form-item>
        <el-form-item label="税率" prop="taxRate">
          <el-select v-model="form.taxRate" style="width:100%" @change="recalcTax">
            <el-option :value="13" label="13%" />
            <el-option :value="9" label="9%" />
            <el-option :value="6" label="6%" />
            <el-option :value="0" label="0%" />
          </el-select>
        </el-form-item>
        <el-form-item label="税额">
          <el-input-number v-model="form.taxAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="发票类型" prop="invoiceType">
          <el-select v-model="form.invoiceType" style="width:100%">
            <el-option label="增值税专用发票" value="SPECIAL" />
            <el-option label="普通发票" value="PLAIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入发票 -->
    <el-dialog v-model="importVisible" title="导入销售发票" width="720px" destroy-on-close>
      <template v-if="!importPreview">
        <el-alert type="info" :closable="false" style="margin-bottom:12px">
          上传销售发票 Excel，系统自动识别列名、匹配客户、生成应收单据和凭证。
        </el-alert>
        <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" @change="onImportFileChange" ref="importUploadRef">
          <el-icon class="el-icon--upload" style="font-size:48px"><upload-filled /></el-icon>
          <div class="el-upload__text">拖放文件到此处或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">支持销项发票格式（发票号码、购方识别号、购买方名称、开票日期、金额、税额、价税合计）</div>
          </template>
        </el-upload>
        <div style="margin-top:12px;text-align:right">
          <el-button @click="importVisible = false">取消</el-button>
          <el-button type="primary" :loading="importPreviewing" :disabled="!importFile" @click="onImportPreview">下一步: 预览</el-button>
        </div>
      </template>

      <template v-else>
        <el-descriptions :column="4" border size="small" style="margin-bottom:12px">
          <el-descriptions-item label="总行数">{{ importPreview.total }}</el-descriptions-item>
          <el-descriptions-item label="有效行数">{{ importPreview.valid }}</el-descriptions-item>
          <el-descriptions-item label="已有">
            <el-tag v-if="importPreview.existing" type="warning">{{ importPreview.existing }}</el-tag>
            <el-tag v-else type="success">0</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="错误">
            <el-tag v-if="importPreview.errors?.length" type="danger">{{ importPreview.errors.length }}</el-tag>
            <el-tag v-else type="success">0</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="(importPreview.previews || []).slice(0, 50)" border size="small" max-height="300">
          <el-table-column type="index" label="#" width="40" />
          <el-table-column prop="invoiceNo" label="发票号" width="160" />
          <el-table-column label="状态" width="60" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.existing" type="warning" size="small">已有</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="buyerName" label="购方" min-width="140" show-overflow-tooltip />
          <el-table-column prop="invoiceDate" label="日期" width="90" />
          <el-table-column prop="goodsName" label="商品" width="120" show-overflow-tooltip />
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="税额" width="90" align="right">
            <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
          </el-table-column>
          <el-table-column label="价税合计" width="100" align="right">
            <template #default="{ row }">{{ fmtAmount(row.totalAmount) }}</template>
          </el-table-column>
        </el-table>
        <div style="margin-top:12px;text-align:right">
          <el-button @click="importPreview = null; importFile = null">重新上传</el-button>
          <el-button @click="importVisible = false">取消</el-button>
          <el-button type="primary" :loading="importConfirming"
            :disabled="!importPreview.valid || (importPreview.valid - (importPreview.existing || 0)) <= 0"
            @click="onImportConfirm">
            确认导入 {{ Math.max(0, importPreview.valid - (importPreview.existing || 0)) }} 条
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 发票详情 -->
    <el-dialog v-model="detailVisible" title="发票详情" width="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="发票号" span="2">{{ detail.invoiceNo }}</el-descriptions-item>
          <el-descriptions-item label="开票日期">{{ detail.invoiceDate }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ detail.period }}</el-descriptions-item>
          <el-descriptions-item label="客户名称" span="2">{{ detail.customerName }}</el-descriptions-item>
          <el-descriptions-item label="金额(不含税)">{{ fmtAmount(detail.amount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ fmtAmount(detail.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="价税合计">{{ fmtAmount(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税率">{{ Number(detail.taxRate || 0).toFixed(2) }}%</el-descriptions-item>
          <el-descriptions-item label="发票类型">{{ detail.invoiceType === 'SPECIAL' ? '增值税专用发票' : '普通发票' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="(STATUS_TAG_MAP[detail.status] || 'info') as any" size="small">
              {{ STATUS_MAP[detail.status] || detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关联单据ID">{{ detail.docId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联凭证ID">{{ detail.voucherId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-space>
          <template v-if="detail?.status === 'PENDING_CONFIRM'">
            <el-button type="primary" size="small" @click="doAction(detail, 'submitReview')">提交审核</el-button>
            <el-button type="danger" size="small" @click="doAction(detail, 'void')">作废</el-button>
          </template>
          <template v-else-if="detail?.status === 'PENDING_REVIEW'">
            <el-button type="primary" size="small" @click="doAction(detail, 'confirm')">审核通过</el-button>
            <el-button type="warning" size="small" @click="doAction(detail, 'reject')">驳回</el-button>
            <el-button type="danger" size="small" @click="doAction(detail, 'void')">作废</el-button>
          </template>
          <template v-else-if="detail?.status === 'CONFIRMED'">
            <el-button type="primary" size="small" @click="doAction(detail, 'markVouchered')">生成凭证</el-button>
            <el-button type="warning" size="small" @click="doAction(detail, 'revert')">回退到待审核</el-button>
            <el-button type="danger" size="small" @click="doAction(detail, 'void')">作废</el-button>
          </template>
          <el-button type="danger" @click="onDelete(detail)" :loading="deleting">删除</el-button>
          <el-button @click="detailVisible = false">关闭</el-button>
        </el-space>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { UploadFilled, Document, Money, Minus, Warning, Refresh, Delete } from '@element-plus/icons-vue'
import { pageOutputInvoice, createOutputInvoice, getOutputInvoice, deleteOutputInvoice,
  outputInvoiceSummary,
  submitForReview, confirmOutputInvoice, rejectOutputInvoice, revertOutputInvoice, voidOutputInvoice, markVouchered } from '@/api/modules/tax'
import { previewSalesInvoices, confirmSalesInvoicesImport, batchLinkRedFlush } from '@/api/modules/salesInvoice'

const detailVisible = ref(false)
const detail = ref<any>(null)
const deleting = ref(false)
const linkingRedFlush = ref(false)

const onBatchLinkRedFlush = async () => {
  linkingRedFlush.value = true
  try {
    const res = await batchLinkRedFlush()
    ElMessage.success(`红冲关联完成: 匹配 ${res.matched} 对, 跳过 ${res.skipped} 条`)
    fetchData(); fetchStats()
  } catch { /* */ }
  finally { linkingRedFlush.value = false }
}

const showDetail = async (row: any) => {
  try {
    detail.value = await getOutputInvoice(row.id)
    detailVisible.value = true
  } catch { detail.value = row; detailVisible.value = true }
}

const onDelete = async (row: any) => {
  if (!row?.id) return
  deleting.value = true
  try {
    await deleteOutputInvoice(row.id)
    ElMessage.success('删除成功')
    detailVisible.value = false
    fetchData(); fetchStats()
  } finally { deleting.value = false }
}

const doAction = async (row: any, action: string) => {
  const id = row?.id
  if (!id) return
  const label = ({ submitReview: '提交审核', confirm: '审核通过', reject: '驳回', revert: '回退', void: '作废', markVouchered: '生成凭证' } as any)[action] || action

  if (action === 'reject' || action === 'void') {
    const { value: reason } = await (await import('element-plus')).ElMessageBox.prompt(
      `请输入${label}原因`, label, { inputType: 'textarea', inputValidator: (v: string) => !!v?.trim(), inputErrorMessage: '原因不能为空' }
    ).catch(() => ({ value: null }))
    if (!reason) return
    try {
      if (action === 'reject') await rejectOutputInvoice(id, reason)
      else await voidOutputInvoice(id, reason)
      ElMessage.success(`${label}成功`)
      detailVisible.value = false
      fetchData(); fetchStats()
    } catch { /* backend handles error msg */ }
    return
  }

  try {
    if (action === 'submitReview') await submitForReview(id)
    else if (action === 'confirm') await confirmOutputInvoice(id)
    else if (action === 'revert') await revertOutputInvoice(id)
    else if (action === 'markVouchered') await markVouchered(id, 0)
    ElMessage.success(`${label}成功`)
    detailVisible.value = false
    fetchData(); fetchStats()
  } catch { /* backend handles error msg */ }
}

const STATUS_MAP: Record<string, string> = {
  PENDING_CONFIRM: '待确认', PENDING_REVIEW: '待审核', CONFIRMED: '已确认',
  VOUCHERED: '已生成凭证', FULLY_RECONCILED: '已核销', PARTIALLY_RECONCILED: '部分核销',
  VOIDED: '已作废', REVERSED: '已冲销',
}
const STATUS_TAG_MAP: Record<string, string> = {
  PENDING_CONFIRM: 'warning', PENDING_REVIEW: 'info', CONFIRMED: 'success',
  VOUCHERED: '', FULLY_RECONCILED: 'success', PARTIALLY_RECONCILED: '',
  VOIDED: 'danger', REVERSED: 'danger',
}

const query = reactive({ customerName: '', period: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<any>({})
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ invoiceType: 'SPECIAL', taxRate: 13 })
const rules = {
  invoiceNo: [{ required: true, message: '请输入发票号', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
  customerName: [{ required: true, message: '请输入客户', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  taxRate: [{ required: true, message: '请选择税率', trigger: 'change' }],
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const recalcTax = () => {
  if (form.amount && form.taxRate != null) {
    form.taxAmount = Number((form.amount * form.taxRate / 100).toFixed(2))
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageOutputInvoice(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const fetchStats = async () => {
  try { stats.value = await outputInvoiceSummary() } catch {}
}

const openEdit = () => {
  Object.assign(form, {
    id: undefined, invoiceNo: '', invoiceDate: '', customerName: '',
    amount: 0, taxRate: 13, taxAmount: 0, invoiceType: 'SPECIAL', remark: '',
  })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await createOutputInvoice(form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchData(); fetchStats()
  })
}

onMounted(() => { fetchData(); fetchStats() })

// ====== 发票导入 ======
const importVisible = ref(false)
const importPreviewing = ref(false)
const importConfirming = ref(false)
const importFile = ref<File | null>(null)
const importPreview = ref<any>(null)
const importUploadRef = ref<any>(null)

const openImportDialog = () => {
  importFile.value = null
  importPreview.value = null
  importVisible.value = true
}

const onImportFileChange = (f: any) => {
  importFile.value = f.raw || null
  importPreview.value = null
}

const onImportPreview = async () => {
  if (!importFile.value) { ElMessage.warning('请选择文件'); return }
  importPreviewing.value = true
  try {
    importPreview.value = await previewSalesInvoices(importFile.value)
    if (importPreview.value.total === 0) {
      ElMessage.warning('未解析到有效发票行')
    } else {
      ElMessage.success(`解析完成: ${importPreview.value.total} 行, 有效 ${importPreview.value.valid} 行`)
    }
  } finally { importPreviewing.value = false }
}

const onImportConfirm = async () => {
  if (!importPreview.value?.batchId) { ElMessage.warning('请先预览'); return }
  importConfirming.value = true
  try {
    const res = await confirmSalesInvoicesImport(importPreview.value.batchId)
    let msg = `导入 ${res.success} 张发票，生成 ${res.voucherCreated} 张凭证`
    if (res.duplicateSkipped) msg += `，${res.duplicateSkipped} 张重复已跳过`
    ElMessage.success(msg)
    importVisible.value = false
    importPreview.value = null
    importFile.value = null
    fetchData(); fetchStats()
  } finally { importConfirming.value = false }
}
</script>

<style scoped>
.output-invoice {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.page-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.filter-form {
  margin-bottom: 16px;
}

/* 统计卡片样式 */
.stat-card {
  margin-bottom: 0;
  border-radius: 8px;
  opacity: 0;
  transform: translateY(20px);
  animation: statCardIn 0.5s ease forwards;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1) !important;
}

@keyframes statCardIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
}

.icon-total {
  background: linear-gradient(135deg, #409EFF, #66b1ff);
}

.icon-blue {
  background: linear-gradient(135deg, #67C23A, #85ce61);
}

.icon-red {
  background: linear-gradient(135deg, #F56C6C, #f89898);
}

.icon-red-count {
  background: linear-gradient(135deg, #E6A23C, #ebb563);
}

.icon-reversed {
  background: linear-gradient(135deg, #909399, #a6a9ad);
}

.icon-voided {
  background: linear-gradient(135deg, #c71585, #db5fa6);
}
</style>
