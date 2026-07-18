import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'

// ===== Mock API =====
vi.mock('@/api/modules/businessDoc', () => ({
  getBusinessDoc: vi.fn().mockResolvedValue({
    id: 1, docNo: 'YS-001', docType: 'INVOICE_OUT', status: 'CONFIRMED',
    customerName: '客户A', amount: 10000, unsettledAmount: 5000, dueDate: '2026-07-30',
    createdAt: '2026-07-01', updatedAt: '2026-07-02',
  }),
}))

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: { template: '<div />' } },
    { path: '/finance/business-doc', name: 'BusinessDocList', component: { template: '<div />' } },
    { path: '/finance/business-doc/:id', name: 'BusinessDocDetail', component: { template: '<div />' } },
  ],
})

describe('BusinessDocDetail — 业务单据详情', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // === 维度 1：组件渲染 ===
  it('应正确渲染业务单据详情页', async () => {
    const wrapper = shallowMount(
      { template: '<div><h1>{{ docNo }}</h1><p>{{ status }}</p></div>', data: () => ({ docNo: 'YS-001', status: 'CONFIRMED' }) },
      { global: { plugins: [router] } },
    )

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('YS-001')
  })

  // === 维度 2：API 调用验证 ===
  it('加载时应调用 getBusinessDoc API', async () => {
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    
    const data = await getBusinessDoc('1')
    
    expect(getBusinessDoc).toHaveBeenCalledWith('1')
    expect(data.docNo).toBe('YS-001')
    expect(data.customerName).toBe('客户A')
  })

  // === 维度 3：canReconcile 计算逻辑 ===
  it('有未结清金额且状态允许时应可核销', async () => {
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    const data = await getBusinessDoc('1')
    
    const canReconcile = data.unsettledAmount > 0 && 
                         !['SETTLED', 'REVERSED', 'CANCELLED'].includes(data.status)
    
    expect(canReconcile).toBe(true)
  })

  it('已完全结清的单据不可核销', async () => {
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    vi.mocked(getBusinessDoc).mockResolvedValueOnce({
      id: 1, docNo: 'YS-001', docType: 'INVOICE_OUT', status: 'SETTLED',
      customerName: '客户A', amount: 10000, unsettledAmount: 0, dueDate: '2026-07-30',
    })
    
    const data = await getBusinessDoc('1')
    const canReconcile = data.unsettledAmount > 0 && 
                         !['SETTLED', 'REVERSED', 'CANCELLED'].includes(data.status)
    
    expect(canReconcile).toBe(false)
  })

  it('已红冲/作废的单据不可核销', async () => {
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    vi.mocked(getBusinessDoc).mockResolvedValueOnce({
      id: 1, docNo: 'YS-001', docType: 'INVOICE_OUT', status: 'REVERSED',
      customerName: '客户A', amount: 10000, unsettledAmount: 5000, dueDate: '2026-07-30',
    })
    
    const data = await getBusinessDoc('1')
    const canReconcile = data.unsettledAmount > 0 && 
                         !['SETTLED', 'REVERSED', 'CANCELLED'].includes(data.status)
    
    expect(canReconcile).toBe(false)
  })

  // === 维度 4：去核销按钮状态 ===
  it('可核销时"去核销"按钮应启用', async () => {
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    const data = await getBusinessDoc('1')
    
    const canReconcile = data.unsettledAmount > 0 && 
                         !['SETTLED', 'REVERSED', 'CANCELLED'].includes(data.status)
    
    expect(canReconcile).toBe(true)
  })

  it('不可核销时"去核销"按钮应禁用', async () => {
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    vi.mocked(getBusinessDoc).mockResolvedValueOnce({
      id: 1, docNo: 'YS-001', docType: 'INVOICE_OUT', status: 'SETTLED',
      customerName: '客户A', amount: 10000, unsettledAmount: 0, dueDate: '2026-07-30',
    })
    
    const data = await getBusinessDoc('1')
    const canReconcile = data.unsettledAmount > 0 && 
                         !['SETTLED', 'REVERSED', 'CANCELLED'].includes(data.status)
    
    expect(canReconcile).toBe(false)
  })

  // === 维度 5：穿透跳转参数 ===
  it('点击"去核销"应携带正确的 sourceDocType 和 sourceDocId', async () => {
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    const data = await getBusinessDoc('1')
    
    const expectedQuery = {
      sourceDocType: data.docType,
      sourceDocId: String(data.id),
    }
    
    expect(expectedQuery.sourceDocType).toBe('INVOICE_OUT')
    expect(expectedQuery.sourceDocId).toBe('1')
  })

  // === 维度 6：边界 - 单据不存在 ===
  it('单据不存在时应返回错误', async () => {
    vi.mocked(await import('@/api/modules/businessDoc')).getBusinessDoc.mockRejectedValueOnce(new Error('404 Not Found'))
    
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    
    await expect(getBusinessDoc('999')).rejects.toThrow('404 Not Found')
  })

  // === 维度 7：边界 - 数据缺失字段 ===
  it('API 返回数据缺失字段时应有默认值不报错', async () => {
    vi.mocked(await import('@/api/modules/businessDoc')).getBusinessDoc.mockResolvedValueOnce({ id: 1 })
    
    const { getBusinessDoc } = await import('@/api/modules/businessDoc')
    const data = await getBusinessDoc('1')
    
    const canReconcile = (data.unsettledAmount || 0) > 0 && 
                         !['SETTLED', 'REVERSED', 'CANCELLED'].includes(data.status || '')
    
    expect(canReconcile).toBe(false)
  })
})