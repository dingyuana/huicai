import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import BalanceSummaryView from '@/views/arap/balance/BalanceSummaryView.vue'

vi.mock('@/utils/period', () => ({
  resolveDefaultPeriod: vi.fn().mockResolvedValue('202607'),
}))

vi.mock('@/api/modules/arap', () => ({
  getBalanceSummary: vi.fn().mockResolvedValue({
    period: '202607',
    consistent: true,
    receivableTotal: 1000,
    payableTotal: 800,
    receivables: [],
    payables: [],
  }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  }
})

describe('BalanceSummaryView — 应收应付余额汇总', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 维度 1: 默认期间挂载后自动查询
  it('挂载时用 resolveDefaultPeriod 的期间调用 getBalanceSummary', async () => {
    const { resolveDefaultPeriod } = await import('@/utils/period')
    vi.mocked(resolveDefaultPeriod).mockResolvedValue('202607')
    const { getBalanceSummary } = await import('@/api/modules/arap')

    shallowMount(BalanceSummaryView)
    await nextTick()
    await nextTick()

    expect(getBalanceSummary).toHaveBeenCalledWith({ period: '202607' })
  })

  // 维度 2: 切换应收/应付 Tab 不重新查询
  it('切换应收/应付 Tab 不触发 getBalanceSummary', async () => {
    const { getBalanceSummary } = await import('@/api/modules/arap')

    // 用 mount 让 el-tabs 真实渲染（shallowMount 的 el-card 桩不渲染插槽）
    const wrapper = mount(BalanceSummaryView)
    await nextTick()
    await nextTick()

    const callsBefore = vi.mocked(getBalanceSummary).mock.calls.length

    const tabs = wrapper.findComponent({ name: 'ElTabs' })
    expect(tabs.exists()).toBe(true)
    tabs.vm.$emit('update:modelValue', 'payable')
    await nextTick()

    expect(vi.mocked(getBalanceSummary).mock.calls.length).toBe(callsBefore)
  })

  // 维度 3: 期间选择器变更后自动查询
  it('期间选择器变更后用新期间调用 getBalanceSummary', async () => {
    const { getBalanceSummary } = await import('@/api/modules/arap')

    const wrapper = shallowMount(BalanceSummaryView)
    const vm = wrapper.vm as any
    await nextTick()
    await nextTick()
    vi.mocked(getBalanceSummary).mockClear()

    vm.query.period = '202603'
    vm.fetchSummary()
    await nextTick()
    await nextTick()

    expect(getBalanceSummary).toHaveBeenCalledWith({ period: '202603' })
  })

  // 维度 4: 上一期间 — 1 月回退到上年 12 月
  it('shiftPeriod(-1): 1 月回退到上年 12 月并自动查询', async () => {
    const { getBalanceSummary } = await import('@/api/modules/arap')

    const wrapper = shallowMount(BalanceSummaryView)
    const vm = wrapper.vm as any
    await nextTick()
    await nextTick()
    vi.mocked(getBalanceSummary).mockClear()

    vm.query.period = '202601'
    vm.shiftPeriod(-1)
    await nextTick()
    await nextTick()

    expect(vm.query.period).toBe('202512')
    expect(getBalanceSummary).toHaveBeenCalledWith({ period: '202512' })
  })

  // 维度 5: 下一期间 — 12 月前进到次年 1 月
  it('shiftPeriod(1): 12 月前进到次年 1 月并自动查询', async () => {
    const { getBalanceSummary } = await import('@/api/modules/arap')

    const wrapper = shallowMount(BalanceSummaryView)
    const vm = wrapper.vm as any
    await nextTick()
    await nextTick()
    vi.mocked(getBalanceSummary).mockClear()

    vm.query.period = '202612'
    vm.shiftPeriod(1)
    await nextTick()
    await nextTick()

    expect(vm.query.period).toBe('202701')
    expect(getBalanceSummary).toHaveBeenCalledWith({ period: '202701' })
  })

  // 维度 6: 非法期间（月份 13）不调用 API
  it('非法期间（月份 13）不调用 getBalanceSummary', async () => {
    const { getBalanceSummary } = await import('@/api/modules/arap')
    const { ElMessage } = await import('element-plus')

    const wrapper = shallowMount(BalanceSummaryView)
    const vm = wrapper.vm as any
    await nextTick()
    await nextTick()
    vi.mocked(getBalanceSummary).mockClear()

    vm.query.period = '202613'
    vm.fetchSummary()
    await nextTick()

    expect(getBalanceSummary).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })
})
