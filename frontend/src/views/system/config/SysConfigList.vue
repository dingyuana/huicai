<template>
  <div class="config-list">
    <el-card shadow="never">
      <div class="page-header">
        <el-form :model="query" inline>
          <el-form-item label="类型">
            <el-select v-model="query.configType" placeholder="全部" clearable @change="fetchData">
              <el-option label="全部" value="" />
              <el-option label="系统" value="system" />
              <el-option label="业务" value="business" />
              <el-option label="财务" value="accounting" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="参数键/说明" clearable @clear="fetchData" @keyup.enter="fetchData" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="fetchData">查询</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openCreate">新增参数</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="configKey" label="参数键" min-width="200" />
        <el-table-column prop="configValue" label="参数值" min-width="200" show-overflow-tooltip />
        <el-table-column prop="configType" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="configTypeTag(row.configType)" size="small">{{ configTypeLabel(row.configType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column prop="isActive" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑参数' : '新增参数'" width="560" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100">
        <el-form-item label="参数键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如 company.name" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="参数值" prop="configValue">
          <el-input v-model="form.configValue" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="参数类型">
          <el-select v-model="form.configType" style="width:100%">
            <el-option label="系统" value="system" />
            <el-option label="业务" value="business" />
            <el-option label="财务" value="accounting" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.isActive" />
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
import { getConfigPage, createConfig, updateConfig, deleteConfig } from '@/api/modules/sysConfig'
import type { SysConfigVO } from '@/api/modules/sysConfig'

const loading = ref(false)
const saving = ref(false)
const list = ref<SysConfigVO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const query = ref({ page: 1, size: 20, keyword: '', configType: '' })
const form = ref({ configKey: '', configValue: '', configType: 'system', description: '', isActive: true })
const formRules = {
  configKey: [{ required: true, message: '请输入参数键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数值', trigger: 'blur' }],
}

function configTypeTag(t: string): 'primary' | 'success' | 'warning' {
  return t === 'system' ? 'primary' : t === 'business' ? 'success' : 'warning'
}
function configTypeLabel(t: string) {
  return t === 'system' ? '系统' : t === 'business' ? '业务' : '财务'
}

async function fetchData() {
  loading.value = true
  try {
    const p: any = { page: query.value.page, size: query.value.size }
    if (query.value.keyword) p.keyword = query.value.keyword
    if (query.value.configType) p.configType = query.value.configType
    const res = await getConfigPage(p)
    list.value = res.records
    total.value = res.total
  } catch { /* handled */ } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { configKey: '', configValue: '', configType: 'system', description: '', isActive: true }
  dialogVisible.value = true
}

function openEdit(row: SysConfigVO) {
  isEdit.value = true
  editId.value = row.id
  form.value = { configKey: row.configKey, configValue: row.configValue, configType: row.configType, description: row.description, isActive: row.isActive }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateConfig(editId.value, form.value)
      ElMessage.success('修改成功')
    } else {
      await createConfig(form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await fetchData()
  } catch { /* handled */ } finally { saving.value = false }
}

async function handleDelete(row: SysConfigVO) {
  await deleteConfig(row.id)
  ElMessage.success('删除成功')
  await fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.config-list .page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.page-pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
