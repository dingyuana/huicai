import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import DashboardView from '@/views/dashboard/DashboardView.vue'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/api/modules/bankStatement', () => ({
  getPendingSettlementCount: vi.fn().mockResolvedValue(0),
}))

const slotStubs = {
  ElRow: { template: '<div><slot /></div>' },
  ElCol: { template: '<div><slot /></div>' },
  ElCard: { template: '<div><slot /></div>' },
  ElSpace: { template: '<div><slot /></div>' },
  ElButton: { template: '<button><slot /></button>' },
}

function mountDashboard() {
  return shallowMount(DashboardView, { global: { stubs: slotStubs } })
}

describe('DashboardView — 待核销提醒卡片', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载时拉取待核销数并渲染卡片', async () => {
    const { getPendingSettlementCount } = await import('@/api/modules/bankStatement')
    vi.mocked(getPendingSettlementCount).mockResolvedValue(5)

    const wrapper = mountDashboard()
    await nextTick()
    await nextTick()

    expect(getPendingSettlementCount).toHaveBeenCalled()
    expect(wrapper.text()).toContain('待核销业务单据')
    expect(wrapper.text()).toContain('5 条')
  })

  it('接口失败时卡片显示占位符', async () => {
    const { getPendingSettlementCount } = await import('@/api/modules/bankStatement')
    vi.mocked(getPendingSettlementCount).mockRejectedValue(new Error('network'))

    const wrapper = mountDashboard()
    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('待核销业务单据')
    expect(wrapper.text()).toContain('-- 条')
  })

  it('点击卡片跳转银行流水页并带 reviewStatus=payment_created', async () => {
    const wrapper = mountDashboard()
    await nextTick()
    await nextTick()

    await wrapper.find('.clickable-card').trigger('click')
    expect(pushMock).toHaveBeenCalledWith({
      path: '/finance/bank-statement',
      query: { reviewStatus: 'payment_created' },
    })
  })
})
