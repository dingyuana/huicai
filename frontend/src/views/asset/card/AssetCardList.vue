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
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text type="success" @click="openChange(row)">变动</el-button>
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

    <!-- 资产变动对话框（V1.106 D-4 收尾：VALUE_ADJUST 调整原值 / DEPT_TRANSFER 部门转移；
         STATUS_CHANGE/DEPRECIATION 后端 400 拒绝，不提供入口） -->
    <el-dialog v-model="changeDialogVisible" :title="`资产变动 - ${changeAsset?.assetName || ''}`" width="720px">
      <el-form :model="changeForm" label-width="100px" :rules="changeRules" ref="changeFormRef">
        <el-form-item label="变动类型" prop="changeType">
          <el-radio-group v-model="changeForm.changeType" @change="onChangeTypeSwitch">
            <el-radio-button value="VALUE_ADJUST">调整原值</el-radio-button>
            <el-radio-button value="DEPT_TRANSFER">部门转移</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="changeForm.changeType === 'VALUE_ADJUST'" label="调整金额" prop="amount">
          <el-input-number v-model="changeForm.amount" :precision="2" style="width:100%" />
          <div class="change-amount-hint">正数为调增，负数为调减；调减后净值不得为负</div>
        </el-form-item>
        <el-form-item v-if="changeForm.changeType === 'DEPT_TRANSFER'" label="转入部门" prop="deptId">
          <el-tree-select
            v-model="changeForm.deptId"
            :data="deptTree"
            :props="({ label: 'name', value: 'id' } as any)"
            node-key="id"
            check-strictly
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="变动日期" prop="changeDate">
          <el-date-picker v-model="changeForm.changeDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="changeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="changeSubmitting" @click="onChangeSubmit">提交变动</el-button>
      </template>

      <el-divider>变动流水</el-divider>
      <el-table :data="changeList" v-loading="changeListLoading" size="small" border>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">{{ CHANGE_TYPE_MAP[row.changeType] || row.changeType }}</template>
        </el-table-column>
        <el-table-column label="变动前" width="110" align="right">
          <template #default="{ row }">{{ row.beforeValue != null ? fmtAmount(row.beforeValue) : '—' }}</template>
        </el-table-column>
        <el-table-column label="变动后" width="110" align="right">
          <template #default="{ row }">{{ row.afterValue != null ? fmtAmount(row.afterValue) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="changeDate" label="日期" width="110" />
        <el-table-column label="凭证" width="130">
          <template #default="{ row }">
            <span v-if="row.voucherNo">{{ row.voucherNo }}</span>
            <span v-else class="change-voucher-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import dayjs from 'dayjs'
import { resolveDefaultPeriod } from '@/utils/period'
import { getDeptTree } from '@/api/modules/system'
import {
  pageAssetCard,
  createAssetCard,
  updateAssetCard,
  deleteAssetCard,
  depreciatePeriod,
  depreciateOne,
  listAssetCategory,
  createAssetChange,
  listAssetChanges,
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

// ─── 资产变动 ───
const CHANGE_TYPE_MAP: Record<string, string> = {
  VALUE_ADJUST: '调整原值',
  DEPT_TRANSFER: '部门转移',
}

const changeDialogVisible = ref(false)
const changeSubmitting = ref(false)
const changeFormRef = ref<FormInstance>()
const changeAsset = ref<any>(null)
const changeForm = reactive<any>({})
const changeList = ref<any[]>([])
const changeListLoading = ref(false)
const deptTree = ref<any[]>([])

const changeRules = {
  changeType: [{ required: true, message: '请选择变动类型', trigger: 'change' }],
  changeDate: [{ required: true, message: '请选择变动日期', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入调整金额', trigger: 'blur' },
    {
      validator: (_rule: any, value: any, callback: (e?: Error) => void) => {
        if (value === 0) callback(new Error('调整金额不能为 0'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  deptId: [{ required: true, message: '请选择转入部门', trigger: 'change' }],
}

const onChangeTypeSwitch = () => {
  // 类型切换时清另一形态的字段，避免脏值随提交送出（后端按类型分别校验）
  if (changeForm.changeType === 'VALUE_ADJUST') {
    changeForm.deptId = undefined
  } else {
    changeForm.amount = undefined
  }
  changeFormRef.value?.clearValidate()
}

const openChange = async (row: any) => {
  changeAsset.value = row
  Object.assign(changeForm, {
    changeType: 'VALUE_ADJUST',
    changeDate: dayjs().format('YYYY-MM-DD'),
    amount: undefined,
    deptId: undefined,
    remark: '',
  })
  changeDialogVisible.value = true
  loadDeptTree()
  loadChangeList(row.id)
}

const loadDeptTree = async () => {
  if (deptTree.value.length) return
  try {
    deptTree.value = await getDeptTree()
  } catch {
    deptTree.value = []
  }
}

const loadChangeList = async (assetId: number) => {
  changeListLoading.value = true
  try {
    changeList.value = (await listAssetChanges(assetId)) || []
  } finally {
    changeListLoading.value = false
  }
}

const onChangeSubmit = async () => {
  if (!changeFormRef.value || !changeAsset.value) return
  await changeFormRef.value.validate(async (valid) => {
    if (!valid) return
    changeSubmitting.value = true
    try {
      const changeId = await createAssetChange({
        assetId: changeAsset.value.id,
        changeType: changeForm.changeType,
        changeDate: changeForm.changeDate,
        amount: changeForm.amount,
        deptId: changeForm.deptId,
        remark: changeForm.remark,
      })
      ElMessage.success(`变动已登记，凭证 ADJ-${changeId} 已生成（草稿，可在凭证管理过账）`)
      loadChangeList(changeAsset.value.id)
      fetchData()
    } finally {
      changeSubmitting.value = false
    }
  })
}

onMounted(async () => {
  depreciationPeriod.value = await resolveDefaultPeriod()
  loadCategories()
  fetchData()
})
</script>

<style scoped>
.change-amount-hint {
  width: 100%;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.change-voucher-empty {
  color: #c0c4cc;
}
</style>
