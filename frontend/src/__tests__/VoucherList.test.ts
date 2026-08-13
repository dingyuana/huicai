import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { nextTick } from 'vue'
import VoucherList from '@/views/finance/voucher/VoucherList.vue'

// Mock API module — must match component's actual imports
vi.mock('@/api/modules/voucher', () => ({
  getVoucherPage: vi.fn(),
  getVoucherStats: vi.fn(),
  submitVoucher: vi.fn(),
  auditVoucher: vi.fn(),
  postVoucher: vi.fn(),
  deleteVoucher: vi.fn(),
  reverseVoucher: vi.fn(),
  rejectVoucher: vi.fn(),
  unpostVoucher: vi.fn(),
  batchSubmitVouchers: vi.fn(),
  batchAuditVouchers: vi.fn(),
  batchPostVouchers: vi.fn(),
  VOUCHER_STATUS_MAP: { DRAFT: '草稿', SUBMITTED: '已提交', AUDITED: '已审核', POSTED: '已记账' },
  VOUCHER_STATUS_OPTIONS: [
    { value: 'DRAFT', label: '草稿' },
    { value: 'SUBMITTED', label: '已提交' },
    { value: 'AUDITED', label: '已审核' },
    { value: 'POSTED', label: '已记账' },
  ],
}))

// Mock element-plus ElMessage
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
    { path: '/finance/voucher', name: 'VoucherList', component: { template: '<div />' } },
    { path: '/finance/voucher/edit', name: 'VoucherEdit', component: { template: '<div />' } },
    { path: '/finance/voucher/detail', name: 'VoucherDetail', component: { template: '<div />' } },
  ],
})

function mockPageResponse(records: any[] = [], total = 0) {
  return { records, total, page: 1, size: 20, pages: Math.ceil(total / 20) }
}

function mockVoucher(id: number, status: string, overrides = {}) {
  return {
    id, voucherNo: `JZ-202607-${String(id).padStart(4, '0')}`,
    voucherTypeName: '记账', status, period: '202607',
    totalDebit: 10000, totalCredit: 10000,
    createdAt: '2026-07-01', createdByName: '管理员',
    summary: `测试凭证${id}`,
    ...overrides,
  }
}

