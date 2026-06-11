<template>
  <div class="bank-account-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">银行账户</span>
        <div>
          <el-button type="primary" @click="openCreate">新增账户</el-button>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="账号/名称/开户行" clearable style="width:240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="accountNo" label="账号" width="200" />
        <el-table-column prop="accountName" label="账户名称" min-width="180" />
        <el-table-column prop="bankName" label="开户银行" min-width="180" />
        <el-table-column prop="currency" label="币种" width="80" align="center" />
        <el-table-column label="余额" width="160" align="right">
          <template #default="{ row }">{{ fmtAmount(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="onDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next, jumper"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑账户' : '新增账户'" width="520" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100">
        <el-form-item label="账号" prop="accountNo">
          <el-input v-model="form.accountNo" placeholder="银行账号" />
        </el-form-item>
        <el-form-item label="账户名称" prop="accountName">
          <el-input v-model="form.accountName" placeholder="账户名称" />
        </el-form-item>
        <el-form-item label="开户银行" prop="bankName">
          <el-input v-model="form.bankName" placeholder="开户银行" />
        </el-form-item>
        <el-form-item label="币种" prop="currency">
          <el-input v-model="form.currency" placeholder="默认 CNY" />
        </el-form-item>
        <el-form-item label="对应科目" prop="subjectId">
          <el-tree-select
            v-model="form.subjectId"
            :data="leafSubjectOptions"
            :props="{ value: 'id', label: 'name' }"
            check-strictly
            :render-after-expand="false"
            placeholder="选择末级科目(银行存款)"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="启用" prop="isActive">
          <el-switch v-model="form.isActive" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getBankAccountPage, createBankAccount, updateBankAccount, deleteBankAccount, type BankAccountVO } from '@/api/modules/bankAccount'
import { getSubjectTree, type SubjectVO } from '@/api/modules/subject'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const list = ref<BankAccountVO[]>([])
const total = ref(0)
const subjectTree = ref<SubjectVO[]>([])

const query = ref({ keyword: '', current: 1, size: 20 })
const form = ref({
  accountNo: '',
  accountName: '',
  bankName: '',
  currency: 'CNY',
  subjectId: undefined as unknown as number,
  isActive: true,
  remark: '',
})
const formRules = {
  accountNo: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  accountName: [{ required: true, message: '请输入账户名称', trigger: 'blur' }],
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

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBankAccountPage(query.value)
    list.value = res.records
    total.value = res.total
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.value.current = 1
  fetchData()
}
function onReset() {
  query.value = { keyword: '', current: 1, size: 20 }
  fetchData()
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { accountNo: '', accountName: '', bankName: '', currency: 'CNY', subjectId: undefined as unknown as number, isActive: true, remark: '' }
  dialogVisible.value = true
}
function openEdit(row: BankAccountVO) {
  isEdit.value = true
  editId.value = row.id
  form.value = {
    accountNo: row.accountNo,
    accountName: row.accountName,
    bankName: row.bankName || '',
    currency: row.currency || 'CNY',
    subjectId: row.subjectId || (undefined as unknown as number),
    isActive: row.isActive,
    remark: row.remark || '',
  }
  dialogVisible.value = true
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateBankAccount(editId.value, form.value)
      ElMessage.success('修改成功')
    } else {
      await createBankAccount({ ...form.value, balance: 0 })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await fetchData()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function onDelete(row: BankAccountVO) {
  await deleteBankAccount(row.id)
  ElMessage.success('删除成功')
  await fetchData()
}

onMounted(async () => {
  try {
    subjectTree.value = await getSubjectTree()
  } catch {
    // ignore
  }
  await fetchData()
})
</script>

<style scoped>
.bank-account-list .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
.page-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
