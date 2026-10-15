<template>
  <div class="enterprise-list-page">
    <h2>客户企业列表</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总客户数" :value="stats.total" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="活跃客户" :value="stats.active" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="本月到期" :value="stats.expiring" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="待审核凭证" :value="stats.pendingVouchers" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchName" placeholder="企业名称" clearable style="width: 200px" />
      <el-input v-model="searchTaxId" placeholder="纳税人识别号" clearable style="width: 200px; margin-left: 12px" />
      <el-button type="primary" @click="fetchList" style="margin-left: 12px">搜索</el-button>
      <el-button type="success" @click="openCreateDialog" style="margin-left: 12px">新增企业</el-button>
      <el-button @click="router.push('/agency/batch-operation')" style="margin-left: auto">
        批量操作
      </el-button>
    </div>

    <!-- 客户表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="enterpriseName" label="企业名称" min-width="160" />
      <el-table-column prop="taxId" label="纳税人识别号" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(row as EnterpriseVO).status === 'ACTIVE' ? 'success' : (row as EnterpriseVO).status === 'PENDING' ? 'warning' : 'info'">
            {{ statusLabel((row as EnterpriseVO).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="初始化" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as EnterpriseVO).seedDataDone ? 'success' : 'warning'" size="small">
            {{ (row as EnterpriseVO).seedDataDone ? '已完成' : '未完成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="enterEnterprise(row as EnterpriseVO)">
            进入
          </el-button>
          <el-button size="small" @click="openEditDialog(row as EnterpriseVO)">
            编辑
          </el-button>
          <el-button
            v-if="(row as EnterpriseVO).status === 'PENDING'"
            size="small"
            type="success"
            @click="handleActivate(row as EnterpriseVO)"
          >
            激活
          </el-button>
          <el-button
            v-if="(row as EnterpriseVO).status === 'ACTIVE'"
            size="small"
            type="warning"
            @click="handleSuspend(row as EnterpriseVO)"
          >
            暂停
          </el-button>
          <el-button
            size="small"
            type="danger"
            @click="handleDelete(row as EnterpriseVO)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="fetchList"
      style="margin-top: 16px; justify-content: flex-end"
    />

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增企业' : '编辑企业'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="企业编码" prop="enterpriseCode">
          <el-input v-model="form.enterpriseCode" :disabled="dialogMode === 'edit'" placeholder="企业编码" />
        </el-form-item>
        <el-form-item label="企业名称" prop="enterpriseName">
          <el-input v-model="form.enterpriseName" placeholder="企业名称" />
        </el-form-item>
        <el-form-item label="纳税人识别号" prop="taxId">
          <el-input v-model="form.taxId" placeholder="纳税人识别号" />
        </el-form-item>
        <!-- SPEC-P129 §1①：联系信息，全部可选（§7 排除项：不因格式或缺失阻断建档） -->
        <el-divider content-position="left">联系信息（选填）</el-divider>
        <el-alert
          v-if="dialogMode === 'edit' && contactMasked"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          title="你看到的是脱敏后的联系方式"
          description="留空表示不修改。如需改为新的号码或邮箱，直接覆盖填写即可。"
        />
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" placeholder="联系人" />
        </el-form-item>
        <el-form-item label="电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="手机或含区号固话" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱" prop="contactEmail">
          <el-input v-model="form.contactEmail" placeholder="邮箱" maxlength="128" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" placeholder="地址" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- SPEC-P129 §1②：建档后就绪度面板 -->
    <el-dialog
      v-model="readinessVisible"
      :title="`企业「${readiness.enterpriseName}」创建成功`"
      width="520px"
    >
      <el-alert
        type="success"
        :closable="false"
        show-icon
        title="基本信息已完成"
        description="编码、名称、纳税人识别号已建档。下面两项主数据会影响后续业务操作。"
      />
      <el-descriptions :column="1" border style="margin-top: 16px">
        <el-descriptions-item label="基本信息">
          <span style="color: #67c23a">✅ 已完成</span>
        </el-descriptions-item>
        <el-descriptions-item label="银行账号">
          <span :style="{ color: readiness.bankAccount ? '#67c23a' : '#e6a23c' }">
            {{ readiness.bankAccount ? '✅ 已配置' : '⚠️ 未配置' }}
          </span>
          <span v-if="!readiness.bankAccount" style="margin-left: 8px; color: #909399; font-size: 12px">
            影响收款单、付款单、银行对账与自动制证
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="凭证类型">
          <span :style="{ color: readiness.voucherType ? '#67c23a' : '#e6a23c' }">
            {{ readiness.voucherType ? '✅ 已配置' : '⚠️ 未配置' }}
          </span>
          <span v-if="!readiness.voucherType" style="margin-left: 8px; color: #909399; font-size: 12px">
            影响凭证生成；可在【基础资料 → 凭证类型】配置
          </span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="readinessVisible = false">稍后再说</el-button>
        <el-button
          v-if="!readiness.bankAccount"
          type="primary"
          :disabled="readiness.enterpriseId == null"
          @click="goConfigureBankAccount"
        >
          去配置银行账号
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { getEnterpriseList, createEnterprise, updateEnterprise, deleteEnterprise, activateEnterprise, suspendEnterprise, type EnterpriseVO, type EnterpriseCreateDTO } from '@/api/modules/agency'
import { hasBankAccount, hasVoucherType } from '@/api/modules/readiness'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const searchName = ref('')
const searchTaxId = ref('')
const tableData = ref<EnterpriseVO[]>([])

// 对话框状态
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const editingId = ref<number | null>(null)
/** 编辑态下当前看到的是否是脱敏串（SPEC-P129 D-1） */
const contactMasked = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<EnterpriseCreateDTO>({
  enterpriseCode: '',
  enterpriseName: '',
  taxId: '',
  // SPEC-P129：联系信息全部可选
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  address: '',
})

const formRules: FormRules = {
  enterpriseCode: [{ required: true, message: '请输入企业编码', trigger: 'blur' }],
  enterpriseName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  // ⚠️ 联系信息一律不设 required（SPEC-P129 §7 排除项）：
  // 建档是高频轻操作，强制填全是门槛倒退；格式问题也不阻断建档。
}

const stats = reactive({
  total: 0,
  active: 0,
  expiring: 0,
  pendingVouchers: 0,
})

function statusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待激活', ACTIVE: '已激活', SUSPENDED: '已暂停', TERMINATED: '已终止' }
  return map[status] || status
}

async function fetchList() {
  loading.value = true
  try {
    // SPEC-P120：不再自填 agencyId，可见范围由后端按角色收敛
    const res = await getEnterpriseList(page.value, size.value)
    tableData.value = res.records
    total.value = res.total
    stats.total = res.total
    stats.active = res.records.filter(r => r.status === 'ACTIVE').length
  } finally {
    loading.value = false
  }
}

async function enterEnterprise(enterprise: EnterpriseVO) {
  try {
    await authStore.switchEnterprise(enterprise.id)
    ElMessage.success(`已进入「${enterprise.enterpriseName}」`)
    router.push('/dashboard')
  } catch {
    ElMessage.error('进入企业失败')
  }
}

async function handleActivate(enterprise: EnterpriseVO) {
  await ElMessageBox.confirm(`确认激活企业「${enterprise.enterpriseName}」？`)
  try {
    await activateEnterprise(enterprise.id)
    ElMessage.success('激活成功')
    fetchList()
  } catch {
    ElMessage.error('激活失败')
  }
}

async function handleSuspend(enterprise: EnterpriseVO) {
  await ElMessageBox.confirm(`确认暂停企业「${enterprise.enterpriseName}」？`)
  try {
    await suspendEnterprise(enterprise.id)
    ElMessage.success('已暂停')
    fetchList()
  } catch {
    ElMessage.error('暂停失败')
  }
}

// ===== 新增/编辑 =====

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  form.enterpriseCode = ''
  form.enterpriseName = ''
  form.taxId = ''
  formRef.value?.resetFields()
  dialogVisible.value = true
}

function openEditDialog(enterprise: EnterpriseVO) {
  dialogMode.value = 'edit'
  editingId.value = enterprise.id
  form.enterpriseCode = enterprise.enterpriseCode
  form.enterpriseName = enterprise.enterpriseName
  form.taxId = enterprise.taxId || ''
  // SPEC-P129：回填联系信息。非管理员看到的是**脱敏值**（后端 D-1），
  // 因此编辑框标注「留空表示不修改」，避免把掩码串回写进库。
  form.contactName = enterprise.contactName || ''
  form.contactPhone = enterprise.contactPhone || ''
  form.contactEmail = enterprise.contactEmail || ''
  form.address = enterprise.address || ''
  contactMasked.value = isMasked(enterprise.contactPhone) || isMasked(enterprise.contactEmail)
  dialogVisible.value = true
}

/** 是否是脱敏串（含 **** 或 *** 片段） */
function isMasked(v?: string | null) {
  return !!v && (v.includes('****') || v.includes('***'))
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload: EnterpriseCreateDTO = {
      enterpriseCode: form.enterpriseCode,
      enterpriseName: form.enterpriseName,
      taxId: form.taxId || undefined,
      agencyId: authStore.agencyId ?? undefined,
      // SPEC-P129：空串转 undefined，让后端落 NULL 而非空串
      // （空串会与「已填但为空」混淆，见 §1①）
      contactName: form.contactName || undefined,
      contactPhone: form.contactPhone || undefined,
      contactEmail: form.contactEmail || undefined,
      address: form.address || undefined,
    }

    if (dialogMode.value === 'create') {
      const created = await createEnterprise(payload)
      // SPEC-P129 §1②：创建成功不静默返回列表，而是给「就绪度 + 下一步」面板。
      // 判据（§4.5 第 50 条）：必须同时回答「为什么现在要配」与「点了会发生什么」。
      // 只弹一句「创建成功」再让人自己找入口，等于把「找不到」换成「不知道为什么要找」。
      dialogVisible.value = false
      await showReadiness(created?.id ?? null, created?.enterpriseName ?? form.enterpriseName)
    } else {
      await updateEnterprise(editingId.value!, payload)
      ElMessage.success('保存成功')
      dialogVisible.value = false
    }
    fetchList()
  } catch {
    ElMessage.error(dialogMode.value === 'create' ? '创建失败' : '保存失败')
  } finally {
    submitting.value = false
  }
}

// ===== SPEC-P129 §1②：建档后就绪度面板 =====

const readinessVisible = ref(false)
const readiness = reactive({
  enterpriseId: null as number | null,
  enterpriseName: '',
  bankAccount: false,
  voucherType: false,
  checking: false,
})

/**
 * 展示「企业 X 创建成功」+ 就绪度清单 + 可点击的下一步。
 *
 * <p>⚠️ 就绪度是**派生事实**（由「有没有」计算），SPEC-P129 §3 明确禁止把它
 * 持久化成列或状态 —— 一旦固化就会与实际配置漂移（这正是 P128 里
 * `enterprise_id=0` 死数据的成因）。
 */
async function showReadiness(enterpriseId: number | null, enterpriseName: string) {
  readiness.enterpriseId = enterpriseId
  readiness.enterpriseName = enterpriseName
  readiness.bankAccount = false
  readiness.voucherType = false
  readiness.checking = true
  readinessVisible.value = true
  try {
    if (enterpriseId != null) {
      // 并行探测两项主数据；任一失败都按「未配置」呈现，不让面板卡死
      const [bank, voucherType] = await Promise.all([
        hasBankAccount(enterpriseId).catch(() => false),
        hasVoucherType(enterpriseId).catch(() => false),
      ])
      readiness.bankAccount = bank
      readiness.voucherType = voucherType
    }
  } finally {
    readiness.checking = false
  }
}

function goConfigureBankAccount() {
  if (readiness.enterpriseId == null) return
  readinessVisible.value = false
  router.push({ path: '/finance/bank-account', query: { enterpriseId: String(readiness.enterpriseId) } })
}

// ===== 删除 =====

async function handleDelete(enterprise: EnterpriseVO) {
  await ElMessageBox.confirm(
    `确认删除企业「${enterprise.enterpriseName}」？此操作不可恢复。`,
    '删除确认',
    { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
  )
  try {
    await deleteEnterprise(enterprise.id)
    ElMessage.success('已删除')
    fetchList()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.enterprise-list-page {
  padding: 16px;

  h2 { margin: 0 0 16px; font-size: 18px; }

  .stats-row { margin-bottom: 16px; }

  .search-bar {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
  }
}
</style>
