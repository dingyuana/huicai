import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { nextTick } from 'vue'

import ReconciliationWorkbench from '@/views/arap/reconciliation-workbench/ReconciliationWorkbench.vue'

// ===== Mock API =====
vi.mock('@/api/modules/businessDoc', () => ({
  getBusinessDocPage: vi.fn(),
}))

vi.mock('@/api/modules/reconciliation', () => ({
  getReceiptRecommend: vi.fn(),
  getPaymentRecommend: vi.fn(),
  executeReconciliation: vi.fn(),
  preCheckReconciliation: vi.fn(),
  autoFifoReconciliation: vi.fn(),
}))

// ElMessage mock 掉但保留 element-plus 组件导出
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
    { path: '/finance/reconciliation/workbench', name: 'ReconciliationWorkbench', component: { template: '<div />' } },
  ],
})

/**
 * ReconciliationWorkbench 真实挂载测试
 *
 * 覆盖场景：
 * - 组件渲染（标题、Tab、表格）
 * - activeTab 切换触发 fetchData 并传 docTypes
 * - 列表数据过滤（unsettledAmount > 0）
 * - 核销推荐参数（sourceDocType/customerId/vendorId）
 * - 执行核销参数（lowercase sourceDocType）
 * - countExactMatches 精确匹配计数
 */
