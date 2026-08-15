<template>
  <div class="output-invoice">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">销项发票</span>
        <el-space>
          <el-button @click="openImportDialog">导入发票</el-button>
          <el-button type="primary" @click="openEdit()">新增发票</el-button>
          <el-tag v-if="selectedRows.length > 0" type="info" effect="plain">已选 {{ selectedRows.length }} 条</el-tag>
          <el-tooltip
            v-for="ba in BATCH_ACTIONS"
            :key="ba.action"
            :disabled="canBatch(ba.action)"
            :content="batchTooltip(ba.action)"
            placement="top"
          >
            <span>
              <el-button
                :type="ba.type"
                plain
                size="small"
                :disabled="!canBatch(ba.action)"
                @click="onBatchAction(ba.action)"
              >{{ ba.label }}</el-button>
            </span>
          </el-tooltip>
        </el-space>
      </div>

      <!-- 统计栏 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="4">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">总发票数</span>
                <span class="stat-value">{{ fmtNum(stats.totalCount || 0) }}</span>
              </div>
              <div class="stat-icon icon-total">
                <el-icon><Document /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="5">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.1s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">蓝字总金额</span>
                <span class="stat-value">¥ {{ fmtAmount(stats.blueAmount) }}</span>
              </div>
              <div class="stat-icon icon-blue">
                <el-icon><Money /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="5">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.2s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">红字金额</span>
                <span class="stat-value">¥ {{ fmtAmount(stats.redAmount) }}</span>
              </div>
              <div class="stat-icon icon-red">
                <el-icon><Minus /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="3">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.3s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">红字数</span>
                <span class="stat-value">{{ fmtNum(stats.redCount || 0) }}</span>
              </div>
              <div class="stat-icon icon-red-count">
                <el-icon><Warning /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="3">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.4s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">已冲销</span>
                <span class="stat-value">{{ fmtNum(stats.reversedCount || 0) }}</span>
              </div>
              <div class="stat-icon icon-reversed">
                <el-icon><Refresh /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card class="stat-card" shadow="hover" :style="{ animationDelay: '0.5s' }">
            <div class="stat-content">
              <div class="stat-info">
                <span class="stat-label">已作废</span>
                <span class="stat-value">{{ fmtNum(stats.voidedCount || 0) }}</span>
              </div>
              <div class="stat-icon icon-voided">
                <el-icon><Delete /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 分类标签 -->
      <el-radio-group v-model="tabType" style="margin-bottom:12px" @change="onTabChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="SPECIAL">专用发票</el-radio-button>
        <el-radio-button value="PLAIN">普通发票</el-radio-button>
        <el-radio-button value="RED">红字发票</el-radio-button>
      </el-radio-group>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="客户">
          <el-input v-model="query.customerName" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border @selection-change="onSelectionChange" @row-click="showDetail" style="cursor:pointer">
        <el-table-column type="selection" width="50" :selectable="rowSelectable" />
        <el-table-column prop="invoiceNo" label="发票号" width="180" />
        <el-table-column prop="invoiceDate" label="开票日期" width="120" />
        <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="80" align="center">
          <template #default="{ row }">{{ Number(row.taxRate).toFixed(2) }}%</template>
        </el-table-column>
        <el-table-column label="发票类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.amount) < 0 || row.originalInvoiceNo" type="danger" size="small">红字发票</el-tag>
            <span v-else>{{ row.invoiceType === 'SPECIAL' ? '专用发票' : row.invoiceType === 'PLAIN' ? '普通发票' : row.invoiceType }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG_MAP[row.status] || 'info') as any" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="AI 标记" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.aiRiskTag" :type="row.aiRiskTag.includes('CRITICAL') ? 'danger' : row.aiRiskTag.includes('MEDIUM') ? 'warning' : 'info'" size="small">
              {{ row.aiRiskTag }}
            </el-tag>
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

    <el-dialog v-model="dialogVisible" title="新增销项发票" width="640px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="发票号" prop="invoiceNo"><el-input v-model="form.invoiceNo" /></el-form-item>
        <el-form-item label="开票日期" prop="invoiceDate">
          <el-date-picker v-model="form.invoiceDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="客户" prop="customerName"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item label="金额(不含税)" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" @change="recalcTax" />
        </el-form-item>
        <el-form-item label="税率" prop="taxRate">
          <el-select v-model="form.taxRate" style="width:100%" @change="recalcTax">
            <el-option :value="13" label="13%" />
            <el-option :value="9" label="9%" />
            <el-option :value="6" label="6%" />
            <el-option :value="0" label="0%" />
          </el-select>
        </el-form-item>
        <el-form-item label="税额">
          <el-input-number v-model="form.taxAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="发票类型" prop="invoiceType">
          <el-select v-model="form.invoiceType" style="width:100%">
            <el-option label="增值税专用发票" value="SPECIAL" />
            <el-option label="普通发票" value="PLAIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入发票 -->
    <el-dialog v-model="importVisible" title="导入销售发票" width="720px" destroy-on-close>
      <template v-if="!importPreview">
        <el-alert type="info" :closable="false" style="margin-bottom:12px">
          上传销售发票 Excel，系统自动识别列名、匹配客户、生成应收单据。
        </el-alert>
        <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" @change="onImportFileChange" ref="importUploadRef">
          <el-icon class="el-icon--upload" style="font-size:48px"><upload-filled /></el-icon>
          <div class="el-upload__text">拖放文件到此处或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">支持销项发票格式（发票号码、购方识别号、购买方名称、开票日期、金额、税额、价税合计）</div>
          </template>
        </el-upload>
        <div style="margin-top:12px;text-align:right">
          <el-button @click="importVisible = false">取消</el-button>
          <el-button type="primary" :loading="importPreviewing" :disabled="!importFile" @click="onImportPreview">下一步: 预览</el-button>
        </div>
      </template>

      <template v-else>
        <el-descriptions :column="4" border size="small" style="margin-bottom:12px">
          <el-descriptions-item label="总行数">{{ fmtNum(importPreview.total) }}</el-descriptions-item>
          <el-descriptions-item label="有效行数">{{ fmtNum(importPreview.valid) }}</el-descriptions-item>
          <el-descriptions-item label="已有">
            <el-tag v-if="importPreview.existing" type="warning">{{ fmtNum(importPreview.existing) }}</el-tag>
            <el-tag v-else type="success">0</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="错误">
            <el-tag v-if="importPreview.errors?.length" type="danger">{{ fmtNum(importPreview.errors.length) }}</el-tag>
            <el-tag v-else type="success">0</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="(importPreview.previews || []).slice(0, 50)" border size="small" max-height="300">
          <el-table-column type="index" label="#" width="40" />
          <el-table-column prop="invoiceNo" label="发票号" width="160" />
          <el-table-column label="状态" width="60" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.existing" type="warning" size="small">已有</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="buyerName" label="购方" min-width="140" show-overflow-tooltip />
          <el-table-column prop="invoiceDate" label="日期" width="90" />
          <el-table-column prop="goodsName" label="商品" width="120" show-overflow-tooltip />
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="税额" width="90" align="right">
            <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
          </el-table-column>
          <el-table-column label="价税合计" width="100" align="right">
            <template #default="{ row }">{{ fmtAmount(row.totalAmount) }}</template>
          </el-table-column>
        </el-table>
        <div style="margin-top:12px;text-align:right">
          <el-button @click="importPreview = null; importFile = null">重新上传</el-button>
          <el-button @click="importVisible = false">取消</el-button>
          <el-button type="primary" :loading="importConfirming"
            :disabled="!importPreview.valid || (importPreview.valid - (importPreview.existing || 0)) <= 0"
            @click="onImportConfirm">
            确认导入 {{ fmtNum(Math.max(0, importPreview.valid - (importPreview.existing || 0))) }} 条
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 发票详情 -->
    <el-dialog v-model="detailVisible" title="发票详情" width="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="发票号" :span="2">{{ detail.invoiceNo }}</el-descriptions-item>
          <el-descriptions-item label="开票日期">{{ detail.invoiceDate }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ detail.period }}</el-descriptions-item>
          <el-descriptions-item label="客户名称" :span="2">{{ detail.customerName }}</el-descriptions-item>
          <el-descriptions-item label="金额(不含税)">{{ fmtAmount(detail.amount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ fmtAmount(detail.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="价税合计">{{ fmtAmount(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税率">{{ Number(detail.taxRate || 0).toFixed(2) }}%</el-descriptions-item>
          <el-descriptions-item label="发票类型">{{ detail.invoiceType === 'SPECIAL' ? '增值税专用发票' : '普通发票' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="(STATUS_TAG_MAP[detail.status!] || 'info') as any" size="small">
              {{ STATUS_MAP[detail.status!] || detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="业务流程" :span="2">
            <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
              <!-- 销售发票 -->
              <el-tag type="primary" effect="dark" size="small">销售发票</el-tag>
              <span style="font-family:monospace">{{ detail.invoiceNo }}</span>
              
              <template v-if="detail.docNo || detail.receivableNo || detail.voucherNo">
                <el-icon color="#409EFF"><ArrowRight /></el-icon>
                
                <!-- 业务单据 -->
                <template v-if="detail.docNo">
                  <el-tag type="success" size="small">业务单</el-tag>
                  <span style="font-family:monospace">{{ detail.docNo }}</span>
                </template>
                
                <!-- 应收单据 -->
                <template v-if="detail.receivableNo">
                  <el-icon color="#409EFF"><ArrowRight /></el-icon>
                  <el-tag type="warning" size="small">应收单</el-tag>
                  <span style="font-family:monospace">{{ detail.receivableNo }}</span>
                </template>
                
                <!-- 记账凭证 -->
                <template v-if="detail.voucherNo">
                  <el-icon color="#409EFF"><ArrowRight /></el-icon>
                  <el-tag type="info" size="small">凭证</el-tag>
                  <span style="font-family:monospace">{{ detail.voucherNo }}</span>
                </template>
              </template>
            </div>
          </el-descriptions-item>
          <template v-if="detail.originalInvoiceNo">
            <el-descriptions-item label="红冲发票">
              <el-tag type="danger" size="small">红字发票</el-tag>
              <span style="margin-left:8px">冲销蓝字发票: <a href="#" @click.prevent="jumpToOriginalInvoice(detail)" style="color:#409EFF">{{ detail.originalInvoiceNo }}</a></span>
            </el-descriptions-item>
          </template>
          <template v-if="detail.reversedByInvoiceId && detail.reversedByInvoiceNo">
            <el-descriptions-item label="被红冲">
              <el-tag type="warning" size="small">已冲销</el-tag>
              <span style="margin-left:8px">被红字发票冲销: <a href="#" @click.prevent="jumpToRedInvoice(detail)" style="color:#F56C6C">{{ detail.reversedByInvoiceNo }}</a></span>
            </el-descriptions-item>
          </template>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-space>
          <template v-if="detail?.status === 'PENDING_CONFIRM'">
            <el-button type="primary" size="small" @click="doAction(detail, 'submitReview')">提交审核</el-button>
            <el-button type="danger" size="small" @click="doAction(detail, 'void')">作废</el-button>
          </template>
          <template v-else-if="detail?.status === 'PENDING_REVIEW'">
            <el-button type="primary" size="small" @click="doAction(detail, 'confirm')">审核通过</el-button>
            <el-button type="warning" size="small" @click="doAction(detail, 'reject')">驳回</el-button>
            <el-button type="danger" size="small" @click="doAction(detail, 'void')">作废</el-button>
          </template>
          <template v-else-if="detail?.status === 'CONFIRMED'">
            <el-button type="primary" size="small" @click="doAction(detail, 'markVouchered')">生成凭证</el-button>
            <el-button type="primary" size="small" @click="onAiRecommend(detail)">AI 推荐科目</el-button>
            <el-button type="warning" size="small" @click="doAction(detail, 'revert')">回退到待审核</el-button>
            <el-button type="danger" size="small" @click="doAction(detail, 'void')">作废</el-button>
          </template>
          <el-button type="danger" @click="onDelete(detail)" :loading="deleting">删除</el-button>
          <el-button @click="detailVisible = false">关闭</el-button>
        </el-space>
      </template>
    </el-dialog>

    <el-dialog v-model="batchResultVisible" title="批量操作结果" width="640px">
      <el-alert
        v-if="batchResult"
        :type="(batchResult.failure?.length || 0) > 0 ? 'warning' : 'success'"
        :closable="false"
        style="margin-bottom:12px"
      >
        成功 {{ batchResult.success?.length || 0 }} 条，失败 {{ batchResult.failure?.length || 0 }} 条
      </el-alert>
      <el-table v-if="(batchResult?.failure?.length || 0) > 0" :data="batchResult?.failure || []" border size="small">
        <el-table-column prop="id" label="发票ID" width="120" />
        <el-table-column prop="reason" label="失败原因" />
      </el-table>
      <template #footer>
        <el-button @click="batchResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { UploadFilled, Document, Money, Minus, Warning, Refresh, Delete, ArrowRight } from '@element-plus/icons-vue'
import type { OutputInvoice, BatchResult } from '@/api/modules/tax'
import { pageOutputInvoice, createOutputInvoice, getOutputInvoice, deleteOutputInvoice,
  outputInvoiceSummary,
  submitForReview, confirmOutputInvoice, rejectOutputInvoice, revertOutputInvoice, voidOutputInvoice, markVouchered,
  batchSubmitForReview, batchConfirmOutputInvoice, batchRejectOutputInvoice, batchRevertOutputInvoice,
  batchMarkVouchered, batchVoidOutputInvoice, batchReverseOutputInvoice } from '@/api/modules/tax'
import { previewSalesInvoices, confirmSalesInvoicesImport } from '@/api/modules/salesInvoice'

const detailVisible = ref(false)
const detail = ref<OutputInvoice | null>(null)
const deleting = ref(false)

// 分类标签
const tabType = ref('')

const onTabChange = () => {
  query.current = 1
  fetchData()
}

const showDetail = async (row: any) => {
  try {
    detail.value = await getOutputInvoice(row.id)
    detailVisible.value = true
  } catch {
    detail.value = row
    detailVisible.value = true
  }
}

const onDelete = async (row: any) => {
  if (!row?.id) return
  const deleteOutputInvoice = (await import('@/api/modules/tax')).deleteOutputInvoice
  deleting.value = true
  try {
    await deleteOutputInvoice(row.id)
    ElMessage.success('删除成功')
    detailVisible.value = false
    fetchData(); fetchStats()
  } finally { deleting.value = false }
}

const jumpToOriginalInvoice = async (invoice: any) => {
  if (!invoice.originalInvoiceId) return
  try {
    detail.value = await getOutputInvoice(invoice.originalInvoiceId)
  } catch {
    ElMessage.warning('未找到被冲销的蓝字发票')
  }
}

const jumpToRedInvoice = async (invoice: any) => {
  if (!invoice.reversedByInvoiceId) return
  try {
    detail.value = await getOutputInvoice(invoice.reversedByInvoiceId)
  } catch {
    ElMessage.warning('未找到红字发票')
  }
}

const doAction = async (row: any, action: string) => {
  const id = row?.id
  if (!id) return
  const label = ({ submitReview: '提交审核', confirm: '审核通过', reject: '驳回', revert: '回退', void: '作废', markVouchered: '生成凭证' } as any)[action] || action

  if (action === 'reject' || action === 'void') {
    const { value: reason } = await (await import('element-plus')).ElMessageBox.prompt(
      `请输入${label}原因`, label, { inputType: 'textarea', inputValidator: (v: string) => !!v?.trim(), inputErrorMessage: '原因不能为空' }
    ).catch(() => ({ value: null }))
    if (!reason) return
    try {
      if (action === 'reject') await rejectOutputInvoice(id, reason)
      else await voidOutputInvoice(id, reason)
      ElMessage.success(`${label}成功`)
      detailVisible.value = false
      fetchData(); fetchStats()
    } catch { /* backend handles error msg */ }
    return
  }

  try {
    if (action === 'submitReview') await submitForReview(id)
    else if (action === 'confirm') await confirmOutputInvoice(id)
    else if (action === 'revert') await revertOutputInvoice(id)
    else if (action === 'markVouchered') await markVouchered(id)
    ElMessage.success(`${label}成功`)
    detailVisible.value = false
    fetchData(); fetchStats()
  } catch { /* backend handles error msg */ }
}

const onAiRecommend = async (row: any) => {
  const summary = row.goodsName || row.remark || ''
  if (!summary) { ElMessage.warning('缺少摘要信息，无法推荐'); return }
  try {
    const { aiSubjectMapping } = await import('@/api/modules/tax')
    const res: any = await aiSubjectMapping(summary, Number(row.amount), row.customerName)
    if (res?.result?.best) {
      ElMessage.success(`AI 推荐科目: ${res.result.best.account_name} (${res.result.best.account_code})`)
    } else {
      ElMessage.info('AI 未找到匹配科目，请手工选择')
    }
  } catch { ElMessage.error('AI 推荐失败') }
}

const STATUS_MAP: Record<string, string> = {
  PENDING_CONFIRM: '待确认', PENDING_REVIEW: '待审核', CONFIRMED: '已确认',
  VOUCHERED: '已生成凭证', FULLY_RECONCILED: '已核销', PARTIALLY_RECONCILED: '部分核销',
  VOIDED: '已作废', REVERSED: '已冲销',
}
const STATUS_TAG_MAP: Record<string, string> = {
  PENDING_CONFIRM: 'warning', PENDING_REVIEW: 'info', CONFIRMED: 'success',
  VOUCHERED: '', FULLY_RECONCILED: 'success', PARTIALLY_RECONCILED: '',
  VOIDED: 'danger', REVERSED: 'danger',
}

const query = reactive({ customerName: '', period: '', invoiceType: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<any>({})
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ invoiceType: 'SPECIAL', taxRate: 13 })
const rules = {
  invoiceNo: [{ required: true, message: '请输入发票号', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
  customerName: [{ required: true, message: '请输入客户', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  taxRate: [{ required: true, message: '请选择税率', trigger: 'change' }],
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
}

/** 金额格式：千分位 + 2 位小数 */
const fmtAmount = (v: any) => {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 数字格式：千分位，无小数 */
const fmtNum = (v: any) => {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN')
}

const recalcTax = () => {
  if (form.amount && form.taxRate != null) {
    form.taxAmount = Number((form.amount * form.taxRate / 100).toFixed(2))
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params: any = { current: query.current, size: query.size }
    if (query.customerName) params.customerName = query.customerName
    if (query.period) params.period = query.period
    if (tabType.value) params.invoiceType = tabType.value
    const res: any = await pageOutputInvoice(params)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const fetchStats = async () => {
  try { stats.value = await outputInvoiceSummary() } catch {}
}

const openEdit = () => {
  Object.assign(form, {
    id: undefined, invoiceNo: '', invoiceDate: '', customerName: '',
    amount: 0, taxRate: 13, taxAmount: 0, invoiceType: 'SPECIAL', remark: '',
  })
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await createOutputInvoice(form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchData(); fetchStats()
  })
}

onMounted(() => { fetchData(); fetchStats() })

// P56 销项发票批量操作
const selectedRows = ref<any[]>([])
const batchResultVisible = ref(false)
const batchResult = ref<BatchResult | null>(null)

const BATCH_ACTIONS: { action: string; label: string; type: 'primary' | 'warning' | 'danger' }[] = [
  { action: 'submitReview', label: '批量提交审核', type: 'primary' },
  { action: 'confirm', label: '批量审核通过', type: 'primary' },
  { action: 'reject', label: '批量驳回', type: 'warning' },
  { action: 'revert', label: '批量回退', type: 'warning' },
  { action: 'markVouchered', label: '批量生成凭证', type: 'primary' },
  { action: 'void', label: '批量作废', type: 'danger' },
  { action: 'reverse', label: '批量红冲', type: 'danger' },
]

const TERMINAL_STATUSES = ['VOIDED', 'REVERSED', 'FULLY_RECONCILED']
const rowSelectable = (row: any) => !TERMINAL_STATUSES.includes(row.status)

const BATCH_AVAILABLE_BY_STATUS: Record<string, string[]> = {
  PENDING_CONFIRM: ['submitReview', 'void'],
  PENDING_REVIEW: ['confirm', 'reject', 'void'],
  CONFIRMED: ['markVouchered', 'revert', 'void'],
  VOUCHERED: ['reverse'],
  PARTIALLY_RECONCILED: ['reverse'],
  FULLY_RECONCILED: [],
  VOIDED: [],
  REVERSED: [],
}

const canBatch = (action: string) => {
  if (selectedRows.value.length === 0) return false
  return selectedRows.value.every(r => BATCH_AVAILABLE_BY_STATUS[r.status]?.includes(action))
}

const batchTooltip = (action: string) => {
  if (selectedRows.value.length === 0) return '请先勾选发票'
  const cfg = BATCH_ACTIONS.find(b => b.action === action)
  return `当前选中发票不支持「${cfg?.label || action}」`
}

const onSelectionChange = (rows: any[]) => {
  selectedRows.value = rows
}

const onBatchAction = async (action: string) => {
  const ids = selectedRows.value.map(r => r.id).filter(Boolean)
  if (ids.length === 0) { ElMessage.warning('请先选择发票'); return }
  if (ids.length > 100) { ElMessage.warning('单次最多批量操作 100 张'); return }

  let reason = ''
  if (['reject', 'void', 'reverse'].includes(action)) {
    const labelMap: any = { reject: '批量驳回', void: '批量作废', reverse: '批量红冲' }
    const { value } = await (await import('element-plus')).ElMessageBox.prompt(
      `请输入${labelMap[action]}原因（将应用于所有选中发票）`,
      labelMap[action],
      { inputType: 'textarea', inputValidator: (v: string) => !!v?.trim(), inputErrorMessage: '原因不能为空' }
    ).catch(() => ({ value: null }))
    if (!value) return
    reason = value
  }

  if (action === 'reverse' || action === 'void') {
    const label = action === 'reverse' ? '批量红冲' : '批量作废'
    try {
      await (await import('element-plus')).ElMessageBox.confirm(
        `确认对 ${ids.length} 张发票执行【${label}】？此操作不可撤销。`,
        label,
        { type: 'warning' }
      )
    } catch { return }
  }

  const fnMap: Record<string, () => Promise<BatchResult>> = {
    submitReview: () => batchSubmitForReview(ids),
    confirm: () => batchConfirmOutputInvoice(ids),
    reject: () => batchRejectOutputInvoice(ids, reason),
    revert: () => batchRevertOutputInvoice(ids),
    markVouchered: () => batchMarkVouchered(ids),
    void: () => batchVoidOutputInvoice(ids, reason),
    reverse: () => batchReverseOutputInvoice(ids, reason),
  }

  try {
    batchResult.value = await fnMap[action]()
    batchResultVisible.value = true
    const succ = batchResult.value.success.length
    const fail = batchResult.value.failure.length
    if (fail === 0) {
      ElMessage.success(`批量操作完成：成功 ${succ} 条`)
    } else {
      ElMessage.warning(`批量操作完成：成功 ${succ} 条，失败 ${fail} 条（详见弹窗）`)
    }
    selectedRows.value = []
    fetchData(); fetchStats()
  } catch {
    // backend error handled
  }
}

// ====== 发票导入 ======
const importVisible = ref(false)
const importPreviewing = ref(false)
const importConfirming = ref(false)
const importFile = ref<File | null>(null)
const importPreview = ref<any>(null)
const importUploadRef = ref<any>(null)

const openImportDialog = () => {
  importFile.value = null
  importPreview.value = null
  importVisible.value = true
}

const onImportFileChange = (f: any) => {
  importFile.value = f.raw || null
  importPreview.value = null
}

const onImportPreview = async () => {
  if (!importFile.value) { ElMessage.warning('请选择文件'); return }
  importPreviewing.value = true
  try {
    importPreview.value = await previewSalesInvoices(importFile.value)
    if (importPreview.value.total === 0) {
      ElMessage.warning('未解析到有效发票行')
    } else {
      ElMessage.success(`解析完成: ${importPreview.value.total} 行, 有效 ${importPreview.value.valid} 行`)
    }
  } finally { importPreviewing.value = false }
}

const onImportConfirm = async () => {
  if (!importPreview.value?.batchId) { ElMessage.warning('请先预览'); return }
  importConfirming.value = true
  try {
    const res = await confirmSalesInvoicesImport(importPreview.value.batchId)
    let msg = `导入 ${fmtNum(res.success)} 张发票`
    if ((res as any).duplicateSkipped) msg += `，${fmtNum((res as any).duplicateSkipped)} 张重复已跳过`
    ElMessage.success(msg)
    importVisible.value = false
    importPreview.value = null
    importFile.value = null
    fetchData(); fetchStats()
  } finally { importConfirming.value = false }
}
</script>

<style scoped>
.output-invoice {
  padding: 0;
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
  color: #303133;
}

.page-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.filter-form {
  margin-bottom: 16px;
}

/* 统计卡片样式 */
.stat-card {
  margin-bottom: 0;
  border-radius: 8px;
  opacity: 0;
  transform: translateY(20px);
  animation: statCardIn 0.5s ease forwards;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1) !important;
}

@keyframes statCardIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 统计图标：彩色渐变背景 + 白图标 */
.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}
.stat-icon .el-icon {
  font-size: 28px;
  color: #fff;
}
.stat-card:hover .stat-icon {
  transform: scale(1.08) rotate(-5deg);
}

/* 数字：与logo同色渐变 + 大字号 + 现代风格 */
.stat-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  font-weight: 500;
  letter-spacing: 0.3px;
}
.stat-value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.5px;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  transition: all 0.3s ease;
}

/* 总发票数 - 蓝色渐变文字 */
.icon-total ~ * .stat-value,
.stat-card:has(.icon-total) .stat-value {
  background-image: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}
/* 蓝字总金额 - 绿色 */
.stat-card:has(.icon-blue) .stat-value {
  background-image: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}
/* 红字金额 - 粉黄 */
.stat-card:has(.icon-red) .stat-value {
  background-image: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
}
/* 红字数 - 橙粉 */
.stat-card:has(.icon-red-count) .stat-value {
  background-image: linear-gradient(135deg, #ff9a44 0%, #fc6076 100%);
}
/* 已冲销 - 紫粉 */
.stat-card:has(.icon-reversed) .stat-value {
  background-image: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%);
}
/* 已作废 - 灰蓝 */
.stat-card:has(.icon-voided) .stat-value {
  background-image: linear-gradient(135deg, #8e9eab 0%, #5a6a7e 100%);
}

/* 总发票数 - 蓝色 */
.icon-total {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}
/* 蓝字总金额 - 绿色 */
.icon-blue {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}
/* 红字金额 - 红色 */
.icon-red {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
}
/* 红字数 - 橙色 */
.icon-red-count {
  background: linear-gradient(135deg, #ff9a44 0%, #fc6076 100%);
}
/* 已冲销 - 紫色 */
.icon-reversed {
  background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%);
}
/* 已作废 - 灰色 */
.icon-voided {
  background: linear-gradient(135deg, #8e9eab 0%, #eef2f3 100%);
}
.icon-voided .el-icon {
  color: #5a6a7e;
}
</style>
