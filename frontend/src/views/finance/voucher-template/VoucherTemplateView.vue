<template>
  <div class="voucher-template-page">
    <div class="page-header">
      <span class="page-title">凭证模板</span>
      <el-button type="primary" @click="handleAdd">新建模板</el-button>
    </div>

    <el-table :data="templates" stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="模板名称" min-width="140" />
      <el-table-column prop="classification" label="分类" width="120" />
      <el-table-column label="来源" width="100">
        <template #default="{ row }">{{ row.source || '-' }}</template>
      </el-table-column>
      <el-table-column label="业务类型" width="100">
        <template #default="{ row }">{{ row.businessType || '-' }}</template>
      </el-table-column>
      <el-table-column label="分录" min-width="200">
        <template #default="{ row }">
          <span v-if="row.lines && row.lines.length > 0" class="line-preview">
            <span v-for="(line, i) in row.lines" :key="i">
              {{ line.direction === 'debit' ? '借' : '贷' }}:{{ line.subjectCode }}
              <span v-if="line.drAmountTemplate">({{ line.drAmountTemplate }})</span>
              <span v-if="line.crAmountTemplate">({{ line.crAmountTemplate }})</span>
              <em v-if="i < row.lines.length - 1"> | </em>
            </span>
          </span>
          <span v-else class="text-muted">无分录</span>
        </template>
      </el-table-column>
      <el-table-column prop="numberPrefix" label="凭证前缀" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-switch
            :model-value="row.isActive"
            :loading="togglingMap[row.id] ?? false"
            @change="(val: string | number | boolean) => handleToggleActive(row, val as boolean)"
            size="small"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑模板' : '新建模板'"
      width="800px"
      :close-on-click-modal="false"
      @close="handleDialogClose"
    >
      <el-form :model="form" label-width="100px" size="small">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模板名称" required>
              <el-input v-model="form.name" placeholder="如: 银行手续费" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.classification" placeholder="绑定分类" filterable allow-create clearable>
                <el-option label="银行手续费" value="bank_fee" />
                <el-option label="利息收入" value="interest_income" />
                <el-option label="税务缴费" value="tax_payment" />
                <el-option label="社保缴费" value="social_security" />
                <el-option label="保险费用" value="insurance_fee" />
                <el-option label="收款" value="business_receipt" />
                <el-option label="付款" value="business_payment" />
                <el-option label="内部转账" value="internal_transfer" />
                <el-option label="工资发放" value="salary_payment" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="来源">
              <el-select v-model="form.source" placeholder="来源" clearable>
                <el-option label="银行流水" value="BANK_STMT" />
                <el-option label="业务单据" value="BUSINESS_DOC" />
                <el-option label="发票" value="INVOICE" />
                <el-option label="期末结转" value="PERIOD_CLOSE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务类型">
              <el-input v-model="form.businessType" placeholder="如: RECEIPT" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="方向">
              <el-select v-model="form.direction" placeholder="方向" clearable>
                <el-option label="入/in" value="in" />
                <el-option label="出/out" value="out" />
                <el-option label="双向" value="" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="优先级">
              <el-input-number v-model="form.matchPriority" :min="0" :max="99" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="模板用途描述" />
        </el-form-item>

        <!-- 分录行 -->
        <el-divider content-position="left">分录行</el-divider>
        <div v-for="(line, idx) in form.lines" :key="idx" class="line-row">
          <el-row :gutter="8" align="middle">
            <el-col :span="2">
              <el-tag :type="line.direction === 'debit' ? 'danger' : 'success'" size="small">
                {{ line.direction === 'debit' ? '借' : '贷' }}
              </el-tag>
            </el-col>
            <el-col :span="6">
              <el-tree-select
                v-model="line.subjectId"
                :data="subjectTree"
                :props="{ label: 'name', value: 'id', children: 'children' }"
                placeholder="选择科目"
                filterable clearable style="width: 100%"
              />
            </el-col>
            <el-col :span="4"><el-input v-model="line.drAmountTemplate" placeholder="借金额" size="small" /></el-col>
            <el-col :span="4"><el-input v-model="line.crAmountTemplate" placeholder="贷金额" size="small" /></el-col>
            <el-col :span="3">
              <el-select v-model="line.assistType" placeholder="辅助核算" clearable size="small" style="width:100%">
                <el-option label="客户" value="CUSTOMER" />
                <el-option label="供应商" value="VENDOR" />
                <el-option label="部门" value="DEPT" />
                <el-option label="员工" value="EMPLOYEE" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
            </el-col>
            <el-col :span="1">
              <el-tooltip content="必填辅助核算">
                <el-checkbox v-model="line.assistRequired" size="small" />
              </el-tooltip>
            </el-col>
            <el-col :span="2">
              <el-button circle size="small" type="danger" :icon="Remove" @click="removeLine(idx)" />
            </el-col>
          </el-row>
          <el-row style="margin-top:4px">
            <el-col :span="2" />
            <el-col :span="22">
              <el-input v-model="line.summaryTemplate" placeholder="摘要模板, 如: 银行手续费 {{summary}}" size="small" />
            </el-col>
          </el-row>
        </div>
        <el-button type="primary" link size="small" @click="addLine('debit')">+ 添加借方行</el-button>
        <el-button type="primary" link size="small" @click="addLine('credit')">+ 添加贷方行</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Remove } from '@element-plus/icons-vue'
