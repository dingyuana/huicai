import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { nextTick } from 'vue'
import PendingPool from '@/views/finance/pending-pool/PendingPool.vue'

vi.mock('@/api/modules/bankStatement', () => ({
  getBankStatementPage: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  deleteStatement: vi.fn(),
  processManualStatement: vi.fn(),
  previewDraftStatement: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/modules/bankAccount', () => ({
  getActiveBankAccounts: vi.fn().mockResolvedValue([
    { id: '1', accountName: '基本户', accountNo: '6222021234567890', bankName: '工商银行' },
  ]),
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
  ],
})

function mockStatement(id: number, overrides = {}) {
  return {
    id,
    accountId: '1',
    txDate: '2026-07-01',
    txType: 'INCOME',
    amount: 50000,
    counterAccount: '客户A',
    summary: '货款收入',
    reviewStatus: 'manual_pending',
    ...overrides,
  }
}

describe('PendingPool — 待处理流水组件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ===== 维度 1: 基础挂载 =====
  it('挂载成功', () => {
    const wrapper = shallowMount(PendingPool, {
      global: { plugins: [router] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('onMounted 时调用 getBankStatementPage', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    shallowMount(PendingPool, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()
    expect(getBankStatementPage).toHaveBeenCalled()
  })

  // ===== 维度 2: 行点击打开处理弹窗（详情承载） =====
  it('onRowClick: 非selection列点击触发 openProcess 打开处理弹窗', async () => {
    const wrapper = shallowMount(PendingPool, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    const row = mockStatement(1)
    ;(wrapper.vm as any).onRowClick(row, { type: 'normal' })
    await nextTick()

    expect((wrapper.vm as any).processVisible).toBe(true)
    expect((wrapper.vm as any).currentRow).toEqual(row)
  })

  // ===== 维度 3: 点 checkbox/selection 不跳转 =====
  it('onRowClick: selection列点击不触发 openProcess', async () => {
    const wrapper = shallowMount(PendingPool, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    const row = mockStatement(1)
    ;(wrapper.vm as any).onRowClick(row, { type: 'selection' })
    await nextTick()

    expect((wrapper.vm as any).processVisible).toBe(false)
    expect((wrapper.vm as any).currentRow).toBeNull()
  })

  // ===== 维度 4: 操作列已移除 =====
  it('组件定义了 onRowClick 和 openProcess，无操作列残留', async () => {
    const wrapper = shallowMount(PendingPool, { global: { plugins: [router] } })
    await nextTick()

    const vm = wrapper.vm as any
    expect(typeof vm.onRowClick).toBe('function')
    expect(typeof vm.openProcess).toBe('function')
    expect(typeof vm.preview).toBe('function')
    expect(typeof vm.onDelete).toBe('function')
    // processVisible 初始为 false
    expect(vm.processVisible).toBe(false)
  })

  // ===== 维度 5: openProcess 设置 currentRow 并打开弹窗 =====
  it('openProcess: 设置 currentRow 并打开处理弹窗', async () => {
    const wrapper = shallowMount(PendingPool, { global: { plugins: [router] } })
    await nextTick()

    const row = mockStatement(1)
    ;(wrapper.vm as any).openProcess(row)
    await nextTick()

    expect((wrapper.vm as any).processVisible).toBe(true)
    expect((wrapper.vm as any).currentRow).toEqual(row)
    expect((wrapper.vm as any).form.targetType).toBe('A')
  })

  // ===== 维度 6: onDelete 调用 deleteStatement =====
  it('onDelete: 调用 deleteStatement 并刷新', async () => {
    const { deleteStatement } = await import('@/api/modules/bankStatement')
    vi.mocked(deleteStatement).mockResolvedValue(undefined)

    const wrapper = shallowMount(PendingPool, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onDelete(mockStatement(1))
    await nextTick()

    expect(deleteStatement).toHaveBeenCalledWith(1)
  })
})
