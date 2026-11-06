import request from '@/api/request'

/**
 * SPEC-P143（REQ-2026-160）：工资薪酬 API。
 * 后端 SalaryItemController / SalaryBillController（salary:list/save/confirm/pay）。
 */

// ─── 薪资项目（D-4 可配置）───

export interface SalaryItem {
  id?: number
  itemCode: string
  itemName: string
  /** EARN 收入 / SOCIAL_INS 个人社保公积金 / SPECIAL_ADD 专项附加扣除 / OTHER_DEDUCT 其他扣款 */
  category: string
  defaultAmount?: number
  isTaxable?: boolean
  sortOrder?: number
  remark?: string
}

export function listSalaryItems(): Promise<SalaryItem[]> {
  return request.get('/sme/salary/v1/salary-items')
}

export function createSalaryItem(data: SalaryItem): Promise<number> {
  return request.post('/sme/salary/v1/salary-items', data)
}

export function updateSalaryItem(id: number, data: SalaryItem): Promise<void> {
  return request.put(`/sme/salary/v1/salary-items/${id}`, data)
}

export function deleteSalaryItem(id: number): Promise<void> {
  return request.delete(`/sme/salary/v1/salary-items/${id}`)
}

// ─── 工资单 ───

export interface SalaryBill {
  id?: number
  /** yyyyMM */
  period: string
  /** DRAFT / CONFIRMED / PAID */
  status?: string
  totalGross?: number
  totalSocial?: number
  totalSpecialAdd?: number
  totalOtherDeduct?: number
  totalTax?: number
  totalNet?: number
  voucherId?: number
  voucherNo?: string
  payVoucherId?: number
  payVoucherNo?: string
  remark?: string
}

/** 员工级汇总 + 个税快照（confirm 后生成） */
export interface SalaryBillEmployeeRow {
  employeeId: number
  employeeName?: string
  gross?: number
  taxableGross?: number
  social?: number
  specialAdd?: number
  otherDeduct?: number
  tax?: number
  net?: number
}

export interface SalaryBillDetail extends SalaryBill {
  employees: SalaryBillEmployeeRow[]
}

/** 原始明细行（纵表，编辑草稿回显） */
export interface SalaryBillItemRow {
  employeeId: number
  employeeName?: string
  itemId: number
  itemName?: string
  amount?: number
}

export function listSalaryBills(): Promise<SalaryBill[]> {
  return request.get('/sme/salary/v1/salary-bills')
}

export function getSalaryBill(id: number): Promise<SalaryBillDetail> {
  return request.get(`/sme/salary/v1/salary-bills/${id}`)
}

export function getSalaryBillItems(id: number): Promise<SalaryBillItemRow[]> {
  return request.get(`/sme/salary/v1/salary-bills/${id}/items`)
}

export function saveSalaryBill(data: {
  period: string
  remark?: string
  items: Array<{ employeeId: number; itemId: number; amount: number }>
}): Promise<number> {
  return request.post('/sme/salary/v1/salary-bills', data)
}

/** 确认（个税累计预扣 + 计提凭证 SALC-{id}） */
export function confirmSalaryBill(id: number): Promise<void> {
  return request.post(`/sme/salary/v1/salary-bills/${id}/confirm`)
}

/** 发放（发放凭证 SALP-{id}） */
export function paySalaryBill(id: number): Promise<void> {
  return request.post(`/sme/salary/v1/salary-bills/${id}/pay`)
}
