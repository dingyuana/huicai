<template>
  <div class="voucher-template-ref-page">
    <div class="page-header">
      <div>
        <span class="page-title">模板参考库</span>
        <span class="page-subtitle">系统预置的标准凭证模板，新建账套时可一键导入</span>
      </div>
      <el-button type="primary" :loading="importing" @click="handleImportAll" size="large">
        <el-icon style="margin-right:4px"><Download /></el-icon>
        一键导入
      </el-button>
    </div>

    <el-alert
      title="模板参考库说明"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:16px"
    >
      <template #default>
        <p>参考库中的模板为系统预置的标准财务模板，适用于常见业务场景。</p>
        <p>点击「一键导入」可将所有模板复制到当前账套，导入后可在「凭证模板」页面中查看和编辑。</p>
        <p>已导入的模板不会重复导入（按 template_code 去重）。</p>
      </template>
    </el-alert>

    <el-table :data="templates" stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="模板名称" min-width="160" />
      <el-table-column prop="businessType" label="适用单据类型" width="160">
        <template #default="{ row }">
          <el-tag size="small">{{ row.businessType || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分录" min-width="240">
        <template #default="{ row }">
          <span v-if="row.lines && row.lines.length > 0" class="line-preview">
            <span v-for="(line, i) in row.lines" :key="i">
              <el-tag :type="line.direction === 'debit' ? 'danger' : 'success'" size="small" style="margin-right:2px">
                {{ line.direction === 'debit' ? '借' : '贷' }}
              </el-tag>
              {{ line.subjectCode || line.subjectName || '(科目)' }}
              <span v-if="line.drAmountTemplate || line.crAmountTemplate" class="amount-tpl">
                {{ line.drAmountTemplate || line.crAmountTemplate }}
              </span>
              <em v-if="i < row.lines.length - 1" style="margin:0 6px;color:#c0c4cc">|</em>
            </span>
          </span>
          <span v-else class="text-muted">无分录</span>
        </template>
      </el-table-column>
      <el-table-column label="导入状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="importedCodes.has(row.name)" type="success" size="small">已导入</el-tag>
          <el-tag v-else type="info" size="small">未导入</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="import-result" v-if="importResult !== null">
      <el-alert
        :title="importResult > 0 ? `成功导入 ${importResult} 个模板` : '所有模板已存在，无需导入'"
        :type="importResult > 0 ? 'success' : 'info'"
        show-icon
        :closable="true"
        @close="importResult = null"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import {
  listReferenceTemplates,
  importFromReference,
  listTemplates,
  type VoucherTemplateVO,
} from '@/api/modules/voucherTemplate'

const loading = ref(false)
const importing = ref(false)
const templates = ref<VoucherTemplateVO[]>([])
const importedCodes = ref<Set<string>>(new Set())
const importResult = ref<number | null>(null)

async function fetchTemplates() {
  loading.value = true
  try {
    const res = await listReferenceTemplates()
    templates.value = (res as any) || []
  } catch (e: any) {
    ElMessage.error('加载参考库模板失败: ' + (e.message || ''))
  } finally {
    loading.value = false
  }
}

async function checkImportedStatus() {
  try {
    const res = await listTemplates()
    const existing = (res as any) || []
    importedCodes.value = new Set(existing.map((t: any) => t.name))
  } catch (_e: any) {
    // ignore
  }
}

async function handleImportAll() {
  importing.value = true
  try {
    const res = await importFromReference()
    const count = (res as any) || 0
    importResult.value = count
    if (count > 0) {
      ElMessage.success(`成功导入 ${count} 个模板`)
      await checkImportedStatus()
    } else {
      ElMessage.info('所有模板已存在，无需导入')
    }
  } catch (e: any) {
    ElMessage.error('导入失败: ' + (e.message || ''))
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  fetchTemplates()
  checkImportedStatus()
})
</script>

<style scoped>
.voucher-template-ref-page {
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  display: block;
}
.page-subtitle {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
  display: block;
}
.line-preview {
  font-size: 12px;
  color: #606266;
}
.text-muted {
  color: #c0c4cc;
}
.amount-tpl {
  color: #909399;
  font-style: italic;
  margin-left: 2px;
}
.import-result {
  margin-top: 16px;
}
</style>