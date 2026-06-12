<template>
  <div class="role-list">
    <el-card shadow="never">
      <div class="page-header">
        <el-form :model="query" inline>
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="角色名称" clearable @clear="search" />
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
        <el-button type="primary" @click="openCreate">新增角色</el-button>
        <el-button @click="showPresets = true">默认角色模板</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="code" label="角色编码" width="150" />
        <el-table-column prop="name" label="角色名称" width="150" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="sortOrder" label="排序" width="60" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dataScope" label="数据权限" width="100" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" @click="openMenuAssign(row)">分配菜单</el-button>
            <el-popconfirm title="确认禁用此角色？" @confirm="handleToggleStatus(row)" v-if="row.status === 'active'">
              <template #reference>
                <el-button text type="warning" size="small">禁用</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm title="确认启用此角色？" @confirm="handleToggleStatus(row)" v-else>
              <template #reference>
                <el-button text type="success" size="small">启用</el-button>
              </template>
            </el-popconfirm>
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
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="500" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
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

    <el-dialog v-model="menuDialogVisible" title="分配菜单权限" width="400" destroy-on-close>
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        default-expand-all
        check-strictly
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignMenu">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPresets" title="选择默认角色模板" width="640" destroy-on-close>
      <el-table :data="ROLE_PRESETS" border stripe @row-click="selectPreset">
        <el-table-column prop="code" label="角色编码" width="140" />
        <el-table-column prop="name" label="角色名称" width="140" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click.stop="selectPreset(row)">创建</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="creatingPreset" title="正在创建默认角色..." width="400" :close-on-click-modal="false">
      <div style="text-align:center;padding:20px">
        <el-progress :percentage="presetProgress" :stroke-width="8" style="margin-bottom:16px" />
        <p>{{ presetStatus }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getRolePage, createRole, updateRole, updateRoleStatus, deleteRole,
  getRoleMenus, assignRoleMenus, getAllRoles,
  getMenuTree,
  type RoleVO, type MenuVO,
} from '@/api/modules/system'

const loading = ref(false)
const submitting = ref(false)
const list = ref<RoleVO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const menuDialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const assignRoleId = ref<number>(0)
const menuTree = ref<MenuVO[]>([])
const menuTreeRef = ref()
const showPresets = ref(false)
const creatingPreset = ref(false)
const presetProgress = ref(0)
const presetStatus = ref('')

// 默认角色模板 - 基于财务软件常见岗位预置菜单权限
const ROLE_PRESETS = [
  { code: 'accountant', name: '会计', description: '凭证录入/账簿查询/业务单据/科目管理/基础数据',
    menuIds: [2,40,50,60,70, 1000,1010,1011,1012,1013,1014,1015,1016,1017,1018, 1020,1021, 1040,1041,1042,1043,1044,1045,
              2000,2010,2011,2012,2013,2014, 2020,2021,2022,2023,2024,2025, 2030,2031,2032,
              3000,3010,3011,3012,3013,3014, 3020,3021,3022,3023,3024, 3030,3031, 3040,3041, 3050,3051,3052, 3060,3061,3062,3063,
              4000,4010,4011,4012,4013, 4020,4021,4022, 4030,4031] },
  { code: 'chief_accountant', name: '会计主管', description: '会计权限 + 审核/结账/报表/分析',
    menuIds: [2,40,50,60,70, 1000,1010,1011,1012,1013,1014,1015,1016,1017,1018,
              1020,1021, 1030,1031,1032, 1033, 1040,1041,1042,1043,1044,1045,
              2000,2010,2011,2012,2013,2014, 2020,2021,2022,2023,2024,2025, 2030,2031,2032,
              3000,3010,3011,3012,3013,3014, 3020,3021,3022,3023,3024, 3030,3031, 3040,3041, 3050,3051,3052, 3060,3061,3062,3063,
              4000,4010,4011,4012,4013, 4020,4021,4022, 4030,4031,
              6000,6010,6011, 6020,6021, 6030,6031, 6040,6041,
              7000,7010,7011, 7020,7021,
              8000,8010,8011, 8020,8021] },
  { code: 'cashier', name: '出纳', description: '银行账户/日记账/对账/现金日记账/票据管理',
    menuIds: [2,50, 1000,1050,1051,1052,1053,1054, 1060,1061, 1070,1071, 1080,1081,
              1090,1091,1092,1093,1094, 1095,1096,1097,1098,1099] },
  { code: 'finance_manager', name: '财务经理', description: '全部查看权限 + 报表/分析/预算',
    menuIds: [1,2,40,50,60,70,80,90,91,92,93,94, 95,96, 1000,1010,1011,1020,1021,1030,1031,1032,1033,
              1040,1041,1050,1051,1060,1061,1070,1071,1080,1081,1090,1091,1095,1096,1100,1101,1102,1103,
              2000,2010,2011,2020,2021,2030,2031,
              3000,3010,3011,3020,3021,3030,3031,3040,3041,3050,3051,3060,3061,
              4000,4010,4011,4020,4021,4030,4031,
              5000,5001,5002,5003,5004,
              6000,6010,6011,6020,6021,6030,6031,6040,6041,
              7000,7010,7011,7020,7021,
              8000,8010,8011,8020,8021] },
  { code: 'arap_accountant', name: '往来会计', description: '客户/供应商/应收/应付/坏账/核销',
    menuIds: [2,40,50,60,70, 1000,1010,1011, 1040,1041,1042,1043,1044,1045,
              3000,3010,3011,3012,3013,3014, 3020,3021,3022,3023,3024, 3030,3031, 3040,3041,
              3050,3051,3052, 3060,3061,3062,3063] },
  { code: 'tax_accountant', name: '税务会计', description: '进项发票/销项发票/增值税计算/申报',
    menuIds: [2,40,50,60,70, 1000,1010,1011, 4000,4010,4011,4012,4013, 4020,4021,4022, 4030,4031] },
  { code: 'company_manager', name: '公司经理', description: '报表查看/财务分析/预算查看/经营概览',
    menuIds: [2,40,50,60,70, 1000,1010,1011, 1020,1021, 1100,1101,
              5000,5001,
              6000,6010,6011, 6020,6021, 6030,6031, 6040,6041,
              7000,7010,7011, 7020,7021] },
  { code: 'employee', name: '普通员工', description: '基础数据查看/我的单据',
    menuIds: [2,40, 1000,1040,1041] },
]

