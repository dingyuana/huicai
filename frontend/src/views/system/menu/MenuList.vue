<template>
  <div class="menu-list">
    <el-card shadow="never">
      <div class="page-header">
        <el-button type="primary" @click="openCreate(null)">新增菜单</el-button>
      </div>

      <el-table :data="treeData" v-loading="loading" border stripe row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column prop="name" label="菜单名称" min-width="180" />
        <el-table-column prop="icon" label="图标" width="60" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'menu'" type="primary" size="small">菜单</el-tag>
            <el-tag v-else-if="row.type === 'button'" type="info" size="small">按钮</el-tag>
            <el-tag v-else type="warning" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="permissionCode" label="权限标识" width="180" />
        <el-table-column prop="path" label="路由路径" width="160" />
        <el-table-column prop="sortOrder" label="排序" width="60" />
        <el-table-column prop="isVisible" label="可见" width="60" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.isVisible" color="#67c23a"><Check /></el-icon>
            <el-icon v-else color="#f56c6c"><Close /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">
              {{ row.isActive ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openCreate(row as MenuVO)">新增子菜单</el-button>
            <el-button text size="small" @click="openEdit(row as MenuVO)">编辑</el-button>
            <el-popconfirm title="确认删除此菜单？" @confirm="handleDelete(row as MenuVO)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑菜单' : '新增菜单'" width="550" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="menuOptions"
            :props="({ label: 'name', children: 'children', value: 'id' } as any)"
            placeholder="顶级菜单"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio value="menu">菜单</el-radio>
            <el-radio value="button">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="权限标识" prop="permissionCode">
          <el-input v-model="form.permissionCode" placeholder="如 system:user:list" />
        </el-form-item>
        <el-form-item label="路由路径" v-if="form.type !== 'button'">
          <el-input v-model="form.path" placeholder="如 /system/user" />
        </el-form-item>
        <el-form-item label="组件路径" v-if="form.type !== 'button'">
          <el-input v-model="form.component" placeholder="如 system/user/UserList" />
        </el-form-item>
        <el-form-item label="图标" v-if="form.type !== 'button'">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.isActive">
            <el-radio :value="true">启用</el-radio>
            <el-radio :value="false">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="可见" v-if="form.type !== 'button'">
          <el-radio-group v-model="form.isVisible">
            <el-radio :value="true">显示</el-radio>
            <el-radio :value="false">隐藏</el-radio>
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
import { getMenuTree, getMenuOptions, createMenu, updateMenu, deleteMenu, type MenuVO } from '@/api/modules/system'

const loading = ref(false)
const submitting = ref(false)
const treeData = ref<MenuVO[]>([])
const menuOptions = ref<MenuVO[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive({
  parentId: null as number | null,
  type: 'menu',
  name: '',
  permissionCode: '',
  path: '',
  component: '',
  icon: '',
  sortOrder: 0,
  isActive: true,
  isVisible: true,
  keepAlive: false,
  alwaysShow: false,
})

const formRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  permissionCode: [{ required: true, message: '请输入权限标识', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
}

async function fetchData() {
  loading.value = true
  try {
    treeData.value = await getMenuTree()
    menuOptions.value = await getMenuOptions()
  } finally {
    loading.value = false
  }
}

function openCreate(parent: MenuVO | null) {
  isEdit.value = false
  editingId.value = null
  form.parentId = parent?.id ?? null
  form.type = 'menu'
  form.name = ''
  form.permissionCode = ''
  form.path = ''
  form.component = ''
  form.icon = ''
  form.sortOrder = 0
  form.isActive = true
  form.isVisible = true
  form.keepAlive = false
  form.alwaysShow = false
  dialogVisible.value = true
}

async function openEdit(row: MenuVO) {
  isEdit.value = true
  editingId.value = row.id
  form.parentId = row.parentId
  form.type = row.type
  form.name = row.name
  form.permissionCode = row.permissionCode
  form.path = row.path
  form.component = row.component
  form.icon = row.icon
  form.sortOrder = row.sortOrder
  form.isActive = row.isActive
  form.isVisible = row.isVisible
  form.keepAlive = row.keepAlive
  form.alwaysShow = row.alwaysShow
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateMenu(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createMenu(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: MenuVO) {
  try {
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* ignore */ }
}

onMounted(() => fetchData())
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
</style>
