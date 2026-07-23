import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { nextTick } from 'vue'

import BusinessDocDetail from '@/views/finance/business-doc/BusinessDocDetail.vue'

// ===== Mock API =====
vi.mock('@/api/modules/businessDoc', () => ({
  getBusinessDoc: vi.fn(),
  submitBusinessDoc: vi.fn(),
  approveBusinessDoc: vi.fn(),
  rejectBusinessDoc: vi.fn(),
  generateVoucherFromDoc: vi.fn(),
  DOC_TYPE_LABELS: {
    RECEIPT: '收款单',
    PAYMENT: '付款单',
    INVOICE_OUT: '应收单（销售）',
  },
  DOC_STATUS_LABELS: {
    DRAFT: '草稿',
    SUBMITTED: '已提交',
    APPROVED: '已审批',
    VOUCHERED: '已生成凭证',
  },
}))

vi.mock('@/api/modules/reconciliation', () => ({
  getReceiptRecommend: vi.fn(),
  getPaymentRecommend: vi.fn(),
  executeReconciliation: vi.fn(),
}))

// ElMessage 在测试环境下直接 mock 掉，但保留 element-plus 组件导出
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
    { path: '/finance/business-doc', name: 'BusinessDocList', component: { template: '<div />' } },
    { path: '/finance/business-doc/:id', name: 'BusinessDocEdit', component: { template: '<div />' } },
  ],
})

/**
 * BusinessDocDetail 组件真实挂载测试
 *
 * 覆盖场景：
 * - canReconcile 计算属性（曾因漏传 sourceDocType 导致 500）
 * - 推荐 API 调用参数回归测试
 * - 状态机渲染（DRAFT/SUBMITTED/APPROVED 不同按钮显示）
 */
