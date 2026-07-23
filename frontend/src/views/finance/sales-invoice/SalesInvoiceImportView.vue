<template>
  <div class="sales-invoice-import">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">销售发票导入</span>
      </div>

      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        上传销售发票Excel文件，系统将自动识别列名、匹配客户（按税号→名称→自动创建），
        生成应收业务单据和收入确认凭证（草稿状态）。
      </el-alert>

      <!-- 步骤1: 选择文件 + 上传 -->
      <el-upload
        v-if="!previewData"
        ref="uploadRef" drag
        :auto-upload="false" :show-file-list="true"
        accept=".xlsx,.xls"
        :limit="1"
        @change="onFileChange">
        <el-icon class="el-icon--upload" style="font-size:48px"><upload-filled /></el-icon>
        <div class="el-upload__text">
          拖放销售发票Excel文件到此处或 <em>点击选择</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持恺拓销售发票格式（列名: 发票号码, 购方识别号, 购买方名称, 开票日期, 金额, 税额, 价税合计, 是否正数发票 等）
          </div>
        </template>
      </el-upload>

      <div v-if="!previewData" style="margin-top:16px;text-align:center">
        <el-button
          type="primary"
          :loading="previewing"
          :disabled="!selectedFile"
          size="large"
          @click="onPreview">
          {{ previewing ? '解析中...' : '下一步: 预览' }}
        </el-button>
      </div>

      <!-- 步骤2: 预览 + 确认 -->
      <div v-else>
        <el-descriptions :column="5" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="总行数">{{ previewData.total }}</el-descriptions-item>
          <el-descriptions-item label="有效行数">{{ previewData.valid }}</el-descriptions-item>
          <el-descriptions-item label="已有">
            <el-tag v-if="previewData.existing" type="warning">{{ previewData.existing }}</el-tag>
            <el-tag v-else type="success">0</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="失败行数">
            <el-tag v-if="previewData.errors?.length" type="danger">{{ previewData.errors.length }}</el-tag>
            <el-tag v-else type="success">0</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="批次号">{{ previewData.batchId }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin:0 0 8px">预览 ({{ previewData.previews?.length || 0 }} 行)</h4>
        <el-table :data="(previewData.previews || []).slice(0, 50)" border size="small" max-height="400">
          <el-table-column type="index" label="行号" width="50" />
          <el-table-column prop="invoiceNo" label="发票号" width="180" />
          <el-table-column label="状态" width="60" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.existing" type="warning" size="small">已有</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="buyerName" label="购方名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="invoiceDate" label="开票日期" width="120" />
          <el-table-column prop="goodsName" label="商品" min-width="150" show-overflow-tooltip />
          <el-table-column label="金额(不含税)" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="税额" width="100" align="right">
            <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
          </el-table-column>
          <el-table-column label="价税合计" width="120" align="right">
            <template #default="{ row }">{{ fmtAmount(row.totalAmount) }}</template>
          </el-table-column>
        </el-table>
        <p v-if="(previewData.previews?.length || 0) > 50" style="text-align:center;color:#909399;margin-top:8px">
          仅显示前 50 行, 共 {{ previewData.previews.length }} 条
        </p>

        <el-collapse v-if="previewData.errors?.length" style="margin-top:12px">
          <el-collapse-item title="错误明细" name="errors">
            <el-table :data="previewData.errors" border size="small">
              <el-table-column prop="row" label="行号" width="80" />
              <el-table-column prop="invoiceNo" label="发票号" width="180" />
              <el-table-column prop="message" label="错误原因" min-width="200" />
            </el-table>
          </el-collapse-item>
        </el-collapse>

        <div style="margin-top:16px;text-align:center">
          <el-button @click="previewData = null">重新上传</el-button>
          <el-button
            type="primary"
            :loading="confirming"
            :disabled="!previewData.valid || (previewData.valid - (previewData.existing || 0)) <= 0"
            size="large"
            @click="onConfirm">
            {{ confirming ? '导入中...' : `确认导入 ${Math.max(0, previewData.valid - (previewData.existing || 0))} 张` }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 导入完成结果 -->
    <el-dialog v-model="resultVisible" title="导入结果" width="500">
      <template v-if="result">
        <el-result
          :icon="result.success === result.total ? 'success' : 'warning'"
          :title="`导入完成: ${result.success}/${result.total}`">
          <template #extra>
            <p>成功导入: {{ result.success }} 张</p>
            <p v-if="result.duplicateSkipped">跳过重复: {{ result.duplicateSkipped }} 张</p>
            <p>生成业务单据: {{ result.docCreated }}</p>
            <p>生成凭证: {{ result.voucherCreated }}</p>
            <p>批次号: {{ result.batchId }}</p>
          </template>
        </el-result>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { previewSalesInvoices, confirmSalesInvoicesImport } from '@/api/modules/salesInvoice'

const previewing = ref(false)
const confirming = ref(false)
const selectedFile = ref<File | null>(null)
const resultVisible = ref(false)
const result = ref<{
  total: number; success: number; docCreated: number; voucherCreated: number; duplicateSkipped?: number
  errors: Array<{ row: number; invoiceNo: string; message: string }>; batchId: string
} | null>(null)

const previewData = ref<{
  total: number; valid: number; existing: number; errors: any[]; batchId: string; previews: any[]
} | null>(null)

function onFileChange(uploadFile: any) {
  selectedFile.value = uploadFile.raw || null
  previewData.value = null
}

function fmtAmount(v: any) {
  if (v == null) return ''
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function onPreview() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  previewing.value = true
  try {
    previewData.value = await previewSalesInvoices(selectedFile.value) as any
    if (!previewData.value || previewData.value.total === 0) {
      ElMessage.warning('未解析到有效发票行')
    } else {
      ElMessage.success(`解析完成: ${previewData.value.total} 行, 有效 ${previewData.value.valid} 行`)
    }
  } finally {
    previewing.value = false
  }
}

async function onConfirm() {
  if (!previewData.value?.batchId) {
    ElMessage.warning('请先预览')
    return
  }
  confirming.value = true
  try {
    result.value = await confirmSalesInvoicesImport(previewData.value.batchId)
    resultVisible.value = true
    ElMessage.success(`成功导入 ${result.value.success} 张发票`)
    // 重置状态, 允许用户继续导入其他文件
    previewData.value = null
    selectedFile.value = null
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.sales-invoice-import .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
</style>