<template>
  <div class="bank-statement">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">银行对账单</span>
        <el-button @click="fetchData">刷新</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="银行账户">
          <el-select v-model="query.accountId" placeholder="选择账户" clearable style="width:240px" @change="onAccountChange">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.accountName} (${a.accountNo})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="确认状态">
          <el-select v-model="query.reviewStatus" placeholder="全部" clearable style="width:140px" @change="onSearch">
            <el-option v-for="(label, value) in REVIEW_STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-radio-group v-model="query.classification" class="classification-tabs" @change="onSearch">
        <el-radio-button :key="'__all__'" :value="''">全部 ({{ totalCount }})</el-radio-button>
        <el-radio-button
          v-for="(label, value) in CLASSIFICATION_LABELS"
          :key="value"
          :value="value">
          {{ label }} ({{ classificationCounts[value] || 0 }})
        </el-radio-button>
      </el-radio-group>

      <el-space style="margin-bottom: 12px">
        <el-button type="primary" @click="openImport">导入对账单</el-button>
        <el-button :disabled="!selectedIds.length" type="success" @click="onBatchConfirm">批量确认并生成</el-button>
        <el-button :disabled="!query.accountId" @click="onAutoClassify">自动分类全部</el-button>
      </el-space>

      <el-table :data="list" v-loading="loading" border stripe @selection-change="onSelectionChange" @row-click="onRowClick" style="cursor:pointer">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="txDate" label="日期" width="110" />
        <el-table-column prop="txType" label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.txType === 'INCOME' ? 'success' : 'warning'" size="small">
              {{ row.txType === 'INCOME' ? '收' : '支' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="counterAccount" label="对方" min-width="140" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="分类" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.classification && row.classification !== 'pending'"
              :type="row.reviewStatus === 'approved' ? 'success' : 'warning'" size="small">
              {{ CLASSIFICATION_LABELS[row.classification] || row.classification }}
            </el-tag>
            <el-tag v-else type="info" size="small">未分类</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="流程状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="reviewStatusTagType(row.reviewStatus)" size="small">
              {{ REVIEW_STATUS_LABELS[row.reviewStatus] || '待确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成结果" width="130" align="center">
          <template #default="{ row }">
            <span v-if="row.generatedVoucherNo" style="color:var(--el-color-success)">
              {{ row.generatedVoucherNo }}
            </span>
            <span v-else-if="row.generatedDocNo" style="color:var(--el-color-primary)">
              {{ row.generatedDocNo }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.classification || row.classification === 'pending'"
              text size="small" type="primary" @click="onClassify(row)">分类</el-button>
            <el-button v-if="canReview(row)"
              text size="small" type="success" @click.stop="onReview(row)">确认</el-button>
            <el-button v-if="row.generatedVoucherId" text size="small" type="primary"
              @click="openVoucher(row.generatedVoucherId!)">查看凭证</el-button>
            <el-button v-if="canApprove(row)"
              text size="small" type="primary" @click="onApprove(row)">核准</el-button>
            <el-popconfirm title="确定删除该条流水?" @confirm="onDelete(row)">
              <template #reference>
                <el-button text size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end"
        @change="fetchData"
      />
    </el-card>

    <!-- 导入对话框: CSV + Excel 双标签 -->
    <el-dialog v-model="importDialogVisible" title="导入对账单" width="780" destroy-on-close>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="CSV导入" name="csv">
          <el-alert type="info" :closable="false" style="margin-bottom:12px">
            格式: 日期,类型(收/支),金额,对方,摘要 (每行一条,首行可为表头)
          </el-alert>
          <el-input v-model="csvContent" type="textarea" :rows="10" placeholder="2026-06-01,收,1000.00,客户A,货款&#10;2026-06-02,支,500.00,供应商B,采购款" />
          <div style="margin-top:16px;text-align:right">
            <el-button @click="importDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="importing" @click="onImportCsv">导入CSV</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="Excel导入" name="excel">
          <el-alert type="info" :closable="false" style="margin-bottom:12px">
            支持 .xlsx 格式。表头在第2行（自动跳过第1行查询信息行），自动识别列名。两步流程：① 上传文件预览 → ② 确认后写入数据库并执行智能分类。
          </el-alert>

          <!-- 步骤1: 上传 -->
          <div v-if="!previewData && !mappingStep">
            <el-upload
              ref="uploadRef" drag
              :auto-upload="false" :show-file-list="true"
              accept=".xlsx,.xls"
              :limit="1"
              @change="onFileChange">
              <el-icon class="el-icon--upload" style="font-size:48px"><upload-filled /></el-icon>
              <div class="el-upload__text">拖放对账单Excel文件到此处或 <em>点击选择</em></div>
              <template #tip><div class="el-upload__tip">支持银行标准对账单格式（交易日期、交易金额、摘要等列）</div></template>
            </el-upload>
            <div style="margin-top:16px;text-align:right">
              <el-button @click="importDialogVisible = false">取消</el-button>
              <el-button type="primary" :loading="mappingLoading" :disabled="!selectedFile" @click="onParseHeaders">
                {{ mappingLoading ? '解析表头...' : '下一步: 列映射' }}
              </el-button>
            </div>
          </div>

          <!-- 步骤1.5: 列映射 -->
          <div v-else-if="mappingStep && !previewData">
            <el-alert type="info" :closable="false" style="margin-bottom:12px">
              请将Excel列名映射到系统字段。标记 <el-tag size="small" type="danger">必填</el-tag> 的字段必须映射。
            </el-alert>

            <el-table :data="systemFields" border size="small" max-height="400">
              <el-table-column label="系统字段" min-width="160">
                <template #default="{ row }">
                  {{ row.label }}
                  <el-tag v-if="row.required" size="small" type="danger" style="margin-left:4px">必填</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="映射Excel列" min-width="280">
                <template #default="{ row }">
                  <el-select v-model="columnMapping[row.field]" placeholder="选择列..." clearable filterable style="width:100%">
                    <el-option v-for="h in excelHeaders" :key="h" :label="h || '(空列)'" :value="h" />
                  </el-select>
                </template>
              </el-table-column>
            </el-table>

            <div style="margin-top:16px;text-align:right">
              <el-button @click="mappingStep = false; previewData = null">重新上传</el-button>
              <el-button @click="importDialogVisible = false">取消</el-button>
              <el-button type="primary" :loading="previewing" @click="onPreviewWithMapping">
                {{ previewing ? '预览中...' : '下一步: 预览' }}
              </el-button>
            </div>
          </div>

          <!-- 步骤2: 预览 -->
          <div v-else-if="previewData">
            <el-descriptions :column="3" border size="small" style="margin-bottom:12px">
              <el-descriptions-item label="总行数">{{ previewData.total }}</el-descriptions-item>
              <el-descriptions-item label="有效行数">{{ previewData.valid }}</el-descriptions-item>
              <el-descriptions-item label="错误行数">
                <el-tag v-if="previewData.errors?.length" type="danger">{{ previewData.errors.length }}</el-tag>
                <el-tag v-else type="success">0</el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <h4 style="margin:12px 0 8px">预览 ({{ previewData.previews?.length || 0 }} 行)</h4>
            <el-table :data="(previewData.previews || []).slice(0, 50)" border size="small" max-height="300"
              :row-class-name="previewRowClass">
              <el-table-column type="index" label="行号" width="50" />
              <el-table-column prop="txDate" label="交易日期" width="100" />
              <el-table-column label="方向" width="60">
                <template #default="{ row }">
                  <el-tag v-if="row.txType" :type="row.txType === 'INCOME' ? 'success' : 'warning'" size="small">
                    {{ row.txType === 'INCOME' ? '收' : '支' }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="counterAccount" label="对方" min-width="140" show-overflow-tooltip />
              <el-table-column prop="summary" label="摘要" min-width="160" show-overflow-tooltip />
              <el-table-column prop="externalNo" label="流水号" width="160" show-overflow-tooltip />
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.isError" type="danger" size="small" :title="row.errorMessage">失败</el-tag>
                  <el-tag v-else-if="row.isDuplicate" type="warning" size="small">重复</el-tag>
                  <el-tag v-else type="success" size="small">有效</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <p v-if="(previewData.previews?.length || 0) > 50" style="text-align:center;color:#909399;margin-top:8px">
              仅显示前 50 行, 共 {{ previewData.previews.length }} 条
            </p>

            <el-collapse v-if="previewData.errors?.length" style="margin-top:12px">
              <el-collapse-item :title="`错误明细 (${previewData.errors.length} 条)`" name="errors">
                <el-table :data="previewData.errors" border size="small">
                  <el-table-column prop="row" label="行号" width="80" />
                  <el-table-column prop="message" label="错误原因" min-width="200" />
                </el-table>
              </el-collapse-item>
            </el-collapse>

            <div style="margin-top:16px;text-align:right">
              <el-button @click="previewData = null">重新上传</el-button>
              <el-button @click="importDialogVisible = false">取消</el-button>
              <el-button type="primary" :loading="confirming" :disabled="!previewData.valid" @click="onConfirmImport">
                {{ confirming ? '导入中...' : `确认导入 ${previewData.valid} 条` }}
              </el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 导入结果摘要 -->
    <el-dialog v-model="importResultVisible" title="导入完成" width="480" :close-on-click-modal="false">
      <el-result v-if="importResult" :icon="importResult.failed === 0 ? 'success' : 'warning'"
        :title="importResult.failed === 0 ? '导入成功' : '部分行导入失败'">
        <template #extra>
          <el-descriptions :column="1" border size="small" style="text-align:left">
            <el-descriptions-item label="共解析">{{ importResult.total }} 条</el-descriptions-item>
            <el-descriptions-item label="成功导入">
              <el-tag type="success">{{ importResult.success }} 条</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="importResult.duplicate > 0" label="重复跳过">
              <el-tag type="warning">{{ importResult.duplicate }} 条</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="importResult.failed > 0" label="失败">
              <el-tag type="danger">{{ importResult.failed }} 条</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="已自动分类">
              <el-tag type="primary">{{ importResult.classified }} 条</el-tag>
              <span style="color:#909399;margin-left:8px">待出纳在工作台确认</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-result>
      <template #footer>
        <el-button type="primary" @click="importResultVisible = false">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量确认结果 -->
    <el-dialog v-model="resultDialogVisible" title="批量确认结果" width="420">
          <el-result v-if="batchConfirmed > 0" icon="success"
            :title="`已确认 ${batchConfirmed} 条`">
          </el-result>
    </el-dialog>

    <!-- 流水详情弹窗 -->
    <el-dialog v-model="detailVisible" title="流水详情" width="600px">
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="交易日期">{{ detailData.txDate }}</el-descriptions-item>
          <el-descriptions-item label="银行账户">{{ bankNameMap[detailData.accountId] || '未知' }}</el-descriptions-item>
          <el-descriptions-item label="方向">
            <el-tag :type="detailData.txType === 'INCOME' ? 'success' : 'warning'" size="small">
              {{ detailData.txType === 'INCOME' ? '收款' : '付款' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="金额">
            <span :style="{ color: detailData.txType === 'INCOME' ? 'var(--el-color-success)' : 'var(--el-color-danger)' }">
              ¥{{ fmtAmount(detailData.amount) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="对方名称" :span="2">{{ detailData.counterAccount || '-' }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detailData.summary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="流水号" :span="2">{{ detailData.externalNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类">
            <el-select v-if="detailEditable" v-model="editClassification" placeholder="选择分类" size="small" style="width:140px">
              <el-option v-for="(label, value) in CLASSIFICATION_LABELS" :key="value" :label="label" :value="value" />
            </el-select>
            <el-tag v-else-if="detailData.classification" :type="detailData.reviewStatus === 'approved' ? 'success' : 'warning'" size="small">
              {{ CLASSIFICATION_LABELS[detailData.classification] || detailData.classification }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="流程状态">
            <el-tag :type="reviewStatusTagType(detailData.reviewStatus)" size="small">
              {{ REVIEW_STATUS_LABELS[detailData.reviewStatus] || detailData.reviewStatus || '待确认' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="导入时间">{{ detailData.importedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生成结果">
            <span v-if="detailData.generatedVoucherNo" style="color:var(--el-color-success)">{{ detailData.generatedVoucherNo }}</span>
            <span v-else-if="detailData.generatedDocNo" style="color:var(--el-color-primary)">{{ detailData.generatedDocNo }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="!detailEditable" text type="primary" @click="startEdit">修改分类</el-button>
        <template v-if="detailEditable">
          <el-button @click="cancelEdit">取消</el-button>
          <el-button type="primary" @click="saveClassification">保存</el-button>
        </template>
        <el-button v-if="!detailEditable && detailData && (!detailData.classification || detailData.classification === 'pending')"
          type="primary" @click="onClassify(detailData); detailVisible = false">自动分类</el-button>
        <el-button v-if="!detailEditable && canReview(detailData)"
          type="success" @click="onReview(detailData); detailVisible = false">确认</el-button>
        <el-button v-if="!detailEditable && canApprove(detailData)"
          type="primary" @click="onApprove(detailData); detailVisible = false">核准</el-button>
        <el-button v-if="!detailEditable && detailData && detailData.generatedVoucherId" type="primary"
          @click="openVoucher(detailData.generatedVoucherId!); detailVisible = false">查看凭证</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  getBankStatementPage, previewStatementExcel, previewStatementExcelWithMapping,
  confirmStatementImport, importStatementCsv, parseExcelHeaders,
  classifyStatement, reviewStatement, approveStatement,
  batchConfirmStatements, deleteStatement, updateStatementClassification,
  getBankStatementDetail, getClassificationCounts,
  CLASSIFICATION_LABELS, REVIEW_STATUS_LABELS,
  type BankStatementVO,
} from '@/api/modules/bankStatement'
import { getActiveBankAccounts, type BankAccountVO } from '@/api/modules/bankAccount'

const loading = ref(false)
const importing = ref(false)
const previewing = ref(false)
const confirming = ref(false)
const list = ref<BankStatementVO[]>([])
const total = ref(0)
const accounts = ref<BankAccountVO[]>([])
const selectedIds = ref<number[]>([])
const selectedFile = ref<File | null>(null)

const importDialogVisible = ref(false)
const activeTab = ref('csv')
const previewData = ref<{
  total: number; valid: number; errors: any[]; batchId: string; previews: any[]
} | null>(null)
const importResultVisible = ref(false)
const importResult = ref<{ total: number; success: number; duplicate: number; failed: number; classified: number; message: string } | null>(null)
const resultDialogVisible = ref(false)
const detailVisible = ref(false)
const detailData = ref<any>(null)
const detailEditable = ref(false)
const editClassification = ref('')
const bankNameMap = ref<Record<number, string>>({})
const batchConfirmed = ref(0)
const csvContent = ref('')

// 列映射状态
const mappingStep = ref(false)
const mappingLoading = ref(false)
const excelHeaders = ref<string[]>([])
const systemFields = ref<Array<{ field: string; label: string; required: boolean }>>([])
const columnMapping = ref<Record<string, string>>({})

// 分类 tab 计数: { classification: count }
const classificationCounts = ref<Record<string, number>>({})
const totalCount = computed(() => Object.values(classificationCounts.value).reduce((a, b) => a + b, 0))

const query = ref<{ accountId?: number; reviewStatus?: string; classification?: string; current: number; size: number }>({
  current: 1, size: 20,
})

type ElTagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

function reviewStatusTagType(status: string): ElTagType {
  if (!status || status === 'PENDING' || status === 'classified') return 'warning'
  if (status === 'voucher_generated' || status === 'payment_created') return 'success'
  if (status === 'approved') return 'primary'
  if (status === 'manual_pending') return 'info'
  return 'warning'
}

function canReview(row: any): boolean {
  const s = row.reviewStatus
  return row.classification && row.classification !== 'pending'
    && (!s || s === 'PENDING' || s === 'classified' || s === 'RECLASSIFIED')
}

function canApprove(row: any): boolean {
  return row.reviewStatus === 'voucher_generated' || row.reviewStatus === 'payment_created'
}

function fmtAmount(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBankStatementPage(query.value as any)
    list.value = (res as any).records || []
    total.value = (res as any).total || 0
  } finally {
    loading.value = false
  }
}

async function fetchClassificationCounts() {
  if (!query.value.accountId) {
    classificationCounts.value = {}
    return
  }
  try {
    const res: any = await getClassificationCounts(query.value.accountId, query.value.reviewStatus)
    classificationCounts.value = res || {}
  } catch {
    classificationCounts.value = {}
  }
}

async function onAccountChange() {
  query.value.classification = ''
  query.value.current = 1
  await Promise.all([fetchData(), fetchClassificationCounts()])
}

async function refreshAll() {
  await Promise.all([fetchData(), fetchClassificationCounts()])
}

function onSearch() { query.value.current = 1; fetchData() }
function onReset() {
  query.value = { current: 1, size: 20 }
  fetchData()
}
function onSelectionChange(rows: BankStatementVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

function openImport() {
  if (!query.value.accountId) {
    ElMessage.warning('请先选择银行账户')
    return
  }
  csvContent.value = ''
  selectedFile.value = null
  previewData.value = null
  mappingStep.value = false
  excelHeaders.value = []
  systemFields.value = []
  columnMapping.value = {}
  activeTab.value = 'csv'
  importDialogVisible.value = true
}

function onFileChange(uploadFile: any) {
  selectedFile.value = uploadFile.raw || null
  previewData.value = null
  mappingStep.value = false
  columnMapping.value = {}
}

async function onParseHeaders() {
  if (!query.value.accountId || !selectedFile.value) {
    ElMessage.warning('请选择账户和文件')
    return
  }
  mappingLoading.value = true
  try {
    const res = await parseExcelHeaders(selectedFile.value)
    excelHeaders.value = res.headers
    systemFields.value = res.fields

    // Auto-map: try to match system field label to Excel headers
    const autoMap: Record<string, string> = {}
    for (const sf of res.fields) {
      const match = res.headers.find(h => h && h.toLowerCase().includes(sf.label.toLowerCase()))
      if (match) autoMap[sf.field] = match
    }
    // Fallback: map TX_DATE to column containing "日期" or "date"
    if (!autoMap['TX_DATE']) {
      const d = res.headers.find(h => h && (h.includes('日期') || h.toLowerCase().includes('date')))
      if (d) autoMap['TX_DATE'] = d
    }
    // Fallback: map AMOUNT to column containing "金额" or "amount"
    if (!autoMap['AMOUNT']) {
      const a = res.headers.find(h => h && (h.includes('金额') || h.toLowerCase().includes('amount')))
      if (a) autoMap['AMOUNT'] = a
    }
    columnMapping.value = autoMap
    mappingStep.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '解析表头失败')
  } finally {
    mappingLoading.value = false
  }
}

async function onPreviewWithMapping() {
  if (!query.value.accountId || !selectedFile.value) return
  const missingRequired = systemFields.value
    .filter(sf => sf.required && !columnMapping.value[sf.field])
    .map(sf => sf.label)
  if (missingRequired.length) {
    ElMessage.warning(`请先映射必填字段: ${missingRequired.join(', ')}`)
    return
  }
  previewing.value = true
  try {
    previewData.value = await previewStatementExcelWithMapping(query.value.accountId, selectedFile.value, { ...columnMapping.value })
    if (previewData.value.total === 0) {
      ElMessage.warning('未解析到有效数据, 请检查列映射是否正确')
    } else {
      ElMessage.success(`解析完成: 共 ${previewData.value.total} 行, 有效 ${previewData.value.valid} 行`)
    }
  } finally {
    previewing.value = false
  }
}

async function onPreviewExcel() {
  if (!query.value.accountId || !selectedFile.value) {
    ElMessage.warning('请选择账户和文件')
    return
  }
  previewing.value = true
  try {
    previewData.value = await previewStatementExcel(query.value.accountId, selectedFile.value)
    if (previewData.value.total === 0) {
      ElMessage.warning('未解析到有效数据, 请检查Excel格式')
    } else {
      ElMessage.success(`解析完成: 共 ${previewData.value.total} 行, 有效 ${previewData.value.valid} 行`)
    }
  } finally {
    previewing.value = false
  }
}

async function onConfirmImport() {
  if (!previewData.value?.batchId) {
    ElMessage.warning('请先预览')
    return
  }
  if (previewData.value.valid === 0) {
    ElMessage.warning('没有有效行可导入')
    return
  }
  confirming.value = true
  try {
    const res = await confirmStatementImport(previewData.value.batchId) as any
    importResult.value = {
      total: res.total || 0,
      success: res.success || 0,
      duplicate: res.duplicate || 0,
      failed: res.failed || 0,
      classified: res.classified || 0,
      message: res.message || '',
    }
    importDialogVisible.value = false
    importResultVisible.value = true
    previewData.value = null
    selectedFile.value = null
    await refreshAll()
  } finally {
    confirming.value = false
  }
}

function previewRowClass({ row }: { row: any }) {
  if (row.isError) return 'preview-row-error'
  if (row.isDuplicate) return 'preview-row-duplicate'
  return ''
}

async function onImportCsv() {
  if (!csvContent.value.trim()) {
    ElMessage.warning('CSV内容不能为空')
    return
  }
  importing.value = true
  try {
    const n = await importStatementCsv(query.value.accountId!, csvContent.value)
    ElMessage.success(`导入 ${n} 条`)
    importDialogVisible.value = false
    await refreshAll()
  } finally {
    importing.value = false
  }
}

async function onClassify(row: BankStatementVO) {
  try {
    await classifyStatement(row.id)
    ElMessage.success('分类完成')
    await refreshAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '分类失败')
  }
}

async function onReview(row: BankStatementVO) {
  try {
    await reviewStatement(row.id)
    ElMessage.success('确认完成')
    await refreshAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '确认失败')
  }
}

async function onApprove(row: BankStatementVO) {
  await approveStatement(row.id)
  ElMessage.success('已核准过账')
  await refreshAll()
}

async function onAutoClassify() {
  if (!query.value.accountId) return
  loading.value = true
  try {
    const res = await getBankStatementPage({ accountId: query.value.accountId, current: 1, size: 9999 } as any)
    const items = (res as any).records || []
    let classified = 0
    for (const item of items) {
      if (!item.classification || item.classification === 'pending') {
        try { await classifyStatement(item.id); classified++ } catch { /* skip */ }
      }
    }
    ElMessage.success(`自动分类 ${classified} 条`)
    await refreshAll()
  } finally {
    loading.value = false
  }
}

async function onBatchConfirm() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先选择流水')
    return
  }
  try {
    batchConfirmed.value = await batchConfirmStatements(selectedIds.value)
    resultDialogVisible.value = true
    await refreshAll()
  } catch { /* handled */ }
}

function openVoucher(id: number) {
  window.open(`/#/finance/voucher/detail?id=${id}`, '_blank')
}

async function onRowClick(row: any, column: any) {
  if (column?.type === 'selection') return
  try {
    detailData.value = await getBankStatementDetail(row.id)
    detailEditable.value = false
    editClassification.value = ''
    detailVisible.value = true
  } catch { /* handled */ }
}

function startEdit() {
  editClassification.value = detailData.value.classification || 'pending'
  detailEditable.value = true
}

function cancelEdit() {
  detailEditable.value = false
  editClassification.value = ''
}

async function saveClassification() {
  if (!detailData.value) return
  try {
    await updateStatementClassification(detailData.value.id, editClassification.value)
    detailData.value.classification = editClassification.value
    detailEditable.value = false
    await refreshAll()
    ElMessage.success('分类已更新')
  } catch { /* handled */ }
}

async function onDelete(row: any) {
  try {
    await deleteStatement(row.id)
    ElMessage.success('已删除')
    await refreshAll()
  } catch { /* handled */ }
}

onMounted(async () => {
  try {
    accounts.value = await getActiveBankAccounts()
    // Build bank name map for detail dialog
    for (const a of accounts.value) {
      bankNameMap.value[a.id] = `${a.accountName} (${a.accountNo})`
    }
    // 只有一个账户时自动选中
    if (accounts.value.length === 1) {
      query.value.accountId = accounts.value[0].id
    }
  } catch { /* ignore */ }
  await Promise.all([fetchData(), fetchClassificationCounts()])
})
</script>

<style scoped>
.bank-statement .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
.filter-form { margin-bottom: 12px; }
.classification-tabs { margin-bottom: 12px; flex-wrap: wrap; row-gap: 4px; }
.classification-tabs :deep(.el-radio-button__inner) { padding: 8px 14px; }
:deep(.preview-row-error) {
  background-color: #fef0f0 !important;
  color: var(--el-color-danger);
}
:deep(.preview-row-error:hover > td) {
  background-color: #fde2e2 !important;
}
:deep(.preview-row-duplicate) {
  background-color: #fdf6ec !important;
}
</style>