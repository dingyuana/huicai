<template>
  <div class="clear-data">
    <div class="page-header">
      <h2 class="page-title">数据维护</h2>
      <el-button text @click="fetchStats">
        <el-icon style="margin-right:4px"><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <el-alert type="warning" :closable="false" class="warning-banner" show-icon>
      <template #title>
        <strong>注意：</strong>清空操作将物理删除数据，不可恢复。请谨慎操作。
      </template>
    </el-alert>

    <!-- 数据概览 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="s in statItems" :key="s.key">
        <el-card shadow="never" class="stat-card" :body-style="{ padding: '16px' }">
          <div class="stat-inner">
            <el-icon :size="28" :class="s.color"><component :is="s.icon" /></el-icon>
            <div class="stat-text">
              <span class="stat-num">{{ stats[s.key as keyof typeof stats] }}</span>
              <span class="stat-label">{{ s.label }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据清理 -->
    <section class="section">
      <h3 class="section-title">数据清理</h3>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" v-for="item in normalOps" :key="item.type">
          <el-card shadow="hover" class="action-card">
            <div class="action-body">
              <el-icon :size="20" :class="item.color" class="action-icon"><component :is="item.icon" /></el-icon>
              <div class="action-info">
                <strong>{{ item.title }}</strong>
                <p>{{ item.desc }}</p>
              </div>
              <el-popconfirm
                :title="`确定${item.title}?`"
                confirm-button-text="确认清空"
                @confirm="onClear(item.type)"
              >
                <template #reference>
                  <el-button :type="item.btnType" round size="small">{{ item.btnText }}</el-button>
                </template>
              </el-popconfirm>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </section>

    <!-- 危险操作 -->
    <section class="section danger-zone">
      <h3 class="section-title danger-title">
        <el-icon style="margin-right:6px"><WarningFilled /></el-icon>危险操作
      </h3>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" v-for="item in dangerOps" :key="item.type">
          <el-card shadow="hover" class="action-card danger-card">
            <div class="action-body">
              <el-icon :size="20" class="danger-icon"><component :is="item.icon" /></el-icon>
              <div class="action-info">
                <strong>{{ item.title }}</strong>
                <p>{{ item.desc }}</p>
              </div>
              <el-popconfirm
                :title="`⚠️ 确定${item.title}?此操作不可恢复!`"
                confirm-button-text="确认全部清空"
                @confirm="onClear(item.type)"
              >
                <template #reference>
                  <el-button type="danger" round size="small">{{ item.btnText }}</el-button>
                </template>
              </el-popconfirm>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Refresh, Document, DataBoard, Tickets, PriceTag,
  List, Coin, WarningFilled, Delete, FolderDelete,
  Remove, CircleCheck
} from '@element-plus/icons-vue'
import {
  clearBankStatements, clearInvoiceRecords, clearBusinessDocs, clearVouchers,
  clearAll, clearReceivables, clearPayables, clearSettlements
} from '@/api/modules/system'
import { getBankStatementPage } from '@/api/modules/bankStatement'
import { getBusinessDocPage } from '@/api/modules/businessDoc'
import { getVoucherPage } from '@/api/modules/voucher'
import { pageReceivable, pagePayable } from '@/api/modules/arap'

const stats = ref({ statements: 0, invoices: 0, receivables: 0, payables: 0, businessDocs: 0, vouchers: 0 })
const loadErrors = ref<string[]>([])

const statItems = [
  { key: 'statements', label: '银行流水', icon: Document, color: 'blue' },
  { key: 'invoices', label: '发票记录', icon: Tickets, color: 'green' },
  { key: 'receivables', label: '应收明细', icon: PriceTag, color: 'orange' },
  { key: 'payables', label: '应付明细', icon: PriceTag, color: 'purple' },
  { key: 'businessDocs', label: '业务单据', icon: DataBoard, color: 'cyan' },
  { key: 'vouchers', label: '生成凭证', icon: List, color: 'indigo' },
]

