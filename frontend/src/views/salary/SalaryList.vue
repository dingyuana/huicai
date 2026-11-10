<template>
  <div class="salary-list">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>工资单（个税累计预扣，确认生成计提凭证，发放生成发放凭证）</span>
          <div>
            <el-button type="primary" @click="openCreate">新建工资单</el-button>
            <el-button @click="openItemConfig">薪资项目配置</el-button>
            <el-button @click="openSocialConfig">社保与分摊配置</el-button>
            <el-button circle @click="loadBills">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="bills" v-loading="loading" border>
        <el-table-column prop="period" label="期间" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusCn(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalGross" label="应发合计" align="right" :formatter="money" />
        <el-table-column prop="totalCompanySocial" label="社保(单位)" align="right" :formatter="money" />
        <el-table-column prop="totalSocial" label="社保(个人)" align="right" :formatter="money" />
        <el-table-column prop="totalSpecialAdd" label="专项附加" align="right" :formatter="money" />
        <el-table-column prop="totalOtherDeduct" label="其他扣款" align="right" :formatter="money" />
        <el-table-column prop="totalTax" label="个税" align="right" :formatter="money" />
        <el-table-column prop="totalNet" label="实发合计" align="right" :formatter="money" />
        <el-table-column label="凭证" min-width="150">
          <template #default="{ row }">
            <div v-if="row.voucherNo">计提: {{ row.voucherNo }}</div>
            <div v-if="row.payVoucherNo">发放: {{ row.payVoucherNo }}</div>
            <div v-if="row.socialVoucherNo">缴纳: {{ row.socialVoucherNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="warning" @click="onConfirm(row)">确认计提</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" link type="success" @click="onPay(row)">发放</el-button>
            <el-button
              v-if="(row.status === 'CONFIRMED' || row.status === 'PAID') && !row.socialPaid"
              link type="success" @click="onPaySocial(row)">缴社保</el-button>
            <el-button link type="info" @click="onExportPayroll(row)">工资条</el-button>
            <el-button link type="info" @click="onExportTax(row)">个税表</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建 / 编辑工资单 -->
    <el-dialog v-model="billDialogVisible" :title="editingBillId ? `编辑工资单 ${form.period}` : '新建工资单'" width="720px">
      <el-form label-width="80px">
        <el-form-item label="期间">
          <el-input v-model="form.period" placeholder="yyyyMM，如 202601" style="width: 160px"
                    :disabled="editingBillId !== null" maxlength="6" />
          <span class="hint">同期间仅一张工资单；编辑仅限草稿，保存为整单替换</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
      </el-form>

      <el-table :data="form.rows" border size="small">
        <el-table-column label="员工" min-width="160">
          <template #default="{ row }">
            <el-select v-model="row.employeeId" filterable placeholder="选择员工" style="width: 100%">
              <el-option v-for="e in employees" :key="e.id" :value="e.id!" :label="`${e.name}（${e.code}）`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="薪资项目" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.itemId" filterable placeholder="选择项目" style="width: 100%">
              <el-option v-for="it in items" :key="it.id" :value="it.id!"
                         :label="`${it.itemName}（${categoryCn(it.category)}${it.isTaxable ? '/计税' : ''}）`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" :min="0" :precision="2" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="" width="60">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="hint" style="margin-top: 8px">
        个税在「确认」时按累计预扣法计算：员工级快照减除费用按任职月份抵 5000，确认后不可再改。
      </div>
      <template #footer>
        <el-button @click="addRow">加一行</el-button>
        <el-button @click="billDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveBill">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情（员工级快照） -->
    <el-dialog v-model="detailVisible" :title="`工资单详情 ${detail?.period ?? ''}`" width="860px">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="状态">{{ statusCn(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="计提凭证">{{ detail.voucherNo ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="发放凭证">{{ detail.payVoucherNo ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="应发合计">{{ detail.totalGross }}</el-descriptions-item>
          <el-descriptions-item label="个税合计">{{ detail.totalTax }}</el-descriptions-item>
          <el-descriptions-item label="实发合计">{{ detail.totalNet }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.employees" border size="small">
          <el-table-column prop="employeeName" label="员工" min-width="100" />
          <el-table-column prop="gross" label="应发" align="right" :formatter="money" />
          <el-table-column prop="taxableGross" label="计税应发" align="right" :formatter="money" />
          <el-table-column prop="social" label="社保(个人)" align="right" :formatter="money" />
          <el-table-column prop="specialAdd" label="专项附加" align="right" :formatter="money" />
          <el-table-column prop="otherDeduct" label="其他扣款" align="right" :formatter="money" />
          <el-table-column prop="tax" label="个税" align="right" :formatter="money" />
          <el-table-column prop="net" label="实发" align="right" :formatter="money" />
        </el-table>
        <div class="hint" v-if="detail.employees.length === 0">草稿状态尚无快照，确认后生成员工级个税明细。</div>
      </template>
    </el-dialog>

    <!-- 薪资项目配置 -->
    <el-dialog v-model="itemDialogVisible" title="薪资项目配置" width="760px">
      <el-table :data="items" border size="small">
        <el-table-column prop="itemCode" label="编码" width="120" />
        <el-table-column prop="itemName" label="名称" min-width="140" />
        <el-table-column label="类别" width="150">
          <template #default="{ row }">{{ categoryCn(row.category) }}</template>
        </el-table-column>
        <el-table-column label="计税" width="70" align="center">
          <template #default="{ row }">{{ row.isTaxable ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="defaultAmount" label="默认金额" align="right" :formatter="money" />
        <el-table-column label="" width="70">
          <template #default="{ row }">
            <el-button link type="danger" @click="onDeleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider />
      <el-form :inline="true">
        <el-form-item label="编码"><el-input v-model="itemForm.itemCode" style="width: 110px" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="itemForm.itemName" style="width: 130px" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="itemForm.category" style="width: 150px">
            <el-option v-for="c in CATEGORIES" :key="c.value" :value="c.value" :label="c.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="计税">
          <el-switch v-model="itemForm.isTaxable" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingItem" @click="onSaveItem">新增项目</el-button>
        </el-form-item>
      </el-form>
      <div class="hint">EARN=收入（参与计税看「计税」开关）；SOCIAL_INS=个人社保公积金（减税不减实发）；SPECIAL_ADD=专项附加扣除（只进个税公式）；OTHER_DEDUCT=其他扣款（直接扣实发）；COMPANY_SOCIAL=单位社保（按比例自动算，锁定只读）。</div>
    </el-dialog>

    <!-- SPEC-P155：社保比例 + 部门分摊配置（双 tab） -->
    <el-dialog v-model="socialConfigVisible" title="社保与分摊配置" width="780px">
      <el-tabs>
        <el-tab-pane label="单位社保比例">
          <el-table :data="socialRates" border size="small">
            <el-table-column prop="itemName" label="险种项目" min-width="140" />
            <el-table-column label="单位比例" width="110" align="right">
              <template #default="{ row }">{{ (row.companyRate * 100).toFixed(2) }}%</template>
            </el-table-column>
            <el-table-column prop="baseFloor" label="基数下限" width="100" align="right" />
            <el-table-column prop="baseCap" label="基数上限" width="100" align="right" />
            <el-table-column label="基数口径" width="150">
              <template #default="{ row }">{{ row.baseMode === 'EMP_DECL' ? '社保核定基数' : '当期应发' }}</template>
            </el-table-column>
            <el-table-column label="" width="70">
              <template #default="{ row }">
                <el-button link type="danger" @click="onDeleteSocialRate(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="hint" v-if="socialRates.length === 0">
            先在「薪资项目配置」新建类别为 COMPANY_SOCIAL 的项目（如 养老保险(单位)），再为其配比例。
          </div>
          <el-divider />
          <el-form :inline="true">
            <el-form-item label="项目">
              <el-select v-model="socialForm.itemId" filterable style="width: 180px">
                <el-option v-for="it in companySocialItems" :key="it.id" :value="it.id!" :label="it.itemName" />
              </el-select>
            </el-form-item>
            <el-form-item label="比例%">
              <el-input-number v-model="socialForm.ratePercent" :min="0.01" :max="100" :precision="2" :controls="false" style="width: 100px" />
            </el-form-item>
            <el-form-item label="基数下限">
              <el-input-number v-model="socialForm.baseFloor" :min="0" :precision="2" :controls="false" style="width: 110px" />
            </el-form-item>
            <el-form-item label="基数上限">
              <el-input-number v-model="socialForm.baseCap" :min="0" :precision="2" :controls="false" style="width: 110px" />
            </el-form-item>
            <el-form-item label="基数口径">
              <el-select v-model="socialForm.baseMode" style="width: 150px">
                <el-option value="GROSS" label="当期应发" />
                <el-option value="EMP_DECL" label="社保核定基数" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingSocial" @click="onSaveSocialRate"
                         :disabled="companySocialItems.length === 0">保存比例</el-button>
            </el-form-item>
          </el-form>
          <div class="hint">保存工资单时自动算：基数 × 比例，明细锁定只读。基数口径二选一——「当期应发」=该员工当期应发合计（clamp 上下限，历史口径）；「社保核定基数」=该员工社保局年度核定值（t_employee 核定基数，员工未核定保存工资单会拒绝并点名），上下限对两种口径通用。</div>
        </el-tab-pane>

        <el-tab-pane label="部门费用科目分摊">
          <el-table :data="deptMaps" border size="small">
            <el-table-column prop="deptName" label="部门" min-width="120" />
            <el-table-column label="费用科目" min-width="160">
              <template #default="{ row }">{{ row.subjectCode }} {{ row.subjectName }}</template>
            </el-table-column>
            <el-table-column label="" width="70">
              <template #default="{ row }">
                <el-button link type="danger" @click="onDeleteDeptMap(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="hint" v-if="deptMaps.length === 0">无映射时全部员工费用计入 6602 管理费用；配置后按员工部门拆分（如销售部→6601 销售费用）。</div>
          <el-divider />
          <el-form :inline="true">
            <el-form-item label="部门">
              <el-select v-model="mapForm.deptId" filterable style="width: 150px">
                <el-option v-for="d in depts" :key="d.id" :value="d.id" :label="d.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="费用科目">
              <el-select v-model="mapForm.subjectId" filterable style="width: 200px">
                <el-option v-for="s in expenseSubjects" :key="s.id" :value="s.id" :label="`${s.code} ${s.name}`" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingMap" @click="onSaveDeptMap">保存映射</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  type SalaryBill,
  type SalaryBillDetail,
  type SalaryItem,
  type SocialRate,
  type DeptSubjectMap,
  listSalaryBills,
  getSalaryBill,
  getSalaryBillItems,
  saveSalaryBill,
  confirmSalaryBill,
  paySalaryBill,
  paySocialSalaryBill,
  exportPayroll,
  exportTaxDeclaration,
  listSalaryItems,
  createSalaryItem,
  deleteSalaryItem,
  listSocialRates,
  saveSocialRate,
  deleteSocialRate,
  listDeptSubjectMaps,
  saveDeptSubjectMap,
  deleteDeptSubjectMap,
  listDepts,
} from '@/api/modules/salary'
import { getSubjectTree, type SubjectVO } from '@/api/modules/subject'
import { listEmployee, type Employee } from '@/api/modules/employee'

const CATEGORIES = [
  { value: 'EARN', label: '收入' },
  { value: 'SOCIAL_INS', label: '个人社保公积金' },
  { value: 'SPECIAL_ADD', label: '专项附加扣除' },
  { value: 'OTHER_DEDUCT', label: '其他扣款' },
]

const bills = ref<SalaryBill[]>([])
const items = ref<SalaryItem[]>([])
const employees = ref<Employee[]>([])
const loading = ref(false)
const saving = ref(false)
const savingItem = ref(false)

const billDialogVisible = ref(false)
const detailVisible = ref(false)
const itemDialogVisible = ref(false)

const editingBillId = ref<number | null>(null)
const form = reactive({
  period: '',
  remark: '',
  rows: [] as Array<{ employeeId: number | null; itemId: number | null; amount: number | null }>,
})
const detail = ref<SalaryBillDetail | null>(null)
const itemForm = reactive<SalaryItem>({
  itemCode: '',
  itemName: '',
  category: 'EARN',
  isTaxable: true,
})

function statusCn(s?: string): string {
  return s === 'DRAFT' ? '草稿' : s === 'CONFIRMED' ? '已确认' : s === 'PAID' ? '已发放' : (s ?? '—')
}
function statusTag(s?: string): 'warning' | 'primary' | 'success' | 'info' {
  return s === 'DRAFT' ? 'warning' : s === 'CONFIRMED' ? 'primary' : s === 'PAID' ? 'success' : 'info'
}
function categoryCn(c: any): string {
  return CATEGORIES.find((x) => x.value === c)?.label ?? String(c)
}
function money(row: any, _col: any, v: any): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadBills(): Promise<void> {
  loading.value = true
  try {
    bills.value = await listSalaryBills()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '工资单列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadItems(): Promise<void> {
  items.value = await listSalaryItems()
}

function currentPeriod(): string {
  const now = new Date()
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`
}

function openCreate(): void {
  editingBillId.value = null
  form.period = currentPeriod()
  form.remark = ''
  form.rows = []
  billDialogVisible.value = true
}

async function openEdit(row: any): Promise<void> {
  editingBillId.value = row.id ?? null
  form.period = row.period
  form.remark = row.remark ?? ''
  form.rows = []
  try {
    const rows = await getSalaryBillItems(row.id!)
    form.rows = rows.map((r) => ({ employeeId: r.employeeId, itemId: r.itemId, amount: r.amount ?? 0 }))
  } catch (e: any) {
    ElMessage.error(e?.message ?? '明细加载失败')
  }
  billDialogVisible.value = true
}

function addRow(): void {
  form.rows.push({ employeeId: null, itemId: null, amount: null })
}

function removeRow(idx: number): void {
  form.rows.splice(idx, 1)
}

async function onSaveBill(): Promise<void> {
  if (!/^\d{6}$/.test(form.period)) {
    ElMessage.warning('期间格式必须为 yyyyMM')
    return
  }
  if (form.rows.length === 0) {
    ElMessage.warning('请至少录入一行明细')
    return
  }
  for (const r of form.rows) {
    if (!r.employeeId || !r.itemId || r.amount == null) {
      ElMessage.warning('明细行必须选齐员工、项目并填写金额')
      return
    }
  }
  saving.value = true
  try {
    await saveSalaryBill({
      period: form.period,
      remark: form.remark,
      items: form.rows.map((r) => ({
        employeeId: r.employeeId!,
        itemId: r.itemId!,
        amount: r.amount!,
      })),
    })
    ElMessage.success('工资单已保存')
    billDialogVisible.value = false
    await loadBills()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(row: any): Promise<void> {
  try {
    detail.value = await getSalaryBill(row.id!)
    detailVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message ?? '详情加载失败')
  }
}

async function onConfirm(row: any): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认 ${row.period} 工资单？将按累计预扣法计算个税并生成计提凭证（SALC-${row.id}），确认后不可再改。`,
      '确认计提',
    )
  } catch {
    return
  }
  try {
    await confirmSalaryBill(row.id!)
    ElMessage.success('已确认（计提凭证已生成）')
    await loadBills()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '确认失败')
  }
}

async function onPay(row: any): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `发放 ${row.period} 工资单？将生成发放凭证（SALP-${row.id}）：借应付职工薪酬 / 贷个税+社保个人+银行存款。`,
      '工资发放',
    )
  } catch {
    return
  }
  try {
    await paySalaryBill(row.id!)
    ElMessage.success('已发放（发放凭证已生成）')
    await loadBills()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '发放失败')
  }
}

// ─── SPEC-P155 R1：缴纳社保 ───

async function onPaySocial(row: any): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `缴纳 ${row.period} 社保公积金？将生成缴纳凭证（SALJ-${row.id}）：借应付职工薪酬(单位) + 借其他应付款(个人代扣) / 贷银行存款。仅可缴纳一次。`,
      '缴纳社保',
    )
  } catch {
    return
  }
  try {
    await paySocialSalaryBill(row.id!)
    ElMessage.success('社保已缴纳（缴纳凭证已生成）')
    await loadBills()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '缴纳失败')
  }
}

