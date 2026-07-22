<template>
  <div class="subject-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">科目管理</span>
        <div>
          <el-button type="primary" @click="openCreate(null)">新增一级科目</el-button>
          <el-button type="success" @click="handleImportStandard">一键导入常用科目</el-button>
          <el-button type="primary" @click="showImportDialog = true">导入科目</el-button>
          <el-button @click="downloadTemplate">下载模板</el-button>
          <el-button @click="fetchTree">刷新</el-button>
        </div>
      </div>

      <el-table :data="treeData" v-loading="loading" row-key="id" border stripe default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }" style="width: 100%">
        <el-table-column prop="code" label="科目编码" width="160" />
        <el-table-column prop="name" label="科目名称" min-width="180" />
        <el-table-column prop="direction" label="方向" width="80">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'debit' ? 'danger' : 'success'" size="small">
              {{ row.direction === 'debit' ? '借方' : '贷方' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="层级" width="60" align="center" />
        <el-table-column prop="auxCalcType" label="辅助核算" width="120">
          <template #default="{ row }">
            <span v-if="!row.auxCalcType">—</span>
            <el-tag v-else size="small">{{ auxLabel(row.auxCalcType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isLeaf" label="末级" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isLeaf ? 'success' : 'info'" size="small">{{ row.isLeaf ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openCreate(row)">新增下级</el-button>
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除此科目？如有下级科目无法删除" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑科目' : '新增科目'" width="560" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110">
        <el-form-item label="科目编码" prop="code">
          <el-input v-model="form.code" placeholder="如 1001.01.001" />
        </el-form-item>
        <el-form-item label="科目名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="上级科目" v-if="form.parentId">
          <el-input :model-value="parentPath" disabled />
        </el-form-item>
        <el-form-item label="借贷方向" prop="direction">
          <el-select v-model="form.direction" style="width:100%">
            <el-option label="借方" value="debit" />
            <el-option label="贷方" value="credit" />
          </el-select>
        </el-form-item>
        <el-form-item label="辅助核算类型">
          <el-select v-model="form.auxCalcType" placeholder="无" clearable style="width:100%">
            <el-option v-for="o in AUX_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.isActive" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入科目对话框 -->
    <el-dialog v-model="showImportDialog" title="导入科目" width="640" destroy-on-close @closed="handleImportClose">
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
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { getSubjectTree, createSubject, updateSubject, deleteSubject, getSubject, importStandardSubjects, importSubjects, downloadSubjectTemplate } from '@/api/modules/subject'
import type { SubjectVO, SubjectCreateParam, SubjectUpdateParam, ImportResult } from '@/api/modules/subject'

const loading = ref(false)
const saving = ref(false)
const treeData = ref<SubjectVO[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<string | null>(null)
const selectedParent = ref<SubjectVO | null>(null)
const formRef = ref<FormInstance>()

const showImportDialog = ref(false)
const uploading = ref(false)
const importResult = ref<ImportResult | null>(null)

const form = ref({
  code: '',
  name: '',
  parentId: null as string | null,
  direction: 'debit',
  auxCalcType: '' as string,
  isActive: true,
  remark: '',
})

const AUX_OPTIONS = [
  { value: 'customer',  label: '客户' },
  { value: 'vendor',    label: '供应商' },
  { value: 'department',label: '部门' },
  { value: 'project',   label: '项目' },
  { value: 'employee',  label: '员工' },
]

const formRules = {
  code: [{ required: true, message: '请输入科目编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入科目名称', trigger: 'blur' }],
  direction: [{ required: true, message: '请选择借贷方向', trigger: 'change' }],
}

const parentPath = computed(() => {
  if (!selectedParent.value) return ''
  return `${selectedParent.value.code} ${selectedParent.value.name}`
})

function auxLabel(type: string | null): string {
  const map: Record<string, string> = { customer: '客户', vendor: '供应商', department: '部门', project: '项目', employee: '员工' }
  return type ? (map[type] || type) : '—'
}

async function fetchTree() {
  loading.value = true
  try {
    treeData.value = await getSubjectTree()
  } catch {
    // ElMessage already handled by request interceptor
  } finally {
    loading.value = false
  }
}

function openCreate(parent: SubjectVO | null) {
  isEdit.value = false
  editId.value = null
  selectedParent.value = parent
  form.value = { code: '', name: '', parentId: parent?.id ?? null, direction: 'debit', auxCalcType: '', isActive: true, remark: '' }
  dialogVisible.value = true
}

async function openEdit(row: SubjectVO) {
  isEdit.value = true
  editId.value = row.id
  selectedParent.value = null
  try {
    const detail = await getSubject(row.id)
    form.value = {
      code: detail.code,
      name: detail.name,
      parentId: detail.parentId,
      direction: detail.direction,
      auxCalcType: detail.auxCalcType ?? '',
      isActive: detail.isActive,
      remark: detail.remark,
    }
    dialogVisible.value = true
  } catch {
    // handled
  }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateSubject(editId.value, form.value as SubjectUpdateParam)
      ElMessage.success('修改成功')
    } else {
      await createSubject(form.value as SubjectCreateParam)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await fetchTree()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: SubjectVO) {
  try {
    await deleteSubject(row.id)
    ElMessage.success('删除成功')
    await fetchTree()
  } catch {
    // handled
  }
}

async function handleImportStandard() {
  try {
    await ElMessageBox.confirm(
      '确认一键导入国家标准科目？此操作会为所有6大类（资产/负债/共同/权益/成本/损益）创建一级科目。\\n注意：科目表必须为空才能导入。',
      '导入确认',
      { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'warning' }
    )
    const count = await importStandardSubjects()
    ElMessage.success(`成功导入 ${count} 个国家标准一级科目`)
    await fetchTree()
  } catch {
    // cancelled or error
  }
}

async function handleImportFileChange(uploadFile: any) {
  const file = uploadFile.raw
  if (!file) return
  uploading.value = true
  importResult.value = null
  try {
    const res = await importSubjects(file)
    importResult.value = res
    if (res.errors.length === 0) {
      ElMessage.success(`成功导入 ${res.success} 条科目`)
    } else {
      ElMessage.warning(`导入完成，${res.success}/${res.total} 成功，${res.errors.length} 条失败`)
    }
    await fetchTree()
  } catch {
    importResult.value = null
  } finally {
    uploading.value = false
  }
}

async function downloadTemplate() {
  try {
    await downloadSubjectTemplate()
    ElMessage.success('模板下载成功')
  } catch {
    // handled
  }
}

function handleImportClose() {
  importResult.value = null
}

onMounted(fetchTree)
</script>

<style scoped>
.subject-list .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
</style>
