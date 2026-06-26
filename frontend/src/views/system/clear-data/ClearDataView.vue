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
        <el-descriptions-item label="应收明细数">{{ stats.receivables }}</el-descriptions-item>
        <el-descriptions-item label="应付明细数">{{ stats.payables }}</el-descriptions-item>
        <el-descriptions-item label="业务单据数">{{ stats.businessDocs }}</el-descriptions-item>
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
              <strong>清空业务单据</strong>
              <p style="margin:4px 0 0;color:#909399;font-size:12px">删除所有业务单据及明细行（保留银行流水、发票、凭证）</p>
            </div>
            <el-popconfirm title="确定清空业务单据?" confirm-button-text="确认清空" @confirm="onClear('businessDocs')">
              <template #reference>
                <el-button type="danger" plain>清空业务单据</el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-card>

        <el-card shadow="hover">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>
              <strong>清空应收明细</strong>
              <p style="margin:4px 0 0;color:#909399;font-size:12px">删除所有应收明细及关联的核销记录（保留业务单据、凭证）</p>
            </div>
            <el-popconfirm title="确定清空所有应收明细?" confirm-button-text="确认清空" @confirm="onClear('receivables')">
              <template #reference>
                <el-button type="danger" plain>清空应收明细</el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-card>

        <el-card shadow="hover">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>
              <strong>清空应付明细</strong>
              <p style="margin:4px 0 0;color:#909399;font-size:12px">删除所有应付明细及关联的核销记录（保留业务单据、凭证）</p>
            </div>
            <el-popconfirm title="确定清空所有应付明细?" confirm-button-text="确认清空" @confirm="onClear('payables')">
              <template #reference>
                <el-button type="danger" plain>清空应付明细</el-button>
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
import { clearBankStatements, clearInvoiceRecords, clearBusinessDocs, clearVouchers, clearAll, clearReceivables, clearPayables } from '@/api/modules/system'
import { getBankStatementPage } from '@/api/modules/bankStatement'
import { getBusinessDocPage } from '@/api/modules/businessDoc'
import { getVoucherPage } from '@/api/modules/voucher'
import { pageReceivable, pagePayable } from '@/api/modules/arap'

const stats = ref({ statements: 0, invoices: 0, receivables: 0, payables: 0, businessDocs: 0, vouchers: 0 })
const loadErrors = ref<string[]>([])

async function fetchStats() {
  loadErrors.value = []
  const tryFetch = async (label: string, fn: () => Promise<any>, setter: (v: number) => void) => {
    try {
      const res = await fn()
      setter(res.total || 0)
    } catch (e: any) {
      loadErrors.value.push(`${label}: ${e?.message || '请求失败'}`)
    }
  }
  await Promise.all([
    tryFetch('银行流水', () => getBankStatementPage({ current: 1, size: 1 }), v => stats.value.statements = v),
    tryFetch('发票记录', () => getBusinessDocPage({ docType: 'INVOICE_OUT', current: 1, size: 1 }), v => stats.value.invoices = v),
    tryFetch('业务单据', () => getBusinessDocPage({ current: 1, size: 1 }), v => stats.value.businessDocs = v),
    tryFetch('凭证', () => getVoucherPage({ current: 1, size: 1 }), v => stats.value.vouchers = v),
    tryFetch('应收明细', () => pageReceivable({ current: 1, size: 1 }), v => stats.value.receivables = v),
    tryFetch('应付明细', () => pagePayable({ current: 1, size: 1 }), v => stats.value.payables = v),
  ])
  if (loadErrors.value.length > 0) {
    ElMessage.warning(`部分统计数据加载失败 (${loadErrors.value.length} 项)`)
    console.warn('数据维护-统计加载异常:', loadErrors.value)
  }
}

async function onClear(type: string) {
  try {
    let res: any
    if (type === 'statements') res = await clearBankStatements()
    else if (type === 'invoices') res = await clearInvoiceRecords()
    else if (type === 'businessDocs') res = await clearBusinessDocs()
    else if (type === 'vouchers') res = await clearVouchers()
    else if (type === 'receivables') res = await clearReceivables()
    else if (type === 'payables') res = await clearPayables()
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