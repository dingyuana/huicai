<template>
  <div class="reconciliation-timeline">
    <div v-if="loading" style="text-align:center;padding:20px;color:#909399">加载中...</div>
    <div v-else-if="error" style="text-align:center;padding:20px;color:#E6A23C">{{ error }}</div>
    <div v-else class="timeline-container">
      <!-- 时间轴节点列表 -->
      <div v-for="(node, idx) in timelineNodes" :key="idx" class="timeline-node" @click="onNodeClick(node)">
        <div class="timeline-dot" :class="node.statusClass"></div>
        <div v-if="idx < timelineNodes.length - 1" class="timeline-line"></div>
        <div class="timeline-card" :class="node.statusClass">
          <div class="tl-header">
            <el-tag :type="node.tagType" size="small">{{ node.label }}</el-tag>
            <span class="tl-time">{{ node.time }}</span>
          </div>
          <div class="tl-body">
            <span class="tl-no">{{ node.docNo || '-' }}</span>
            <span class="tl-amount" v-if="node.amount != null">¥{{ fmtAmount(node.amount) }}</span>
          </div>
          <div class="tl-footer" v-if="node.operator">
            <span>{{ node.operator }}</span>
            <span v-if="node.statusText" :class="'tl-status ' + node.statusClass">{{ node.statusText }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getReconciliationTrace, type ReconciliationTraceVO } from '@/api/modules/reconciliation'

const props = defineProps<{ settlementId: number }>()
const emit = defineEmits<{ jump: [path: string] }>()

const loading = ref(false)
const error = ref('')
const traceData = ref<ReconciliationTraceVO | null>(null)

interface TimelineNode {
  label: string
  tagType: 'success' | 'primary' | 'warning' | 'info'
  statusClass: string
  docNo: string | null
  amount: number | null
  time: string
  operator: string | null
  statusText: string | null
  jumpPath: string | null
}

onMounted(async () => {
  if (!props.settlementId) return
  loading.value = true
  try {
    traceData.value = await getReconciliationTrace(props.settlementId)
    buildNodes()
  } catch (e: any) {
    error.value = '追溯数据加载失败: ' + (e?.message || '')
  } finally {
    loading.value = false
  }
})

const timelineNodes = ref<TimelineNode[]>([])

function buildNodes() {
  const nodes: TimelineNode[] = []
  const data = traceData.value
  if (!data) return

  // 1. 银行流水节点
  if (data.upstream?.bankTransaction) {
    const bt = data.upstream.bankTransaction
    nodes.push({
      label: '银行流水', tagType: 'success', statusClass: 'completed',
      docNo: bt.transactionNo, amount: bt.amount,
      time: '', operator: '', statusText: '已完成',
      jumpPath: '/finance/bank-statement',
    })
  }

  // 2. 收款单节点
  if (data.upstream?.receipt) {
    const r = data.upstream.receipt
    nodes.push({
      label: '收款单', tagType: 'success', statusClass: 'completed',
      docNo: r.docNo, amount: r.amount,
      time: '', operator: '', statusText: r.status,
      jumpPath: `/finance/business-doc/detail?id=${r.id}`,
    })
  }

  // 3. 核销单节点
  if (data.settlement) {
    const s = data.settlement
    nodes.push({
      label: '核销单', tagType: 'primary', statusClass: 'active',
      docNo: s.settlementNo, amount: s.amount,
      time: s.createdAt || '', operator: '', statusText: s.status,
      jumpPath: '/arap/reconciliation?tab=settlement',
    })
  }

  // 4. 下游单据节点
  if (data.downstream?.businessDocs?.length) {
    for (const doc of data.downstream.businessDocs) {
      nodes.push({
        label: doc.docType === 'INVOICE_OUT' ? '应收单' : '应付单',
        tagType: 'warning', statusClass: 'completed',
        docNo: doc.docNo, amount: doc.amount,
        time: '', operator: '', statusText: doc.settledAmount === doc.amount ? '已核销' : '部分核销',
        jumpPath: `/finance/business-doc/detail?id=${doc.id}`,
      })
    }
  }

  // 4.1 下游发票节点（G3: trace 填充 downstream.invoices）
  if (data.downstream?.invoices?.length) {
    for (const inv of data.downstream.invoices) {
      nodes.push({
        label: '关联发票', tagType: 'warning', statusClass: 'completed',
        docNo: inv.invoiceNo, amount: inv.amount,
        time: '', operator: '', statusText: inv.status,
        jumpPath: inv.invoiceType === 'INVOICE_OUT' ? '/tax/output-invoice' : '/tax/input-invoice',
      })
    }
  }

  // 5. 凭证节点
  if (data.voucher) {
    const v = data.voucher
    nodes.push({
      label: '会计凭证', tagType: 'success', statusClass: 'completed',
      docNo: v.voucherNo, amount: null,
      time: '', operator: '', statusText: v.status,
      jumpPath: `/finance/voucher/detail?id=${v.id}`,
    })
  }

  // 6. 操作轨迹
  if (data.operationTrail?.length) {
    data.operationTrail.forEach(trail => {
      nodes.push({
        label: trail.operationType === 'CREATE' ? '创建核销' :
               trail.operationType === 'CONFIRM' ? '审核通过' :
               trail.operationType === 'REJECT' ? '驳回' :
               trail.operationType === 'CANCEL' ? '反核销' : trail.operationType,
        tagType: 'info', statusClass: 'completed',
        docNo: null, amount: null,
        time: trail.time || '',
        operator: trail.operator || '',
        statusText: trail.remark || null,
        jumpPath: null,
      })
    })
  }

  timelineNodes.value = nodes
}

function onNodeClick(node: TimelineNode) {
  if (node.jumpPath) emit('jump', node.jumpPath)
}

function fmtAmount(v: number | null | undefined) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}
</script>

<style scoped>
.reconciliation-timeline { padding: 8px 0; }
.timeline-container { position: relative; padding-left: 20px; }
.timeline-node { position: relative; margin-bottom: 12px; cursor: pointer; }
.timeline-dot {
  position: absolute; left: -14px; top: 6px;
  width: 10px; height: 10px; border-radius: 50%;
  background: #C0C4CC; z-index: 1;
}
.timeline-dot.completed { background: #67C23A; }
.timeline-dot.active { background: #409EFF; width: 12px; height: 12px; left: -15px; top: 5px; }
.timeline-line {
  position: absolute; left: -10px; top: 16px;
  width: 2px; height: calc(100% - 4px);
  background: #E4E7ED;
}
.timeline-card {
  background: #F5F7FA; border-radius: 4px; padding: 8px 12px;
  border-left: 3px solid #C0C4CC; transition: background 0.2s;
}
.timeline-card:hover { background: #ECF5FF; }
.timeline-card.completed { border-left-color: #67C23A; }
.timeline-card.active { border-left-color: #409EFF; background: #ECF5FF; }
.tl-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.tl-time { font-size: 12px; color: #909399; }
.tl-body { display: flex; justify-content: space-between; font-size: 13px; }
.tl-no { color: #303133; font-weight: 500; }
.tl-amount { color: #E6A23C; font-weight: 600; }
.tl-footer { display: flex; justify-content: space-between; margin-top: 4px; font-size: 12px; color: #909399; }
.tl-status { font-size: 11px; padding: 1px 4px; border-radius: 2px; }
.tl-status.completed { color: #67C23A; }
.tl-status.active { color: #409EFF; }
</style>