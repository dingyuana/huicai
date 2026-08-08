<template>
  <div class="asset-card">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">资产卡片</span>
        <div>
          <el-button type="primary" @click="openEdit()">新增资产</el-button>
          <el-button type="warning" @click="onDepreciateAll">一键计提折旧</el-button>
        </div>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="编码/名称" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:130px">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="depreciationPeriod" placeholder="YYYYMM" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="assetCode" label="资产编码" width="140" />
        <el-table-column prop="assetName" label="资产名称" min-width="180" />
        <el-table-column label="原值" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.originalValue) }}</template>
        </el-table-column>
        <el-table-column label="累计折旧" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.accumulatedDepreciation) }}</template>
        </el-table-column>
        <el-table-column label="净值" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.netValue) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG_MAP[row.status] || 'info') as any" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="acquisitionDate" label="购置日期" width="120" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text type="warning" @click="onDepreciateOne(row)">计提</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑资产' : '新增资产'" width="640px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="资产编码" prop="assetCode"><el-input v-model="form.assetCode" /></el-form-item>
        <el-form-item label="资产名称" prop="assetName"><el-input v-model="form.assetName" /></el-form-item>
        <el-form-item label="类别" prop="categoryId">
          <el-select v-model="form.categoryId" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="`${c.code} - ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="购置日期" prop="acquisitionDate">
          <el-date-picker v-model="form.acquisitionDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="原值" prop="originalValue">
          <el-input-number v-model="form.originalValue" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="残值">
          <el-input-number v-model="form.residualValue" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="使用年限" prop="usefulLife">
          <el-input-number v-model="form.usefulLife" :min="1" :max="100" style="width:100%" />
        </el-form-item>
        <el-form-item label="存放地点">
          <el-input v-model="form.location" />
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
import { resolveDefaultPeriod } from '@/utils/period'
import {
  pageAssetCard,
  createAssetCard,
  updateAssetCard,
  deleteAssetCard,
  depreciatePeriod,
  depreciateOne,
  listAssetCategory,
} from '@/api/modules/asset'

const STATUS_OPTIONS = [
  { value: 'IN_USE', label: '在用' },
  { value: 'IDLE', label: '闲置' },
  { value: 'DISPOSED', label: '已处置' },
  { value: 'SCRAPPED', label: '已报废' },
]
const STATUS_MAP: Record<string, string> = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label]))
const STATUS_TAG_MAP: Record<string, string> = {
  IN_USE: 'success', IDLE: 'info', DISPOSED: 'warning', SCRAPPED: 'danger',
}

const query = reactive({ keyword: '', status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({})
const categories = ref<any[]>([])
const depreciationPeriod = ref('')

const rules = {
  assetCode: [{ required: true, message: '请输入资产编码', trigger: 'blur' }],
  assetName: [{ required: true, message: '请输入资产名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择类别', trigger: 'change' }],
  acquisitionDate: [{ required: true, message: '请选择购置日期', trigger: 'change' }],
  originalValue: [{ required: true, message: '请输入原值', trigger: 'blur' }],
  usefulLife: [{ required: true, message: '请输入使用年限', trigger: 'blur' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageAssetCard(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  categories.value = await listAssetCategory()
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const openEdit = (row?: any) => {
  if (row) Object.assign(form, row)
  else Object.assign(form, {
    id: undefined, assetCode: '', assetName: '', categoryId: undefined,
    acquisitionDate: dayjs().format('YYYY-MM-DD'), originalValue: 0,
    residualValue: 0, usefulLife: 5, location: '', remark: '',
  })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.id) {
      await updateAssetCard(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await createAssetCard(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  })
}

const onDelete = async (row: any) => {
  await deleteAssetCard(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

const onDepreciateAll = async () => {
  if (!depreciationPeriod.value) {
    ElMessage.warning('请先填写期间')
    return
  }
  await depreciatePeriod(depreciationPeriod.value)
  ElMessage.success('已计提 ' + depreciationPeriod.value + ' 折旧')
  fetchData()
}

const onDepreciateOne = async (row: any) => {
  if (!depreciationPeriod.value) {
    ElMessage.warning('请先填写期间')
    return
  }
  await depreciateOne(row.id, depreciationPeriod.value)
  ElMessage.success('已计提资产 ' + row.assetCode + ' 折旧')
  fetchData()
}

onMounted(async () => {
  depreciationPeriod.value = await resolveDefaultPeriod()
  loadCategories()
  fetchData()
})
</script>
