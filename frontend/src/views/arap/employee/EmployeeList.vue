<template>
  <div class="employee-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">员工档案</span>
        <div>
          <el-button type="primary" @click="openCreate">新增员工</el-button>
          <el-button @click="onImportClick">导入员工</el-button>
        </div>
      </div>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="工号" min-width="140" />
        <el-table-column prop="name" label="姓名" min-width="140" />
        <el-table-column prop="deptName" label="部门" min-width="140" />
        <el-table-column prop="phone" label="电话" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="position" label="职位" min-width="140" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive !== false ? 'success' : 'danger'" size="small">
              {{ row.isActive !== false ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" :title="isEdit ? '编辑员工' : '新增员工'" width="520" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80">
        <el-form-item label="工号" prop="code">
          <el-input v-model="form.code" placeholder="如 EMP001" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.deptName" placeholder="部门名称（仅展示，未关联部门档案）" />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="form.position" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isActive" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { listEmployee, createEmployee, updateEmployee } from '@/api/modules/employee'

const list = ref<any[]>([])
const loading = ref(false)
const createVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = ref({ code: '', name: '', deptName: '', position: '', phone: '', email: '', isActive: true })
const rules = {
  code: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    list.value = await listEmployee()
  } catch { list.value = [] }
  finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { code: '', name: '', deptName: '', position: '', phone: '', email: '', isActive: true }
  createVisible.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  form.value = {
    code: row.code || '',
    name: row.name || '',
    deptName: row.deptName || '',
    position: row.position || '',
    phone: row.phone || '',
    email: row.email || '',
    isActive: row.isActive !== false,
  }
  createVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateEmployee(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createEmployee(form.value)
      ElMessage.success('新增成功')
    }
    createVisible.value = false
    await fetchData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function onImportClick() {
  ElMessage.info('导入员工功能开发中，请先使用新增员工')
}

onMounted(fetchData)
</script>

<style scoped>
.employee-list { padding: 0; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
</style>