describe('VoucherList — 凭证列表组件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ===== 维度 1: 基础挂载 =====
  it('挂载成功', () => {
    const wrapper = shallowMount(VoucherList, {
      global: { plugins: [router] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('包含页面标题', () => {
    const wrapper = shallowMount(VoucherList, {
      global: { plugins: [router] },
    })
    expect(wrapper.find('.voucher-list').exists()).toBe(true)
  })

  // ===== 维度 2: API 调用 =====
  it('onMounted 时调用 getVoucherPage', async () => {
    const { getVoucherPage } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))

    shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    expect(getVoucherPage).toHaveBeenCalled()
  })

  it('分页查询携带正确参数', async () => {
    const { getVoucherPage } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))

    shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    expect(getVoucherPage).toHaveBeenCalledWith(expect.objectContaining({
      current: 1,
      size: 20,
    }))
  })

  // ===== 维度 3: 列表渲染 =====
  it('渲染多条凭证记录', async () => {
    const { getVoucherPage } = await import('@/api/modules/voucher')
    const mockRecords = [
      mockVoucher(1, 'DRAFT'),
      mockVoucher(2, 'SUBMITTED'),
      mockVoucher(3, 'AUDITED'),
      mockVoucher(4, 'POSTED'),
    ]
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse(mockRecords, 4))

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    // Verify vm has the data loaded
    expect((wrapper.vm as any).list).toHaveLength(4)
    expect((wrapper.vm as any).total).toBe(4)
  })

  // ===== 维度 4: 状态机 — 提交操作 =====
  it('onSubmit(DRAFT) 调用 submitVoucher', async () => {
    const { getVoucherPage, submitVoucher } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([mockVoucher(1, 'DRAFT')], 1))
    vi.mocked(submitVoucher).mockResolvedValue(undefined)

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    const callCountBefore = vi.mocked(getVoucherPage).mock.calls.length
    await (wrapper.vm as any).onSubmit(mockVoucher(1, 'DRAFT'))
    await nextTick()

    expect(submitVoucher).toHaveBeenCalledWith(1)
    // Should refresh after submit (call count increased)
    expect(vi.mocked(getVoucherPage).mock.calls.length).toBeGreaterThan(callCountBefore)
  })

  // ===== 维度 5: 状态机 — 审核操作 =====
  it('onSubmit(SUBMITTED) 调用 auditVoucher', async () => {
    const { getVoucherPage, auditVoucher } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([mockVoucher(2, 'SUBMITTED')], 1))
    vi.mocked(auditVoucher).mockResolvedValue(undefined)

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onAudit(mockVoucher(2, 'SUBMITTED'))
    await nextTick()

    expect(auditVoucher).toHaveBeenCalledWith(2)
  })

  // ===== 维度 6: 状态机 — 记账操作 =====
  it('onPost(AUDITED) 调用 postVoucher', async () => {
    const { getVoucherPage, postVoucher } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([mockVoucher(3, 'AUDITED')], 1))
    vi.mocked(postVoucher).mockResolvedValue(undefined)

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onPost(mockVoucher(3, 'AUDITED'))
    await nextTick()

    expect(postVoucher).toHaveBeenCalledWith(3)
  })

  // ===== 维度 7: 状态机 — 删除操作 =====
  it('onDelete(DRAFT) 调用 deleteVoucher', async () => {
    const { getVoucherPage, deleteVoucher } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([mockVoucher(1, 'DRAFT')], 1))
    vi.mocked(deleteVoucher).mockResolvedValue(undefined)

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onDelete(mockVoucher(1, 'DRAFT'))
    await nextTick()

    expect(deleteVoucher).toHaveBeenCalledWith(1)
  })

  // ===== 维度 8: 状态机 — 反过账操作 =====
  it('onUnpost(POSTED) 调用 unpostVoucher', async () => {
    const { getVoucherPage, unpostVoucher } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([mockVoucher(4, 'POSTED')], 1))
    vi.mocked(unpostVoucher).mockResolvedValue(undefined)

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onUnpost(mockVoucher(4, 'POSTED'))
    await nextTick()

    expect(unpostVoucher).toHaveBeenCalledWith(4)
  })

  // ===== 维度 9: 状态机 — 红冲操作 =====
  it('onReverse(POSTED) 调用 reverseVoucher', async () => {
    const { getVoucherPage, reverseVoucher } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([mockVoucher(4, 'POSTED')], 1))
    vi.mocked(reverseVoucher).mockResolvedValue({
      id: 5, voucherNo: 'JZ-202607-0005', status: 'DRAFT',
    } as any)

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()
    await nextTick()

    await (wrapper.vm as any).onReverse(mockVoucher(4, 'POSTED'))
    await nextTick()

    expect(reverseVoucher).toHaveBeenCalledWith(4)
  })

  // ===== 维度 10: 批量操作 — canBatchSubmit =====
  it('选择 DRAFT 凭证后 canBatchSubmit 为 true', async () => {
    const { getVoucherPage } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()

    // Simulate selecting a DRAFT row
    const draftRow = mockVoucher(1, 'DRAFT')
    ;(wrapper.vm as any).onSelectionChange([draftRow])

    expect((wrapper.vm as any).canBatchSubmit).toBe(true)
    expect((wrapper.vm as any).canBatchAudit).toBe(false)
    expect((wrapper.vm as any).canBatchPost).toBe(false)
  })

  // ===== 维度 11: 批量操作 — canBatchAudit =====
  it('选择 SUBMITTED 凭证后 canBatchAudit 为 true', async () => {
    const { getVoucherPage } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()

    ;(wrapper.vm as any).onSelectionChange([mockVoucher(2, 'SUBMITTED')])

    expect((wrapper.vm as any).canBatchAudit).toBe(true)
    expect((wrapper.vm as any).canBatchSubmit).toBe(false)
    expect((wrapper.vm as any).canBatchPost).toBe(false)
  })

  // ===== 维度 12: 批量操作 — onBatchSubmit =====
  it('onBatchSubmit 调用 batchSubmitVouchers 并携带 DRAFT 凭证 ID', async () => {
    const { getVoucherPage, batchSubmitVouchers } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))
    vi.mocked(batchSubmitVouchers).mockResolvedValue(undefined)

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()

    ;(wrapper.vm as any).onSelectionChange([
      mockVoucher(1, 'DRAFT'),
      mockVoucher(2, 'SUBMITTED'),
    ])
    await (wrapper.vm as any).onBatchSubmit()
    await nextTick()

    expect(batchSubmitVouchers).toHaveBeenCalledWith({ ids: [1] })
  })

  // ===== 维度 13: 批量操作 — onBatchAudit =====
  it('onBatchAudit 调用 batchAuditVouchers 并携带 SUBMITTED 凭证 ID', async () => {
    const { getVoucherPage, batchAuditVouchers } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))
    vi.mocked(batchAuditVouchers).mockResolvedValue(undefined)

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()

    ;(wrapper.vm as any).onSelectionChange([
      mockVoucher(1, 'DRAFT'),
      mockVoucher(2, 'SUBMITTED'),
      mockVoucher(3, 'AUDITED'),
    ])
    await (wrapper.vm as any).onBatchAudit()
    await nextTick()

    expect(batchAuditVouchers).toHaveBeenCalledWith({ ids: [2] })
  })

  // ===== 维度 14: 分类标签切换 =====
  it('tabType 切换后重置 current 并重新查询', async () => {
    const { getVoucherPage } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()

    ;(wrapper.vm as any).tabType = 'DRAFT'
    ;(wrapper.vm as any).onTabChange()
    await nextTick()

    expect(getVoucherPage).toHaveBeenCalledWith(expect.objectContaining({
      status: 'DRAFT',
      current: 1,
    }))
  })

  // ===== 维度 15: 搜索功能 =====
  it('onSearch 重置 current 为 1 并重新查询', async () => {
    const { getVoucherPage } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()

    // Change query and search
    ;(wrapper.vm as any).query.period = '202607'
    ;(wrapper.vm as any).onSearch()
    await nextTick()

    expect(getVoucherPage).toHaveBeenCalledWith(expect.objectContaining({
      period: '202607',
      current: 1,
    }))
  })

  // ===== 维度 16: 重置功能 =====
  it('onReset 清空查询条件并重新查询', async () => {
    const { getVoucherPage } = await import('@/api/modules/voucher')
    vi.mocked(getVoucherPage).mockResolvedValue(mockPageResponse([], 0))

    const wrapper = shallowMount(VoucherList, { global: { plugins: [router] } })
    await nextTick()

    // Set some values
    ;(wrapper.vm as any).query.period = '202607'
    ;(wrapper.vm as any).query.keyword = 'test'
    ;(wrapper.vm as any).tabType = 'DRAFT'
    ;(wrapper.vm as any).onReset()
    await nextTick()

    expect((wrapper.vm as any).query.period).toBe('')
    expect((wrapper.vm as any).query.keyword).toBe('')
    expect((wrapper.vm as any).tabType).toBe('')
  })
})