import {
  listTemplates,
  getTemplate,
  createTemplate,
  updateTemplate,
  updateTemplateLines,
  toggleTemplateActive,
  deleteTemplate,
  type VoucherTemplateVO,
  type TemplateLineVO,
  type VoucherTemplateCreateRequest,
} from '@/api/modules/voucherTemplate'
import { getSubjectTree } from '@/api/modules/subject'

const loading = ref(false)
const templates = ref<VoucherTemplateVO[]>([])
const subjectTree = ref<any[]>([])

const dialogVisible = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)

const form = ref<{
  name: string
  description: string
  classification: string
  source: string
  businessType: string
  direction: string
  matchPriority: number
  numberPrefix: string
  isActive: boolean
  lines: TemplateLineVO[]
}>({
  name: '',
  description: '',
  classification: '',
  source: '',
  businessType: '',
  direction: '',
  matchPriority: 0,
  numberPrefix: 'JZ',
  isActive: true,
  lines: [],
})

async function fetchTemplates() {
  loading.value = true
  try {
    const res = await listTemplates()
    templates.value = res.data || []
  } catch (e: any) {
    ElMessage.error('加载模板列表失败: ' + (e.message || ''))
  } finally {
    loading.value = false
  }
}

async function fetchSubjectTree() {
  try {
    const res = await getSubjectTree()
    subjectTree.value = res || []
  } catch (_e: any) {
    // ignore
  }
}

function handleAdd() {
  isEditing.value = false
  editId.value = null
  form.value = { name: '', description: '', classification: '', source: '', businessType: '', direction: '', matchPriority: 0, numberPrefix: 'JZ', isActive: true, lines: [] }
  dialogVisible.value = true
}

async function handleEdit(row: VoucherTemplateVO) {
  isEditing.value = true
  editId.value = row.id
  form.value = {
    name: row.name,
    description: row.description || '',
    classification: row.classification || '',
    source: row.source || '',
    businessType: row.businessType || '',
    direction: row.direction || '',
    matchPriority: row.matchPriority || 0,
    numberPrefix: row.numberPrefix || 'JZ',
    isActive: row.isActive,
    lines: (row.lines || []).map(l => ({ ...l })),
  }
  dialogVisible.value = true
}

function handleDialogClose() {
  // reset
}

function addLine(direction: string) {
  form.value.lines.push({
    subjectId: 0,
    direction,
    drAmountTemplate: '',
    crAmountTemplate: '',
    summaryTemplate: '',
    assistType: undefined,
    assistRequired: false,
    lineOrder: form.value.lines.length + 1,
  })
}

function removeLine(idx: number) {
  form.value.lines.splice(idx, 1)
  // re-index
  form.value.lines.forEach((l, i) => (l.lineOrder = i + 1))
}

async function handleSave() {
  if (!form.value.name) {
    ElMessage.warning('请输入模板名称')
    return
  }

  saving.value = true
  try {
    // Normalize lines: set lineOrder
    form.value.lines.forEach((l, i) => (l.lineOrder = i + 1))

    if (isEditing.value && editId.value) {
      await updateTemplate(editId.value, {
        name: form.value.name,
        description: form.value.description,
        classification: form.value.classification || undefined,
        source: form.value.source || undefined,
        businessType: form.value.businessType || undefined,
        direction: form.value.direction || undefined,
        matchPriority: form.value.matchPriority,
        numberPrefix: form.value.numberPrefix,
      })
      // Update lines
      await updateTemplateLines(editId.value, form.value.lines)
      // Update active status if changed
      const orig = templates.value.find(t => t.id === editId.value)
      if (orig && orig.isActive !== form.value.isActive) {
        await toggleTemplateActive(editId.value, form.value.isActive)
      }
      ElMessage.success('模板更新成功')
    } else {
      // Create
      const req: VoucherTemplateCreateRequest = {
        name: form.value.name,
        description: form.value.description || undefined,
        classification: form.value.classification || undefined,
        source: form.value.source || undefined,
        businessType: form.value.businessType || undefined,
        direction: form.value.direction || undefined,
        matchPriority: form.value.matchPriority,
        numberPrefix: form.value.numberPrefix,
        isActive: form.value.isActive,
        lines: form.value.lines,
      }
      await createTemplate(req)
      ElMessage.success('模板创建成功')
    }
    dialogVisible.value = false
    await fetchTemplates()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || ''))
  } finally {
    saving.value = false
  }
}

const togglingMap = ref<Record<number, boolean>>({})

async function handleToggleActive(row: VoucherTemplateVO, active: boolean) {
  togglingMap.value[row.id] = true
  try {
    await toggleTemplateActive(row.id, active)
    row.isActive = active
    ElMessage.success(active ? '已激活' : '已停用')
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e.message || ''))
  } finally {
    togglingMap.value[row.id] = false
  }
}

async function handleDelete(row: VoucherTemplateVO) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.name}」?`, '确认删除', { type: 'warning' })
    await deleteTemplate(row.id)
    ElMessage.success('已删除')
    await fetchTemplates()
  } catch (_e: any) {
    // cancelled or error
  }
}

onMounted(() => {
  fetchTemplates()
  fetchSubjectTree()
})
</script>

<style scoped>
.voucher-template-page {
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
}
.line-preview {
  font-size: 12px;
  color: #606266;
}
.text-muted {
  color: #c0c4cc;
}
.line-row {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
  margin-bottom: 6px;
}
</style>
