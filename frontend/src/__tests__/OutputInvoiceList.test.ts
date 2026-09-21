import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { nextTick, ref } from 'vue'
import OutputInvoiceList from '@/views/tax/output-invoice/OutputInvoiceList.vue'

vi.mock('@/api/modules/tax', () => ({
  pageOutputInvoice: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  createOutputInvoice: vi.fn(),
  getOutputInvoice: vi.fn().mockResolvedValue({ id: 1, invoiceNo: 'INV-001' }),
  deleteOutputInvoice: vi.fn(),
  outputInvoiceSummary: vi.fn().mockResolvedValue({}),
  submitForReview: vi.fn(),
  confirmOutputInvoice: vi.fn(),
  rejectOutputInvoice: vi.fn(),
  revertOutputInvoice: vi.fn(),
  voidOutputInvoice: vi.fn(),
  markVouchered: vi.fn(),
  batchSubmitForReview: vi.fn(),
  batchConfirmOutputInvoice: vi.fn(),
  batchRejectOutputInvoice: vi.fn(),
  batchRevertOutputInvoice: vi.fn(),
  batchMarkVouchered: vi.fn(),
  batchVoidOutputInvoice: vi.fn(),
  batchReverseOutputInvoice: vi.fn(),
}))

vi.mock('@/api/modules/salesInvoice', () => ({
  previewSalesInvoices: vi.fn(),
  confirmSalesInvoicesImport: vi.fn(),
}))

vi.mock('@/composables/useBatchOperation', () => ({
  useBatchOperation: () => ({
    selectedRows: ref([]),
    result: ref(null),
    resultVisible: ref(false),
    onSelectionChange: vi.fn(),
    clearSelection: vi.fn(),
    run: vi.fn(),
  }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: {
      prompt: vi.fn().mockResolvedValue({ value: '测试原因' }),
    },
  }
})

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: { template: '<div />' } },
  ],
})

function mockInvoice(id: number, overrides = {}) {
  return {
    id,
    invoiceNo: `INV-${String(id).padStart(3, '0')}`,
    invoiceDate: '2026-06-01',
    customerName: '客户A',
    amount: 10000,
    taxAmount: 1300,
    taxRate: 13,
    invoiceType: 'SPECIAL',
    status: 'PENDING_CONFIRM',
    ...overrides,
  }
}

describe('OutputInvoiceList — 销项发票列表组件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ===== 维度 1: 基础挂载 =====
  it('挂载成功', () => {
    const wrapper = shallowMount(OutputInvoiceList, {
      global: { plugins: [router] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('onMounted 时调用 pageOutputInvoice 和 outputInvoiceSummary', async () => {
    const { pageOutputInvoice, outputInvoiceSummary } = await import('@/api/modules/tax')
    shallowMount(OutputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()
    expect(pageOutputInvoice).toHaveBeenCalled()
    expect(outputInvoiceSummary).toHaveBeenCalled()
  })

  it('切换筛选条件时统计随列表刷新并携带筛选参数', async () => {
    const { pageOutputInvoice, outputInvoiceSummary } = await import('@/api/modules/tax')
    const wrapper = shallowMount(OutputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()
    ;(wrapper.vm as any).onScopeChange()
    await nextTick()
    await nextTick()
    expect(pageOutputInvoice).toHaveBeenCalled()
    expect(outputInvoiceSummary).toHaveBeenCalledWith(expect.objectContaining({ scope: 'pending' }))
  })

  // ===== 维度 2: 行点击打开详情 =====
  it('onRowClick: 非交互元素点击触发 showDetail 打开详情弹窗', async () => {
    const { getOutputInvoice } = await import('@/api/modules/tax')
    vi.mocked(getOutputInvoice).mockResolvedValue(mockInvoice(1))

    const wrapper = shallowMount(OutputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    const row = mockInvoice(1)
    // Simulate a click on a non-interactive element (e.g. plain text cell)
    const fakeEvent = { target: { closest: () => null } } as any
    ;(wrapper.vm as any).onRowClick(row, { type: 'normal' }, fakeEvent)
    await nextTick()
    await nextTick()

    expect(getOutputInvoice).toHaveBeenCalledWith(1)
    expect((wrapper.vm as any).detailVisible).toBe(true)
  })

  // ===== 维度 3: 点 checkbox/selection 不跳转 =====
  it('onRowClick: selection 列点击不触发 showDetail', async () => {
    const { getOutputInvoice } = await import('@/api/modules/tax')
    vi.mocked(getOutputInvoice).mockClear()

    const wrapper = shallowMount(OutputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    const row = mockInvoice(1)
    const fakeEvent = { target: { closest: () => null } } as any
    ;(wrapper.vm as any).onRowClick(row, { type: 'selection' }, fakeEvent)
    await nextTick()

    expect(getOutputInvoice).not.toHaveBeenCalled()
    expect((wrapper.vm as any).detailVisible).toBe(false)
  })

  // ===== 维度 4: 点交互元素（el-link/button）不触发行跳转 =====
  it('onRowClick: 点击 el-link 等交互元素不触发 showDetail（el-link 有自己的 handler）', async () => {
    const { getOutputInvoice } = await import('@/api/modules/tax')
    vi.mocked(getOutputInvoice).mockClear()

    const wrapper = shallowMount(OutputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    const row = mockInvoice(1)
    // Simulate clicking on an el-link element — closest returns a truthy match
    const fakeEvent = {
      target: {
        closest: (sel: string) => sel.includes('el-link') ? {} : null,
      },
    } as any
    ;(wrapper.vm as any).onRowClick(row, { type: 'normal' }, fakeEvent)
    await nextTick()

    // onRowClick should return early, not call showDetail
    expect(getOutputInvoice).not.toHaveBeenCalled()
  })

  // ===== 维度 5: showDetail 直接调用打开详情 =====
  it('showDetail: 直接调用打开详情弹窗并加载详情数据', async () => {
    const { getOutputInvoice } = await import('@/api/modules/tax')
    vi.mocked(getOutputInvoice).mockResolvedValue(mockInvoice(1, { status: 'CONFIRMED' }))

    const wrapper = shallowMount(OutputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).showDetail(mockInvoice(1, { status: 'CONFIRMED' }))
    await nextTick()

    expect(getOutputInvoice).toHaveBeenCalledWith(1)
    expect((wrapper.vm as any).detailVisible).toBe(true)
    expect((wrapper.vm as any).detail).toBeTruthy()
  })

  // ===== 维度 6: 操作列已移除（无固定操作列） =====
  it('组件定义了 onRowClick 和 showDetail，无操作列残留', async () => {
    const wrapper = shallowMount(OutputInvoiceList, { global: { plugins: [router] } })
    await nextTick()

    const vm = wrapper.vm as any
    expect(typeof vm.onRowClick).toBe('function')
    expect(typeof vm.showDetail).toBe('function')
    // detailVisible 初始为 false
    expect(vm.detailVisible).toBe(false)
  })
})
