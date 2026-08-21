import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import ReconciliationWorkbench from '@/views/arap/reconciliation-workbench/ReconciliationWorkbench.vue'

vi.mock('@/api/modules/reconciliation', () => ({
  pageReceivable: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  pagePayable: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  getReceiptRecommend: vi.fn(),
  getPaymentRecommend: vi.fn(),
  executeReconciliation: vi.fn(),
}))

vi.mock('@/api/modules/businessDoc', () => ({
  getBusinessDocPage: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  DOC_TYPE_LABELS: { INVOICE_OUT: '销售发票', RECEIPT: '收款单', PAYMENT: '付款单' },
  DOC_STATUS_LABELS: { DRAFT: '草稿', CONFIRMED: '已确认', SETTLED: '已结清' },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  }
})

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: { template: '<div />' } },
    { path: '/arap/reconciliation', name: 'ReconciliationWorkbench', component: { template: '<div />' } },
  ],
})

describe('ReconciliationWorkbench — 核销工作台组件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ===== 维度 1: 基础挂载 =====
  it('挂载成功', () => {
    const wrapper = shallowMount(ReconciliationWorkbench, {
      global: { plugins: [router] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  // ===== 维度 2: 推荐 API 调用 =====
  it('getReceiptRecommend 被调用时返回推荐数据', async () => {
    const { getReceiptRecommend } = await import('@/api/modules/reconciliation')
    const mockRecommend = {
      message: 'ok',
      items: [{ targetDocId: 100, targetDocNo: 'YS-001', targetDocType: 'INVOICE_OUT', originalAmount: 10000, unsettledAmount: 5000, matchScore: 0.95, matchLevel: 'HIGH', suggestedAmount: 5000 }],
    }
    ;(getReceiptRecommend as any).mockResolvedValue(mockRecommend)

    const result = await getReceiptRecommend(1, { sourceDocType: 'RECEIPT', customerId: 6, amount: 1000 })
    expect(result.items).toHaveLength(1)
    expect(result.items[0].targetDocNo).toBe('YS-001')
  })

  it('getPaymentRecommend 被调用时返回推荐数据', async () => {
    const { getPaymentRecommend } = await import('@/api/modules/reconciliation')
    const mockRecommend = {
      message: 'ok',
      items: [{ targetDocId: 200, targetDocNo: 'CG-001', targetDocType: 'INVOICE_IN', originalAmount: 15000, unsettledAmount: 15000, matchScore: 0.88, matchLevel: 'MEDIUM', suggestedAmount: 8000 }],
    }
    ;(getPaymentRecommend as any).mockResolvedValue(mockRecommend)

    const result = await getPaymentRecommend(2, { sourceDocType: 'PAYMENT', vendorId: 8, amount: 2000 })
    expect(result.items).toHaveLength(1)
    expect(result.items[0].targetDocNo).toBe('CG-001')
  })

  // ===== 维度 3: 执行核销 =====
  it('executeReconciliation 被调用时返回成功', async () => {
    const { executeReconciliation } = await import('@/api/modules/reconciliation')
    ;(executeReconciliation as any).mockResolvedValue({ success: true })

    const result = await executeReconciliation({
      sourceDocType: 'RECEIPT', sourceDocId: 1, targetDocType: 'INVOICE_OUT',
      targetDocId: 100, amount: 5000, matchScore: 0.95, matchMethod: 'MANUAL',
    })

    expect(result.success).toBe(true)
    expect(executeReconciliation).toHaveBeenCalledWith(expect.objectContaining({
      sourceDocType: 'RECEIPT',
      sourceDocId: 1,
    }))
  })
})