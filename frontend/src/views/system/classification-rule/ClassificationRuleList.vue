<template>
  <div class="classification-rule-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">分类规则管理</span>
        <el-space>
          <el-button @click="onSeed">初始化种子规则</el-button>
          <el-button type="primary" @click="openEdit()">新增规则</el-button>
        </el-space>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">
            <el-space>
              <el-button text size="small" :disabled="row.priority <= 1" @click="moveUp(row as ClassificationRule)">▲</el-button>
              <span>{{ row.priority }}</span>
              <el-button text size="small" :disabled="row.priority >= list.length" @click="moveDown(row as ClassificationRule)">▼</el-button>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="规则名称" width="150" />
        <el-table-column label="匹配类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ TYPE_LABELS[row.ruleType] || row.ruleType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pattern" label="匹配模式" min-width="220" show-overflow-tooltip />
        <el-table-column prop="matchField" label="匹配字段" width="110" align="center">
          <template #default="{ row }">
            {{ row.matchField === 'description' ? '摘要' : row.matchField === 'counterparty' ? '对方户名' : row.matchField }}
          </template>
        </el-table-column>
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">
            <span v-if="!row.direction">不限</span>
            <el-tag v-else :type="row.direction === 'in' ? 'success' : 'warning'" size="small">
              {{ row.direction === 'in' ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="CLASSIFICATION_TAG[row.classification] || 'info'" size="small">
              {{ CLASSIFICATION_LABELS[row.classification] || row.classification }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路由" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.routeType" :type="routeTagType(row.routeType)" size="small">
              {{ row.routeType === 'A' ? 'A-直接制证' : row.routeType === 'B' ? 'B-生单后制证' : 'C-待人工' }}
            </el-tag>
            <span v-else class="text-muted">默认</span>
          </template>
        </el-table-column>
        <el-table-column label="科目" width="170" align="center">
          <template #default="{ row }">
            <span v-if="row.subjectLevel1" :title="getRuleSubjectPath(row)" style="font-size:12px">
              <span v-if="row.subjectLevel1">{{ row.subjectLevel1 }}</span>
              <span v-if="row.subjectLevel2">/{{ row.subjectLevel2 }}</span>
              <span v-if="row.subjectLevel3">/{{ row.subjectLevel3 }}</span>
            </span>
            <span v-else class="text-muted">未设置</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isSystem" size="small" type="info">系统兜底</el-tag>
            <span v-else class="text-muted">用户</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.isActive"
              :loading="row._toggling"
              :disabled="row.isSystem"
              @change="(v: any) => toggleActive(row, v)"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.isSystem" text size="small" type="primary" @click="openEdit(row as ClassificationRule)">编辑</el-button>
            <span v-else class="text-muted" style="font-size:12px">内置规则</span>
            <el-popconfirm v-if="!row.isSystem" title="确认删除?" @confirm="onDelete(row as ClassificationRule)">
              <template #reference><el-button text size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑规则' : '新增规则'" width="600px">
      <el-form :model="form" label-width="110px" :rules="rules" ref="formRef">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="form.name" placeholder="如：银行手续费" />
        </el-form-item>
        <el-form-item label="匹配类型" prop="ruleType">
          <el-select v-model="form.ruleType" style="width:100%">
            <el-option label="关键词正则 (| 分隔)" value="keyword_regex" />
            <el-option label="关键词包含" value="keyword" />
            <el-option label="对方户名匹配" value="counterparty_match" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配模式" prop="pattern">
          <el-input v-model="form.pattern" type="textarea" :rows="2"
            :placeholder="form.ruleType === 'counterparty_match' ? '输入对方户名关键词' : '关键词，keyword_regex 用 | 分隔'"
          />
        </el-form-item>
        <el-form-item label="匹配字段" prop="matchField">
          <el-select v-model="form.matchField" style="width:100%">
            <el-option label="摘要" value="description" />
            <el-option label="对方户名" value="counterparty" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="方向" prop="direction">
              <el-select v-model="form.direction" style="width:100%">
                <el-option label="不限" value="" />
                <el-option label="收入(in)" value="in" />
                <el-option label="支出(out)" value="out" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="classification">
              <el-select v-model="form.classification" style="width:100%">
                <el-option v-for="(label, key) in CLASSIFICATION_LABELS" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="借方科目">
          <el-select v-model="form.debitSubjectId" filterable clearable placeholder="选择借方科目" style="width:100%">
            <el-option v-for="s in flatSubjects" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="贷方科目">
          <el-select v-model="form.creditSubjectId" filterable clearable placeholder="选择贷方科目" style="width:100%">
            <el-option v-for="s in flatSubjects" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="路由类型">
          <el-select v-model="form.routeType" clearable placeholder="继承分类默认路由" style="width:100%">
            <el-option label="A-直接制证" value="A" />
            <el-option label="B-生单后制证" value="B" />
            <el-option label="C-待人工" value="C" />
          </el-select>
        </el-form-item>
        <el-divider>三级科目（规则命中时展示）</el-divider>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="一级科目">
              <el-input v-model="form.subjectLevel1" placeholder="如：销售" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="二级科目">
              <el-input v-model="form.subjectLevel2" placeholder="如：主营业务收入" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="三级科目">
              <el-input v-model="form.subjectLevel3" placeholder="如：货款收入" clearable />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  pageRules, createRule, updateRule, deleteRule,
  reorderRules, seedRules, type ClassificationRule,
} from '@/api/modules/classificationRule'
import { getSubjectTree } from '@/api/modules/subject'

type ElTagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

function routeTagType(routeType: string): ElTagType {
  return routeType === 'A' ? 'success' : routeType === 'B' ? 'warning' : 'info'
}
function getRuleSubjectPath(row: { subjectLevel1?: string; subjectLevel2?: string; subjectLevel3?: string }) {
  return [row.subjectLevel1, row.subjectLevel2, row.subjectLevel3]
    .filter(Boolean)
    .join(' / ')
}

const TYPE_LABELS: Record<string, string> = {
  keyword: '关键词包含',
  keyword_regex: '关键词正则',
  counterparty_match: '对方户名匹配',
}

const CLASSIFICATION_LABELS: Record<string, string> = {
  bank_interest_fee: '银行利息与手续费',
  tax_withholding: '税费扣缴',
  salary_social: '薪酬与社保',
  business_receipt: '业务收款',
  business_payment: '业务付款',
  internal_transfer: '内部转账',
  financing_invest: '筹资与投资活动',
  other_unknown: '其它/待认领',
}

const CLASSIFICATION_TAG: Record<string, ElTagType> = {
  bank_interest_fee: 'danger',
  tax_withholding: 'danger',
  salary_social: 'warning',
  business_receipt: 'success',
  business_payment: 'warning',
  internal_transfer: 'info',
  financing_invest: 'primary',
  other_unknown: 'info',
}

// Subject tree → flat list for selector
const subjectTree = ref<any[]>([])
const flatSubjects = computed(() => {
  const flatten = (nodes: any[]): any[] => {
    const result: any[] = []
    for (const n of nodes) {
      result.push(n)
      if (n.children?.length) result.push(...flatten(n.children))
    }
    return result
  }
  return flatten(subjectTree.value)
})

// Table
const query = reactive({ current: 1, size: 20 })
const list = ref<ClassificationRule[]>([])
const total = ref(0)
const loading = ref(false)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageRules(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

// Move priority up/down
const moveUp = async (row: ClassificationRule) => {
  const idx = list.value.indexOf(row)
  if (idx <= 0) return
  const ids = list.value.map(r => r.id!)
  const swap = ids[idx - 1]
  ids[idx - 1] = ids[idx]
  ids[idx] = swap
  await reorderRules(ids)
  ElMessage.success('排序已更新')
  await fetchData()
}

const moveDown = async (row: ClassificationRule) => {
  const idx = list.value.indexOf(row)
  if (idx < 0 || idx >= list.value.length - 1) return
  const ids = list.value.map(r => r.id!)
  const swap = ids[idx + 1]
  ids[idx + 1] = ids[idx]
  ids[idx] = swap
  await reorderRules(ids)
  ElMessage.success('排序已更新')
  await fetchData()
}

// Toggle active
const toggleActive = async (row: any, val: boolean) => {
  row._toggling = true
  try {
    await updateRule(row.id, { ...row, isActive: val })
    ElMessage.success(val ? '已启用' : '已禁用')
  } finally {
    row._toggling = false
  }
}

// Create / Edit dialog
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ClassificationRule>({
  name: '',
  ruleType: 'keyword_regex',
  pattern: '',
  matchField: 'description',
  direction: '',
  classification: 'business_receipt',
  priority: 99,
  isActive: true,
  routeType: undefined,
  debitSubjectId: undefined,
  creditSubjectId: undefined,
  subjectLevel1: undefined,
  subjectLevel2: undefined,
  subjectLevel3: undefined,
})

const rules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  pattern: [{ required: true, message: '请输入匹配模式', trigger: 'blur' }],
  classification: [{ required: true, message: '请选择分类', trigger: 'change' }],
}

const openEdit = (row?: ClassificationRule) => {
  isEdit.value = !!row
  editingId.value = row?.id ?? null
  if (row) {
    Object.assign(form, {
      name: row.name,
      ruleType: row.ruleType,
      pattern: row.pattern,
      matchField: row.matchField,
      direction: row.direction || '',
      classification: row.classification,
      priority: row.priority,
      isActive: row.isActive,
      routeType: row.routeType,
      debitSubjectId: row.debitSubjectId,
      creditSubjectId: row.creditSubjectId,
      subjectLevel1: row.subjectLevel1,
      subjectLevel2: row.subjectLevel2,
      subjectLevel3: row.subjectLevel3,
    })
  } else {
    Object.assign(form, {
      name: '', ruleType: 'keyword_regex', pattern: '',
      matchField: 'description', direction: '', classification: 'business_receipt',
      priority: (list.value.length || 0) + 1, isActive: true,
      routeType: undefined,
      debitSubjectId: undefined, creditSubjectId: undefined,
      subjectLevel1: undefined, subjectLevel2: undefined, subjectLevel3: undefined,
    })
  }
  dialogVisible.value = true
}

const onSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      // direction: empty string is OK for backend, but ensure it's sent as null
      const payload = { ...form }
      if (!payload.direction) payload.direction = ''
      if (isEdit.value && editingId.value != null) {
        await updateRule(editingId.value, payload)
        ElMessage.success('更新成功')
      } else {
        const res = await createRule(payload) as any
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      await fetchData()
    } finally {
      saving.value = false
    }
  })
}

const onDelete = async (row: ClassificationRule) => {
  await deleteRule(row.id!)
  ElMessage.success('已删除')
  await fetchData()
}

// Seed
const onSeed = async () => {
  await seedRules(1)
  ElMessage.success('种子规则已初始化')
  await fetchData()
}

onMounted(async () => {
  subjectTree.value = (await getSubjectTree()) as any[]
  fetchData()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 16px; font-weight: 600; }
.page-pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