// ─── SPEC-P155：社保比例 + 部门分摊配置 ───

const socialConfigVisible = ref(false)
const socialRates = ref<SocialRate[]>([])
const deptMaps = ref<DeptSubjectMap[]>([])
const depts = ref<Array<{ id: number; name: string }>>([])
const savingSocial = ref(false)
const savingMap = ref(false)
const socialForm = reactive<{ itemId?: number; ratePercent?: number; baseFloor?: number; baseCap?: number; baseMode?: 'GROSS' | 'EMP_DECL' }>({})
const mapForm = reactive<{ deptId?: number; subjectId?: number }>({})

const companySocialItems = computed(() => items.value.filter((it) => it.category === 'COMPANY_SOCIAL'))
const subjects = ref<SubjectVO[]>([])
const expenseSubjects = computed(() =>
  subjects.value.filter((s) => s.code.startsWith('66') || s.code.startsWith('5')),
)

async function openSocialConfig(): Promise<void> {
  socialConfigVisible.value = true
  try {
    ;[socialRates.value, deptMaps.value, depts.value] = await Promise.all([
      listSocialRates(),
      listDeptSubjectMaps(),
      listDepts(),
    ])
  } catch (e: any) {
    ElMessage.error(e?.message ?? '配置加载失败')
  }
  loadItems()
  loadSubjects()
}

