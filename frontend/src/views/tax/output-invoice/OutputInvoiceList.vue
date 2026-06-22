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
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG_MAP[row.status] || 'info') as any" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { pageOutputInvoice, createOutputInvoice } from '@/api/modules/tax'
import { previewSalesInvoices, confirmSalesInvoicesImport } from '@/api/modules/salesInvoice'

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
    fetchData()
  })
}

onMounted(fetchData)

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
    fetchData()
  } finally { importConfirming.value = false }
}
</script>