describe('BusinessDocDetail.vue — 单据详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 模拟一个已审核的收款单
  const mockReceiptDoc = {
    id: 20,
    docNo: 'SH202607080001',
    docType: 'RECEIPT',
    docDate: '2026-07-08',
    period: '202607',
    amount: 1000,
    status: 'APPROVED',
    customerId: 6,
    customerName: '测试客户',
    unsettledAmount: 1000,
    settledAmount: 0,
    summary: '测试收款',
    entries: [],
  }

  // 模拟一个已审核的付款单
  const mockPaymentDoc = {
    id: 30,
    docNo: 'FK202607080001',
    docType: 'PAYMENT',
    docDate: '2026-07-08',
    period: '202607',
    amount: 2000,
    status: 'APPROVED',
    supplierId: 8,
    supplierName: '测试供应商',
    unsettledAmount: 2000,
    settledAmount: 0,
    summary: '测试付款',
    entries: [],
  }

  // 模拟一个草稿单据（不应显示去核销按钮）
  const mockDraftDoc = { ...mockReceiptDoc, id: 40, status: 'DRAFT' }

  async function mountWithDoc(doc: any, docIdProp?: number) {
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    vi.mocked(getBusinessDoc).mockResolvedValue(doc as any)

    // el-card / el-button 必须渲染默认 slot，否则内部 .page-header 与按钮文本不会出现在 DOM 中
    const ElCardSlot = { name: 'ElCard', template: '<div><slot /></div>' }
    const ElButtonSlot = { name: 'ElButton', template: '<button><slot /></button>' }

    const wrapper = shallowMount(BusinessDocDetail, {
      props: { docId: docIdProp ?? doc.id },
      global: {
        plugins: [router],
        stubs: {
          'el-card': ElCardSlot,
          'el-button': ElButtonSlot,
          'el-descriptions': true,
          'el-descriptions-item': true,
          'el-tag': true,
          'el-table': true,
          'el-table-column': true,
          'el-drawer': true,
          'el-icon': true,
        },
      },
    })
    // 等待 onMounted 内的异步 fetchData 完成
    await nextTick()
    await nextTick()
    return wrapper
  }

  // === 维度 1：组件渲染 ===
  it('应正确渲染单据详情页标题并加载单据数据', async () => {
    const wrapper = await mountWithDoc(mockReceiptDoc)

    expect(wrapper.find('.doc-detail').exists()).toBe(true)
    expect(wrapper.find('.page-title').text()).toBe('单据详情')
    // docNo 渲染在 el-descriptions 内（被 stub 掉），通过 vm 验证数据已加载
    expect((wrapper.vm as any).doc.docNo).toBe('SH202607080001')
    expect((wrapper.vm as any).doc.docType).toBe('RECEIPT')
  })

  // === 维度 2：canReconcile 计算属性 — 已审核收款单 ===
  it('已审核收款单 canReconcile 应为 true 并显示去核销按钮', async () => {
    const wrapper = await mountWithDoc(mockReceiptDoc)

    expect((wrapper.vm as any).canReconcile).toBe(true)
    // 找到所有按钮，验证存在「去核销」
    const buttons = wrapper.findAll('button')
    const goReconcile = buttons.find(b => b.text().includes('去核销'))
    expect(goReconcile).toBeTruthy()
  })

  // === 维度 3：canReconcile — 草稿状态 ===
  it('草稿状态单据 canReconcile 应为 false 且不显示去核销按钮', async () => {
    const wrapper = await mountWithDoc(mockDraftDoc)

    expect((wrapper.vm as any).canReconcile).toBe(false)
    const buttons = wrapper.findAll('button')
    const goReconcile = buttons.find(b => b.text().includes('去核销'))
    expect(goReconcile).toBeFalsy()
  })

  // === 维度 4：canReconcile — 缺失 customerId 的收款单 ===
  it('缺少 customerId 的收款单 canReconcile 应为 false', async () => {
    const doc = { ...mockReceiptDoc, customerId: undefined }
    const wrapper = await mountWithDoc(doc)

    expect((wrapper.vm as any).canReconcile).toBe(false)
  })

  // === 维度 5：canReconcile — SUBMITTED 状态不应可核销 ===
  it('SUBMITTED 状态单据 canReconcile 应为 false', async () => {
    const doc = { ...mockReceiptDoc, status: 'SUBMITTED' }
    const wrapper = await mountWithDoc(doc)

    expect((wrapper.vm as any).canReconcile).toBe(false)
  })

  // === 维度 6：canReconcile — 付款单必须有 supplierId ===
  it('缺少 supplierId 的付款单 canReconcile 应为 false', async () => {
    const doc = { ...mockPaymentDoc, supplierId: undefined }
    const wrapper = await mountWithDoc(doc)

    expect((wrapper.vm as any).canReconcile).toBe(false)
  })

  // === 维度 7：核销推荐参数（500 bug 回归）===
  it('收款单调用核销推荐应携带 sourceDocType=RECEIPT 等参数', async () => {
    const { getReceiptRecommend } = await import('@/api/modules/reconciliation')
    vi.mocked(getReceiptRecommend).mockResolvedValue({ message: 'ok', items: [] })

    const wrapper = await mountWithDoc(mockReceiptDoc)
    await (wrapper.vm as any).onOpenReconcile()
    await nextTick()

    expect(getReceiptRecommend).toHaveBeenCalledTimes(1)
    expect(getReceiptRecommend).toHaveBeenCalledWith(20, expect.objectContaining({
      sourceDocType: 'RECEIPT',  // ← 上次 500 bug 的根因：漏传 sourceDocType
      customerId: 6,
      amount: 1000,
    }))
  })

  // === 维度 8：付款单应调 getPaymentRecommend 并传 sourceDocType=PAYMENT ===
  it('付款单调用核销推荐应调 getPaymentRecommend 并传 sourceDocType=PAYMENT 与 vendorId', async () => {
    const { getPaymentRecommend } = await import('@/api/modules/reconciliation')
    vi.mocked(getPaymentRecommend).mockResolvedValue({ message: 'ok', items: [] })

    const wrapper = await mountWithDoc(mockPaymentDoc)
    await (wrapper.vm as any).onOpenReconcile()
    await nextTick()

    expect(getPaymentRecommend).toHaveBeenCalledTimes(1)
    expect(getPaymentRecommend).toHaveBeenCalledWith(30, expect.objectContaining({
      sourceDocType: 'PAYMENT',
      vendorId: 8,
      amount: 2000,
    }))
  })

  // === 维度 9：草稿状态渲染对应操作按钮（编辑/提交）===
  it('草稿状态应显示「编辑」「提交」按钮，不显示「审批/驳回/去核销」', async () => {
    const wrapper = await mountWithDoc(mockDraftDoc)

    const texts = wrapper.findAll('button').map(b => b.text())
    expect(texts.some(t => t.includes('编辑'))).toBe(true)
    expect(texts.some(t => t.includes('提交'))).toBe(true)
    expect(texts.some(t => t.includes('审批'))).toBe(false)
    expect(texts.some(t => t.includes('驳回'))).toBe(false)
    expect(texts.some(t => t.includes('去核销'))).toBe(false)
  })

  // === 维度 10：SUBMITTED 状态渲染对应操作按钮 ===
  it('SUBMITTED 状态应显示「审批」「驳回」按钮，不显示「编辑/提交/去核销」', async () => {
    const doc = { ...mockReceiptDoc, status: 'SUBMITTED' }
    const wrapper = await mountWithDoc(doc)

    const texts = wrapper.findAll('button').map(b => b.text())
    expect(texts.some(t => t.includes('审批'))).toBe(true)
    expect(texts.some(t => t.includes('驳回'))).toBe(true)
    expect(texts.some(t => t.includes('编辑'))).toBe(false)
    expect(texts.some(t => t.includes('去核销'))).toBe(false)
  })

  // === 维度 11：执行核销应调 executeReconciliation 并传 sourceDocType ===
  it('执行核销应调用 executeReconciliation 并传 lowercase sourceDocType', async () => {
    const { getReceiptRecommend, executeReconciliation } = await import('@/api/modules/reconciliation')
    vi.mocked(getReceiptRecommend).mockResolvedValue({
      message: 'ok',
      items: [{
        targetDocId: 100,
        targetDocNo: 'YS-001',
        targetDocType: 'INVOICE_OUT',
        originalAmount: 1000,
        unsettledAmount: 1000,
        matchScore: 0.95,
        matchLevel: 'L4',
        suggestedAmount: 1000,
      }],
    })
    vi.mocked(executeReconciliation).mockResolvedValue({ success: true })

    const wrapper = await mountWithDoc(mockReceiptDoc)
    await (wrapper.vm as any).onOpenReconcile()
    await nextTick()

    await (wrapper.vm as any).onExecuteRecon({
      targetDocId: 100,
      targetDocType: 'INVOICE_OUT',
      suggestedAmount: 1000,
      matchScore: 0.95,
    })
    await nextTick()

    expect(executeReconciliation).toHaveBeenCalledTimes(1)
    const callArgs = vi.mocked(executeReconciliation).mock.calls[0][0]
    expect(callArgs.sourceDocType).toBe('receipt')  // 收款单在 execute 时转 lowercase
    expect(callArgs.sourceDocId).toBe(20)
    expect(callArgs.targetDocId).toBe(100)
    expect(callArgs.amount).toBe(1000)
  })
})
