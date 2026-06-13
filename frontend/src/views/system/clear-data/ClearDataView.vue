<template>
  <div class="clear-data">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">数据维护</span>
      </div>

      <el-alert type="warning" :closable="false" style="margin-bottom:16px">
        <strong>注意：</strong>清空操作将物理删除数据，不可恢复。请谨慎操作。
      </el-alert>

      <el-descriptions :column="1" border style="margin-bottom:20px">
        <el-descriptions-item label="银行流水数">{{ stats.statements }}</el-descriptions-item>
        <el-descriptions-item label="发票导入记录">{{ stats.invoices }}</el-descriptions-item>
        <el-descriptions-item label="生成凭证数">{{ stats.vouchers }}</el-descriptions-item>
      </el-descriptions>

      <el-space direction="vertical" style="width:100%">
        <el-card shadow="hover">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>
              <strong>清空银行流水</strong>
              <p style="margin:4px 0 0;color:#909399;font-size:12px">删除所有银行流水、自动生成的业务单据(来源: FROM_BANK_TXN)及凭证</p>
            </div>
            <el-popconfirm title="确定清空银行流水?" confirm-button-text="确认清空" @confirm="onClear('statements')">
              <template #reference>
                <el-button type="danger" plain>清空银行流水</el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-card>

        <el-card shadow="hover">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>
              <strong>清空发票记录</strong>
              <p style="margin:4px 0 0;color:#909399;font-size:12px">删除所有发票导入记录、生成的业务单据(来源: INVOICE_IMPORT)及凭证</p>
            </div>
            <el-popconfirm title="确定清空发票记录?" confirm-button-text="确认清空" @confirm="onClear('invoices')">
              <template #reference>
                <el-button type="danger" plain>清空发票记录</el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-card>

        <el-card shadow="hover">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>
              <strong>清空所有凭证</strong>
              <p style="margin:4px 0 0;color:#909399;font-size:12px">删除所有凭证及分录(保留业务单据、流水、发票；单据状态将回退为草稿)</p>
            </div>
            <el-popconfirm title="确定清空所有凭证?" confirm-button-text="确认清空" @confirm="onClear('vouchers')">
              <template #reference>
                <el-button type="danger" plain>清空所有凭证</el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-card>

        <el-card shadow="hover">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>
              <strong style="color:var(--el-color-danger)">清空全部数据</strong>
              <p style="margin:4px 0 0;color:#909399;font-size:12px">清空银行流水 + 发票记录 + 相关业务单据 + 相关凭证（草稿）</p>
            </div>
            <el-popconfirm title="⚠️ 确定清空全部数据?此操作不可恢复!" confirm-button-text="确认全部清空" @confirm="onClear('all')">
              <template #reference>
                <el-button type="danger" plain>清空全部数据</el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-card>
      </el-space>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { clearBankStatements, clearInvoiceRecords, clearVouchers, clearAll } from '@/api/modules/system'
import { getBankStatementPage } from '@/api/modules/bankStatement'
import { getBusinessDocPage } from '@/api/modules/businessDoc'
import { getVoucherPage } from '@/api/modules/voucher'

const stats = ref({ statements: 0, invoices: 0, vouchers: 0 })

async function fetchStats() {
  try {
    const stmts = await getBankStatementPage({ current: 1, size: 1 }) as any
    stats.value.statements = stmts.total || 0
  } catch { /* ignore */ }
  try {
    const docs = await getBusinessDocPage({ docType: 'INVOICE_OUT', current: 1, size: 1 }) as any
    stats.value.invoices = docs.total || 0
  } catch { /* ignore */ }
  try {
    const vchs = await getVoucherPage({ current: 1, size: 1 }) as any
    stats.value.vouchers = vchs.total || 0
  } catch { /* ignore */ }
}

async function onClear(type: string) {
  try {
    let res: any
    if (type === 'statements') res = await clearBankStatements()
    else if (type === 'invoices') res = await clearInvoiceRecords()
    else if (type === 'vouchers') res = await clearVouchers()
    else res = await clearAll()
    ElMessage.success(res.message || '清空完成')
    await fetchStats()
  } catch { /* handled */ }
}

onMounted(fetchStats)
</script>

<style scoped>
.clear-data .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title { font-size: 16px; font-weight: 600; }
</style>