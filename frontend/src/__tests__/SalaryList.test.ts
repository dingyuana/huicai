import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import SalaryList from '@/views/salary/SalaryList.vue'

vi.mock('@/api/modules/salary', () => ({
  listSalaryItems: vi.fn().mockResolvedValue([
    { id: 1, itemCode: 'BASIC', itemName: '基本工资', category: 'EARN', isTaxable: true },
    { id: 2, itemCode: 'SSI', itemName: '个人社保公积金', category: 'SOCIAL_INS', isTaxable: false },
  ]),
  createSalaryItem: vi.fn().mockResolvedValue(9),
  updateSalaryItem: vi.fn(),
  deleteSalaryItem: vi.fn(),
  listSalaryBills: vi.fn().mockResolvedValue([
    { id: 1, period: '202601', status: 'DRAFT', totalGross: 30000 },
    { id: 2, period: '202512', status: 'CONFIRMED', totalGross: 28000, voucherNo: 'SALC-2' },
    { id: 3, period: '202511', status: 'PAID', totalGross: 27000, voucherNo: 'SALC-3', payVoucherNo: 'SALP-3' },
  ]),
  getSalaryBill: vi.fn().mockResolvedValue({
    id: 1, period: '202601', status: 'CONFIRMED', totalGross: 30000, totalTax: 645, totalNet: 27355,
    employees: [{ employeeId: 1, employeeName: '张三', gross: 30000, tax: 645, net: 27355 }],
  }),
  getSalaryBillItems: vi.fn().mockResolvedValue([
    { employeeId: 1, employeeName: '张三', itemId: 1, itemName: '基本工资', amount: 30000 },
  ]),
  saveSalaryBill: vi.fn().mockResolvedValue(10),
  confirmSalaryBill: vi.fn().mockResolvedValue(undefined),
  paySalaryBill: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/api/modules/employee', () => ({
  listEmployee: vi.fn().mockResolvedValue([
    { id: 1, code: 'E001', name: '张三', isActive: true },
  ]),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue(undefined),
      prompt: vi.fn().mockResolvedValue({ value: '' }),
    },
  }
})

function mount() {
  return shallowMount(SalaryList)
}

