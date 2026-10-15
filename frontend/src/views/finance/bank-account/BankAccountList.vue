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
        <el-form-item v-if="authStore.isSuperAdmin" label="查看范围">
          <el-radio-group v-model="scope" @change="fetchData">
            <el-radio-button value="current">当前企业</el-radio-button>
            <el-radio-button value="all">全部企业</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <!-- SPEC-P129 §1③：企业下拉（数据源 = 当前用户可见企业） -->
        <el-form-item label="所属企业">
          <el-select
            v-model="selectedEnterpriseId"
            placeholder="选择企业"
            clearable
            style="width:240px"
            :disabled="scope === 'all'"
            @change="fetchData"
          >
            <el-option
              v-for="e in enterpriseOptions"
              :key="e.id"
              :label="e.enterpriseName"
              :value="e.id"
            />
          </el-select>
          <!-- ⚠️ 禁用必须同时回答「为什么灰」与「怎么才能点」（§4.5 第 50 条）。
               隐藏下拉会让用户以为「按企业筛选」不存在；只置灰不给出路同理。 -->
          <el-tooltip
            v-if="scope === 'all'"
            content="「全部企业」视图本身就是跨企业总览，无需再按企业筛选。切回「当前企业」即可按企业收敛。"
            placement="top"
          >
            <span class="ml-2 text-xs text-gray-400">全部企业视图下不可用</span>
          </el-tooltip>
          <el-tooltip
            v-else-if="enterpriseOptions.length <= 1"
            content="当前角色仅可见 1 个企业，无需筛选。切换工作企业即可管理它的银行账号。"
            placement="top"
          >
            <span class="ml-2 text-xs text-gray-400">仅可见 1 个企业</span>
          </el-tooltip>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column
          v-if="scope === 'all'"
          prop="enterpriseName"
          label="所属公司"
          width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.enterpriseName || '—' }}</template>
        </el-table-column>
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
            <el-button text size="small" @click="openEdit(row as BankAccountVO)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="onDelete(row as BankAccountVO)">
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
            :props="({ value: 'id', label: 'name' } as any)"
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
import { getBankAccountPage, allEnterprisesBankAccounts, createBankAccount, updateBankAccount, deleteBankAccount, type BankAccountVO } from '@/api/modules/bankAccount'
import { useAuthStore } from '@/stores/auth.store'
import { getSubjectTree, type SubjectVO } from '@/api/modules/subject'
import { useRoute } from 'vue-router'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const authStore = useAuthStore()
const router = useRoute()

/**
 * 查看范围：'current' 当前企业 / 'all' 全部企业（仅系统管理员）
 *
 * 跨企业视图为**只读总览** —— 新建/修改仍绑定当前企业上下文，
 * 避免开一个客户端可控的跨租户写入口。
 */
const scope = ref<'current' | 'all'>('current')

const list = ref<BankAccountVO[]>([])
const total = ref(0)
const subjectTree = ref<SubjectVO[]>([])

const query = ref({ keyword: '', current: 1, size: 20 })

/**
 * SPEC-P129 §1③：页面级企业选择。
 *
 * <p>⚠️ 它<b>只控制「看哪家企业」</b>，不改变「新建账户归属哪家企业」——
 * 写入仍绑定当前企业上下文（见上方 scope 的说明）。
 * 两者刻意分开：混在一起就会开出客户端可控的跨租户写入口。
 *
 * <p>🔴 可见集由<b>后端</b>计算并下发，前端不自行拼装 ——
 * 前端过滤等于没有过滤（改一下 devtools 就能越权）。
 */
const selectedEnterpriseId = ref<number | null>(null)
const enterpriseOptions = ref<{ id: number; enterpriseName: string }[]>([])

async function loadEnterpriseOptions() {
  try {
    // 复用 store 的企业列表：它本就来自后端按角色收敛的可见集
    const list = (authStore as unknown as { enterpriseList?: { id: number; enterpriseName: string }[] })
      .enterpriseList
    enterpriseOptions.value = Array.isArray(list) ? list : []
  } catch {
    enterpriseOptions.value = []
  }
}

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
    if (scope.value === 'all') {
      // 系统管理员跨企业只读总览：后端逐企业建立上下文后聚合，
      // 写入仍绑定当前企业（刻意不提供「指定企业写入」）
      const rows = await allEnterprisesBankAccounts()
      const kw = (query.value.keyword || '').trim().toLowerCase()
      // ⚠️ 「全部企业」视图<b>不</b>按 enterpriseId 做客户端过滤：VO 不含该字段
      //   （BankAccountVO 明确「租户 id 不外露」，且有守卫 MasterDataVoContractTest 钉住）。
      //   宁可让该视图不做二次收敛，也不为它放宽既有安全守卫。
      list.value = kw
        ? rows.filter((r) =>
            [r.accountNo, r.accountName, r.bankName, r.enterpriseName]
              .some((v) => (v || '').toLowerCase().includes(kw)))
        : rows
      total.value = list.value.length
      return
    }
    const res = await getBankAccountPage({
      ...query.value,
      // SPEC-P129 §1③：按页面企业下拉收敛；未选时不传（后端行为与改动前一致）
      enterpriseId: selectedEnterpriseId.value ?? undefined,
    })
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
  editId.value = row.id as any
  form.value = {
    accountNo: row.accountNo,
    accountName: row.accountName,
    bankName: row.bankName || '',
    currency: row.currency || 'CNY',
    subjectId: row.subjectId as any || (undefined as unknown as number),
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
      await updateBankAccount(editId.value as any, form.value as any)
      ElMessage.success('修改成功')
    } else {
      await createBankAccount({ ...form.value, balance: 0 } as any)
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
  // SPEC-P129 §1③：先取可见企业，才能确定「下拉只有 1 项」还是「可多选」
  await loadEnterpriseOptions()
  // 支持从建档引导带参进入：/finance/bank-account?enterpriseId=123
  const qp = router.query.enterpriseId
  if (qp) {
    const n = Number(qp)
    if (Number.isFinite(n)) selectedEnterpriseId.value = n
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
