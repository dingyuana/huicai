<template>
  <div class="input-invoice">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">进项发票</span>
        <el-button type="primary" @click="openEdit()">新增发票</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="供应商">
          <el-input v-model="query.vendorName" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" clearable />
        </el-form-item>
        <el-form-item label="认证状态">
          <el-select v-model="query.certStatus" clearable placeholder="全部" style="width:130px">
            <el-option v-for="o in CERT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="invoiceNo" label="发票号" width="180" />
        <el-table-column prop="invoiceDate" label="开票日期" width="120" />
        <el-table-column prop="vendorName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="80" align="center">
          <template #default="{ row }">{{ (Number(row.taxRate) * 100).toFixed(0) }}%</template>
        </el-table-column>
        <el-table-column label="认证状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="(CERT_TAG_MAP[row.certificationStatus] || 'info') as any" size="small">
              {{ CERT_MAP[row.certificationStatus] || row.certificationStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" v-if="row.certificationStatus === 'UNCERTIFIED'" @click="onCertify(row)">认证</el-button>
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

    <el-dialog v-model="dialogVisible" title="新增进项发票" width="640px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="发票号" prop="invoiceNo"><el-input v-model="form.invoiceNo" /></el-form-item>
        <el-form-item label="开票日期" prop="invoiceDate">
          <el-date-picker v-model="form.invoiceDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="供应商" prop="vendorName"><el-input v-model="form.vendorName" /></el-form-item>
        <el-form-item label="金额(不含税)" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" @change="recalcTax" />
        </el-form-item>
        <el-form-item label="税率" prop="taxRate">
          <el-select v-model="form.taxRate" style="width:100%" @change="recalcTax">
            <el-option :value="0.13" label="13%" />
            <el-option :value="0.09" label="9%" />
            <el-option :value="0.06" label="6%" />
            <el-option :value="0.03" label="3%" />
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
            <el-option label="海关缴款书" value="CUSTOMS" />
            <el-option label="运输发票" value="TRANSPORT" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { pageInputInvoice, createInputInvoice, certifyInputInvoice } from '@/api/modules/tax'

const CERT_OPTIONS = [
  { value: 'UNCERTIFIED', label: '未认证' },
  { value: 'CERTIFIED', label: '已认证' },
  { value: 'INVALID', label: '无效' },
  { value: 'CANCELLED', label: '已注销' },
]
const CERT_MAP: Record<string, string> = Object.fromEntries(CERT_OPTIONS.map((o) => [o.value, o.label]))
const CERT_TAG_MAP: Record<string, string> = {
  UNCERTIFIED: 'warning', CERTIFIED: 'success', INVALID: 'danger', CANCELLED: 'info',
}

const query = reactive({ vendorName: '', period: '', certStatus: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ invoiceType: 'SPECIAL', taxRate: 0.13 })
const rules = {
  invoiceNo: [{ required: true, message: '请输入发票号', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
  vendorName: [{ required: true, message: '请输入供应商', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  taxRate: [{ required: true, message: '请选择税率', trigger: 'change' }],
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const recalcTax = () => {
  if (form.amount && form.taxRate != null) {
    form.taxAmount = Number((form.amount * form.taxRate).toFixed(2))
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageInputInvoice(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const openEdit = () => {
  Object.assign(form, {
    id: undefined, invoiceNo: '', invoiceDate: '', vendorName: '',
    amount: 0, taxRate: 0.13, taxAmount: 0, invoiceType: 'SPECIAL', remark: '',
  })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await createInputInvoice(form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchData()
  })
}

const onCertify = async (row: any) => {
  await certifyInputInvoice(row.id)
  ElMessage.success('已认证')
  fetchData()
}

onMounted(fetchData)
</script>
