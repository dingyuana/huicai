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

      <el-upload
        ref="uploadRef" drag
        :auto-upload="false" :show-file-list="true"
        accept=".xlsx,.xls"
        :limit="1"
        @change="onFileChange">
        <el-icon class="el-icon--upload" style="font-size:48px"><upload-filled /></el-icon>
        <div class="el-upload__text">拖放销售发票Excel文件到此处或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持恺拓销售发票格式（列名: 发票号码, 购方识别号, 购买方名称, 开票日期, 金额, 税额, 价税合计, 是否正数发票 等）
          </div>
        </template>
      </el-upload>

      <div style="margin-top:16px;text-align:center">
        <el-button type="primary" :loading="importing" :disabled="!selectedFile" size="large" @click="onImport">
          {{ importing ? '导入中...' : '开始导入' }}
        </el-button>
      </div>
    </el-card>

    <!-- 导入结果 -->
    <el-dialog v-model="resultVisible" title="导入结果" width="500">
      <template v-if="result">
        <el-result
          :icon="result.success === result.total ? 'success' : 'warning'"
          :title="`导入完成: ${result.success}/${result.total}`">
          <template #extra>
            <p>生成业务单据: {{ result.docCreated }}</p>
            <p>生成凭证: {{ result.voucherCreated }}</p>
            <p>批次号: {{ result.batchId }}</p>
          </template>
        </el-result>

        <el-table v-if="result.errors && result.errors.length" :data="result.errors" border size="small" style="margin-top:12px">
          <el-table-column prop="row" label="行号" width="60" />
          <el-table-column prop="invoiceNo" label="发票号" width="160" />
          <el-table-column prop="message" label="错误原因" min-width="200" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { importSalesInvoices } from '@/api/modules/salesInvoice'

const importing = ref(false)
const selectedFile = ref<File | null>(null)
const resultVisible = ref(false)
const result = ref<{
  total: number; success: number; docCreated: number; voucherCreated: number
  errors: Array<{ row: number; invoiceNo: string; message: string }>; batchId: string
} | null>(null)

function onFileChange(uploadFile: any) {
  selectedFile.value = uploadFile.raw || null
}

async function onImport() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importing.value = true
  try {
    result.value = await importSalesInvoices(selectedFile.value)
    resultVisible.value = true
    if (result.value.success > 0) {
      ElMessage.success(`成功导入 ${result.value.success} 张发票`)
    }
    if (result.value.errors?.length) {
      ElMessage.warning(`${result.value.errors.length} 行导入失败`)
    }
  } finally {
    importing.value = false
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