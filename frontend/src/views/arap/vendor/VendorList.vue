<template>
  <div class="vendor-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">供应商档案</span>
        <div>
          <el-button type="primary" @click="openEdit()">新增供应商</el-button>
          <el-button type="primary" @click="showImportDialog = true">导入供应商</el-button>
          <el-button @click="downloadTemplate">下载模板</el-button>
        </div>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="编码/名称/联系人" clearable style="width:240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="供应商编码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="name" label="供应商名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="contactPerson" label="联系人" min-width="100" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" min-width="140" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="address" label="地址" min-width="240" show-overflow-tooltip />
        <el-table-column label="信用额度" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.creditLimit) }}</template>
        </el-table-column>
        <el-table-column prop="creditDays" label="账期(天)" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑供应商' : '新增供应商'" width="640px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="编码" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="税号"><el-input v-model="form.taxNo" /></el-form-item>
        <el-form-item label="信用额度">
          <el-input-number v-model="form.creditLimit" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="账期(天)">
          <el-input-number v-model="form.creditDays" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isActive" />
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

    <!-- 导入供应商对话框 -->
    <el-dialog v-model="showImportDialog" title="导入供应商" width="640" destroy-on-close @closed="handleImportClose">
      <template v-if="!importResult">
        <el-upload
          drag
          accept=".xlsx"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleImportFileChange"
        >
          <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将 Excel 文件拖到此处，或<em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              仅支持 .xlsx 格式，请先<a href="javascript:void(0)" @click="downloadTemplate">下载模板</a>填写数据
            </div>
          </template>
        </el-upload>
      </template>

      <template v-else>
        <div class="import-result-summary">
          <el-result
            :icon="importResult.errors.length > 0 ? 'warning' : 'success'"
            :title="importResult.errors.length > 0 ? '导入完成，部分失败' : '导入成功'"
            :sub-title="`共 ${importResult.total} 行，成功 ${importResult.success} 行${importResult.errors.length > 0 ? `，失败 ${importResult.errors.length} 行` : ''}`"
          />
        </div>
        <el-table v-if="importResult.errors.length > 0" :data="importResult.errors" border stripe max-height="300" style="width:100%">
          <el-table-column prop="row" label="行号" width="80" align="center" />
          <el-table-column prop="message" label="失败原因" min-width="200" />
        </el-table>
      </template>

      <template #footer>
        <el-button v-if="importResult" type="primary" @click="showImportDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { pageVendor, createVendor, updateVendor, deleteVendor, importVendors, downloadVendorTemplate } from '@/api/modules/arap'
import type { ImportResult } from '@/api/modules/arap'

const query = reactive({ keyword: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ isActive: true, creditLimit: 0, creditDays: 30 })
const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

const showImportDialog = ref(false)
const uploading = ref(false)
const importResult = ref<ImportResult | null>(null)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageVendor(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const openEdit = (row?: any) => {
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: undefined, code: '', name: '', contactPerson: '', phone: '', email: '', address: '', taxNo: '', creditLimit: 0, creditDays: 30, isActive: true, remark: '' })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.id) {
      await updateVendor(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await createVendor(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  })
}

const onDelete = async (row: any) => {
  await deleteVendor(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

async function handleImportFileChange(uploadFile: any) {
  const file = uploadFile.raw
  if (!file) return
  uploading.value = true
  importResult.value = null
  try {
    const res = await importVendors(file)
    importResult.value = res
    if (res.errors.length === 0) {
      ElMessage.success(`成功导入 ${res.success} 条供应商`)
    } else {
      ElMessage.warning(`导入完成，${res.success}/${res.total} 成功，${res.errors.length} 条失败`)
    }
    await fetchData()
  } catch {
    importResult.value = null
  } finally {
    uploading.value = false
  }
}

async function downloadTemplate() {
  try {
    await downloadVendorTemplate()
    ElMessage.success('模板下载成功')
  } catch {
    // handled
  }
}

function handleImportClose() {
  importResult.value = null
}

onMounted(fetchData)
</script>

<style scoped>
.vendor-list .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
.page-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
:deep(.el-table .cell) {
  white-space: normal;
  word-break: break-all;
  line-height: 1.4;
  padding: 4px 6px;
}
:deep(.el-table__row td) {
  padding: 6px 0;
}
</style>