const normalOps: Array<{ type: string; title: string; desc: string; icon: any; color: string; btnType: 'success' | 'warning' | 'info' | 'primary' | 'danger'; btnText: string }> = [
  { type: 'statements', title: '清空银行流水', desc: '删除所有银行流水、自动生成的业务单据(来源: FROM_BANK_TXN)及凭证', icon: Delete, color: 'orange', btnType: 'warning', btnText: '清空' },
  { type: 'invoices', title: '清空发票记录', desc: '删除所有发票导入记录、生成的业务单据(来源: INVOICE_IMPORT)及凭证', icon: FolderDelete, color: 'orange', btnType: 'warning', btnText: '清空' },
  { type: 'vouchers', title: '清空所有凭证', desc: '删除所有凭证及分录(保留业务单据、流水、发票；单据状态回退草稿)', icon: Remove, color: 'orange', btnType: 'warning', btnText: '清空' },
  { type: 'businessDocs', title: '清空业务单据', desc: '删除所有业务单据及明细行(保留银行流水、发票、凭证)', icon: DataBoard, color: 'orange', btnType: 'warning', btnText: '清空' },
  { type: 'receivables', title: '清空应收明细', desc: '删除所有应收明细及关联核销记录(保留业务单据、凭证)', icon: Coin, color: 'orange', btnType: 'warning', btnText: '清空' },
  { type: 'payables', title: '清空应付明细', desc: '删除所有应付明细及关联核销记录(保留业务单据、凭证)', icon: Coin, color: 'orange', btnType: 'warning', btnText: '清空' },
]

const dangerOps = [
  { type: 'settlements', title: '清空核销数据', desc: '清空所有核销单、核销明细、核销日志，重置业务单据核销金额', icon: CircleCheck, btnText: '清空核销' },
  { type: 'all', title: '清空全部数据', desc: '清空银行流水 + 发票记录 + 相关业务单据 + 相关凭证(草稿)', icon: WarningFilled, btnText: '全部清空' },
]

async function fetchStats() {
  loadErrors.value = []
  const tryFetch = async (label: string, fn: () => Promise<any>, setter: (v: number) => void) => {
    try {
      const res = await fn()
      if (Array.isArray(res)) setter(res.length)
      else setter(res?.total || 0)
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
    else if (type === 'settlements') res = await clearSettlements()
    else res = await clearAll()
    ElMessage.success(res.message || '清空完成')
    await fetchStats()
  } catch { /* handled */ }
}

onMounted(fetchStats)
</script>

<style scoped>
.clear-data {
  max-width: 1100px;
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
  margin: 0;
}

.warning-banner {
  margin-bottom: 20px;
}

/* ---- Stats ---- */
.stat-row {
  margin-bottom: 28px;
}

.stat-card {
  border-radius: 8px;
  transition: box-shadow 0.2s;
}
.stat-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.stat-inner {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-card .blue  { color: #409eff; }
.stat-card .green { color: #67c23a; }
.stat-card .orange { color: #e6a23c; }
.stat-card .purple { color: #9b59b6; }
.stat-card .cyan  { color: #36cfc9; }
.stat-card .indigo { color: #5c6bc0; }

/* ---- Sections ---- */
.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px 4px;
  color: #303133;
  display: flex;
  align-items: center;
}

/* ---- Action Cards ---- */
.action-card {
  border-radius: 8px;
  margin-bottom: 12px;
  transition: box-shadow 0.2s;
}

.action-body {
  display: flex;
  align-items: center;
  gap: 14px;
}

.action-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #fef0f0;
}
.action-icon.orange { background: #fdf6ec; color: #e6a23c; }

.action-info {
  flex: 1;
  min-width: 0;
}
.action-info strong {
  font-size: 14px;
  display: block;
  margin-bottom: 2px;
}
.action-info p {
  margin: 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

/* ---- Danger Zone ---- */
.danger-zone {
  background: #fef0f0;
  border-radius: 10px;
  padding: 16px 20px 4px;
  border: 1px solid #fde2e2;
}

.danger-title {
  color: #f56c6c;
}

.danger-card {
  border-color: #fde2e2 !important;
  background: #fff !important;
}

.danger-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #fef0f0;
  color: #f56c6c;
  flex-shrink: 0;
}
</style>
