<template>
  <div class="asset-category">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">资产类别</span>
        <el-button type="primary" @click="openEdit()">新增类别</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="编码/名称" clearable style="width:200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="depreciationMethod" label="折旧方法" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ METHOD_MAP[row.depreciationMethod] || row.depreciationMethod }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="usefulLife" label="使用年限" width="100" align="center" />
        <el-table-column label="残值率" width="100" align="right">
          <template #default="{ row }">{{ (Number(row.residualRate || 0) * 100).toFixed(1) }}%</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该类别?" @confirm="onDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑类别' : '新增类别'" width="560px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="编码" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="折旧方法">
          <el-select v-model="form.depreciationMethod" style="width:100%">
            <el-option v-for="o in METHOD_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用年限"><el-input-number v-model="form.usefulLife" :min="1" :max="100" style="width:100%" /></el-form-item>
        <el-form-item label="残值率">
          <el-input-number v-model="form.residualRate" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
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
import { pageAssetCategory, createAssetCategory, updateAssetCategory, deleteAssetCategory } from '@/api/modules/asset'

const METHOD_OPTIONS = [
  { value: 'STRAIGHT_LINE', label: '平均年限法' },
  { value: 'DOUBLE_DECLINING', label: '双倍余额递减法' },
  { value: 'SUM_OF_YEARS', label: '年数总和法' },
]
const METHOD_MAP: Record<string, string> = Object.fromEntries(METHOD_OPTIONS.map((o) => [o.value, o.label]))

const query = reactive({ keyword: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ depreciationMethod: 'STRAIGHT_LINE', usefulLife: 5, residualRate: 0.05 })

const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageAssetCategory(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const openEdit = (row?: any) => {
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: undefined, code: '', name: '', depreciationMethod: 'STRAIGHT_LINE', usefulLife: 5, residualRate: 0.05, remark: '' })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.id) {
      await updateAssetCategory(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await createAssetCategory(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  })
}

const onDelete = async (row: any) => {
  await deleteAssetCategory(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(fetchData)
</script>
