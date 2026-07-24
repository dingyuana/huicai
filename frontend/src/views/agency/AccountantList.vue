<template>
  <div class="accountant-list-page">
    <h2>会计管理</h2>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索姓名/用户名" clearable style="width: 240px" @keyup.enter="fetchList" />
      <el-button type="primary" @click="fetchList" style="margin-left: 12px">搜索</el-button>
      <el-button type="success" @click="showCreateDialog" style="margin-left: auto">新增会计</el-button>
    </div>

    <!-- 代理用户表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="roleTagType((row as AgencyUserVO).agencyRole)" size="small">
            {{ roleLabel((row as AgencyUserVO).agencyRole) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType((row as AgencyUserVO).status)" size="small">
            {{ statusLabel((row as AgencyUserVO).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enterpriseCount" label="负责客户" width="100" align="center" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="(row as AgencyUserVO).status === 'ACTIVE'"
            type="warning"
            size="small"
            @click="handleSuspend(row as AgencyUserVO)"
          >
            暂停
          </el-button>
          <el-button
            v-if="(row as AgencyUserVO).status === 'SUSPENDED'"
            type="primary"
            size="small"
            @click="handleReactivate(row as AgencyUserVO)"
          >
            恢复
          </el-button>
          <el-button
            v-if="(row as AgencyUserVO).status === 'SUSPENDED'"
            type="danger"
            size="small"
            @click="handleTerminate(row as AgencyUserVO)"
          >
            终止
          </el-button>
          <el-button
            v-if="(row as AgencyUserVO).status === 'ACTIVE'"
            type="info"
            size="small"
            @click="goAssign(row as AgencyUserVO)"
          >
            分配客户
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增会计弹窗 -->
    <el-dialog v-model="createVisible" title="新增会计" width="480px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="createForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="agencyRole">
          <el-select v-model="createForm.agencyRole" placeholder="请选择角色" style="width: 100%">
            <el-option label="会计" value="ACCOUNTANT" />
            <el-option label="审核员" value="REVIEWER" />
            <el-option label="助理" value="ASSISTANT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getAgencyUsers, createAgencyUser, suspendAgencyUser, reactivateAgencyUser, terminateAgencyUser,
  type AgencyUserVO,
} from '@/api/modules/agency'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const keyword = ref('')
const tableData = ref<AgencyUserVO[]>([])

// ---- 创建弹窗 ----
const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  username: '',
  password: '',
  realName: '',
  agencyRole: 'ACCOUNTANT',
})
const createRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  agencyRole: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

// ---- 工具函数 ----
function roleLabel(role: string) {
  const map: Record<string, string> = {
    AGENCY_ADMIN: '经理',
    ACCOUNTANT: '会计',
    REVIEWER: '审核员',
    ASSISTANT: '助理',
  }
  return map[role] || role
}

function roleTagType(role: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    AGENCY_ADMIN: 'info',
    ACCOUNTANT: 'success',
    REVIEWER: 'warning',
    ASSISTANT: 'info',
  }
  return map[role] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    ACTIVE: '活跃',
    SUSPENDED: '已暂停',
    TERMINATED: '已终止',
  }
  return map[status] || status
}

function statusTagType(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    ACTIVE: 'success',
    SUSPENDED: 'warning',
    TERMINATED: 'danger',
  }
  return map[status] || 'info'
}

// ---- 数据加载 ----
async function fetchList() {
  loading.value = true
  try {
    tableData.value = await getAgencyUsers({ keyword: keyword.value || undefined })
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

// ---- 创建 ----
function showCreateDialog() {
  createForm.username = ''
  createForm.password = ''
  createForm.realName = ''
  createForm.agencyRole = 'ACCOUNTANT'
  createVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  creating.value = true
  try {
    await createAgencyUser({
      username: createForm.username,
      password: createForm.password,
      realName: createForm.realName,
      agencyRole: createForm.agencyRole,
      agencyId: authStore.agencyId!,
    })
    ElMessage.success('创建成功')
    createVisible.value = false
    fetchList()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

// ---- 状态操作 ----
async function handleSuspend(row: AgencyUserVO) {
  try {
    await ElMessageBox.confirm(`确定暂停用户「${row.realName}」吗？`, '确认操作', { type: 'warning' })
  } catch {
    return
  }
  try {
    await suspendAgencyUser(row.id)
    ElMessage.success('已暂停')
    fetchList()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleReactivate(row: AgencyUserVO) {
  try {
    await ElMessageBox.confirm(`确定恢复用户「${row.realName}」吗？`, '确认操作', { type: 'info' })
  } catch {
    return
  }
  try {
    await reactivateAgencyUser(row.id)
    ElMessage.success('已恢复')
    fetchList()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleTerminate(row: AgencyUserVO) {
  try {
    await ElMessageBox.confirm(`确定终止用户「${row.realName}」吗？此操作不可恢复！`, '确认操作', { type: 'warning' })
  } catch {
    return
  }
  try {
    await terminateAgencyUser(row.id)
    ElMessage.success('已终止')
    fetchList()
  } catch {
    ElMessage.error('操作失败')
  }
}

// ---- 分配 ----
function goAssign(row: AgencyUserVO) {
  router.push({ path: '/agency/assignment-manage', query: { agencyUserId: row.id } })
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.accountant-list-page {
  padding: 16px;
  h2 { margin-bottom: 16px; }
  .search-bar {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
  }
}
</style>