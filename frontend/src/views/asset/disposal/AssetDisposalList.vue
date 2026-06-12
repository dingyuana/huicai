<template>
  <div class="asset-disposal">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">资产处置</span>
        <el-button type="primary" @click="openEdit()">新增处置</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="disposalNo" label="处置单号" width="180" />
        <el-table-column label="处置类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ TYPE_MAP[row.disposalType] || row.disposalType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="disposalDate" label="处置日期" width="120" />
        <el-table-column label="原值" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.originalValue) }}</template>
        </el-table-column>
        <el-table-column label="净值" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.netValue) }}</template>
        </el-table-column>
        <el-table-column label="处置损益" width="140" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.gainLoss) >= 0 ? '#67c23a' : '#f56c6c' }">
              {{ fmtAmount(row.gainLoss) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG_MAP[row.status] || 'info') as any" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="onApprove(row)" v-if="row.status === 'DRAFT'">审批</el-button>
            <el-popconfirm title="确认删除?" @confirm="onDelete(row)">
              <template #reference>
                <el-button text type="danger">删除</el-button>
              </template>
            </el-popconfirm>
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

    <el-dialog v-model="dialogVisible" title="新增处置" width="560px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="资产ID" prop="assetId">
          <el-input-number v-model="form.assetId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="处置类型" prop="disposalType">
          <el-select v-model="form.disposalType" style="width:100%">
            <el-option v-for="o in TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置日期" prop="disposalDate">
          <el-date-picker v-model="form.disposalDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="处置收入">
          <el-input-number v-model="form.disposalIncome" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="处置费用">
          <el-input-number v-model="form.disposalExpense" :min="0" :precision="2" style="width:100%" />
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
import dayjs from 'dayjs'
import request from '@/api/request'

const TYPE_OPTIONS = [
  { value: 'SCRAP', label: '报废' },
  { value: 'SALE', label: '出售' },
  { value: 'DONATE', label: '捐赠' },
  { value: 'INV_LOSS', label: '盘亏' },
]
const TYPE_MAP: Record<string, string> = Object.fromEntries(TYPE_OPTIONS.map((o) => [o.value, o.label]))
const STATUS_MAP: Record<string, string> = { DRAFT: '草稿', APPROVED: '已审批', VOUCHERED: '已制证' }
const STATUS_TAG_MAP: Record<string, string> = { DRAFT: 'info', APPROVED: 'success', VOUCHERED: 'primary' }

const query = reactive({ current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ disposalType: 'SCRAP' })
const rules = {
  assetId: [{ required: true, message: '请输入资产ID', trigger: 'blur' }],
  disposalType: [{ required: true, message: '请选择处置类型', trigger: 'change' }],
  disposalDate: [{ required: true, message: '请选择处置日期', trigger: 'change' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/asset-disposals/page', { params: query })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const openEdit = () => {
  Object.assign(form, {
    assetId: undefined, disposalType: 'SCRAP', disposalDate: dayjs().format('YYYY-MM-DD'),
    disposalIncome: 0, disposalExpense: 0, remark: '',
  })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await request.post('/asset-disposals', form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchData()
  })
}

const onApprove = async (row: any) => {
  await request.post(`/asset-disposals/${row.id}/approve`)
  ElMessage.success('已审批')
  fetchData()
}

const onDelete = async (row: any) => {
  await request.delete(`/asset-disposals/${row.id}`)
  ElMessage.success('已删除')
  fetchData()
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

onMounted(fetchData)
</script>