async function onSaveSocialRate(): Promise<void> {
  if (!socialForm.itemId || !socialForm.ratePercent) {
    ElMessage.warning('选择项目并填写比例')
    return
  }
  savingSocial.value = true
  try {
    await saveSocialRate({
      itemId: socialForm.itemId,
      companyRate: socialForm.ratePercent / 100,
      baseFloor: socialForm.baseFloor || undefined,
      baseCap: socialForm.baseCap || undefined,
      baseMode: socialForm.baseMode || 'GROSS',
    })
    ElMessage.success('比例已保存')
    socialRates.value = await listSocialRates()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    savingSocial.value = false
  }
}

async function onDeleteSocialRate(row: any): Promise<void> {
  try {
    await deleteSocialRate(row.id!)
    socialRates.value = await listSocialRates()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

async function onSaveDeptMap(): Promise<void> {
  if (!mapForm.deptId || !mapForm.subjectId) {
    ElMessage.warning('选择部门与费用科目')
    return
  }
  savingMap.value = true
  try {
    await saveDeptSubjectMap({ deptId: mapForm.deptId, subjectId: mapForm.subjectId })
    ElMessage.success('映射已保存')
    deptMaps.value = await listDeptSubjectMaps()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    savingMap.value = false
  }
}

async function onDeleteDeptMap(row: any): Promise<void> {
  try {
    await deleteDeptSubjectMap(row.id!)
    deptMaps.value = await listDeptSubjectMaps()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

async function loadSubjects(): Promise<void> {
  if (subjects.value.length) return
  try {
    const tree = await getSubjectTree()
    // 树拍平（费用科目可能挂一级科目下）
    const flat: SubjectVO[] = []
    const walk = (nodes: SubjectVO[]) => {
      for (const n of nodes) {
        flat.push(n)
        if (n.children?.length) walk(n.children)
      }
    }
    walk(tree)
    subjects.value = flat
  } catch {
    subjects.value = []
  }
}

// ─── SPEC-P155 R3：导出 ───

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  URL.revokeObjectURL(url)
}

async function onExportPayroll(row: any): Promise<void> {
  try {
    const blob = await exportPayroll(row.id!)
    downloadBlob(blob, `工资条_${row.period}.xlsx`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '导出失败')
  }
}

async function onExportTax(row: any): Promise<void> {
  try {
    const blob = await exportTaxDeclaration(row.id!)
    downloadBlob(blob, `个税申报表_${row.period}.xlsx`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '导出失败')
  }
}

async function openItemConfig(): Promise<void> {
  itemDialogVisible.value = true
  await loadItems()
}

async function onSaveItem(): Promise<void> {
  if (!itemForm.itemCode || !itemForm.itemName) {
    ElMessage.warning('项目编码与名称不能为空')
    return
  }
  savingItem.value = true
  try {
    await createSalaryItem({ ...itemForm })
    ElMessage.success('项目已新增')
    itemForm.itemCode = ''
    itemForm.itemName = ''
    await loadItems()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '新增失败')
  } finally {
    savingItem.value = false
  }
}

async function onDeleteItem(row: any): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除薪资项目「${row.itemName}」？（软删，已录入的历史明细不受影响）`, '删除项目')
  } catch {
    return
  }
  try {
    await deleteSalaryItem(row.id!)
    ElMessage.success('已删除')
    await loadItems()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

onMounted(async () => {
  await loadBills()
  try {
    const [its, emps] = await Promise.all([listSalaryItems(), listEmployee()])
    items.value = its
    employees.value = emps
  } catch {
    // 下拉数据失败不阻断列表展示
  }
})
</script>

<style scoped>
.salary-list { padding: 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.hint { color: #909399; font-size: 12px; }
</style>
