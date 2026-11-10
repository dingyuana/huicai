import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia } from 'pinia'
import { nextTick } from 'vue'
import BankStatementView from '@/views/finance/bank-statement/BankStatementView.vue'

vi.mock('@/api/modules/bankStatement', () => ({
  getBankStatementPage: vi.fn().mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 }),
  getClassificationCounts: vi.fn().mockResolvedValue({}),
  getBankStatementGroupedByCategory: vi.fn().mockResolvedValue([]),
  classifyStatement: vi.fn(),
  reviewStatement: vi.fn(),
  auditStatement: vi.fn(),
  approveStatement: vi.fn(),
  batchConfirmStatements: vi.fn(),
  batchAuditStatements: vi.fn(),
  deleteStatement: vi.fn(),
  importStatementCsv: vi.fn(),
  getBankStatementDetail: vi.fn(),
  updateStatementClassification: vi.fn(),
  CLASSIFICATION_LABELS: {
    revenue: '营业收入',
    expense: '营业支出',
    salary: '工资薪酬',
    tax: '税费',
    other_income: '其他收入',
    other_expense: '其他支出',
  },
  REVIEW_STATUS_LABELS: {
    PENDING: '待确认',
    classified: '已分类',
    CONFIRMED: '已确认',
    approved: '已核准',
    voucher_generated: '已生成凭证',
    payment_created: '已生成付款单',
    manual_pending: '手工处理',
  },
}))

vi.mock('@/api/modules/bankAccount', () => ({
  getActiveBankAccounts: vi.fn().mockResolvedValue([
    { id: '1', accountName: '基本户', accountNo: '6222021234567890', bankName: '工商银行' },
  ]),
}))

