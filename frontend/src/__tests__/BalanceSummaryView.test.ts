import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import BalanceSummaryView from '@/views/arap/balance/BalanceSummaryView.vue'

vi.mock('@/api/modules/arap', () => ({
  getBalanceSummary: vi.fn().mockResolvedValue({
    period: '202607',
    consistent: true,
    receivableTotal: 1000,
    payableTotal: 800,
    receivables: [],
    payables: [],
  }),
  listPeriods: vi.fn().mockResolvedValue([
    { id: 1, year: 2026, month: 7, periodCode: '202607', status: 'open', openingStatus: 'locked' },
    { id: 2, year: 2026, month: 6, periodCode: '202606', status: 'open', openingStatus: 'locked' },
    { id: 3, year: 2026, month: 8, periodCode: '202608', status: 'open', openingStatus: 'none' },
  ]),
}))

vi.mock('vue-echarts', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    default: { name: 'VChart', props: ['option', 'autoresize'], render() { return null } },
  }
})

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

  // 维度 1: 挂载时加载期间列表并自动查询
  it('挂载时加载期间列表并用首个期间调用 getBalanceSummary', async () => {
    const { getBalanceSummary, listPeriods } = await import('@/api/modules/arap')

    shallowMount(BalanceSummaryView)
    await nextTick()
    await nextTick()

    expect(listPeriods).toHaveBeenCalled()
    expect(getBalanceSummary).toHaveBeenCalledWith({ period: '202607' })
  })

  // 维度 2: 切换应收/应付 Tab 不重新查询
  it('切换应收/应付 Tab 不触发 getBalanceSummary', async () => {
    const { getBalanceSummary } = await import('@/api/modules/arap')

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
    vm.onPeriodChange()
    await nextTick()
    await nextTick()

    expect(getBalanceSummary).toHaveBeenCalledWith({ period: '202603' })
  })

  // 维度 4: 非法期间（月份 13）不调用 API
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
