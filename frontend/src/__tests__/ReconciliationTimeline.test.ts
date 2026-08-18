import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ReconciliationTimeline from '@/views/arap/reconciliation/ReconciliationTimeline.vue'

// Mock API module — must match component's actual imports
vi.mock('@/api/modules/reconciliation', () => ({
  getReconciliationTrace: vi.fn(),
}))

function mockTrace() {
  return {
    traceId: 'TRACE001',
    settlement: { id: 1, settlementNo: 'STL2026070001', amount: 1000, status: 'CONFIRMED', createdAt: '2026-07-15T10:00:00' },
    upstream: {
      bankTransaction: { id: 1, transactionNo: 'TXN2026070001', amount: 1000, counterAccount: 'ACME' },
      receipt: { id: 2, docNo: 'RC2026070001', amount: 1000, status: 'CONFIRMED' },
    },
    downstream: {
      businessDocs: [
        { id: 3, docNo: 'YS2026070001', docType: 'INVOICE_OUT', amount: 1000, settledAmount: 1000, unsettledAmount: 0 },
      ],
      invoices: [
        { id: 4, invoiceNo: 'INV2026070001', amount: 1000, status: 'ISSUED', invoiceType: 'INVOICE_OUT' },
      ],
    },
    operationTrail: [
      { operationType: 'CREATE', operator: 'admin', time: '2026-07-15T09:00:00', remark: '创建核销' },
      { operationType: 'CONFIRM', operator: 'admin', time: '2026-07-15T10:00:00', remark: '审核通过' },
    ],
    voucher: { id: 5, voucherNo: 'V2026070001', status: 'POSTED' },
  }
}

describe('ReconciliationTimeline — 核销全链路追溯时间轴', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ===== 维度 1: 挂载 + 数据加载 =====
  it('onMounted 时调用 getReconciliationTrace', async () => {
    const { getReconciliationTrace } = await import('@/api/modules/reconciliation')
    vi.mocked(getReconciliationTrace).mockResolvedValue(mockTrace() as any)

    shallowMount(ReconciliationTimeline, { props: { settlementId: 1 } })
    await nextTick()
    await nextTick()

    expect(getReconciliationTrace).toHaveBeenCalledWith(1)
  })

  // ===== 维度 2: 节点生成（BDD 场景 6 — jumpPath 规则）=====
  it('生成全部节点且业务节点 jumpPath 非空', async () => {
    const { getReconciliationTrace } = await import('@/api/modules/reconciliation')
    vi.mocked(getReconciliationTrace).mockResolvedValue(mockTrace() as any)

    const wrapper = shallowMount(ReconciliationTimeline, { props: { settlementId: 1 } })
    await nextTick()
    await nextTick()

    const nodes = (wrapper.vm as any).timelineNodes
    // 银行流水 + 收款单 + 核销单 + 应收单 + 关联发票 + 会计凭证 + 2 操作轨迹 = 8
    expect(nodes).toHaveLength(8)

    const byLabel = (label: string) => nodes.find((n: any) => n.label === label)
    expect(byLabel('银行流水').jumpPath).toBe('/finance/bank-statement')
    expect(byLabel('收款单').jumpPath).toBe('/finance/business-doc/detail?id=2')
    expect(byLabel('核销单').jumpPath).toBe('/arap/reconciliation?tab=settlement')
    expect(byLabel('应收单').jumpPath).toBe('/finance/business-doc/detail?id=3')
    expect(byLabel('关联发票').jumpPath).toBe('/tax/output-invoice')
    expect(byLabel('会计凭证').jumpPath).toBe('/finance/voucher/detail?id=5')
  })

  // ===== 维度 3: 操作轨迹节点不可点击（jumpPath 为 null）=====
  it('操作轨迹节点 jumpPath 为 null', async () => {
    const { getReconciliationTrace } = await import('@/api/modules/reconciliation')
    vi.mocked(getReconciliationTrace).mockResolvedValue(mockTrace() as any)

    const wrapper = shallowMount(ReconciliationTimeline, { props: { settlementId: 1 } })
    await nextTick()
    await nextTick()

    const nodes = (wrapper.vm as any).timelineNodes
    const trailNodes = nodes.filter((n: any) => n.label === '创建核销' || n.label === '审核通过')
    expect(trailNodes).toHaveLength(2)
    trailNodes.forEach((n: any) => expect(n.jumpPath).toBeNull())
  })

  // ===== 维度 4: 点击跳转（BDD 场景 6 — 点击节点 emit jump）=====
  it('点击业务节点 emit jump 事件', async () => {
    const { getReconciliationTrace } = await import('@/api/modules/reconciliation')
    vi.mocked(getReconciliationTrace).mockResolvedValue(mockTrace() as any)

    const wrapper = shallowMount(ReconciliationTimeline, { props: { settlementId: 1 } })
    await nextTick()
    await nextTick()

    const nodes = (wrapper.vm as any).timelineNodes
    const receiptNode = nodes.find((n: any) => n.label === '收款单')
    ;(wrapper.vm as any).onNodeClick(receiptNode)
    expect(wrapper.emitted('jump')?.[0]).toEqual(['/finance/business-doc/detail?id=2'])
  })

  // ===== 维度 5: 操作轨迹点击不 emit（负向断言）=====
  it('点击操作轨迹节点不 emit jump', async () => {
    const { getReconciliationTrace } = await import('@/api/modules/reconciliation')
    vi.mocked(getReconciliationTrace).mockResolvedValue(mockTrace() as any)

    const wrapper = shallowMount(ReconciliationTimeline, { props: { settlementId: 1 } })
    await nextTick()
    await nextTick()

    const nodes = (wrapper.vm as any).timelineNodes
    const trailNode = nodes.find((n: any) => n.label === '审核通过')
    ;(wrapper.vm as any).onNodeClick(trailNode)
    expect(wrapper.emitted('jump')).toBeUndefined()
  })
})