<template>
  <div class="voucher-type-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">凭证类型管理</span>
        <div>
          <el-button type="primary" @click="openCreate">新增类型</el-button>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="code" label="编码" width="100" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
        <el-table-column prop="numberingRule" label="编号规则" width="220" />
        <el-table-column prop="isActive" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.page" v-model:page-size="query.size"
          :total="total" layout="total, prev, pager, next, jumper"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑凭证类型' : '新增凭证类型'" width="520" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110">
        <el-form-item label="类型编码" prop="code">
          <el-input v-model="form.code" placeholder="如 JZ/SK/FK" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="form.name" placeholder="如 记账凭证" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="编号规则">
          <el-input v-model="form.numberingRule" placeholder="{type}-{year}{month}-{serial}" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.isActive" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getVoucherTypePage, createVoucherType, updateVoucherType, deleteVoucherType } from '@/api/modules/voucherType'
import type { VoucherTypeVO } from '@/api/modules/voucherType'

const loading = ref(false)
const saving = ref(false)
const list = ref<VoucherTypeVO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const query = ref({ page: 1, size: 20 })

const form = ref({ code: '', name: '', sortOrder: 1, numberingRule: '{type}-{year}{month}-{serial}', isActive: true, remark: '' })
const formRules = {
  code: [{ required: true, message: '请输入类型编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getVoucherTypePage(query.value)
    list.value = res.records
    total.value = res.total
  } catch { /* handled */ } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { code: '', name: '', sortOrder: 1, numberingRule: '{type}-{year}{month}-{serial}', isActive: true, remark: '' }
  dialogVisible.value = true
}

function openEdit(row: VoucherTypeVO) {
  isEdit.value = true
  editId.value = row.id
  form.value = { code: row.code, name: row.name, sortOrder: row.sortOrder, numberingRule: row.numberingRule, isActive: row.isActive, remark: row.remark }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateVoucherType(editId.value, form.value)
      ElMessage.success('修改成功')
    } else {
      await createVoucherType(form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await fetchData()
  } catch { /* handled */ } finally { saving.value = false }
}

async function handleDelete(row: VoucherTypeVO) {
  await deleteVoucherType(row.id)
  ElMessage.success('删除成功')
  await fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.voucher-type-list .page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.page-pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
