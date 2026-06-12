<template>
  <div class="dept-list">
    <el-card shadow="never">
      <div class="page-header">
        <div class="header-left">
          <el-button type="primary" @click="openCreate">新增部门</el-button>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </div>

      <el-table
        :data="list"
        v-loading="loading"
        row-key="id"
        border
        stripe
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        style="width: 100%"
      >
        <el-table-column prop="name" label="部门名称" min-width="180" />
        <el-table-column prop="leader" label="负责人" width="120" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openCreate(row)">新增下级</el-button>
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除此部门？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑部门' : '新增部门'" width="500" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="list"
            :props="{ label: 'name', value: 'id' }"
            placeholder="顶级部门"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">正常</el-radio>
            <el-radio value="inactive">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getDeptTree, createDept, updateDept, deleteDept, type DeptVO } from '@/api/modules/system'

const loading = ref(false)
const submitting = ref(false)
const list = ref<DeptVO[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  parentId: null as number | null,
  leader: '',
  phone: '',
  email: '',
  sortOrder: 0,
  status: 'active',
})

const formRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    list.value = await getDeptTree()
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: DeptVO) {
  isEdit.value = false
  editingId.value = null
  form.name = ''
  form.parentId = parent?.id ?? null
  form.leader = ''
  form.phone = ''
  form.email = ''
  form.sortOrder = 0
  form.status = 'active'
  dialogVisible.value = true
}

function openEdit(row: DeptVO) {
  isEdit.value = true
  editingId.value = row.id
  form.name = row.name
  form.parentId = row.parentId
  form.leader = row.leader || ''
  form.phone = row.phone || ''
  form.email = row.email || ''
  form.sortOrder = row.sortOrder
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateDept(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createDept(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: DeptVO) {
  try {
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* ignore */ }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