const query = reactive({ page: 1, size: 10, keyword: '', status: '' })

const form = reactive({
  code: '',
  name: '',
  description: '',
  sortOrder: 0,
  status: 'active',
})

const formRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getRolePage({ page: query.page, size: query.size, keyword: query.keyword || undefined, status: query.status || undefined })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() { query.page = 1; fetchData() }
function reset() { query.keyword = ''; query.status = ''; search() }

function openCreate() {
  isEdit.value = false
  editingId.value = null
  form.code = ''; form.name = ''; form.description = ''; form.sortOrder = 0; form.status = 'active'
  dialogVisible.value = true
}

function openEdit(row: RoleVO) {
  isEdit.value = true
  editingId.value = row.id
  form.code = row.code; form.name = row.name; form.description = row.description; form.sortOrder = row.sortOrder; form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateRole(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createRole(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleToggleStatus(row: RoleVO) {
  const newStatus = row.status === 'active' ? 'inactive' : 'active'
  try {
    await updateRoleStatus(row.id, newStatus)
    ElMessage.success(newStatus === 'active' ? '已启用' : '已禁用')
    fetchData()
  } catch { /* ignore */ }
}

async function handleDelete(row: RoleVO) {
  try {
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* ignore */ }
}

async function openMenuAssign(row: RoleVO) {
  assignRoleId.value = row.id
  try {
    menuTree.value = await getMenuTree()
    const menuIds = await getRoleMenus(row.id)
    menuTreeRef.value?.setCheckedKeys(menuIds)
    menuDialogVisible.value = true
  } catch { /* ignore */ }
}

async function handleAssignMenu() {
  const checkedKeys = menuTreeRef.value?.getCheckedKeys() || []
  try {
    await assignRoleMenus(assignRoleId.value, checkedKeys)
    ElMessage.success('分配成功')
    menuDialogVisible.value = false
  } catch { /* ignore */ }
}

async function selectPreset(preset: any) {
  showPresets.value = false
  creatingPreset.value = true
  presetProgress.value = 10
  presetStatus.value = '正在创建角色...'
  try {
    const role: any = await createRole({ code: preset.code, name: preset.name, description: preset.description, sortOrder: 0, status: 'active' })
    presetProgress.value = 50
    presetStatus.value = '正在分配菜单权限...'
    await assignRoleMenus(role.id, preset.menuIds)
    presetProgress.value = 100
    presetStatus.value = '创建完成'
    ElMessage.success(`默认角色"${preset.name}"创建成功`)
    fetchData()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    setTimeout(() => { creatingPreset.value = false; presetProgress.value = 0 }, 1000)
  }
}

onMounted(() => fetchData())
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
</style>