describe('SalaryList — 工资单页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载成功且首屏加载工资单/项目/员工三份数据', async () => {
    const { listSalaryBills, listSalaryItems } = await import('@/api/modules/salary')
    const { listEmployee: listEmp } = await import('@/api/modules/employee')
    mount()
    await nextTick()
    await nextTick()
    expect(listSalaryBills).toHaveBeenCalled()
    expect(listSalaryItems).toHaveBeenCalled()
    expect(listEmp).toHaveBeenCalled()
  })

  it('onSaveBill — 期间格式非法时告警且不发请求', async () => {
    const { saveSalaryBill } = await import('@/api/modules/salary')
    const { ElMessage } = await import('element-plus')
    const wrapper = mount()
    await nextTick()
    ;(wrapper.vm as any).form.period = '2026-01'
    ;(wrapper.vm as any).form.rows = [{ employeeId: 1, itemId: 1, amount: 100 }]
    await (wrapper.vm as any).onSaveBill()
    expect(saveSalaryBill).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('onSaveBill — 空明细行告警且不发请求', async () => {
    const { saveSalaryBill } = await import('@/api/modules/salary')
    const wrapper = mount()
    await nextTick()
    ;(wrapper.vm as any).form.period = '202601'
    ;(wrapper.vm as any).form.rows = []
    await (wrapper.vm as any).onSaveBill()
    expect(saveSalaryBill).not.toHaveBeenCalled()
  })

  it('onSaveBill — 合法体整单替换保存并刷新列表', async () => {
    const { saveSalaryBill, listSalaryBills } = await import('@/api/modules/salary')
    const wrapper = mount()
    await nextTick()
    ;(wrapper.vm as any).form.period = '202602'
    ;(wrapper.vm as any).form.remark = '备注'
    ;(wrapper.vm as any).form.rows = [{ employeeId: 1, itemId: 1, amount: 30000 }]
    await (wrapper.vm as any).onSaveBill()
    expect(saveSalaryBill).toHaveBeenCalledWith({
      period: '202602',
      remark: '备注',
      items: [{ employeeId: 1, itemId: 1, amount: 30000 }],
    })
    expect(listSalaryBills).toHaveBeenCalledTimes(2) // 首屏 + 保存后
  })

  it('onConfirm — 确认计提：弹窗确认后调 confirmSalaryBill 并刷新', async () => {
    const { confirmSalaryBill, listSalaryBills } = await import('@/api/modules/salary')
    const wrapper = mount()
    await nextTick()
    await (wrapper.vm as any).onConfirm({ id: 2, period: '202512', status: 'DRAFT' })
    expect(confirmSalaryBill).toHaveBeenCalledWith(2)
    expect(listSalaryBills).toHaveBeenCalledTimes(2)
  })

  it('onConfirm — 弹窗取消则零调用', async () => {
    const { confirmSalaryBill } = await import('@/api/modules/salary')
    const { ElMessageBox } = await import('element-plus')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce(new Error('cancel'))
    const wrapper = mount()
    await nextTick()
    await (wrapper.vm as any).onConfirm({ id: 2, period: '202512', status: 'DRAFT' })
    expect(confirmSalaryBill).not.toHaveBeenCalled()
  })

  it('onPay — 发放：弹窗确认后调 paySalaryBill 并刷新', async () => {
    const { paySalaryBill } = await import('@/api/modules/salary')
    const wrapper = mount()
    await nextTick()
    await (wrapper.vm as any).onPay({ id: 2, period: '202512', status: 'CONFIRMED' })
    expect(paySalaryBill).toHaveBeenCalledWith(2)
  })

  it('openEdit — 编辑草稿时回显原始明细（防整单替换静默清空）', async () => {
    const { getSalaryBillItems } = await import('@/api/modules/salary')
    const wrapper = mount()
    await nextTick()
    await (wrapper.vm as any).openEdit({ id: 5, period: '202601', status: 'DRAFT' })
    expect(getSalaryBillItems).toHaveBeenCalledWith(5)
    expect((wrapper.vm as any).form.rows).toEqual([
      { employeeId: 1, itemId: 1, amount: 30000 },
    ])
  })

  it('onSaveItem — 缺编码/名称告警；合法时新增并刷新项目列表', async () => {
    const { createSalaryItem, listSalaryItems } = await import('@/api/modules/salary')
    const { ElMessage } = await import('element-plus')
    const wrapper = mount()
    await nextTick()
    ;(wrapper.vm as any).itemForm.itemCode = ''
    await (wrapper.vm as any).onSaveItem()
    expect(createSalaryItem).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()

    ;(wrapper.vm as any).itemForm.itemCode = 'Bonus'
    ;(wrapper.vm as any).itemForm.itemName = '奖金'
    await (wrapper.vm as any).onSaveItem()
    expect(createSalaryItem).toHaveBeenCalledWith(
      expect.objectContaining({ itemCode: 'Bonus', itemName: '奖金' }),
    )
    expect(listSalaryItems).toHaveBeenCalledTimes(2)
  })

  it('onDeleteItem — 弹窗确认后软删并刷新', async () => {
    const { deleteSalaryItem, listSalaryItems } = await import('@/api/modules/salary')
    const wrapper = mount()
    await nextTick()
    await (wrapper.vm as any).onDeleteItem({ id: 2, itemName: '个人社保公积金' })
    expect(deleteSalaryItem).toHaveBeenCalledWith(2)
    expect(listSalaryItems).toHaveBeenCalledTimes(2)
  })

  it('statusCn/statusTag — 状态映射三态', async () => {
    const wrapper = mount()
    await nextTick()
    const vm: any = wrapper.vm
    expect(vm.statusCn('DRAFT')).toBe('草稿')
    expect(vm.statusCn('CONFIRMED')).toBe('已确认')
    expect(vm.statusCn('PAID')).toBe('已发放')
    expect(vm.statusTag('DRAFT')).toBe('warning')
    expect(vm.statusTag('CONFIRMED')).toBe('primary')
    expect(vm.statusTag('PAID')).toBe('success')
  })
})