describe('ReconciliationWorkbench.vue — 核销工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 模拟分页响应：3 条记录，其中 1 条已结清（应被过滤掉）
  const mockReceiptPage = {
    records: [
      { id: 1, docNo: 'SK-001', docType: 'RECEIPT', docDate: '2026-07-01', customerName: '客户A', amount: 10000, unsettledAmount: 5000, settledAmount: 5000, status: 'APPROVED' },
      { id: 2, docNo: 'SK-002', docType: 'RECEIPT', docDate: '2026-07-02', customerName: '客户B', amount: 20000, unsettledAmount: 20000, settledAmount: 0, status: 'APPROVED' },
      { id: 3, docNo: 'SK-003', docType: 'RECEIPT', docDate: '2026-07-03', customerName: '客户C', amount: 3000, unsettledAmount: 0, settledAmount: 3000, status: 'APPROVED' },
    ],
    total: 3,
  }

  const mockPaymentPage = {
    records: [
      { id: 10, docNo: 'FK-001', docType: 'PAYMENT', docDate: '2026-07-01', supplierName: '供应商A', amount: 8000, unsettledAmount: 8000, settledAmount: 0, status: 'APPROVED' },
    ],
    total: 1,
  }

  async function mountWorkbench(initialTab: 'RECEIPT' | 'PAYMENT' = 'RECEIPT') {
    const { getBusinessDocPage } = await import('@/api/modules/businessDoc')
    vi.mocked(getBusinessDocPage).mockImplementation(async (params: any) => {
      if (params.docTypes && params.docTypes.includes('PAYMENT')) {
        return mockPaymentPage as any
      }
      return mockReceiptPage as any
    })

    // el-card / el-button / el-space / el-table 渲染 slot，便于验证文本与交互
    const ElCardSlot = { name: 'ElCard', template: '<div><slot /></div>' }
    const ElButtonSlot = { name: 'ElButton', template: '<button><slot /></button>' }
    const ElSpaceSlot = { name: 'ElSpace', template: '<div><slot /></div>' }
    const ElRadioGroupSlot = {
      name: 'ElRadioGroup',
      emits: ['update:modelValue', 'change'],
      props: ['modelValue'],
      template: '<div><slot /></div>',
    }
    const ElRadioButtonSlot = {
      name: 'ElRadioButton',
      props: ['value'],
      template: '<label><slot /></label>',
    }

    const wrapper = shallowMount(ReconciliationWorkbench, {
      global: {
        plugins: [router],
        stubs: {
          'el-card': ElCardSlot,
          'el-button': ElButtonSlot,
          'el-space': ElSpaceSlot,
          'el-radio-group': ElRadioGroupSlot,
          'el-radio-button': ElRadioButtonSlot,
          'el-form': true,
          'el-form-item': true,
          'el-input': true,
          'el-table': true,
          'el-table-column': true,
          'el-pagination': true,
          'el-dialog': true,
          'el-icon': true,
          'el-tag': true,
          'el-alert': true,
        },
      },
    })

    // 设置初始 tab
    ;(wrapper.vm as any).activeTab = initialTab
    await (wrapper.vm as any).fetchData()
    await nextTick()
    await nextTick()
    return wrapper
  }

  // === 维度 1：组件渲染 ===
  it('应正确渲染核销工作台主界面（标题与 Tab 按钮）', async () => {
    const wrapper = await mountWorkbench()

    expect(wrapper.find('.reconciliation-workbench').exists()).toBe(true)
    expect(wrapper.find('.page-title').text()).toBe('核销工作台')
    const texts = wrapper.text()
    expect(texts).toContain('收款单')
    expect(texts).toContain('付款单')
    expect(texts).toContain('自动核销')
    expect(texts).toContain('刷新')
  })

  // === 维度 2：fetchData 应传 docTypes=['RECEIPT'] ===
  it('收款单 Tab 加载应调用 getBusinessDocPage 并传 docTypes=["RECEIPT"]', async () => {
    const { getBusinessDocPage } = await import('@/api/modules/businessDoc')
    await mountWorkbench('RECEIPT')

    const lastCall = vi.mocked(getBusinessDocPage).mock.calls.at(-1)?.[0]
    expect(lastCall).toBeTruthy()
    expect(lastCall!.docTypes).toEqual(['RECEIPT'])
  })

  // === 维度 3：fetchData 应传 docTypes=['PAYMENT'] ===
  it('付款单 Tab 加载应调用 getBusinessDocPage 并传 docTypes=["PAYMENT"]', async () => {
    const { getBusinessDocPage } = await import('@/api/modules/businessDoc')
    await mountWorkbench('PAYMENT')

    const lastCall = vi.mocked(getBusinessDocPage).mock.calls.at(-1)?.[0]
    expect(lastCall).toBeTruthy()
    expect(lastCall!.docTypes).toEqual(['PAYMENT'])
  })

  // === 维度 4：列表过滤逻辑 — 已结清的记录应被过滤掉 ===
  it('列表应只保留 unsettledAmount > 0 的记录（SK-003 已结清应被过滤）', async () => {
    const wrapper = await mountWorkbench('RECEIPT')

    const list = (wrapper.vm as any).list
    expect(list).toHaveLength(2)  // SK-003（unsettled=0）被过滤
    const docNos = list.map((r: any) => r.docNo)
    expect(docNos).toContain('SK-001')
    expect(docNos).toContain('SK-002')
    expect(docNos).not.toContain('SK-003')
  })

  // === 维度 5：核销推荐参数（收款单）===
  it('收款单 onShowRecommend 应调 getReceiptRecommend 并传 sourceDocType=RECEIPT 与 customerId', async () => {
    const { getReceiptRecommend } = await import('@/api/modules/reconciliation')
    vi.mocked(getReceiptRecommend).mockResolvedValue({ message: 'ok', items: [] })

    const wrapper = await mountWorkbench('RECEIPT')
    const row = mockReceiptPage.records[0]  // SK-001, customerId 需补充
    await (wrapper.vm as any).onShowRecommend({ ...row, customerId: 6 })
    await nextTick()

    expect(getReceiptRecommend).toHaveBeenCalledTimes(1)
    expect(getReceiptRecommend).toHaveBeenCalledWith(1, expect.objectContaining({
      sourceDocType: 'RECEIPT',  // ← 关键参数
      customerId: 6,
      amount: 5000,  // unsettledAmount 优先
    }))
  })

  // === 维度 6：核销推荐参数（付款单）===
  it('付款单 onShowRecommend 应调 getPaymentRecommend 并传 sourceDocType=PAYMENT 与 vendorId', async () => {
    const { getPaymentRecommend } = await import('@/api/modules/reconciliation')
    vi.mocked(getPaymentRecommend).mockResolvedValue({ message: 'ok', items: [] })

    const wrapper = await mountWorkbench('PAYMENT')
    const row = mockPaymentPage.records[0]  // FK-001
    await (wrapper.vm as any).onShowRecommend({ ...row, supplierId: 8 })
    await nextTick()

    expect(getPaymentRecommend).toHaveBeenCalledTimes(1)
    expect(getPaymentRecommend).toHaveBeenCalledWith(10, expect.objectContaining({
      sourceDocType: 'PAYMENT',
      vendorId: 8,
      amount: 8000,
    }))
  })

  // === 维度 7：缺 customerId 的收款单应中断推荐 ===
  it('收款单缺少 customerId 时不应调 getReceiptRecommend', async () => {
    const { getReceiptRecommend } = await import('@/api/modules/reconciliation')
    const wrapper = await mountWorkbench('RECEIPT')
    const row = { ...mockReceiptPage.records[0], customerId: undefined }

    await (wrapper.vm as any).onShowRecommend(row)
    await nextTick()

    expect(getReceiptRecommend).not.toHaveBeenCalled()
  })

  // === 维度 8：执行核销应传 lowercase sourceDocType ===
  it('收款单 onExecuteRecon 应调 executeReconciliation 并传 sourceDocType="receipt"', async () => {
    const { executeReconciliation } = await import('@/api/modules/reconciliation')
    vi.mocked(executeReconciliation).mockResolvedValue({ success: true })

    const wrapper = await mountWorkbench('RECEIPT')
    // 先打开推荐弹窗，设置 currentDoc
    const { getReceiptRecommend } = await import('@/api/modules/reconciliation')
    vi.mocked(getReceiptRecommend).mockResolvedValue({ message: 'ok', items: [] })
    await (wrapper.vm as any).onShowRecommend({ ...mockReceiptPage.records[0], customerId: 6 })
    await nextTick()

    await (wrapper.vm as any).onExecuteRecon({
      targetDocId: 100,
      targetDocType: 'INVOICE_OUT',
      suggestedAmount: 5000,
      matchScore: 0.95,
    })
    await nextTick()

    expect(executeReconciliation).toHaveBeenCalledTimes(1)
    const args = vi.mocked(executeReconciliation).mock.calls[0][0]
    expect(args.sourceDocType).toBe('receipt')  // lowercase
    expect(args.sourceDocId).toBe(1)
    expect(args.targetDocId).toBe(100)
    expect(args.amount).toBe(5000)
    expect(args.matchMethod).toBe('AUTO')
  })

  // === 维度 9：付款单执行核销 sourceDocType="payment" ===
  it('付款单 onExecuteRecon 应传 sourceDocType="payment"', async () => {
    const { executeReconciliation, getPaymentRecommend } = await import('@/api/modules/reconciliation')
    vi.mocked(executeReconciliation).mockResolvedValue({ success: true })
    vi.mocked(getPaymentRecommend).mockResolvedValue({ message: 'ok', items: [] })

    const wrapper = await mountWorkbench('PAYMENT')
    await (wrapper.vm as any).onShowRecommend({ ...mockPaymentPage.records[0], supplierId: 8 })
    await nextTick()

    await (wrapper.vm as any).onExecuteRecon({
      targetDocId: 200,
      targetDocType: 'INVOICE_IN',
      suggestedAmount: 8000,
      matchScore: 0.9,
    })
    await nextTick()

    const args = vi.mocked(executeReconciliation).mock.calls[0][0]
    expect(args.sourceDocType).toBe('payment')
    expect(args.sourceDocId).toBe(10)
    expect(args.targetDocId).toBe(200)
  })

  // === 维度 10：countExactMatches — L1/L2/L3 精确匹配计数 ===
  it('countExactMatches 应仅统计 L1/L2/L3 项', async () => {
    const wrapper = await mountWorkbench('RECEIPT')
    // 直接注入 recommendResult 模拟数据
    ;(wrapper.vm as any).recommendResult = {
      message: 'ok',
      items: [
        { matchLevel: 'L1', targetDocId: 1 },
        { matchLevel: 'L2', targetDocId: 2 },
        { matchLevel: 'L3', targetDocId: 3 },
        { matchLevel: 'L4', targetDocId: 4 },  // 不算精确
        { matchLevel: 'L5', targetDocId: 5 },  // 不算精确
        { matchLevel: 'L6', targetDocId: 6 },  // 不算精确
      ],
    }

    expect((wrapper.vm as any).countExactMatches()).toBe(3)
  })

  // === 维度 11：空数据边界 ===
  it('API 返回空列表时 list 与 total 应为空', async () => {
    const { getBusinessDocPage } = await import('@/api/modules/businessDoc')
    vi.mocked(getBusinessDocPage).mockResolvedValue({ records: [], total: 0 } as any)

    const wrapper = shallowMount(ReconciliationWorkbench, {
      global: {
        plugins: [router],
        stubs: {
          'el-card': { name: 'ElCard', template: '<div><slot /></div>' },
          'el-button': { name: 'ElButton', template: '<button><slot /></button>' },
          'el-radio-group': true, 'el-radio-button': true,
          'el-form': true, 'el-form-item': true, 'el-input': true,
          'el-table': true, 'el-table-column': true, 'el-pagination': true,
          'el-dialog': true, 'el-icon': true, 'el-tag': true, 'el-alert': true, 'el-space': true,
        },
      },
    })
    await (wrapper.vm as any).fetchData()
    await nextTick()

    expect((wrapper.vm as any).list).toHaveLength(0)
    expect((wrapper.vm as any).total).toBe(0)
  })

  // === 维度 12：buildPeriod — 缺 period 时回退到当前年月 ===
  it('buildPeriod 在 doc.period 缺失时应回退到当前年月（YYYYMM）', async () => {
    const wrapper = await mountWorkbench('RECEIPT')
    const now = new Date()
    const expected = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`

    const result = (wrapper.vm as any).buildPeriod({ /* 无 period */ } as any)
    expect(result).toBe(expected)
  })

  it('buildPeriod 在 doc.period 存在时应直接返回该 period', async () => {
    const wrapper = await mountWorkbench('RECEIPT')

    const result = (wrapper.vm as any).buildPeriod({ period: '202607' } as any)
    expect(result).toBe('202607')
  })

  // === 维度 13：onTabChange 应重置页码并触发 fetchData ===
  it('onTabChange 应重置 current=1 并重新加载数据', async () => {
    const { getBusinessDocPage } = await import('@/api/modules/businessDoc')
    const wrapper = await mountWorkbench('RECEIPT')
    vi.mocked(getBusinessDocPage).mockClear()

    ;(wrapper.vm as any).query.current = 5
    ;(wrapper.vm as any).onTabChange()
    await nextTick()
    await nextTick()

    expect((wrapper.vm as any).query.current).toBe(1)
    expect(getBusinessDocPage).toHaveBeenCalled()
  })
})
