<template>
  <div class="voucher-edit">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑凭证' : '新增凭证' }}</span>
        <div>
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="saving" @click="onSave(false)">保存草稿</el-button>
          <el-button type="success" :loading="saving" @click="onSave(true)">保存并提交</el-button>
        </div>
      </div>

      <el-form :model="form" :rules="formRules" ref="formRef" label-width="100" inline>
        <el-form-item label="会计期间" prop="period">
          <el-input v-model="form.period" placeholder="YYYYMM" style="width:140px" />
        </el-form-item>
        <el-form-item label="凭证类型" prop="voucherTypeId">
          <el-select v-model="form.voucherTypeId" placeholder="选择类型" style="width:180px" filterable>
            <el-option v-for="t in voucherTypes" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <el-button
            v-if="!isEdit && form.voucherTypeId"
            link
            type="primary"
            style="margin-left:8px"
            @click="tryLoadTemplate()"
          >
            应用模板
          </el-button>
          <span v-if="templateApplied" class="template-hint">
            已加载: {{ boundTemplateName }}
          </span>
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="form.summary" placeholder="凭证摘要" style="width:340px" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="附件ID">
          <el-input v-model="form.attachmentIds" placeholder="可选，逗号分隔" style="width:240px" />
        </el-form-item>
      </el-form>

      <el-table :data="form.entries" border style="width:100%" class="entry-table">
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column label="科目" min-width="260">
          <template #default="{ row }">
            <el-tree-select
              v-model="row.subjectId"
              :data="leafSubjectOptions"
              :props="{ value: 'id', label: 'name' }"
              check-strictly
              :render-after-expand="false"
              placeholder="选择末级科目"
              style="width:100%"
              @change="onSubjectChange(row)"
            />
            <div v-if="row.subjectCode" class="subject-hint">
              {{ row.subjectCode }} | 方向: {{ row.direction === 'debit' ? '借' : '贷' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="借方金额" width="180">
          <template #default="{ row }">
            <el-input-number
              v-model="row.debit"
              :min="0"
              :precision="2"
              :step="0.01"
              style="width:100%"
              @change="onAmountChange"
            />
          </template>
        </el-table-column>
        <el-table-column label="贷方金额" width="180">
          <template #default="{ row }">
            <el-input-number
              v-model="row.credit"
              :min="0"
              :precision="2"
              :step="0.01"
              style="width:100%"
              @change="onAmountChange"
            />
          </template>
        </el-table-column>
        <el-table-column label="分录摘要" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.summary" placeholder="可选" maxlength="500" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button text type="danger" size="small" @click="removeEntry($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="entry-toolbar">
        <el-button @click="addEntry">添加分录</el-button>
        <el-button @click="autoBalance">自动平衡</el-button>
      </div>

      <div class="balance-summary">
        <span>借方合计: <b class="num">{{ fmtAmount(totalDebit) }}</b></span>
        <span>贷方合计: <b class="num">{{ fmtAmount(totalCredit) }}</b></span>
        <span :class="balanced ? 'balanced' : 'unbalanced'">
          差额: {{ fmtAmount(diff) }} {{ balanced ? '✓ 平衡' : '✗ 不平衡' }}
        </span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getVoucher,
  createVoucher,
  updateVoucher,
  submitVoucher,
  getTemplateByVoucherType,
  type VoucherCreateDTO,
} from '@/api/modules/voucher'
import { getAllVoucherTypes, type VoucherTypeVO } from '@/api/modules/voucherType'
import { getSubjectTree, type SubjectVO } from '@/api/modules/subject'

const route = useRoute()
const router = useRouter()

const mode = (route.query.mode as string) || 'create'
const editId = route.query.id ? Number(route.query.id) : null
const isEdit = mode === 'edit' && editId != null

const saving = ref(false)
const formRef = ref<FormInstance>()
const voucherTypes = ref<VoucherTypeVO[]>([])
const subjectTree = ref<SubjectVO[]>([])

const form = ref<VoucherCreateDTO>({
  period: new Date().toISOString().slice(0, 7).replace('-', ''),
  voucherTypeId: undefined as unknown as number,
  summary: '',
  attachmentIds: '',
  entries: [
    { subjectId: undefined as unknown as number, debit: 0, credit: 0, summary: '' },
    { subjectId: undefined as unknown as number, debit: 0, credit: 0, summary: '' },
  ],
})

const templateApplied = ref(false)
const boundTemplateName = ref<string>('')

const formRules = {
  period: [{ required: true, message: '请输入会计期间', trigger: 'blur' }],
  voucherTypeId: [{ required: true, message: '请选择凭证类型', trigger: 'change' }],
}

const leafSubjectOptions = computed(() => {
  const list: SubjectVO[] = []
  const walk = (nodes: SubjectVO[]) => {
    for (const n of nodes) {
      if (n.isLeaf) list.push(n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(subjectTree.value)
  return list
})

const totalDebit = computed(() => form.value.entries.reduce((s, e) => s + (Number(e.debit) || 0), 0))
const totalCredit = computed(() => form.value.entries.reduce((s, e) => s + (Number(e.credit) || 0), 0))
const diff = computed(() => totalDebit.value - totalCredit.value)
const balanced = computed(() => diff.value === 0 && form.value.entries.length >= 2)

function fmtAmount(v: number) {
  return (v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function addEntry() {
  form.value.entries.push({ subjectId: undefined as unknown as number, debit: 0, credit: 0, summary: '' })
}

function removeEntry(i: number) {
  if (form.value.entries.length <= 2) {
    ElMessage.warning('至少保留2条分录')
    return
  }
  form.value.entries.splice(i, 1)
}

function autoBalance() {
  const last = form.value.entries[form.value.entries.length - 1]
  if (!last) return
  if (totalDebit.value > totalCredit.value) {
    last.credit = +(totalDebit.value - totalCredit.value).toFixed(2)
    last.debit = 0
  } else if (totalCredit.value > totalDebit.value) {
    last.debit = +(totalCredit.value - totalDebit.value).toFixed(2)
    last.credit = 0
  }
  ElMessage.success('已自动平衡')
}

function onSubjectChange(row: any) {
  const subj = leafSubjectOptions.value.find((s) => s.id === row.subjectId)
  if (subj) {
    row.subjectCode = subj.code
    row.subjectName = subj.name
    row.direction = subj.direction
  }
}

function onAmountChange() {
  // 触发 computed 重新计算
}

function goBack() {
  router.push({ name: 'VoucherList' })
}

async function loadVoucherTypes() {
  try {
    voucherTypes.value = await getAllVoucherTypes()
    if (voucherTypes.value.length > 0 && !form.value.voucherTypeId) {
      form.value.voucherTypeId = voucherTypes.value[0].id
    }
  } catch {
    // ignore
  }
}

async function loadSubjectTree() {
  try {
    subjectTree.value = await getSubjectTree()
  } catch {
    // ignore
  }
}

function parseAmount(tpl?: string): number {
  if (!tpl) return 0
  const s = String(tpl).trim()
  if (!s || s === '0') return 0
  const num = parseFloat(s)
  return Number.isFinite(num) ? num : 0
}

function applyTemplate(template: { name: string; lines: any[] }) {
  if (!template?.lines?.length) return
  const lines = [...template.lines].sort((a, b) => (a.lineOrder ?? 0) - (b.lineOrder ?? 0))
  form.value.entries = lines.map((l: any) => ({
    subjectId: l.subjectId,
    debit: parseAmount(l.drAmountTemplate),
    credit: parseAmount(l.crAmountTemplate),
    summary: l.summaryTemplate || '',
    sortOrder: l.lineOrder,
  }))
  for (const e of form.value.entries) onSubjectChange(e)
  boundTemplateName.value = template.name
  templateApplied.value = true
  ElMessage.success(`已应用模板「${template.name}」，共 ${lines.length} 条分录`)
}

async function tryLoadTemplate(typeId?: number) {
  const id = typeId ?? form.value.voucherTypeId
  if (!id) return
  boundTemplateName.value = ''
  templateApplied.value = false
  try {
    const tpl = await getTemplateByVoucherType(id)
    if (tpl?.lines?.length) {
      applyTemplate(tpl)
    }
  } catch {
    // ignore
  }
}

watch(
  () => form.value.voucherTypeId,
  (newId, oldId) => {
    if (newId && newId !== oldId && !isEdit) {
      tryLoadTemplate(newId)
    }
  },
)

async function loadVoucher() {
  if (!editId) return
  const v = await getVoucher(editId)
  form.value = {
    id: v.id,
    period: v.period,
    voucherTypeId: v.voucherTypeId,
    summary: v.summary || '',
    attachmentIds: v.attachmentIds || '',
    entries: (v.entries || []).map((e) => ({
      id: e.id,
      subjectId: e.subjectId,
      debit: e.debit,
      credit: e.credit,
      summary: e.summary || '',
      sortOrder: e.sortOrder,
    })),
  }
  // 回填科目展示信息
  for (const e of form.value.entries) {
    onSubjectChange(e)
  }
}

async function onSave(submitAfter: boolean) {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!balanced.value) {
    ElMessage.error('借贷不平衡, 请检查分录')
    return
  }
  if (form.value.entries.some((e) => !e.subjectId)) {
    ElMessage.error('分录科目未选择')
    return
  }
  saving.value = true
  try {
    const dto: VoucherCreateDTO = {
      ...form.value,
      entries: form.value.entries.map((e, i) => ({
        subjectId: e.subjectId,
        debit: e.debit,
        credit: e.credit,
        summary: e.summary,
        sortOrder: e.sortOrder ?? i + 1,
      })),
    }
    let id: number
    if (isEdit) {
      const v = await updateVoucher(editId!, dto)
      id = v.id
      ElMessage.success('保存成功')
    } else {
      const v = await createVoucher(dto)
      id = v.id
      ElMessage.success('创建成功')
    }
    if (submitAfter) {
      await submitVoucher(id)
      ElMessage.success('已提交')
    }
    router.push({ name: 'VoucherList' })
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadVoucherTypes(), loadSubjectTree()])
  if (isEdit) {
    await loadVoucher()
  } else if (form.value.voucherTypeId) {
    await tryLoadTemplate(form.value.voucherTypeId)
  }
})
</script>

<style scoped>
.voucher-edit .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.entry-toolbar {
  margin: 12px 0;
  display: flex;
  gap: 8px;
}
.balance-summary {
  margin-top: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 4px;
  display: flex;
  gap: 32px;
  font-size: 14px;
  align-items: center;
}
.balance-summary .num {
  font-weight: 600;
  margin-left: 4px;
}
.balance-summary .balanced {
  color: #67c23a;
  font-weight: 600;
}
.balance-summary .unbalanced {
  color: #f56c6c;
  font-weight: 600;
}
.subject-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.template-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #67c23a;
}
</style>
