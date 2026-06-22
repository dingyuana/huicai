<template>
  <div class="output-invoice">
    <!-- 背景效果 -->
    <div class="bg-grid"></div>
    <div class="bg-glow glow-1"></div>
    <div class="bg-glow glow-2"></div>

    <!-- 页面标题区 -->
    <div class="page-header">
      <div class="header-left">
        <div class="title-wrapper">
          <span class="title-line"></span>
          <h1 class="page-title">销项发票管理</h1>
          <span class="title-line"></span>
        </div>
        <p class="page-subtitle">Sales Invoice Management System</p>
      </div>
      <div class="header-actions">
        <div class="action-btn secondary" @click="openImportDialog">
          <svg class="icon" viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
          <span>导入发票</span>
        </div>
        <div class="action-btn primary" @click="openEdit()">
          <svg class="icon" viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
          <span>新增发票</span>
        </div>
      </div>
    </div>

    <!-- 统计卡片区 -->
    <div class="stats-grid">
      <div class="stat-card" v-for="(stat, index) in statCards" :key="index" :style="{ '--delay': index * 0.1 + 's' }">
        <div class="stat-icon" :class="stat.type">
          <component :is="stat.icon" />
        </div>
        <div class="stat-content">
          <span class="stat-label">{{ stat.label }}</span>
          <span class="stat-value">{{ stat.value }}</span>
        </div>
        <div class="stat-border"></div>
      </div>
    </div>

    <!-- 筛选区 -->
    <div class="filter-section">
      <div class="filter-card">
        <div class="filter-item">
          <label class="filter-label">客户名称</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
            <input v-model="query.customerName" placeholder="输入客户名称" class="filter-input" />
          </div>
        </div>
        <div class="filter-item">
          <label class="filter-label">会计期间</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24"><path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM9 10H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm-8 4H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2z"/></svg>
            <input v-model="query.period" placeholder="YYYYMM" class="filter-input" />
          </div>
        </div>
        <div class="filter-actions">
          <button class="btn-query" @click="fetchData">
            <svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
            <span>查询</span>
          </button>
          <button class="btn-reset" @click="resetQuery">重置</button>
        </div>
      </div>
    </div>

    <!-- 数据表格区 -->
    <div class="table-section">
      <div class="table-header">
        <div class="table-info">
          <span class="data-count">共 <strong>{{ total }}</strong> 条记录</span>
        </div>
        <div class="table-actions">
          <button class="btn-red-flush" @click="onBatchLinkRedFlush" :disabled="linkingRedFlush">
            <svg viewBox="0 0 24 24"><path d="M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z"/></svg>
            批量红冲关联
          </button>
        </div>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th class="th-checkbox"><input type="checkbox" /></th>
              <th>发票号</th>
              <th>开票日期</th>
              <th>客户名称</th>
              <th class="text-right">金额</th>
              <th class="text-right">税额</th>
              <th class="text-center">税率</th>
              <th class="text-center">发票类型</th>
              <th class="text-center">状态</th>
              <th class="text-center">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in list" :key="row.id" :style="{ '--row-index': index }">
              <td class="td-checkbox"><input type="checkbox" /></td>
              <td class="td-invoice-no">
                <span class="invoice-tag">{{ row.invoiceNo }}</span>
              </td>
              <td>{{ row.invoiceDate }}</td>
              <td class="td-customer">{{ row.customerName }}</td>
              <td class="text-right td-amount">{{ formatAmount(row.amount) }}</td>
              <td class="text-right td-tax">{{ formatAmount(row.taxAmount) }}</td>
              <td class="text-center">{{ Number(row.taxRate || 0).toFixed(2) }}%</td>
              <td class="text-center">
                <span class="type-badge" :class="row.invoiceType?.toLowerCase()">
                  {{ row.invoiceType === 'SPECIAL' ? '专用' : '普通' }}
                </span>
              </td>
              <td class="text-center">
                <span class="status-badge" :class="getStatusClass(row.status)">
                  <span class="status-dot"></span>
                  {{ getStatusText(row.status) }}
                </span>
              </td>
              <td class="text-center td-actions">
                <button class="action-link" @click="showDetail(row)">详情</button>
                <template v-if="row.status === 'PENDING_CONFIRM'">
                  <button class="action-link primary" @click="doAction(row, 'submitReview')">提交</button>
                  <button class="action-link danger" @click="doAction(row, 'void')">作废</button>
                </template>
                <template v-else-if="row.status === 'PENDING_REVIEW'">
                  <button class="action-link primary" @click="doAction(row, 'confirm')">通过</button>
                  <button class="action-link warning" @click="doAction(row, 'reject')">驳回</button>
                </template>
                <template v-else-if="row.status === 'CONFIRMED'">
                  <button class="action-link primary" @click="doAction(row, 'markVouchered')">凭证</button>
                </template>
              </td>
            </tr>
            <tr v-if="loading">
              <td colspan="10" class="loading-cell">
                <div class="loading-spinner"></div>
                <span>加载中...</span>
              </td>
            </tr>
            <tr v-if="!loading && list.length === 0">
              <td colspan="10" class="empty-cell">
                <svg viewBox="0 0 24 24"><path d="M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-4.86 8.86l-3 3.87L9 13.14 6 17h12l-3.86-5.14z"/></svg>
                <span>暂无数据</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-wrapper">
        <div class="pagination-info">显示 {{ list.length }} 条，共 {{ total }} 条</div>
        <div class="pagination-controls">
          <button class="page-btn" :disabled="query.current <= 1" @click="query.current--; fetchData()">
            <svg viewBox="0 0 24 24"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
          </button>
          <span class="page-info">{{ query.current }} / {{ Math.ceil(total / query.size) || 1 }}</span>
          <button class="page-btn" :disabled="query.current >= Math.ceil(total / query.size)" @click="query.current++; fetchData()">
            <svg viewBox="0 0 24 24"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>
          </button>
          <select v-model="query.size" @change="fetchData()" class="page-size-select">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </div>
    </div>

    <!-- 新增发票弹窗 -->
    <div class="modal-overlay" v-if="dialogVisible" @click.self="dialogVisible = false">
      <div class="modal-container">
        <div class="modal-header">
          <h3>新增销项发票</h3>
          <button class="modal-close" @click="dialogVisible = false">
            <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="form-item">
              <label>发票号 <span class="required">*</span></label>
              <input v-model="form.invoiceNo" class="form-input" placeholder="请输入发票号码" />
            </div>
            <div class="form-item">
              <label>开票日期 <span class="required">*</span></label>
              <input v-model="form.invoiceDate" type="date" class="form-input" />
            </div>
            <div class="form-item full-width">
              <label>客户名称 <span class="required">*</span></label>
              <input v-model="form.customerName" class="form-input" placeholder="请输入客户名称" />
            </div>
            <div class="form-item">
              <label>金额(不含税) <span class="required">*</span></label>
              <input v-model.number="form.amount" type="number" class="form-input" placeholder="0.00" @input="recalcTax" />
            </div>
            <div class="form-item">
              <label>税率 <span class="required">*</span></label>
              <select v-model="form.taxRate" class="form-select" @change="recalcTax">
                <option :value="13">13%</option>
                <option :value="9">9%</option>
                <option :value="6">6%</option>
                <option :value="0">0%</option>
              </select>
            </div>
            <div class="form-item">
              <label>税额</label>
              <input v-model.number="form.taxAmount" type="number" class="form-input" placeholder="0.00" />
            </div>
            <div class="form-item">
              <label>发票类型 <span class="required">*</span></label>
              <select v-model="form.invoiceType" class="form-select">
                <option value="SPECIAL">增值税专用发票</option>
                <option value="PLAIN">普通发票</option>
              </select>
            </div>
            <div class="form-item full-width">
              <label>备注</label>
              <textarea v-model="form.remark" class="form-textarea" rows="2" placeholder="请输入备注信息"></textarea>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="dialogVisible = false">取消</button>
          <button class="btn-submit" @click="onSubmit">确认创建</button>
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <div class="modal-overlay" v-if="detailVisible" @click.self="detailVisible = false">
      <div class="modal-container detail-modal">
        <div class="modal-header">
          <h3>发票详情</h3>
          <button class="modal-close" @click="detailVisible = false">
            <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
          </button>
        </div>
        <div class="modal-body" v-if="detail">
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">发票号码</span>
              <span class="detail-value invoice-no">{{ detail.invoiceNo }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">开票日期</span>
              <span class="detail-value">{{ detail.invoiceDate }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">会计期间</span>
              <span class="detail-value">{{ detail.period }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">客户名称</span>
              <span class="detail-value">{{ detail.customerName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">金额(不含税)</span>
              <span class="detail-value amount">¥ {{ formatAmount(detail.amount) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">税额</span>
              <span class="detail-value tax">¥ {{ formatAmount(detail.taxAmount) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">价税合计</span>
              <span class="detail-value total">¥ {{ formatAmount(detail.totalAmount) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">税率</span>
              <span class="detail-value">{{ Number(detail.taxRate || 0).toFixed(2) }}%</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">发票类型</span>
              <span class="detail-value">{{ detail.invoiceType === 'SPECIAL' ? '增值税专用发票' : '普通发票' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">状态</span>
              <span class="status-badge" :class="getStatusClass(detail.status)">
                <span class="status-dot"></span>
                {{ getStatusText(detail.status) }}
              </span>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <template v-if="detail?.status === 'PENDING_CONFIRM'">
            <button class="btn-action" @click="doAction(detail, 'submitReview')">提交审核</button>
            <button class="btn-danger" @click="doAction(detail, 'void')">作废</button>
          </template>
          <template v-else-if="detail?.status === 'PENDING_REVIEW'">
            <button class="btn-action" @click="doAction(detail, 'confirm')">审核通过</button>
            <button class="btn-warning" @click="doAction(detail, 'reject')">驳回</button>
          </template>
          <template v-else-if="detail?.status === 'CONFIRMED'">
            <button class="btn-action" @click="doAction(detail, 'markVouchered')">生成凭证</button>
            <button class="btn-warning" @click="doAction(detail, 'revert')">回退</button>
          </template>
          <button class="btn-close" @click="detailVisible = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- 导入弹窗 -->
    <div class="modal-overlay" v-if="importVisible" @click.self="importVisible = false">
      <div class="modal-container import-modal">
        <div class="modal-header">
          <h3>导入销售发票</h3>
          <button class="modal-close" @click="importVisible = false">
            <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="upload-zone" v-if="!importPreview">
            <input type="file" accept=".xlsx,.xls" @change="onImportFileChange" ref="importUploadRef" />
            <svg class="upload-icon" viewBox="0 0 24 24"><path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z"/></svg>
            <p class="upload-text">拖放文件到此处或点击上传</p>
            <p class="upload-hint">支持 .xlsx 和 .xls 格式</p>
          </div>
          <div class="preview-section" v-else>
            <div class="preview-stats">
              <div class="preview-stat"><span>总行数</span><strong>{{ importPreview.total }}</strong></div>
              <div class="preview-stat"><span>有效行</span><strong class="success">{{ importPreview.valid }}</strong></div>
              <div class="preview-stat"><span>重复行</span><strong class="warning">{{ importPreview.existing || 0 }}</strong></div>
              <div class="preview-stat"><span>错误</span><strong class="danger">{{ importPreview.errors?.length || 0 }}</strong></div>
            </div>
            <div class="preview-table">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>发票号</th>
                    <th>购方</th>
                    <th>日期</th>
                    <th>金额</th>
                    <th>税额</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(item, i) in (importPreview.previews || []).slice(0, 10)" :key="i">
                    <td>{{ i + 1 }}</td>
                    <td>{{ item.invoiceNo }}</td>
                    <td>{{ item.buyerName }}</td>
                    <td>{{ item.invoiceDate }}</td>
                    <td class="text-right">{{ formatAmount(item.amount) }}</td>
                    <td class="text-right">{{ formatAmount(item.taxAmount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <button class="btn-reupload" @click="importPreview = null; importFile = null">重新上传</button>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="importVisible = false">取消</button>
          <button class="btn-submit" v-if="!importPreview" :disabled="!importFile" @click="onImportPreview">
            预览
          </button>
          <button class="btn-submit" v-else :disabled="!importPreview.valid" @click="onImportConfirm">
            确认导入 {{ Math.max(0, importPreview.valid - (importPreview.existing || 0)) }} 条
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { pageOutputInvoice, createOutputInvoice, getOutputInvoice, deleteOutputInvoice,
  outputInvoiceSummary,
  submitForReview, confirmOutputInvoice, rejectOutputInvoice, revertOutputInvoice, voidOutputInvoice, markVouchered } from '@/api/modules/tax'
import { previewSalesInvoices, confirmSalesInvoicesImport, batchLinkRedFlush } from '@/api/modules/salesInvoice'

// 状态映射
const STATUS_MAP: Record<string, string> = {
  PENDING_CONFIRM: '待确认', PENDING_REVIEW: '待审核', CONFIRMED: '已确认',
  VOUCHERED: '已生成凭证', FULLY_RECONCILED: '已核销', PARTIALLY_RECONCILED: '部分核销',
  VOIDED: '已作废', REVERSED: '已冲销',
}

const getStatusText = (status: string) => STATUS_MAP[status] || status
const getStatusClass = (status: string) => {
  const map: Record<string, string> = {
    PENDING_CONFIRM: 'warning', PENDING_REVIEW: 'info', CONFIRMED: 'success',
    VOUCHERED: 'primary', FULLY_RECONCILED: 'success', PARTIALLY_RECONCILED: 'info',
    VOIDED: 'danger', REVERSED: 'danger',
  }
  return map[status] || 'default'
}

// 统计卡片配置
const statCards = computed(() => [
  { label: '总发票数', value: stats.value.totalCount || 0, type: 'total', icon: 'InvoiceIcon' },
  { label: '蓝字总金额', value: formatAmount(stats.value.blueAmount), type: 'blue', icon: 'MoneyIcon' },
  { label: '红字金额', value: formatAmount(stats.value.redAmount), type: 'red', icon: 'RedIcon' },
  { label: '红字数', value: stats.value.redCount || 0, type: 'red-count', icon: 'CountIcon' },
  { label: '已冲销', value: stats.value.reversedCount || 0, type: 'reversed', icon: 'ReversedIcon' },
  { label: '已作废', value: stats.value.voidedCount || 0, type: 'voided', icon: 'VoidedIcon' },
])

// 格式化金额
const formatAmount = (v: any) => Number(v || 0).toFixed(2)

// 查询参数
const query = reactive({ customerName: '', period: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<any>({})

// 弹窗状态
const dialogVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<any>(null)
const deleting = ref(false)

// 表单数据
const form = reactive<any>({ invoiceType: 'SPECIAL', taxRate: 13, amount: 0, taxAmount: 0 })

// 导入相关
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importPreview = ref<any>(null)
const importUploadRef = ref<any>(null)
const linkingRedFlush = ref(false)

// 重置查询
const resetQuery = () => {
  query.customerName = ''
  query.period = ''
  query.current = 1
  fetchData()
}

// 计算税额
const recalcTax = () => {
  if (form.amount && form.taxRate != null) {
    form.taxAmount = Number((form.amount * form.taxRate / 100).toFixed(2))
  }
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageOutputInvoice(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

// 获取统计
const fetchStats = async () => {
  try { stats.value = await outputInvoiceSummary() } catch {}
}

// 打开编辑
const openEdit = () => {
  Object.assign(form, {
    id: undefined, invoiceNo: '', invoiceDate: '', customerName: '',
    amount: 0, taxRate: 13, taxAmount: 0, invoiceType: 'SPECIAL', remark: '',
  })
  dialogVisible.value = true
}

// 提交表单
const onSubmit = async () => {
  if (!form.invoiceNo || !form.invoiceDate || !form.customerName || !form.amount) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    await createOutputInvoice(form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchData()
    fetchStats()
  } catch {}
}

// 显示详情
const showDetail = async (row: any) => {
  try {
    detail.value = await getOutputInvoice(row.id)
  } catch {
    detail.value = row
  }
  detailVisible.value = true
}

// 执行操作
const doAction = async (row: any, action: string) => {
  const id = row?.id
  if (!id) return
  const labels: Record<string, string> = {
    submitReview: '提交审核', confirm: '审核通过', reject: '驳回', revert: '回退', void: '作废', markVouchered: '生成凭证'
  }
  const label = labels[action] || action

  if (action === 'reject' || action === 'void') {
    const reason = prompt(`请输入${label}原因：`)
    if (!reason) return
    try {
      if (action === 'reject') await rejectOutputInvoice(id, reason)
      else await voidOutputInvoice(id, reason)
      ElMessage.success(`${label}成功`)
      detailVisible.value = false
      fetchData()
      fetchStats()
    } catch {}
    return
  }

  try {
    if (action === 'submitReview') await submitForReview(id)
    else if (action === 'confirm') await confirmOutputInvoice(id)
    else if (action === 'revert') await revertOutputInvoice(id)
    else if (action === 'markVouchered') await markVouchered(id)
    ElMessage.success(`${label}成功`)
    detailVisible.value = false
    fetchData()
    fetchStats()
  } catch {}
}

// 批量红冲关联
const onBatchLinkRedFlush = async () => {
  linkingRedFlush.value = true
  try {
    const res = await batchLinkRedFlush()
    ElMessage.success(`红冲关联完成: 匹配 ${res.matched} 对, 跳过 ${res.skipped} 条`)
    fetchData()
    fetchStats()
  } catch {}
  finally { linkingRedFlush.value = false }
}

// 导入相关方法
const openImportDialog = () => {
  importFile.value = null
  importPreview.value = null
  importVisible.value = true
}

const onImportFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  importFile.value = input.files?.[0] || null
  importPreview.value = null
}

const onImportPreview = async () => {
  if (!importFile.value) { ElMessage.warning('请选择文件'); return }
  try {
    importPreview.value = await previewSalesInvoices(importFile.value)
    if (importPreview.value.total === 0) {
      ElMessage.warning('未解析到有效发票行')
    } else {
      ElMessage.success(`解析完成: ${importPreview.value.total} 行`)
    }
  } catch {}
}

const onImportConfirm = async () => {
  if (!importPreview.value?.batchId) return
  try {
    const res = await confirmSalesInvoicesImport(importPreview.value.batchId)
    ElMessage.success(`导入成功: ${res.success} 张发票`)
    importVisible.value = false
    importPreview.value = null
    fetchData()
    fetchStats()
  } catch {}
}

onMounted(() => { fetchData(); fetchStats() })
</script>

<style scoped>
/* 基础变量 */
.output-invoice {
  --primary: #00f5ff;
  --primary-dark: #00c4cc;
  --secondary: #ff6b35;
  --success: #00ff88;
  --warning: #ffcc00;
  --danger: #ff4757;
  --info: #7c83fd;
  --bg-dark: #0a0e1a;
  --bg-card: rgba(15, 23, 42, 0.8);
  --text-primary: #ffffff;
  --text-secondary: #8892b0;
  --border-color: rgba(0, 245, 255, 0.2);
  --glow-color: rgba(0, 245, 255, 0.3);
}

/* 背景效果 */
.bg-grid {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    linear-gradient(rgba(0, 245, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 245, 255, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
  pointer-events: none;
  z-index: 0;
}

.bg-glow {
  position: fixed;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  filter: blur(150px);
  opacity: 0.15;
  pointer-events: none;
  z-index: 0;
}

.glow-1 {
  top: -200px;
  right: -100px;
  background: var(--primary);
}

.glow-2 {
  bottom: -200px;
  left: -100px;
  background: var(--info);
}

/* 页面头部 */
.page-header {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 32px;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title-wrapper {
  display: flex;
  align-items: center;
  gap: 16px;
}

.title-line {
  width: 40px;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--primary));
}

.title-line:last-child {
  background: linear-gradient(90deg, var(--primary), transparent);
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: 4px;
  text-shadow: 0 0 20px var(--glow-color);
}

.page-subtitle {
  font-size: 12px;
  color: var(--text-secondary);
  letter-spacing: 2px;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
}

.action-btn .icon {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

.action-btn.secondary:hover {
  border-color: var(--primary);
  box-shadow: 0 0 20px var(--glow-color);
}

.action-btn.primary {
  background: linear-gradient(135deg, var(--primary-dark), var(--primary));
  border-color: var(--primary);
  color: #000;
}

.action-btn.primary:hover {
  box-shadow: 0 0 30px var(--glow-color);
  transform: translateY(-2px);
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  padding: 0 32px;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.stat-card {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  animation: fadeInUp 0.5s ease forwards;
  animation-delay: var(--delay);
  opacity: 0;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 245, 255, 0.1);
  border: 1px solid rgba(0, 245, 255, 0.2);
}

.stat-icon.total { background: rgba(124, 131, 253, 0.15); border-color: rgba(124, 131, 253, 0.3); }
.stat-icon.blue { background: rgba(0, 255, 136, 0.15); border-color: rgba(0, 255, 136, 0.3); }
.stat-icon.red, .stat-icon.red-count { background: rgba(255, 71, 87, 0.15); border-color: rgba(255, 71, 87, 0.3); }
.stat-icon.reversed { background: rgba(255, 107, 53, 0.15); border-color: rgba(255, 107, 53, 0.3); }
.stat-icon.voided { background: rgba(255, 204, 0, 0.15); border-color: rgba(255, 204, 0, 0.3); }

.stat-icon svg {
  width: 24px;
  height: 24px;
  fill: var(--primary);
}

.stat-icon.total svg { fill: var(--info); }
.stat-icon.blue svg { fill: var(--success); }
.stat-icon.red svg, .stat-icon.red-count svg { fill: var(--danger); }
.stat-icon.reversed svg { fill: var(--secondary); }
.stat-icon.voided svg { fill: var(--warning); }

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
}

.stat-border {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--primary), transparent);
  opacity: 0;
  transition: opacity 0.3s;
}

.stat-card:hover .stat-border {
  opacity: 1;
}

/* 筛选区 */
.filter-section {
  padding: 0 32px;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.filter-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  align-items: flex-end;
  gap: 20px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
}

.input-wrapper {
  position: relative;
}

.input-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  fill: var(--text-secondary);
}

.filter-input {
  width: 180px;
  padding: 10px 12px 10px 38px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
  transition: all 0.3s;
}

.filter-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 15px var(--glow-color);
}

.filter-input::placeholder {
  color: var(--text-secondary);
}

.filter-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

.btn-query {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, var(--primary-dark), var(--primary));
  border: none;
  border-radius: 8px;
  color: #000;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-query svg {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

.btn-query:hover {
  box-shadow: 0 0 25px var(--glow-color);
  transform: translateY(-2px);
}

.btn-reset {
  padding: 10px 20px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s;
}

.btn-reset:hover {
  border-color: var(--primary);
  color: var(--primary);
}

/* 表格区 */
.table-section {
  padding: 0 32px;
  position: relative;
  z-index: 1;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.data-count {
  font-size: 14px;
  color: var(--text-secondary);
}

.data-count strong {
  color: var(--primary);
  font-weight: 600;
}

.btn-red-flush {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: rgba(255, 71, 87, 0.1);
  border: 1px solid rgba(255, 71, 87, 0.3);
  border-radius: 6px;
  color: var(--danger);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-red-flush svg {
  width: 16px;
  height: 16px;
  fill: currentColor;
}

.btn-red-flush:hover:not(:disabled) {
  background: rgba(255, 71, 87, 0.2);
  box-shadow: 0 0 15px rgba(255, 71, 87, 0.3);
}

.btn-red-flush:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.table-container {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  padding: 14px 16px;
  background: rgba(0, 245, 255, 0.05);
  border-bottom: 1px solid var(--border-color);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 1px;
  text-align: left;
}

.data-table td {
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  font-size: 14px;
  color: var(--text-primary);
}

.data-table tbody tr {
  transition: all 0.3s;
  animation: fadeInRow 0.4s ease forwards;
  animation-delay: calc(var(--row-index) * 0.05s);
  opacity: 0;
}

@keyframes fadeInRow {
  from { opacity: 0; transform: translateX(-10px); }
  to { opacity: 1; transform: translateX(0); }
}

.data-table tbody tr:hover {
  background: rgba(0, 245, 255, 0.05);
}

.text-right { text-align: right !important; }
.text-center { text-align: center !important; }

.invoice-tag {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  padding: 4px 8px;
  background: rgba(0, 245, 255, 0.1);
  border: 1px solid rgba(0, 245, 255, 0.2);
  border-radius: 4px;
  color: var(--primary);
}

.td-customer {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.td-amount { color: var(--success) !important; font-family: 'JetBrains Mono', monospace; }
.td-tax { color: var(--warning) !important; font-family: 'JetBrains Mono', monospace; }

.type-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.type-badge.special {
  background: rgba(124, 131, 253, 0.15);
  color: var(--info);
  border: 1px solid rgba(124, 131, 253, 0.3);
}

.type-badge.plain {
  background: rgba(0, 255, 136, 0.1);
  color: var(--success);
  border: 1px solid rgba(0, 255, 136, 0.2);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-badge.warning { background: rgba(255, 204, 0, 0.15); color: var(--warning); }
.status-badge.info { background: rgba(124, 131, 253, 0.15); color: var(--info); }
.status-badge.success { background: rgba(0, 255, 136, 0.15); color: var(--success); }
.status-badge.danger { background: rgba(255, 71, 87, 0.15); color: var(--danger); }
.status-badge.primary { background: rgba(0, 245, 255, 0.15); color: var(--primary); }
.status-badge.default { background: rgba(255, 255, 255, 0.1); color: var(--text-secondary); }

.td-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.action-link {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.action-link:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.action-link.primary {
  border-color: rgba(0, 245, 255, 0.3);
  color: var(--primary);
}

.action-link.primary:hover {
  background: rgba(0, 245, 255, 0.1);
}

.action-link.danger { border-color: rgba(255, 71, 87, 0.3); color: var(--danger); }
.action-link.danger:hover { background: rgba(255, 71, 87, 0.1); }

.action-link.warning { border-color: rgba(255, 204, 0, 0.3); color: var(--warning); }
.action-link.warning:hover { background: rgba(255, 204, 0, 0.1); }

.loading-cell, .empty-cell {
  text-align: center;
  padding: 40px !important;
  color: var(--text-secondary);
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 12px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-cell svg {
  width: 48px;
  height: 48px;
  fill: var(--text-secondary);
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
}

.pagination-info {
  font-size: 13px;
  color: var(--text-secondary);
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}

.page-btn svg {
  width: 18px;
  height: 18px;
  fill: var(--text-secondary);
}

.page-btn:hover:not(:disabled) {
  border-color: var(--primary);
}

.page-btn:hover:not(:disabled) svg {
  fill: var(--primary);
}

.page-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
}

.page-size-select {
  padding: 6px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
  cursor: pointer;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-container {
  width: 560px;
  max-height: 80vh;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  overflow: hidden;
  animation: modalIn 0.3s ease;
}

@keyframes modalIn {
  from { opacity: 0; transform: scale(0.95) translateY(-20px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-container.detail-modal {
  width: 640px;
}

.modal-container.import-modal {
  width: 700px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-color);
  background: rgba(0, 245, 255, 0.02);
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.modal-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.modal-close svg {
  width: 18px;
  height: 18px;
  fill: var(--text-secondary);
}

.modal-close:hover {
  border-color: var(--danger);
}

.modal-close:hover svg {
  fill: var(--danger);
}

.modal-body {
  padding: 24px;
  max-height: 60vh;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.2);
}

/* 表单 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item.full-width {
  grid-column: 1 / -1;
}

.form-item label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.required {
  color: var(--danger);
}

.form-input, .form-select, .form-textarea {
  padding: 10px 14px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
  transition: all 0.3s;
}

.form-input:focus, .form-select:focus, .form-textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 15px var(--glow-color);
}

.form-textarea {
  resize: vertical;
  min-height: 60px;
}

/* 详情 */
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-label {
  font-size: 12px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.detail-value {
  font-size: 15px;
  color: var(--text-primary);
  font-weight: 500;
}

.detail-value.invoice-no {
  font-family: 'JetBrains Mono', monospace;
  color: var(--primary);
}

.detail-value.amount { color: var(--success); }
.detail-value.tax { color: var(--warning); }
.detail-value.total { color: var(--primary); font-size: 18px; font-weight: 700; }

/* 上传区 */
.upload-zone {
  border: 2px dashed var(--border-color);
  border-radius: 12px;
  padding: 48px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.upload-zone:hover {
  border-color: var(--primary);
  background: rgba(0, 245, 255, 0.05);
}

.upload-zone input {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.upload-icon {
  width: 64px;
  height: 64px;
  fill: var(--primary);
  margin-bottom: 16px;
}

.upload-text {
  font-size: 16px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.upload-hint {
  font-size: 13px;
  color: var(--text-secondary);
}

.preview-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.preview-stat {
  flex: 1;
  padding: 16px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  text-align: center;
}

.preview-stat span {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.preview-stat strong {
  font-size: 24px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
}

.preview-stat strong.success { color: var(--success); }
.preview-stat strong.warning { color: var(--warning); }
.preview-stat strong.danger { color: var(--danger); }

.preview-table {
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 16px;
}

.preview-table table {
  width: 100%;
  border-collapse: collapse;
}

.preview-table th, .preview-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
}

.preview-table th {
  background: rgba(0, 245, 255, 0.05);
  color: var(--text-secondary);
  font-weight: 600;
  position: sticky;
  top: 0;
}

.btn-reupload {
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s;
}

.btn-reupload:hover {
  border-color: var(--primary);
  color: var(--primary);
}

/* 按钮 */
.btn-cancel, .btn-close {
  padding: 10px 24px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s;
}

.btn-cancel:hover, .btn-close:hover {
  border-color: var(--text-primary);
  color: var(--text-primary);
}

.btn-submit {
  padding: 10px 24px;
  background: linear-gradient(135deg, var(--primary-dark), var(--primary));
  border: none;
  border-radius: 8px;
  color: #000;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-submit:hover:not(:disabled) {
  box-shadow: 0 0 25px var(--glow-color);
  transform: translateY(-2px);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-action {
  padding: 8px 16px;
  background: rgba(0, 245, 255, 0.1);
  border: 1px solid rgba(0, 245, 255, 0.3);
  border-radius: 6px;
  color: var(--primary);
  cursor: pointer;
  transition: all 0.3s;
}

.btn-action:hover {
  background: rgba(0, 245, 255, 0.2);
}

.btn-danger {
  padding: 8px 16px;
  background: rgba(255, 71, 87, 0.1);
  border: 1px solid rgba(255, 71, 87, 0.3);
  border-radius: 6px;
  color: var(--danger);
  cursor: pointer;
  transition: all 0.3s;
}

.btn-danger:hover {
  background: rgba(255, 71, 87, 0.2);
}

.btn-warning {
  padding: 8px 16px;
  background: rgba(255, 204, 0, 0.1);
  border: 1px solid rgba(255, 204, 0, 0.3);
  border-radius: 6px;
  color: var(--warning);
  cursor: pointer;
  transition: all 0.3s;
}

.btn-warning:hover {
  background: rgba(255, 204, 0, 0.2);
}

/* 滚动条 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.2);
}

::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--primary);
}

/* 响应式 */
@media (max-width: 1400px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
