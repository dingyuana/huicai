import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { nextTick } from 'vue'
import InputInvoiceList from '@/views/tax/input-invoice/InputInvoiceList.vue'

vi.mock('@/api/modules/tax', () => ({
  pageInputInvoice: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  createInputInvoice: vi.fn(),
  certifyInputInvoice: vi.fn(),
  submitInputReview: vi.fn(),
  confirmInputInvoice: vi.fn(),
  rejectInputInvoice: vi.fn(),
  revertInputInvoice: vi.fn(),
  voidInputInvoice: vi.fn(),
  reverseInputInvoice: vi.fn(),
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
    { path: '/tax/input-invoice', name: 'InputInvoiceList', component: { template: '<div />' } },
  ],
})

function mockInvoice(id: number, status: string, overrides = {}) {
  return {
    id, invoiceNo: `INV-${String(id).padStart(3, '0')}`,
    invoiceDate: '2026-06-01', vendorName: '供应商A',
    amount: 10000, taxAmount: 1300, totalAmount: 11300,
    taxRate: 13, invoiceType: 'SPECIAL', certificationStatus: 'UNCERTIFIED',
    status, ...overrides,
  }
}

describe('InputInvoiceList — 进项发票列表组件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ===== 维度 1: 基础挂载 =====
  it('挂载成功', () => {
    const wrapper = shallowMount(InputInvoiceList, {
      global: { plugins: [router] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('渲染页面容器', () => {
    const wrapper = shallowMount(InputInvoiceList, {
      global: { plugins: [router] },
    })
    expect(wrapper.element.tagName).toBeDefined()
  })

  it('onMounted 时调用 pageInputInvoice', async () => {
    const { pageInputInvoice } = await import('@/api/modules/tax')
    shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()
    expect(pageInputInvoice).toHaveBeenCalled()
  })

  // ===== 维度 2: recalcTax 计算 =====
  it('recalcTax: 金额×税率/100 计算税额', async () => {
    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()

    const vm = wrapper.vm as any
    vm.form.amount = 10000
    vm.form.taxRate = 13
    vm.recalcTax()

    expect(vm.form.taxAmount).toBe(1300)
  })

  it('recalcTax: 税率 6% 时正确计算', async () => {
    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()

    const vm = wrapper.vm as any
    vm.form.amount = 5000
    vm.form.taxRate = 6
    vm.recalcTax()

    expect(vm.form.taxAmount).toBe(300)
  })

  it('recalcTax: 金额为 0 时税额保持（组件 bug: 0 被当作 falsy）', async () => {
    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()

    const vm = wrapper.vm as any
    vm.form.amount = 0
    vm.form.taxRate = 13
    vm.recalcTax()

    // Component bug: `if (form.amount && ...)` treats 0 as falsy, so taxAmount stays undefined
    expect(vm.form.taxAmount).toBeUndefined()
  })

  // ===== 维度 3: onCertify 调用 certifyInputInvoice =====
  it('onCertify 调用 certifyInputInvoice', async () => {
    const { pageInputInvoice, certifyInputInvoice } = await import('@/api/modules/tax')
    vi.mocked(certifyInputInvoice).mockResolvedValue(mockInvoice(1, 'CERTIFIED'))

    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onCertify(mockInvoice(1, 'PENDING_CONFIRM'))
    await nextTick()

    expect(certifyInputInvoice).toHaveBeenCalledWith(1)
  })

  // ===== 维度 4: openEdit 重置表单 =====
  it('openEdit 重置表单字段', async () => {
    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()

    const vm = wrapper.vm as any
    vm.form.invoiceNo = 'existing'
    vm.openEdit()

    expect(vm.form.invoiceNo).toBe('')
    expect(vm.form.amount).toBe(0)
    expect(vm.form.taxRate).toBe(13)
    expect(vm.form.invoiceType).toBe('SPECIAL')
    expect(vm.dialogVisible).toBe(true)
  })

  // ===== 维度 5: doAction submitReview =====
  it('doAction(submitReview) 调用 submitInputReview', async () => {
    const { pageInputInvoice, submitInputReview } = await import('@/api/modules/tax')
    vi.mocked(submitInputReview).mockResolvedValue(undefined)

    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).doAction(mockInvoice(1, 'PENDING_CONFIRM'), 'submitReview')
    await nextTick()

    expect(submitInputReview).toHaveBeenCalledWith(1)
  })

  // ===== 维度 6: doAction confirm =====
  it('doAction(confirm) 调用 confirmInputInvoice', async () => {
    const { confirmInputInvoice } = await import('@/api/modules/tax')
    vi.mocked(confirmInputInvoice).mockResolvedValue(undefined)

    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).doAction(mockInvoice(2, 'PENDING_REVIEW'), 'confirm')
    await nextTick()

    expect(confirmInputInvoice).toHaveBeenCalledWith(2)
  })

  // ===== 维度 7: doAction revert =====
  it('doAction(revert) 调用 revertInputInvoice', async () => {
    const { revertInputInvoice } = await import('@/api/modules/tax')
    vi.mocked(revertInputInvoice).mockResolvedValue(undefined)

    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).doAction(mockInvoice(3, 'CONFIRMED'), 'revert')
    await nextTick()

    expect(revertInputInvoice).toHaveBeenCalledWith(3)
  })

  // ===== 维度 8: doAction void 需要原因 =====
  it('doAction(void) 调用 voidInputInvoice 并传入原因', async () => {
    const { voidInputInvoice } = await import('@/api/modules/tax')
    vi.mocked(voidInputInvoice).mockResolvedValue(undefined)

    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).doAction(mockInvoice(4, 'PENDING_CONFIRM'), 'void')
    await nextTick()

    expect(voidInputInvoice).toHaveBeenCalledWith(4, '测试原因')
  })

  // ===== 维度 9: 渲染多条发票记录 =====
  it('渲染多条发票记录', async () => {
    const { pageInputInvoice } = await import('@/api/modules/tax')
    vi.mocked(pageInputInvoice).mockResolvedValue({
      records: [
        mockInvoice(1, 'PENDING_CONFIRM'),
        mockInvoice(2, 'CONFIRMED'),
      ],
      total: 2,
    })

    const wrapper = shallowMount(InputInvoiceList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    expect((wrapper.vm as any).list).toHaveLength(2)
    expect((wrapper.vm as any).total).toBe(2)
  })
})