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

/** SPEC-P155 R1：缴纳社保（凭证 SALJ-{id}，幂等） */
export function paySocialSalaryBill(id: number): Promise<void> {
  return request.post(`/sme/salary/v1/salary-bills/${id}/social-pay`)
}

/** SPEC-P155 R3：工资条导出（xlsx blob 下载） */
export function exportPayroll(id: number): Promise<Blob> {
  return request.get(`/sme/salary/v1/salary-bills/${id}/export`, { responseType: 'blob' })
}

/** SPEC-P155 R3：个税申报表导出（xlsx blob 下载） */
export function exportTaxDeclaration(id: number): Promise<Blob> {
  return request.get(`/sme/salary/v1/salary-bills/${id}/tax-export`, { responseType: 'blob' })
}

// ─── SPEC-P155 R1：单位社保险种比例配置 ───

export interface SocialRate {
  id?: number
  itemId: number
  itemName?: string
  companyRate: number
  baseFloor?: number
  baseCap?: number
  remark?: string
}

export function listSocialRates(): Promise<SocialRate[]> {
  return request.get('/sme/salary/v1/social-rates')
}

export function saveSocialRate(data: {
  itemId: number
  companyRate: number
  baseFloor?: number
  baseCap?: number
  remark?: string
}): Promise<number> {
  return request.post('/sme/salary/v1/social-rates', data)
}

export function deleteSocialRate(id: number): Promise<void> {
  return request.delete(`/sme/salary/v1/social-rates/${id}`)
}

// ─── SPEC-P155 R2：部门→费用科目映射 ───

export interface DeptSubjectMap {
  id?: number
  deptId: number
  deptName?: string
  subjectId: number
  subjectCode?: string
  subjectName?: string
  remark?: string
}

export function listDeptSubjectMaps(): Promise<DeptSubjectMap[]> {
  return request.get('/sme/salary/v1/dept-subject-maps')
}

export function saveDeptSubjectMap(data: {
  deptId: number
  subjectId: number
  remark?: string
}): Promise<number> {
  return request.post('/sme/salary/v1/dept-subject-maps', data)
}

export function deleteDeptSubjectMap(id: number): Promise<void> {
  return request.delete(`/sme/salary/v1/dept-subject-maps/${id}`)
}

/** 部门列表（映射配置用，system 模块 re-export 简化引用） */
export function listDepts(): Promise<Array<{ id: number; name: string }>> {
  return request.get('/v1/system/dept/tree')
}
