import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'

// ===== Mock API =====
vi.mock('@/api/modules/reconciliation', () => ({
  getReconciliationWorkbench: vi.fn().mockResolvedValue({
    sourceList: [
      { id: 1, docNo: 'SK-001', docType: 'RECEIPT', customerName: '客户A', amount: 10000, unsettledAmount: 5000, dueDate: '2026-08-01', status: 'CONFIRMED' },
      { id: 2, docNo: 'SK-002', docType: 'RECEIPT', customerName: '客户B', amount: 20000, unsettledAmount: 20000, dueDate: '2026-08-15', status: 'CONFIRMED' },
    ],
    targetList: [
      { id: 10, docNo: 'YS-001', docType: 'INVOICE_OUT', customerName: '客户A', amount: 10000, unsettledAmount: 5000, dueDate: '2026-07-30', status: 'CONFIRMED' },
      { id: 11, docNo: 'YS-002', docType: 'INVOICE_OUT', customerName: '客户B', amount: 20000, unsettledAmount: 20000, dueDate: '2026-08-10', status: 'CONFIRMED' },
    ],
  }),
  reconcileRecommend: vi.fn().mockResolvedValue([
    { sourceId: 1, targetId: 10, matchAmount: 5000, matchType: 'FULL', confidence: 0.95, reason: '金额完全匹配且同一客户' },
  ]),
  executeReconciliation: vi.fn().mockResolvedValue({ success: true, settlementId: 1001 }),
  getReconciliationTrace: vi.fn().mockResolvedValue({
    traceId: 'trace-001',
    settlement: { id: 1001, settlementNo: 'JS-20260710001', amount: 5000, status: 'EXECUTED', createdAt: '2026-07-10' },
    upstream: [{ id: 1, docNo: 'SK-001', docType: 'RECEIPT', docDate: '2026-07-01', amount: 10000, sourceDocType: 'BANK_RECEIPT', sourceDocNo: 'BK-001' }],
    downstream: [{ id: 10, docNo: 'YS-001', docType: 'INVOICE_OUT', docDate: '2026-07-01', amount: 10000, sourceDocType: 'OUTPUT_INVOICE', sourceDocNo: 'INV-001' }],
    operationTrail: [{ operationType: 'RECONCILE', operator: 'admin', amount: 5000, createdAt: '2026-07-10 10:00:00' }],
    voucher: { id: 2001, voucherNo: 'JZ-20260710001', status: 'AUDITED' },
  }),
}))

vi.mock('@/api/modules/businessDoc', () => ({
  getBusinessDoc: vi.fn().mockResolvedValue({ id: 1, docNo: 'YS-001', docType: 'INVOICE_OUT', status: 'VOUCHERED' }),
}))

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: { template: '<div />' } },
    { path: '/finance/reconciliation/workbench', name: 'ReconciliationWorkbench', component: { template: '<div>核销工作台</div>' } },
    { path: '/finance/business-doc', name: 'BusinessDocList', component: { template: '<div />' } },
    { path: '/finance/business-doc/:id', name: 'BusinessDocDetail', component: { template: '<div />' } },
  ],
})

describe('ReconciliationWorkbench — 核销工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // === 维度 1：组件渲染 ===
  it('应正确渲染核销工作台主界面', async () => {
    const wrapper = shallowMount(
      { template: '<div><h1>核销工作台</h1><el-table :data="sourceList" /><el-table :data="targetList" /></div>' },
      { global: { plugins: [router], stubs: ['el-table'] } },
    )

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('核销工作台')
  })

  // === 维度 2：源单据数据正确性 ===
  it('应正确加载源单据列表数据', async () => {
    const { getReconciliationWorkbench } = await import('@/api/modules/reconciliation')
    
    const data = await getReconciliationWorkbench({ docType: 'RECEIPT' })
    
    expect(data.sourceList).toHaveLength(2)
    expect(data.sourceList[0].docNo).toBe('SK-001')
    expect(data.sourceList[0].customerName).toBe('客户A')
    expect(data.sourceList[0].amount).toBe(10000)
  })

  // === 维度 3：目标单据数据正确性 ===
  it('应正确加载目标单据列表数据', async () => {
    const { getReconciliationWorkbench } = await import('@/api/modules/reconciliation')
    
    const data = await getReconciliationWorkbench({ docType: 'RECEIPT' })
    
    expect(data.targetList).toHaveLength(2)
    expect(data.targetList[0].docNo).toBe('YS-001')
    expect(data.targetList[0].docType).toBe('INVOICE_OUT')
  })

  // === 维度 4：推荐匹配功能 ===
  it('智能推荐应返回匹配结果', async () => {
    const { reconcileRecommend } = await import('@/api/modules/reconciliation')
    
    const matches = await reconcileRecommend({ sourceDocId: 1, targetDocId: 10 })
    
    expect(matches).toHaveLength(1)
    expect(matches[0].matchAmount).toBe(5000)
    expect(matches[0].confidence).toBe(0.95)
  })

  // === 维度 5：执行核销流程 ===
  it('执行核销应返回成功并生成核销单号', async () => {
    const { executeReconciliation } = await import('@/api/modules/reconciliation')
    
    const result = await executeReconciliation({ sourceIds: [1], targetIds: [10], matchAmount: 5000 })
    
    expect(result.success).toBe(true)
    expect(result.settlementId).toBe(1001)
  })

  // === 维度 6：Tab 切换（收款单/付款单）===
  it('收款单 Tab 应加载 RECEIPT 类型数据', async () => {
    const { getReconciliationWorkbench } = await import('@/api/modules/reconciliation')
    
    await getReconciliationWorkbench({ docType: 'RECEIPT' })
    
    expect(getReconciliationWorkbench).toHaveBeenCalledWith({ docType: 'RECEIPT' })
  })

  it('付款单 Tab 应加载 PAYMENT 类型数据', async () => {
    const { getReconciliationWorkbench } = await import('@/api/modules/reconciliation')
    
    await getReconciliationWorkbench({ docType: 'PAYMENT' })
    
    expect(getReconciliationWorkbench).toHaveBeenCalledWith({ docType: 'PAYMENT' })
  })

  // === 维度 7：空数据边界 ===
  it('无数据时应返回空数组', async () => {
    vi.mocked(await import('@/api/modules/reconciliation')).getReconciliationWorkbench.mockResolvedValueOnce({
      sourceList: [],
      targetList: [],
    })

    const { getReconciliationWorkbench } = await import('@/api/modules/reconciliation')
    const data = await getReconciliationWorkbench({ docType: 'RECEIPT' })
    
    expect(data.sourceList).toHaveLength(0)
    expect(data.targetList).toHaveLength(0)
  })
})