vi.mock('@/api/modules/agency', () => ({
  getCurrentPeriod: vi.fn().mockResolvedValue({ currentPeriod: '202610' }),
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
    { path: '/finance/bank-statement', name: 'BankStatementView', component: { template: '<div />' } },
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
    matchStatus: 'MATCHED',
    reviewStatus: 'PENDING',
    classification: '',
    ...overrides,
  }
}

describe('BankStatementView — 银行流水组件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ===== 维度 1: 基础挂载 =====
  it('挂载成功', () => {
    const wrapper = shallowMount(BankStatementView, {
      global: { plugins: [router, createPinia()] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('包含页面容器', () => {
    const wrapper = shallowMount(BankStatementView, {
      global: { plugins: [router, createPinia()] },
    })
    expect(wrapper.find('.bank-statement').exists()).toBe(true)
  })

  it('onMounted 时调用 getBankStatementGroupedByCategory（默认当前账期）', async () => {
    const { getBankStatementGroupedByCategory } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementGroupedByCategory).mockResolvedValue([])

    shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()
    await nextTick()

    // SPEC-P151：默认按分类分组 + 默认账期取企业当前期间（mock 为 202610）
    expect(getBankStatementGroupedByCategory).toHaveBeenCalledWith(
      expect.objectContaining({ period: '202610', scope: 'all' })
    )
  })

  // ===== 维度 2: canReview 逻辑 =====
  it('canReview: 已分类+待确认状态返回 true', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()

    const row = mockStatement(1, { classification: 'revenue', reviewStatus: 'PENDING' })
    expect((wrapper.vm as any).canReview(row)).toBe(true)
  })

  it('canReview: 未分类返回 false', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()

    const row = mockStatement(1, { classification: '', reviewStatus: 'PENDING' })
    expect((wrapper.vm as any).canReview(row)).toBeFalsy()
  })

  it('canReview: 已确认状态返回 false', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()

    const row = mockStatement(1, { classification: 'revenue', reviewStatus: 'CONFIRMED' })
    expect((wrapper.vm as any).canReview(row)).toBeFalsy()
  })

  // ===== 维度 3: canAudit 逻辑 =====
  it('canAudit: 已确认+未生成凭证返回 true', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()

    const row = mockStatement(1, { reviewStatus: 'CONFIRMED', generatedVoucherNo: null, generatedDocNo: null })
    expect((wrapper.vm as any).canAudit(row)).toBe(true)
  })

  it('canAudit: 已生成凭证返回 false', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()

    const row = mockStatement(1, { reviewStatus: 'CONFIRMED', generatedVoucherNo: 'JZ-202607-0001' })
    expect((wrapper.vm as any).canAudit(row)).toBe(false)
  })

  // ===== 维度 4: canApprove 逻辑 =====
  it('canApprove: voucher_generated 状态返回 true', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()

    const row = mockStatement(1, { reviewStatus: 'voucher_generated' })
    expect((wrapper.vm as any).canApprove(row)).toBe(true)
  })

  it('canApprove: PENDING 状态返回 false', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()

    const row = mockStatement(1, { reviewStatus: 'PENDING' })
    expect((wrapper.vm as any).canApprove(row)).toBe(false)
  })

  // ===== 维度 5: onClassify 调用 API =====
  it('onClassify 调用 classifyStatement', async () => {
    const { getBankStatementPage, getClassificationCounts, classifyStatement } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })
    vi.mocked(getClassificationCounts).mockResolvedValue({})
    vi.mocked(classifyStatement).mockResolvedValue(mockStatement(1))

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onClassify(mockStatement(1))
    await nextTick()

    expect(classifyStatement).toHaveBeenCalledWith(1)
  })

  // ===== 维度 6: onReview 调用 reviewStatement =====
  it('onReview 调用 reviewStatement', async () => {
    const { getBankStatementPage, getClassificationCounts, reviewStatement } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })
    vi.mocked(getClassificationCounts).mockResolvedValue({})
    vi.mocked(reviewStatement).mockResolvedValue(mockStatement(1, { classification: 'revenue' }))

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onReview(mockStatement(1, { classification: 'revenue' }))
    await nextTick()

    expect(reviewStatement).toHaveBeenCalledWith(1)
  })

  // ===== 维度 7: onDelete 调用 deleteStatement =====
  it('onDelete 调用 deleteStatement', async () => {
    const { getBankStatementPage, getClassificationCounts, deleteStatement } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })
    vi.mocked(getClassificationCounts).mockResolvedValue({})
    vi.mocked(deleteStatement).mockResolvedValue(undefined)

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onDelete(mockStatement(1))
    await nextTick()

    expect(deleteStatement).toHaveBeenCalledWith(1)
  })

  // ===== 维度 8: 流水包含收入/支出类型 =====
  it('分类视图返回数据包含收入/支出类型', async () => {
    const { getBankStatementGroupedByCategory } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementGroupedByCategory).mockResolvedValue([
      {
        classification: 'revenue',
        count: 2,
        income: 50000,
        expense: 0,
        items: [
          mockStatement(1, { txType: 'INCOME' }),
          mockStatement(2, { txType: 'EXPENSE' }),
        ] as any,
      },
    ] as any)

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    // onMounted 内是「resolveDefaultPeriod → 账户 → 分组查询」串行链，nextTick 数不固定，用宏任务 flush
    await new Promise((r) => setTimeout(r))

    const groups = (wrapper.vm as any).categoryGroups
    const txTypes = groups.flatMap((g: any) => g.items.map((r: any) => r.txType))
    expect(txTypes).toContain('INCOME')
    expect(txTypes).toContain('EXPENSE')
  })

  // ===== 维度 9: 自动选择唯一账户 =====
  it('只有一个账户时自动选中', async () => {
    const { getBankStatementPage } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementPage).mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 })

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()
    await nextTick()
    await nextTick()

    expect((wrapper.vm as any).query.accountId).toBe('1')
  })

  // ===== 维度 10: route.query.reviewStatus 初始过滤 =====
  it('route.query.reviewStatus 挂载时应用为过滤条件', async () => {
    const { getBankStatementGroupedByCategory } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementGroupedByCategory).mockResolvedValue([])

    await router.push({ path: '/finance/bank-statement', query: { reviewStatus: 'payment_created' } })
    shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()
    await nextTick()

    expect(getBankStatementGroupedByCategory).toHaveBeenCalledWith(
      expect.objectContaining({ reviewStatus: 'payment_created', scope: 'all' })
    )
    await router.replace({ path: '/finance/bank-statement' })
  })

  // ===== 维度 11: SPEC-P151 账期口径 =====
  it('默认账期 = 企业当前期间，且展开为月首月末传给后端', async () => {
    const { getBankStatementGroupedByCategory } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementGroupedByCategory).mockResolvedValue([])

    shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()
    await nextTick()

    // mock 的 currentPeriod=202610 → period 与日期区间同口径下发
    expect(getBankStatementGroupedByCategory).toHaveBeenCalledWith(
      expect.objectContaining({ period: '202610', startDate: '2026-10-01', endDate: '2026-10-31' })
    )
  })

  it('clearPeriod 后不再带 period（全量口径）', async () => {
    const { getBankStatementGroupedByCategory } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementGroupedByCategory).mockResolvedValue([])

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()
    await nextTick()

    vi.mocked(getBankStatementGroupedByCategory).mockClear()
    await (wrapper.vm as any).clearPeriod()
    await nextTick()

    expect(getBankStatementGroupedByCategory).toHaveBeenCalledWith(
      expect.not.objectContaining({ period: expect.anything() })
    )
  })

  it('onReset 重置回默认账期而不是全量', async () => {
    const { getBankStatementGroupedByCategory } = await import('@/api/modules/bankStatement')
    vi.mocked(getBankStatementGroupedByCategory).mockResolvedValue([])

    const wrapper = shallowMount(BankStatementView, { global: { plugins: [router, createPinia()] } })
    await nextTick()
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).clearPeriod()
    vi.mocked(getBankStatementGroupedByCategory).mockClear()
    await (wrapper.vm as any).onReset()
    await nextTick()

    expect(getBankStatementGroupedByCategory).toHaveBeenCalledWith(
      expect.objectContaining({ period: '202610' })
    )
  })
})