<template>
  <div class="user-list">
    <el-card shadow="never">
      <div class="page-header">
        <el-form :model="query" inline>
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable @clear="search" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable @change="search">
              <el-option label="正常" value="active" />
              <el-option label="禁用" value="inactive" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="deptName" label="部门" width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button
              :type="row.status === 'active' ? 'warning' : 'success'"
              text
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'active' ? '禁用' : '启用' }}
            </el-button>
            <el-popconfirm title="确认删除此用户？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="600" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100" class="user-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">正常</el-radio>
            <el-radio value="inactive">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
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
import { getUserPage, createUser, updateUser, updateUserStatus, deleteUser, resetPwd, getAllRoles, type UserVO, type RoleVO } from '@/api/modules/system'

const loading = ref(false)
const submitting = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)
const roleOptions = ref<RoleVO[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const query = reactive({ page: 1, size: 10, keyword: '', status: '' })

const form = reactive({
  username: '',
  realName: '',
  nickname: '',
  email: '',
  phone: '',
  password: '',
  status: 'active',
  roleIds: [] as number[],
})

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getUserPage({ page: query.page, size: query.size, keyword: query.keyword || undefined, status: query.status || undefined })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  fetchData()
}

function reset() {
  query.keyword = ''
  query.status = ''
  search()
}

async function loadRoles() {
  try {
    roleOptions.value = await getAllRoles()
  } catch { /* ignore */ }
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  form.username = ''
  form.realName = ''
  form.nickname = ''
  form.email = ''
  form.phone = ''
  form.password = ''
  form.status = 'active'
  form.roleIds = []
  dialogVisible.value = true
}

function openEdit(row: UserVO) {
  isEdit.value = true
  editingId.value = row.id
  form.username = row.username
  form.realName = row.realName
  form.nickname = row.nickname
  form.email = row.email
  form.phone = row.phone
  form.password = ''
  form.status = row.status
  form.roleIds = row.roleIds || []
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateUser(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      if (!form.password) { ElMessage.warning('请设置密码'); submitting.value = false; return }
      await createUser(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleToggleStatus(row: UserVO) {
  const newStatus = row.status === 'active' ? 'inactive' : 'active'
  try {
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(newStatus === 'active' ? '已启用' : '已禁用')
    fetchData()
  } catch { /* ignore */ }
}

async function handleResetPwd(row: UserVO) {
  try {
    await resetPwd(row.id, '123456')
    ElMessage.success('密码已重置为 123456')
  } catch { /* ignore */ }
}

async function handleDelete(row: UserVO) {
  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* ignore */ }
}

onMounted(() => {
  fetchData()
  loadRoles()
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.page-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.user-form {
  padding-right: 24px;
}
</style>
